package com.ffmgr.dto;

import java.util.List;
import java.util.Map;

public class BatchEvaluationRequest {

    private String appId;
    private List<String> flagKeys;
    private Map<String, String> context;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public List<String> getFlagKeys() { return flagKeys; }
    public void setFlagKeys(List<String> flagKeys) { this.flagKeys = flagKeys; }
    public Map<String, String> getContext() { return context; }
    public void setContext(Map<String, String> context) { this.context = context; }
}
