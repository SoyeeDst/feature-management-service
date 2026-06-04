package com.ffmgr.sdk;

import java.util.Map;

public class EvaluationContext {

    private final Map<String, String> attributes;

    private EvaluationContext(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public static EvaluationContext of(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be pairs of key, value");
        }
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return new EvaluationContext(map);
    }

    public static EvaluationContext of(Map<String, String> attributes) {
        return new EvaluationContext(attributes);
    }

    public String get(String key) {
        return attributes.get(key);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
