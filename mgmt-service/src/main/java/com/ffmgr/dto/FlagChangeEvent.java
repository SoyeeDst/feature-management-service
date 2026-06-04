package com.ffmgr.dto;

public class FlagChangeEvent {

    private String eventId;
    private String appId;
    private String flagKey;
    private String op;
    private String before;
    private String after;
    private Long version;
    private Long tsMs;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public String getOp() { return op; }
    public void setOp(String op) { this.op = op; }
    public String getBefore() { return before; }
    public void setBefore(String before) { this.before = before; }
    public String getAfter() { return after; }
    public void setAfter(String after) { this.after = after; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Long getTsMs() { return tsMs; }
    public void setTsMs(Long tsMs) { this.tsMs = tsMs; }
}
