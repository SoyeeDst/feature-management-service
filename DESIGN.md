# Feature Management Service — Design Document

**Date:** 2026-06-02
**Stack:** Java, Spring Cloud, MySQL, Debezium/Canal, Kafka, Redis, Prometheus, Grafana

## 1. Architecture Overview

```
┌──────────────────────────┐
│   Management UI          │
│   (React SPA)            │
└────────┬─────────────────┘
         │ REST API
┌────────▼──────────────────────────────────┐
│  Management Service                       │
│  (Spring Cloud)                           │
│  ┌──────────────────────────────────┐     │
│  │ Admin writes → MySQL             │     │
│  │ SDK/API reads → Redis (fallback→ │     │
│  │                    LocalMap)     │     │
│  └──────────────────────────────────┘     │
└────────┬──────────────────────────────────┘
         │ admin R/W
┌────────▼────────┐
│  MySQL          │
│  (source of     │
│   truth)        │
│  t_apps         │
│  t_flags        │
│  t_audit_log    │
└────────┬────────┘
         │ Binlog
┌────────▼──────────────────────────────────┐
│  CDC Pipeline                              │
│  ┌──────────┐   ┌───────────────┐         │
│  │Debezium/ │──▶│   Kafka       │         │
│  │ Canal    │   │ (single part.,│         │
│  │          │   │  compacted)   │         │
│  └──────────┘   └──────┬────────┘         │
│                        │                  │
│  ┌─────────────────────▼─────────┐        │
│  │ Cache Consumer                │        │
│  │ (Active-Standby)              │        │
│  │ · Writes Redis (SET idempotent)        │
│  │ · Manual full warmup MySQL→   │        │
│  │   Redis                       │        │
│  └───────────────────────────────┘        │
└────────────────────┬──────────────────────┘
                     │ CDC writes
              ┌──────▼────────┐
              │  Redis        │
              │  (cache)      │
              │  no TTL       │
              │  ff:{appId}   │
              │  :{flagKey}   │
              └───────┬───────┘
                      │ reads
              ┌───────┴───────┐
              │ SDK / API     │
              └───────────────┘
```

### Data Flow

**Write Path:** Admin UI → Management Service → MySQL (source of truth)

**CDC Sync Path:**
1. MySQL binlog captures changes
2. Debezium/Canal extracts into Kafka messages
3. Active Consumer consumes messages, writes to Redis

**Read Path:**
- Admin layer (Admin UI): reads MySQL directly (read-write separation, bypasses cache)
- Client (SDK / Evaluation API): reads Redis first, falls back to local Map on failure

### Read Path Hierarchy

```
MySQL (source of truth)
  │
  │ CDC (binlog → Kafka, sub-second latency)
  ▼
Redis ←── near-realtime data replica, primary client read path
  │
  │ fallback on failure
  ▼
 Local Map ←── periodic full snapshot (every 5min from MySQL), emergency fallback only
```

1. **Redis** (primary path): near-realtime data replica, continuously receives MySQL binlog changes through CDC pipeline. Single flag evaluation uses `GET ff:{appId}:{flagKey}`, batch requests use `redisTemplate.opsForValue().multiGet(keys)` to fetch all values in one pipeline call
2. **Local Map** (fallback when Redis unavailable): snapshot pulled from MySQL every 5min, also pulled once at startup, emergency fallback only

### Consistency Model

- **Source of truth:** MySQL — all writes persist to MySQL
- **CDC delay:** MySQL → Redis has sub-second CDC latency (binlog sync + Kafka transport + consumer processing)
- **Admin reads:** direct MySQL reads, strongly consistent (standard read-write separation)
- **Client reads (SDK/API):** Redis first, eventually consistent, latency typically under 100ms; falls back to local Map when Redis is down
- **Failure mode:** when Redis is down, SDK/API path degrades to local Map (data from last MySQL full pull, at most 5min stale); when Consumer fails, Standby automatically takes over
- **Data latency note:** Redis syncs MySQL changes in near-realtime via CDC (sub-second), while the local Map pulls a full MySQL snapshot every 5min. There is a deliberate freshness gap — the local Map is not read during normal operation; it only serves as an emergency fallback when Redis is unavailable, at which point data may be up to 5min behind Redis's last consistent state.

## 2. Client SDK Design

### API

```java
FeatureFlagClient client = FeatureFlagClientFactory.create(
    Config.builder()
        .appId("checkout-service")
        .managementServiceUrl("https://ffmgr.company.com")
        .jwtToken("eyJhbGciOi...")
        .refreshIntervalSeconds(30)
        .build()
);

boolean enabled = client.isEnabled("checkout-v2", EvaluationContext.of(
    "user_id", "u-123",
    "region", "us",
    "org_id", "acme-corp"
));

String variant = client.getVariant("pricing-model", context);

FlagExplanation explanation = client.explain("checkout-v2", context);
```

### Internals

| Component | Responsibility |
|-----------|---------------|
| `RefreshManager` | Background thread, calls `GET /api/allFlags?appId={appId}` every `refreshIntervalSeconds`, overwrites `InMemoryStore` |
| `InMemoryStore` | `ConcurrentHashMap<String, FlagConfig>` + version; full load at startup |
| `HttpClient` | OKHttp / WebClient wrapper, carries JWT in request headers `Authorization: Bearer xxx` |
| `EvaluationEngine` | Reads FlagConfig from `InMemoryStore`, evaluates targeting rules against context |

### SDK Lifecycle

1. **Init:** calls `GET /api/allFlags?appId=xxx` (JWT auth) → writes to `InMemoryStore`
2. **Evaluate:** `isEnabled()` / `getVariant()` reads from `InMemoryStore` locally + `EvaluationEngine` evaluates (no network overhead)
3. **Refresh:** background thread pulls `GET /api/allFlags?appId=xxx` every 30s, overwrites `InMemoryStore`
4. **Error:** when Management Service is unavailable, SDK continues using local cache (stale but available)

## 3. API Design

### Management API (authenticated, Admin UI)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/flags` | Create flag |
| PUT | `/api/flags/{flagKey}` | Update flag (full replace) |
| PATCH | `/api/flags/{flagKey}` | Partial update |
| DELETE | `/api/flags/{flagKey}` | Delete flag |
| GET | `/api/flags` | List flags (paginated, filterable by appId) |
| GET | `/api/flags/{flagKey}` | Get single flag detail |
| GET | `/api/flags/{flagKey}/history` | Audit trail |
| GET | `/api/flags/consistency-check?appId={appId}` | Compare data differences for a given appId between MySQL and Redis, used by admin to troubleshoot inconsistencies |
| POST | `/api/admin/warmup` | Full read of all active flags from MySQL into Redis, used for initial deployment or cache rebuild |

For SDK full pull of a given appId's flag configurations, JWT authenticated.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/allFlags?appId={appId}` | Get full flag configuration for a given appId |

**Response:**

```json
{
  "appId": "checkout",
  "flags": {
    "checkout-v2": { "enabled": true, "targeting": {...} },
    "pricing-model": { "enabled": false, "targeting": {...} }
  }
}
```

### Consistency Check API

Admin interface to compare data differences for a given appId between MySQL and Redis, used for troubleshooting CDC sync issues.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/flags/consistency-check?appId={appId}` | Compare flag configuration differences between MySQL and Redis |

**Response:**

```json
{
  "appId": "checkout",
  "checked_at": "2026-06-02T10:00:00Z",
  "mysql_count": 12,
  "redis_count": 12,
  "diff": [
    {
      "flag_key": "checkout-v2",
      "mysql": { "enabled": true, "version": 12, "updated_at": "..." },
      "redis": { "enabled": false, "version": 11, "updated_at": "..." },
      "status": "MISMATCH"
    }
  ],
  "only_in_mysql": [],
  "only_in_redis": []
}
```

### Evaluation API + Feign Client

Provides a Feign Client package for integration through internal service mesh.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/evaluate` | Single flag evaluation |
| POST | `/api/evaluate/batch` | Batch evaluation (multiple flags, same context) — internally uses `multiGet` to read all flag configs for the app from Redis in one pipeline call, avoiding N separate network IOs |

#### Request/Response

```json
POST /api/evaluate
body: {
  "appId": "checkout",
  "flagKey": "checkout-v2",
  "context": { "user_id": "u-123", "region": "us", "org_id": "acme" }
}
response: {
  "enabled": true,
  "variant": null,
  "reason": "rule_match:region_us"
}
```

### Auth API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | JWT login |


## 4. Explainability Model

```json
GET /api/flags/checkout-v2/explain?user_id=u-123&region=us&org_id=acme

{
  "flag_key": "checkout-v2",
  "app_id": "checkout-service",
  "enabled": true,
  "evaluation": {
    "result": true,
    "matched_rule": {
      "index": 2,
      "condition": "region IN [us, eu] AND org_id EQ acme-corp",
      "priority": 100
    },
    "default_result": false
  },
  "targeting_summary": {
    "user_id": "u-123",
    "region": "us",
    "organization_id": "acme"
  },
  "metadata": {
    "owner": "team-payments",
    "release": "r2026-q2",
    "description": "New checkout flow",
    "created_at": "2026-05-01T10:00:00Z",
    "updated_at": "2026-05-15T14:30:00Z",
    "version": 12
  },
  "history": {
    "last_change_by": "admin@company.com",
    "last_change_at": "2026-05-15T14:30:00Z",
    "last_change_summary": "Added region EU to targeting"
  }
}
```

The SDK also supports local `explain()`:

```java
FlagExplanation explanation = client.explain("checkout-v2", context);
```

The local evaluation logic is identical to the Management Service, based on the locally cached FlagConfig snapshot.

## 5. Observability

### Metrics (Prometheus)

| Metric | Type | Labels |
|--------|------|--------|
| `flag_evaluation_total` | Counter | `app_id, flag_key` |
| `flag_evaluation_duration_ms` | Histogram | `app_id, flag_key` |
| `batch_flag_evaluation_total` | Counter | `app_id` |
| `batch_flag_evaluation_duration_ms` | Histogram | `app_id` |
| `redis_hits_total` | Counter | `app_id, flag_key` |
| `redis_misses_total` | Counter | `app_id, flag_key` |
| `cdc_lag_ms` | Gauge | `consumer_instance` |
| `cdc_events_processed_total` | Counter | `event_type (create/update/delete)` |
| `snapshot.cache.size` | Gauge | - |

### Alerting Rules

```
- alert: RedisMissRateHigh    expr: rate(redis_misses_total[5m]) / rate(redis_hits_total[5m]) > 0.1   severity: warning
- alert: CDCLagHigh           expr: cdc_lag_ms > 1000                                                    severity: warning
- alert: CDCLagCritical       expr: cdc_lag_ms > 5000                                                    severity: critical
- alert: ConsumerDown         expr: consumer_status == 0 for all instances for 60s                       severity: P1
- alert: HighEvalLatency      expr: histogram_quantile(0.99, ...) > 100ms                                severity: P3
```

### Grafana Panels

| Panel | Query |
|-------|-------|
| Evaluation Rate | `sum(rate(flag_evaluation_total[5m])) by (flag_key)` |
| Batch Evaluation Rate | `sum(rate(batch_flag_evaluation_total[5m])) by (app_id)` |
| P99 Latency | `histogram_quantile(0.99, sum(rate(flag_evaluation_duration_bucket[5m])) by (le, flag_key))` |
| Batch P99 Latency | `histogram_quantile(0.99, sum(rate(batch_flag_evaluation_duration_bucket[5m])) by (le, app_id))` |
| Redis Hit/Miss Ratio | `sum(rate(redis_hits_total[5m])) / sum(rate(redis_misses_total[5m]))` |
| CDC Lag | `avg(cdc_lag_ms) by (consumer_instance)` |

### Metrics Pipeline

```
                ┌─────────────────┐
                │  Management     │
                │  Service        │
                │  :8080          │
                │  /actuator      │
                │  /metrics       │
                └──────┬──────────┘
                ┌─────────────────┐
                │  Consumer       │
                │  (Active-       │
                │   Standby)      │
                │  :8080          │
                │  /actuator      │
                │  /metrics       │
                └──────┬──────────┘
                ┌─────────────────┐
                │  Debezium/Canal │
                │  :8080          │
                │  /metrics       │
                └──────┬──────────┘
                       │ Prometheus discovers and scrapes
                ┌──────▼──────────────────────┐
                │  Prometheus Server           │
                │  ┌────────────────────────┐ │
                │  │  TSDB (local storage)  │ │
                │  └────────────────────────┘ │
                └──────┬──────────────────────┘
                       │ PromQL
                  │
                  │ PromQL        │ alerts
                  │               │
            ┌─────▼──────┐  ┌────▼─────────┐
            │  Grafana   │  │  Alertmanager │
            │ (Dashboard)│  │  (routing/    │
            │            │  │   dedup)      │
            └────────────┘  └────┬──────────┘
                                 │ notify
                          ┌──────▼──────┐
                          │  Channels   │
                          │ (DingTalk/  │
                          │  WeCom/     │
                          │  Slack/     │
                          │  Email)     │
                          └─────────────┘
```

**Metric Scraping:** Prometheus discovers component instances through service discovery (K8s SD / static config / Consul etc.), scrapes `/actuator/prometheus`, and distinguishes replicas via the `instance` label.

**Storage & Query:** Prometheus TSDB stores time-series data locally. Grafana is configured with a Prometheus data source and queries dashboards via PromQL.

## 6. Storage: MySQL + Kafka + Redis (Chosen Architecture)

### Data Flow Summary

```
Write Path:    Management Service → MySQL
CDC Pipeline:  MySQL binlog → Debezium/Canal → Kafka → Consumer → Redis
Read Path:     SDK → Management Service → Redis
```

### MySQL (source of truth)

#### Table: `t_apps`

```sql
CREATE TABLE t_apps (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    app_id      VARCHAR(64)     NOT NULL,
    name        VARCHAR(256)    NOT NULL DEFAULT '',
    owner       VARCHAR(128)    NOT NULL DEFAULT '',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1=active, 0=disabled',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_app_id (app_id),
    INDEX idx_owner (owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### Table: `t_flags`

```sql
CREATE TABLE t_flags (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    app_id      VARCHAR(64)     NOT NULL,
    flag_key    VARCHAR(128)    NOT NULL,
    enabled     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'global enable/disable switch',
    targeting   JSON            NOT NULL COMMENT 'targeting rule tree',
    metadata    JSON            NOT NULL COMMENT '{"owner","release","description","tags","type"}',
    version     BIGINT          NOT NULL COMMENT 'monotonic version, per (app_id) monotonically increasing',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1=active, 0=archived',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_app_flag (app_id, flag_key),
    INDEX idx_app_version (app_id, version),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`targeting` JSON schema:

```json
{
  "rules": [
    {
      "name": "region-us-org-acme",
      "priority": 100,
      "conditions": [
        { "attribute": "region", "op": "IN", "values": ["us", "eu"] },
        { "attribute": "org_id", "op": "EQ", "value": "acme-corp" }
      ],
      "enabled": true,
      "variant": "v2"
    }
  ],
  "default": {
    "enabled": false,
    "variant": null
  }
}
```

`metadata` JSON schema:

```json
{
  "owner": "team-payments",
  "release": "r2026-q2",
  "description": "New checkout flow",
  "tags": ["checkout", "payment"],
  "type": "release"
}
```

#### Table: `t_audit_log`

```sql
CREATE TABLE t_audit_log (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    app_id      VARCHAR(64)     NOT NULL,
    flag_key    VARCHAR(128)    NOT NULL,
    event_type  VARCHAR(32)     NOT NULL COMMENT 'CREATE | UPDATE | DELETE | TOGGLE',
    diff        JSON            NOT NULL COMMENT '{"before":{...},"after":{...}}',
    changed_by  VARCHAR(128)    NOT NULL,
    changed_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     BIGINT          NOT NULL COMMENT 'flag version after this change',

    INDEX idx_app_flag_time (app_id, flag_key, changed_at),
    INDEX idx_changed_by (changed_by),
    INDEX idx_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### Entity Relationship

```
t_apps (1) ──────< t_flags (N)
t_flags (1) ─────< t_audit_log (N)
```

### Kafka Topic

| Property | Value |
|----------|-------|
| Topic Name | `feature-flag-changes` |
| Partitions | **1** (low change volume, ordering matters) |
| Cleanup Policy | `compact` + `delete` (retains latest state per flag) |
| Retention | 7 days (compact) / 30 days (segment) |
| Producer | Debezium/Canal (application does not write to Kafka directly) |

#### Message Schema

```json
{
  "event_id": "uuid-v7",
  "app_id": "checkout",
  "flag_key": "checkout-v2",
  "op": "UPDATE",
  "before": { ... },
  "after": { ... },
  "version": 47,
  "ts_ms": 1717344000000
}
```

### Redis (near-realtime data replica)

Redis is the **primary data source** for the Client read path in this system, continuously syncing MySQL changes through the CDC pipeline and serving all online read traffic.

| Property | Value |
|----------|-------|
| Role | Primary data source for Client read path, **near-realtime replica** (CDC sub-second latency) |
| Key Structure | `ff:{appId}:{flagKey}` → String JSON (FlagConfig) |
| TTL | **No TTL**—avoids cache stampede / thundering herd |
| Eviction | `allkeys-lru` (only triggers when memory is full, rarely happens with small flag data volume) |
| Write | Only Cache Consumer writes |
| Read | Management Service read requests: reads Redis first (single flag via `GET`, batch via `multiGet` pipeline), falls back to local Map on failure |

### Local Cache (periodic full snapshot)

| Property | Value |
|----------|-------|
| Key Structure | `snapshot:{appId}` → Map<String, Object> (full snapshot) |
| Role | **Emergency fallback only when Redis is unavailable**, not read during normal operation |
| Data Structure | `ConcurrentHashMap<String, Map<String, Object>>` + `ReadWriteLock` for thread safety |
| Data Freshness | **5min gap**——scheduled task (`@Scheduled(fixedDelayString = "snapshot.refresh-interval-ms"`) defaults to 5min) pulls full data from MySQL, overwrites local Map |
| Policy | Pulls once at startup (`@PostConstruct`); then pulls full MySQL (`SELECT * FROM t_flags`) every 5min, groups by appId to rebuild snapshot, atomically replaces cache |

> **Relationship between Redis and local Map:** Redis is a CDC-driven near-realtime replica (sub-second latency) and the primary Client read path; the local Map is a full snapshot periodically pulled from MySQL (5min interval, pulled once at startup), serving only as an emergency escape hatch when Redis is unavailable. The intentional freshness gap means the local Map does not participate in the read path during normal operation, avoiding stale data reads.

#### Refresh Flow

```
Startup (@PostConstruct) + Timer (every 5min)
  │
  ├── flagRepository.findAll()  →  MySQL full scan
  │
  ├── Group by appId, rebuild Map<String, Map<String, Object>>
  │
  ├── writeLock.lock()
  │     cache.clear()
  │     cache.putAll(newData)
  │   writeLock.unlock()
  │
  └── Done, cache data persists until next refresh
```

### Consistency Guarantees

- **MySQL → Redis:** Eventually consistent, CDC latency typically < 500ms
- **Binlog position:** Each Consumer records its consumed binlog position, resumes from breakpoint after restart
- **Read-your-write:** Admin can optionally send `X-Read-From-Master: true` header after writing to Management Service to bypass the cache and read MySQL directly

## 7. CDC Pipeline & Consumer Design

### Pipeline Architecture

```
MySQL binlog → Debezium/Canal → Kafka topic (single partition)
                                    ↓
                      ┌───────────────────────────┐
                      │  Consumer Group            │
                      │  group.id = ffmgr-cache    │
                      │  ┌───────────┐ ┌────────┐ │
                      │  │ Consumer  │ │Consumer │ │
                      │  │ 0 (Active)│ │1(Standy)│ │
                      │  └─────┬─────┘ └────────┘ │
                      └────────┼───────────────────┘
                               │
                        ┌──────▼──────┐
                        │   Redis     │
                        └─────────────┘
```

### Consumer Implementation

The Consumer does not perform idempotent deduplication — `SET` / `DEL` are inherently idempotent, and duplicate writes are harmless. With a single partition and compacted topic, message replay is extremely unlikely.

```java
@Component
public class CdcConsumerService {

    @KafkaListener(topics = "feature-flag-changes", groupId = "ffmgr-cache")
    public void consume(FlagChangeEvent event) {
        String key = "ff:" + event.getAppId() + ":" + event.getFlagKey();

        switch (event.getOp()) {
            case "CREATE":
            case "UPDATE":
                Map<String, Object> val = new LinkedHashMap<>();
                val.put("flagKey", event.getFlagKey());
                val.put("enabled", extract(event.getAfter(), "enabled"));
                val.put("targeting", extract(event.getAfter(), "targeting"));
                val.put("metadata", extract(event.getAfter(), "metadata"));
                val.put("version", event.getVersion());
                redisTemplate.opsForValue().set(key, val);
                break;
            case "DELETE":
                redisTemplate.delete(key);
                break;
        }
    }
}
```

### Active-Standby Mechanism

- Two Consumer instances under the same `group.id`
- Single partition → only the leader instance is assigned this partition
- Leader goes down → rebalance → Standby takes over
- After takeover: consumes from the last committed offset (idempotent writes guarantee replay safety)
- Health check: Kafka heartbeats automatically trigger rebalance

### Warmup API

Manual full warmup for initial deployment or Redis cache rebuild. Consumer does not need warmup after restart — it continues consuming from the last offset.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/warmup` | Full read of all active flags from MySQL into Redis |

### Failure Scenarios

| Scenario | Behavior |
|----------|----------|
| Consumer OOM/Kill | Kafka rebalance → Standby takes over (~10-30s unavailable period) |
| Kafka broker down | Consumer blocks and waits, exponential backoff retry |
| MySQL down | Debezium pauses, Kafka accumulates, Consumer waits silently |
| Redis down | Consumer pauses (write to Redis throws exception), Kafka offset not committed, resumes on recovery |

## 8. Future Considerations

- **Redis cluster:** Migrate to Redis Cluster when single-node capacity is insufficient
- **WebSocket push:** Change SDK from polling to WebSocket for lower latency
- **Feature flag events to Data Platform:** Dual-write flag change events from Kafka to data platform (Hive/ClickHouse) for analytics
- **Configuration drift detection:** Periodically compare MySQL and Redis data consistency

---

## Appendix A: Why CDC (binlog → Kafka → Consumer) over Dual-Write

### The Dual-Write Approach (Not Chosen)

```
Management Service → MySQL + Redis (synchronous dual-write)
```

The application layer writes to both MySQL and Redis within the same business transaction. This looks simpler but has two fundamental problems.

### Why CDC Instead

**1. Redis as a store requires strong consistency**

In this design, Redis is not an expendable cache — it is the core data source for the Evaluation API (SDK does not read MySQL directly). If Redis data is inconsistent with MySQL, the SDK would receive incorrect flag evaluation results. Under dual-write, the application layer writes to two stores simultaneously without atomicity guarantees — MySQL succeeds while Redis fails (or vice versa), and the system enters an inconsistent state. By introducing a CDC pipeline, MySQL becomes the single write entry point (source of truth), and Redis is passively synchronized via binlog, naturally eliminating the source of inconsistency inherent in dual-write.

**2. Avoiding Partial Failure**

The fundamental problem with dual-write is that writing to MySQL and writing to Redis are two independent operations without a global transaction. Even with local transactions + message table approaches, the following risks remain:

- MySQL commit succeeds but Redis write times out / fails → data inconsistency
- Redis write succeeds but MySQL commit fails → data inconsistency
- Application restarts / crashes between the two writes → data inconsistency

Introducing Kafka as an intermediary solves this:

- **Idempotent writes:** Redis `SET` / `DEL` are inherently idempotent — writing the same key repeatedly produces the same result, no additional dedup logic needed
- **Replay safety:** Consumer resumes from Kafka offset and replays messages into Redis without data errors
- **Retry delivery:** Debezium/Canal has built-in retry mechanisms — binlog connection interruptions auto-recover and resume from breakpoint
- **Guaranteed eventual consistency:** Writes only need to ensure MySQL succeeds; the CDC pipeline reliably propagates changes to Redis. Any link failure retries, never producing permanent inconsistency

**Summary:** The CDC approach transforms the problem of "writing to two stores simultaneously" into "reliably syncing changes from one store to another," using Kafka's transactional and retry mechanisms to guarantee propagation reliability. Although it introduces additional middleware and sub-second latency, it provides provable consistency guarantees — a necessary trade-off when Redis serves as a core store.
