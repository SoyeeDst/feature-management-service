package com.ffmgr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ffmgr.dto.FlagChangeEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CdcConsumerService {

    private static final Logger log = LoggerFactory.getLogger(CdcConsumerService.class);
    private static final String REDIS_FLAG_PREFIX = "ff:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter eventsCreatedCounter;
    private Counter eventsUpdatedCounter;
    private Counter eventsDeletedCounter;
    private String consumerInstance;
    private AtomicLong latestLag;

    public CdcConsumerService(RedisTemplate<String, Object> redisTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        try {
            consumerInstance = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            consumerInstance = "unknown";
        }

        eventsCreatedCounter = Counter.builder("cdc_events_processed_total")
                .tag("event_type", "CREATE")
                .register(meterRegistry);
        eventsUpdatedCounter = Counter.builder("cdc_events_processed_total")
                .tag("event_type", "UPDATE")
                .register(meterRegistry);
        eventsDeletedCounter = Counter.builder("cdc_events_processed_total")
                .tag("event_type", "DELETE")
                .register(meterRegistry);

        latestLag = meterRegistry.gauge("cdc_lag_ms",
                Tags.of("consumer_instance", consumerInstance),
                new AtomicLong(0));
    }

    @KafkaListener(topics = "${kafka.topic.flag-changes}", groupId = "ffmgr-cache")
    public void consume(FlagChangeEvent event) {
        log.info("received change event: appId={}, flagKey={}, op={}, version={}",
                event.getAppId(), event.getFlagKey(), event.getOp(), event.getVersion());
        try {
            String flagKey = REDIS_FLAG_PREFIX + event.getAppId() + ":" + event.getFlagKey();

            switch (event.getOp()) {
                case "CREATE":
                    Map<String, Object> createValue = new LinkedHashMap<>();
                    createValue.put("flagKey", event.getFlagKey());
                    createValue.put("enabled", extractField(event.getAfter(), "enabled"));
                    createValue.put("targeting", extractField(event.getAfter(), "targeting"));
                    createValue.put("metadata", extractField(event.getAfter(), "metadata"));
                    createValue.put("version", event.getVersion());
                    redisTemplate.opsForValue().set(flagKey, createValue);
                    eventsCreatedCounter.increment();
                    break;
                case "UPDATE":
                    Map<String, Object> updateValue = new LinkedHashMap<>();
                    updateValue.put("flagKey", event.getFlagKey());
                    updateValue.put("enabled", extractField(event.getAfter(), "enabled"));
                    updateValue.put("targeting", extractField(event.getAfter(), "targeting"));
                    updateValue.put("metadata", extractField(event.getAfter(), "metadata"));
                    updateValue.put("version", event.getVersion());
                    redisTemplate.opsForValue().set(flagKey, updateValue);
                    eventsUpdatedCounter.increment();
                    break;
                case "DELETE":
                    redisTemplate.delete(flagKey);
                    eventsDeletedCounter.increment();
                    break;
                default:
                    log.warn("unknown event type: {}", event.getOp());
            }

            if (event.getTsMs() != null) {
                long lag = System.currentTimeMillis() - event.getTsMs();
                latestLag.set(lag);
            }

            log.info("redis updated: appId={}, flagKey={}, op={}",
                    event.getAppId(), event.getFlagKey(), event.getOp());
        } catch (Exception e) {
            log.error("failed to process event: eventId={}", event.getEventId(), e);
            throw e;
        }
    }

    private Object extractField(String json, String field) {
        try {
            return objectMapper.readTree(json).get(field);
        } catch (Exception e) {
            return null;
        }
    }

}
