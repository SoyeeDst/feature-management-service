package com.ffmgr.dto;

import java.util.HashMap;
import java.util.Map;

public class PatchFlagRequest {

    private String appId;
    private Boolean enabled;
    private Object targeting;
    private Object metadata;
    private String changedBy;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Object getTargeting() { return targeting; }
    public void setTargeting(Object targeting) { this.targeting = targeting; }
    public Object getMetadata() { return metadata; }
    public void setMetadata(Object metadata) { this.metadata = metadata; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public Map<String, Object> toChangeMap() {
        Map<String, Object> map = new HashMap<>();
        if (appId != null) map.put("appId", appId);
        if (enabled != null) map.put("enabled", enabled);
        if (targeting != null) map.put("targeting", targeting);
        if (metadata != null) map.put("metadata", metadata);
        if (changedBy != null) map.put("changedBy", changedBy);
        return map;
    }

}
