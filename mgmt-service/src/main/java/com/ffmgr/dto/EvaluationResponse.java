package com.ffmgr.dto;

public class EvaluationResponse {

    private Boolean enabled;
    private String variant;
    private String reason;

    public EvaluationResponse() {}

    public EvaluationResponse(Boolean enabled, String variant, String reason) {
        this.enabled = enabled;
        this.variant = variant;
        this.reason = reason;
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
