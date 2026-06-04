package com.ffmgr.dto;

import java.util.Map;

public class FlagSnapshot {

    private String appId;
    private Map<String, FlagConfig> flags;

    public FlagSnapshot() {}

    public FlagSnapshot(String appId, Map<String, FlagConfig> flags) {
        this.appId = appId;
        this.flags = flags;
    }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public Map<String, FlagConfig> getFlags() { return flags; }
    public void setFlags(Map<String, FlagConfig> flags) { this.flags = flags; }
}
