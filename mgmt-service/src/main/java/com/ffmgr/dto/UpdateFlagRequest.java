package com.ffmgr.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

public class UpdateFlagRequest {

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

    public String getTargetingJson() {
        return toJson(targeting);
    }

    public String getMetadataJson() {
        return toJson(metadata);
    }

    private static String toJson(Object obj) {
        if (obj == null) return "{}";
        if (obj instanceof String) return (String) obj;
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

}
