package com.ffmgr.dto;

import java.util.Map;

public class EvaluationRequest {

    private String appId;
    private String flagKey;
    private Map<String, String> context;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public Map<String, String> getContext() { return context; }
    public void setContext(Map<String, String> context) { this.context = context; }
}
