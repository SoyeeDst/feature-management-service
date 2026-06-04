package com.ffmgr.service;

import com.ffmgr.entity.Flag;
import com.ffmgr.repository.FlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WarmupService {

    private static final Logger log = LoggerFactory.getLogger(WarmupService.class);
    private static final String REDIS_FLAG_PREFIX = "ff:";

    private final FlagRepository flagRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public WarmupService(FlagRepository flagRepository,
                         RedisTemplate<String, Object> redisTemplate) {
        this.flagRepository = flagRepository;
        this.redisTemplate = redisTemplate;
    }

    public void warmup() {
        log.info("starting cache warmup...");

        List<Flag> activeFlags = flagRepository.findAll().stream()
                .filter(f -> f.getStatus() == 1)
                .collect(Collectors.toList());

        if (activeFlags.isEmpty()) {
            log.info("no active flags to warm up");
            return;
        }

        int count = 0;
        for (Flag flag : activeFlags) {
            String redisKey = REDIS_FLAG_PREFIX + flag.getAppId() + ":" + flag.getFlagKey();
            Map<String, Object> value = new HashMap<>();
            value.put("flagKey", flag.getFlagKey());
            value.put("enabled", flag.getEnabled());
            value.put("targeting", flag.getTargeting());
            value.put("metadata", flag.getMetadata());
            value.put("version", flag.getVersion());
            redisTemplate.opsForValue().set(redisKey, value);
            count++;
        }

        log.info("cache warmup completed: {} flags loaded into redis across {} apps",
                count, activeFlags.stream().map(Flag::getAppId).distinct().count());
    }
}
