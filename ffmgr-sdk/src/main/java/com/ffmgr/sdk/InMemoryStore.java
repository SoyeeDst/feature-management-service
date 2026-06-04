package com.ffmgr.sdk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryStore {

    private final ConcurrentHashMap<String, FlagConfig> store = new ConcurrentHashMap<>();
    private volatile long version;

    public FlagConfig get(String flagKey) {
        return store.get(flagKey);
    }

    public void putAll(Map<String, FlagConfig> flags) {
        store.clear();
        store.putAll(flags);
        version = System.currentTimeMillis();
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    public long getVersion() {
        return version;
    }
}
