package com.ffmgr.sdk;

public class FlagConfig {

    private String flagKey;
    private boolean enabled;
    private String targeting;
    private String metadata;

    public FlagConfig() {}

    public FlagConfig(String flagKey, boolean enabled, String targeting, String metadata) {
        this.flagKey = flagKey;
        this.enabled = enabled;
        this.targeting = targeting;
        this.metadata = metadata;
    }

    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTargeting() { return targeting; }
    public void setTargeting(String targeting) { this.targeting = targeting; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
