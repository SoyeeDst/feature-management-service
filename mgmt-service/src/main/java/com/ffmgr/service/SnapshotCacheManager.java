package com.ffmgr.service;

import com.ffmgr.entity.Flag;
import com.ffmgr.repository.FlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SnapshotCacheManager {

    private static final Logger log = LoggerFactory.getLogger(SnapshotCacheManager.class);
    private static final String CACHE_SNAPSHOT_PREFIX = "snapshot:";

    private final ConcurrentHashMap<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private final FlagRepository flagRepository;

    public SnapshotCacheManager(FlagRepository flagRepository) {
        this.flagRepository = flagRepository;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${snapshot.refresh-interval-ms:300000}")
    public void refresh() {
        try {
            List<Flag> allFlags = flagRepository.findAll();

            Map<String, Map<String, Object>> appSnapshots = new HashMap<>();
            for (Flag flag : allFlags) {
                if (flag.getStatus() != 1) continue;

                Map<String, Object> flagValue = new HashMap<>();
                flagValue.put("flagKey", flag.getFlagKey());
                flagValue.put("enabled", flag.getEnabled());
                flagValue.put("targeting", flag.getTargeting());
                flagValue.put("metadata", flag.getMetadata());
                flagValue.put("version", flag.getVersion());

                String cacheKey = CACHE_SNAPSHOT_PREFIX + flag.getAppId();
                Map<String, Object> snapshot = appSnapshots.get(cacheKey);
                if (snapshot == null) {
                    snapshot = new HashMap<>();
                    snapshot.put("appId", flag.getAppId());
                    snapshot.put("flags", new HashMap<String, Object>());
                    appSnapshots.put(cacheKey, snapshot);
                }
                ((Map<String, Object>) snapshot.get("flags")).put(flag.getFlagKey(), flagValue);
            }

            writeLock.lock();
            try {
                cache.clear();
                cache.putAll(appSnapshots);
            } finally {
                writeLock.unlock();
            }

            log.debug("snapshot cache refreshed: {} apps, {} total flags",
                    appSnapshots.size(), allFlags.size());
        } catch (Exception e) {
            log.error("failed to refresh snapshot cache", e);
        }
    }

    public Map<String, Object> get(String key) {
        readLock.lock();
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();
        }
    }

    public void invalidate(String key) {
        writeLock.lock();
        try {
            cache.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    public int size() {
        readLock.lock();
        try {
            return cache.size();
        } finally {
            readLock.unlock();
        }
    }

    public Map<String, Map<String, Object>> getAll() {
        readLock.lock();
        try {
            return Collections.unmodifiableMap(new HashMap<String, Map<String, Object>>(cache));
        } finally {
            readLock.unlock();
        }
    }

}
