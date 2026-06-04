package com.ffmgr.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ffmgr.dto.AllFlagsResponse;
import com.ffmgr.dto.FlagConfig;
import com.ffmgr.entity.AuditLog;
import com.ffmgr.entity.Flag;
import com.ffmgr.repository.AuditLogRepository;
import com.ffmgr.repository.FlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
public class FlagService {

    private static final Logger log = LoggerFactory.getLogger(FlagService.class);
    private static final String REDIS_FLAG_PREFIX = "ff:";
    private static final String CACHE_SNAPSHOT_PREFIX = "snapshot:";

    private final FlagRepository flagRepository;
    private final AuditLogRepository auditLogRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SnapshotCacheManager snapshotCache;

    public FlagService(FlagRepository flagRepository,
                       AuditLogRepository auditLogRepository,
                       RedisTemplate<String, Object> redisTemplate,
                       ObjectMapper objectMapper,
                       SnapshotCacheManager snapshotCache) {
        this.flagRepository = flagRepository;
        this.auditLogRepository = auditLogRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.snapshotCache = snapshotCache;
    }

    public AllFlagsResponse getAllFlags(String appId) {
        Map<String, FlagConfig> flagMap = null;

        try {
            flagMap = buildFromRedis(appId);
        } catch (Exception e) {
            log.warn("redis unavailable for getAllFlags, fallback to local snapshot: {}", e.getMessage());
        }

        if (flagMap == null) {
            flagMap = buildFromLocalSnapshot(appId);
        }

        AllFlagsResponse response = new AllFlagsResponse();
        response.setAppId(appId);
        response.setFlags(flagMap != null ? flagMap : new HashMap<String, FlagConfig>());
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, FlagConfig> buildFromRedis(String appId) {
        String pattern = REDIS_FLAG_PREFIX + appId + ":*";
        String prefix = REDIS_FLAG_PREFIX + appId + ":";

        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return null;
        }

        List<String> keyList = new ArrayList<String>(keys);
        List<Object> values = redisTemplate.opsForValue().multiGet(keyList);

        Map<String, FlagConfig> flagMap = new LinkedHashMap<String, FlagConfig>();
        for (int i = 0; i < keyList.size(); i++) {
            String flagKey = keyList.get(i).substring(prefix.length());
            Object val = values.get(i);
            if (val instanceof Map) {
                flagMap.put(flagKey, mapToFlagConfig(flagKey, (Map<String, Object>) val));
            }
        }
        return flagMap.isEmpty() ? null : flagMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, FlagConfig> buildFromLocalSnapshot(String appId) {
        String cacheKey = CACHE_SNAPSHOT_PREFIX + appId;
        Map<String, Object> snapshot = snapshotCache.get(cacheKey);
        if (snapshot == null) {
            return null;
        }

        Map<String, Object> flags = (Map<String, Object>) snapshot.get("flags");
        if (flags == null || flags.isEmpty()) {
            return null;
        }

        Map<String, FlagConfig> flagMap = new LinkedHashMap<String, FlagConfig>();
        for (Map.Entry<String, Object> entry : flags.entrySet()) {
            if (entry.getValue() instanceof Map) {
                flagMap.put(entry.getKey(), mapToFlagConfig(entry.getKey(), (Map<String, Object>) entry.getValue()));
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

    public Optional<Flag> getFlag(String appId, String flagKey) {
        return flagRepository.findByAppIdAndFlagKey(appId, flagKey);
    }

    public List<Flag> listFlags(String appId) {
        return flagRepository.findByAppId(appId);
    }

    @Transactional
    public Flag createFlag(String appId, String flagKey, Boolean enabled,
                           String targeting, String metadata, String changedBy) {
        Flag flag = new Flag();
        flag.setAppId(appId);
        flag.setFlagKey(flagKey);
        flag.setEnabled(enabled != null ? enabled : false);
        flag.setTargeting(targeting);
        flag.setMetadata(metadata);
        flag.setVersion(1L);
        flag.setStatus(1);
        flag = flagRepository.save(flag);

        AuditLog auditLog = new AuditLog();
        auditLog.setAppId(appId);
        auditLog.setFlagKey(flagKey);
        auditLog.setEventType("CREATE");
        auditLog.setDiff("{\"before\":null,\"after\":" + metadata + "}");
        auditLog.setChangedBy(changedBy);
        auditLog.setVersion(1L);
        auditLogRepository.save(auditLog);

        log.info("flag created: appId={}, flagKey={}, changedBy={}", appId, flagKey, changedBy);
        return flag;
    }

    @Transactional
    public Flag updateFlag(String appId, String flagKey, Boolean enabled,
                           String targeting, String metadata, String changedBy) {
        Flag flag = flagRepository.findByAppIdAndFlagKey(appId, flagKey)
                .orElseThrow(() -> new NoSuchElementException("flag not found: " + flagKey));

        String beforeJson = toJson(flag);
        if (enabled != null) flag.setEnabled(enabled);
        if (targeting != null) flag.setTargeting(targeting);
        if (metadata != null) flag.setMetadata(metadata);
        flag.setVersion(flag.getVersion() + 1);
        flag = flagRepository.save(flag);

        AuditLog auditLog = new AuditLog();
        auditLog.setAppId(appId);
        auditLog.setFlagKey(flagKey);
        auditLog.setEventType("UPDATE");
        auditLog.setDiff("{\"before\":" + beforeJson + ",\"after\":" + toJson(flag) + "}");
        auditLog.setChangedBy(changedBy);
        auditLog.setVersion(flag.getVersion());
        auditLogRepository.save(auditLog);

        log.info("flag updated: appId={}, flagKey={}, version={}, changedBy={}",
                appId, flagKey, flag.getVersion(), changedBy);
        return flag;
    }

    @Transactional
    public Flag patchFlag(String appId, String flagKey, Map<String, Object> changes, String changedBy) {
        Flag flag = flagRepository.findByAppIdAndFlagKey(appId, flagKey)
                .orElseThrow(() -> new NoSuchElementException("flag not found: " + flagKey));

        String beforeJson = toJson(flag);

        if (changes.containsKey("enabled")) {
            flag.setEnabled((Boolean) changes.get("enabled"));
        }
        if (changes.containsKey("targeting")) {
            flag.setTargeting(toJson(changes.get("targeting")));
        }
        if (changes.containsKey("metadata")) {
            flag.setMetadata(toJson(changes.get("metadata")));
        }
        flag.setVersion(flag.getVersion() + 1);
        flag = flagRepository.save(flag);

        AuditLog auditLog = new AuditLog();
        auditLog.setAppId(appId);
        auditLog.setFlagKey(flagKey);
        auditLog.setEventType("UPDATE");
        auditLog.setDiff("{\"before\":" + beforeJson + ",\"after\":" + toJson(flag) + "}");
        auditLog.setChangedBy(changedBy);
        auditLog.setVersion(flag.getVersion());
        auditLogRepository.save(auditLog);

        log.info("flag patched: appId={}, flagKey={}, version={}, changedBy={}",
                appId, flagKey, flag.getVersion(), changedBy);
        return flag;
    }

    @Transactional
    public void deleteFlag(String appId, String flagKey, String changedBy) {
        Flag flag = flagRepository.findByAppIdAndFlagKey(appId, flagKey)
                .orElseThrow(() -> new NoSuchElementException("flag not found: " + flagKey));

        flag.setStatus(0);
        flag.setVersion(flag.getVersion() + 1);
        flagRepository.save(flag);

        AuditLog auditLog = new AuditLog();
        auditLog.setAppId(appId);
        auditLog.setFlagKey(flagKey);
        auditLog.setEventType("DELETE");
        auditLog.setDiff("{\"before\":" + toJson(flag) + ",\"after\":null}");
        auditLog.setChangedBy(changedBy);
        auditLog.setVersion(flag.getVersion());
        auditLogRepository.save(auditLog);

        log.info("flag deleted: appId={}, flagKey={}, changedBy={}", appId, flagKey, changedBy);
    }

    public List<AuditLog> getFlagHistory(String appId, String flagKey) {
        return auditLogRepository.findByAppIdAndFlagKeyOrderByChangedAtDesc(appId, flagKey);
    }

    private String toJson(Flag flag) {
        try {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("enabled", flag.getEnabled());
            map.put("targeting", flag.getTargeting());
            map.put("metadata", flag.getMetadata());
            map.put("version", flag.getVersion());
            map.put("updated_at", flag.getUpdatedAt() != null ? flag.getUpdatedAt().toString() : null);
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("failed to serialize flag to json", e);
            return "{}";
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

}
