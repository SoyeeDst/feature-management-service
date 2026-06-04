package com.ffmgr.metrics;

import com.ffmgr.service.SnapshotCacheManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class CacheMetricsBinder {

    private final SnapshotCacheManager snapshotCache;
    private final MeterRegistry meterRegistry;

    public CacheMetricsBinder(SnapshotCacheManager snapshotCache,
                              MeterRegistry meterRegistry) {
        this.snapshotCache = snapshotCache;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void bindMetrics() {
        Gauge.builder("snapshot.cache.size", snapshotCache, SnapshotCacheManager::size)
                .description("Snapshot cache app count")
                .register(meterRegistry);
    }

}
