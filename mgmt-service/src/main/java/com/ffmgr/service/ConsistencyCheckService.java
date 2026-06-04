package com.ffmgr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ffmgr.dto.ConsistencyCheckResponse;
import com.ffmgr.entity.Flag;
import com.ffmgr.repository.FlagRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConsistencyCheckService {

    private static final String REDIS_FLAG_PREFIX = "ff:";

    private final FlagRepository flagRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public ConsistencyCheckService(FlagRepository flagRepository,
                                   RedisTemplate<String, Object> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.flagRepository = flagRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public ConsistencyCheckResponse check(String appId) {
        List<Flag> mysqlFlags = flagRepository.findByAppIdAndStatus(appId, 1);

        Map<String, Flag> mysqlFlagMap = new LinkedHashMap<>();
        for (Flag flag : mysqlFlags) {
            mysqlFlagMap.put(flag.getFlagKey(), flag);
        }

        Set<String> mysqlKeys = mysqlFlagMap.keySet();
        Set<String> redisKeys = getRedisKeysForApp(appId);

        ConsistencyCheckResponse response = new ConsistencyCheckResponse();
        response.setAppId(appId);
        response.setCheckedAt(LocalDateTime.now().toString());
        response.setMysqlCount(mysqlFlags.size());
        response.setRedisCount(redisKeys.size());

        List<ConsistencyCheckResponse.FlagDiff> diffs = new ArrayList<>();
        List<String> onlyInMysql = new ArrayList<>();
        List<String> onlyInRedis = new ArrayList<>();

        Set<String> allKeys = new HashSet<>(mysqlKeys);
        allKeys.addAll(redisKeys);

        for (String flagKey : allKeys) {
            Flag mysqlFlag = mysqlFlagMap.get(flagKey);
            Object redisVal = getRedisFlagValue(appId, flagKey);

            if (mysqlFlag == null) {
                onlyInRedis.add(flagKey);
                continue;
            }
            if (redisVal == null) {
                onlyInMysql.add(flagKey);
                continue;
            }

            String redisVersion = extractVersion(redisVal);

            ConsistencyCheckResponse.FlagDiff diff = new ConsistencyCheckResponse.FlagDiff();
            diff.setFlagKey(flagKey);

            ConsistencyCheckResponse.FlagState mysqlState = new ConsistencyCheckResponse.FlagState();
            mysqlState.setEnabled(mysqlFlag.getEnabled());
            mysqlState.setVersion(mysqlFlag.getVersion());
            mysqlState.setUpdatedAt(mysqlFlag.getUpdatedAt() != null ? mysqlFlag.getUpdatedAt().toString() : null);

            ConsistencyCheckResponse.FlagState redisState = new ConsistencyCheckResponse.FlagState();
            redisState.setEnabled(redisVersion != null ? mysqlFlag.getEnabled() : null);
            redisState.setVersion(redisVersion != null ? Long.valueOf(redisVersion) : null);

            diff.setMysql(mysqlState);
            diff.setRedis(redisState);

            boolean match = String.valueOf(mysqlFlag.getVersion()).equals(redisVersion);
            diff.setStatus(match ? "MATCH" : "MISMATCH");

            if (!match) {
                diffs.add(diff);
            }
        }

        response.setDiff(diffs);
        response.setOnlyInMysql(onlyInMysql);
        response.setOnlyInRedis(onlyInRedis);
        return response;
    }

    private Set<String> getRedisKeysForApp(String appId) {
        String pattern = REDIS_FLAG_PREFIX + appId + ":*";
        Set<String> rawKeys = redisTemplate.keys(pattern);
        if (rawKeys == null) return new HashSet<>();
        String prefix = REDIS_FLAG_PREFIX + appId + ":";
        return rawKeys.stream()
                .map(k -> k.substring(prefix.length()))
                .collect(Collectors.toSet());
    }

    private Object getRedisFlagValue(String appId, String flagKey) {
        return redisTemplate.opsForValue().get(REDIS_FLAG_PREFIX + appId + ":" + flagKey);
    }

    private String extractVersion(Object val) {
        try {
            String json;
            if (val instanceof Map) {
                Object v = ((Map<?, ?>) val).get("version");
                return v != null ? v.toString() : null;
            }
            json = objectMapper.writeValueAsString(val);
            JsonNode node = objectMapper.readTree(json);
            if (node.has("version")) {
                return node.get("version").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
