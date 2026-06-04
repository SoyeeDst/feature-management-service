package com.ffmgr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ffmgr.dto.EvaluationResponse;
import com.ffmgr.dto.FlagConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final String REDIS_FLAG_PREFIX = "ff:";
    private static final String CACHE_SNAPSHOT_PREFIX = "snapshot:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SnapshotCacheManager snapshotCache;
    private final MeterRegistry meterRegistry;

    public EvaluationService(RedisTemplate<String, Object> redisTemplate,
                             ObjectMapper objectMapper,
                             SnapshotCacheManager snapshotCache,
                             MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.snapshotCache = snapshotCache;
        this.meterRegistry = meterRegistry;
    }

    public EvaluationResponse evaluate(String appId, String flagKey, Map<String, String> context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            FlagConfig config = resolveFlagConfig(appId, flagKey);
            if (config == null) {
                return new EvaluationResponse(false, null, "flag_not_found");
            }
            if (!config.getEnabled()) {
                return new EvaluationResponse(false, null, "disabled");
            }
            return evaluateAgainstConfig(config.getTargeting(), context);
        } finally {
            sample.stop(Timer.builder("flag_evaluation_duration_ms")
                    .tag("app_id", appId)
                    .tag("flag_key", flagKey)
                    .register(meterRegistry));
            meterRegistry.counter("flag_evaluation_total",
                    "app_id", appId, "flag_key", flagKey).increment();
        }
    }

    public List<EvaluationResponse> batchEvaluate(String appId, List<String> flagKeys, Map<String, String> context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        List<EvaluationResponse> results = new ArrayList<EvaluationResponse>(flagKeys.size());
        Map<String, FlagConfig> allConfigs = null;

        try {
            allConfigs = buildAllFlagsFromRedis(appId);
        } catch (Exception e) {
            log.warn("redis unavailable for batch evaluate, fallback to local snapshot", e);
        }

        if (allConfigs == null) {
            String cacheKey = CACHE_SNAPSHOT_PREFIX + appId;
            Map<String, Object> snapshot = snapshotCache.get(cacheKey);
            if (snapshot != null) {
                allConfigs = new HashMap<String, FlagConfig>();
                Map<String, Object> flags = (Map<String, Object>) snapshot.get("flags");
                if (flags != null) {
                    for (Map.Entry<String, Object> entry : flags.entrySet()) {
                        if (entry.getValue() instanceof Map) {
                            allConfigs.put(entry.getKey(),
                                    mapToFlagConfig(entry.getKey(), (Map<String, Object>) entry.getValue()));
                        }
                    }
                }
            }
        }

        for (String flagKey : flagKeys) {
            FlagConfig config = allConfigs != null ? allConfigs.get(flagKey) : null;
            if (config == null) {
                results.add(new EvaluationResponse(false, null, "flag_not_found"));
            } else if (!config.getEnabled()) {
                results.add(new EvaluationResponse(false, null, "disabled"));
            } else {
                results.add(evaluateAgainstConfig(config.getTargeting(), context));
            }
        }

        sample.stop(Timer.builder("batch_flag_evaluation_duration_ms")
                .tag("app_id", appId)
                .register(meterRegistry));

        meterRegistry.counter("batch_flag_evaluation_total",
                "app_id", appId).increment();

        return results;
    }

    @SuppressWarnings("unchecked")
    private FlagConfig resolveFlagConfig(String appId, String flagKey) {
        try {
            String redisKey = REDIS_FLAG_PREFIX + appId + ":" + flagKey;
            Object val = redisTemplate.opsForValue().get(redisKey);
            if (val instanceof Map) {
                return mapToFlagConfig(flagKey, (Map<String, Object>) val);
            }
        } catch (Exception e) {
            log.warn("redis unavailable, fallback to local snapshot: {}", e.getMessage());
        }

        String cacheKey = CACHE_SNAPSHOT_PREFIX + appId;
        Map<String, Object> snapshot = snapshotCache.get(cacheKey);
        if (snapshot != null) {
            Map<String, Object> flags = (Map<String, Object>) snapshot.get("flags");
            if (flags != null && flags.containsKey(flagKey)) {
                return mapToFlagConfig(flagKey, (Map<String, Object>) flags.get(flagKey));
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, FlagConfig> buildAllFlagsFromRedis(String appId) {
        String pattern = REDIS_FLAG_PREFIX + appId + ":*";
        String prefix = REDIS_FLAG_PREFIX + appId + ":";

        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return null;
        }

        List<String> keyList = new ArrayList<String>(keys);
        List<Object> values = redisTemplate.opsForValue().multiGet(keyList);

        Map<String, FlagConfig> flagMap = new HashMap<String, FlagConfig>();
        for (int i = 0; i < keyList.size(); i++) {
            String flagKey = keyList.get(i).substring(prefix.length());
            Object val = values.get(i);
            if (val instanceof Map) {
                flagMap.put(flagKey, mapToFlagConfig(flagKey, (Map<String, Object>) val));
            }
        }
        return flagMap.isEmpty() ? null : flagMap;
    }

    private FlagConfig mapToFlagConfig(String flagKey, Map<String, Object> map) {
        FlagConfig config = new FlagConfig();
        config.setFlagKey(flagKey);
        Object enabledVal = map.get("enabled");
        if (enabledVal instanceof Boolean) {
            config.setEnabled((Boolean) enabledVal);
        } else if (enabledVal instanceof Number) {
            config.setEnabled(((Number) enabledVal).intValue() == 1);
        } else {
            config.setEnabled(false);
        }
        config.setTargeting(String.valueOf(map.getOrDefault("targeting", "{}")));
        config.setMetadata(String.valueOf(map.getOrDefault("metadata", "{}")));
        return config;
    }

    private EvaluationResponse evaluateAgainstConfig(String targetingJson, Map<String, String> context) {
        try {
            JsonNode targeting = objectMapper.readTree(targetingJson);
            JsonNode rules = targeting.get("rules");
            JsonNode defaultNode = targeting.get("default");

            if (rules != null && rules.isArray()) {
                for (int i = 0; i < rules.size(); i++) {
                    JsonNode rule = rules.get(i);
                    if (rule.has("enabled") && !rule.get("enabled").asBoolean()) {
                        continue;
                    }
                    if (evaluateRule(rule, context)) {
                        String variant = rule.has("variant") && !rule.get("variant").isNull()
                                ? rule.get("variant").asText() : null;
                        return new EvaluationResponse(true, variant,
                                "rule_match:" + (rule.has("name") ? rule.get("name").asText() : "rule_" + i));
                    }
                }
            }

            if (defaultNode != null) {
                boolean defaultEnabled = defaultNode.get("enabled") != null
                        && defaultNode.get("enabled").asBoolean();
                return new EvaluationResponse(defaultEnabled,
                        defaultNode.has("variant") ? defaultNode.get("variant").asText() : null,
                        "default");
            }

            return new EvaluationResponse(false, null, "default");
        } catch (Exception e) {
            log.warn("evaluation error", e);
            return new EvaluationResponse(false, null, "error:" + e.getMessage());
        }
    }

    private boolean evaluateRule(JsonNode rule, Map<String, String> context) {
        JsonNode conditions = rule.get("conditions");
        if (conditions == null || !conditions.isArray()) {
            return true;
        }
        for (JsonNode condition : conditions) {
            String attr = condition.get("attribute").asText();
            String op = condition.get("op").asText();
            String contextValue = context.get(attr);
            if (contextValue == null) {
                return false;
            }
            switch (op) {
                case "EQ":
                    if (!contextValue.equals(condition.get("value").asText())) return false;
                    break;
                case "IN":
                    boolean matched = false;
                    for (JsonNode v : condition.get("values")) {
                        if (contextValue.equals(v.asText())) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) return false;
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

}
