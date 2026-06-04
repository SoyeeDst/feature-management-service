package com.ffmgr.sdk;

public class FeatureFlagConfig {

    private final String appId;
    private final String managementServiceUrl;
    private final String jwtToken;
    private final int refreshIntervalSeconds;

    private FeatureFlagConfig(Builder builder) {
        this.appId = builder.appId;
        this.managementServiceUrl = builder.managementServiceUrl;
        this.jwtToken = builder.jwtToken;
        this.refreshIntervalSeconds = builder.refreshIntervalSeconds;
    }

    public String getAppId() { return appId; }
    public String getManagementServiceUrl() { return managementServiceUrl; }
    public String getJwtToken() { return jwtToken; }
    public int getRefreshIntervalSeconds() { return refreshIntervalSeconds; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String appId;
        private String managementServiceUrl;
        private String jwtToken;
        private int refreshIntervalSeconds = 30;

        public Builder appId(String appId) { this.appId = appId; return this; }
        public Builder managementServiceUrl(String url) { this.managementServiceUrl = url; return this; }
        public Builder jwtToken(String token) { this.jwtToken = token; return this; }
        public Builder refreshIntervalSeconds(int seconds) { this.refreshIntervalSeconds = seconds; return this; }
        public FeatureFlagConfig build() {
            if (appId == null || appId.isEmpty()) throw new IllegalArgumentException("appId is required");
            if (managementServiceUrl == null || managementServiceUrl.isEmpty())
                throw new IllegalArgumentException("managementServiceUrl is required");
            if (jwtToken == null || jwtToken.isEmpty()) throw new IllegalArgumentException("jwtToken is required");
            return new FeatureFlagConfig(this);
        }
    }
}
