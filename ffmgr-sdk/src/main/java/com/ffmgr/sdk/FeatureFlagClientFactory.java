package com.ffmgr.sdk;

public class FeatureFlagClientFactory {

    public static FeatureFlagClient create(FeatureFlagConfig config) {
        return new DefaultFeatureFlagClient(config);
    }
}
