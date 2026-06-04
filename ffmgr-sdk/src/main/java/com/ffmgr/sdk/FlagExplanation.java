package com.ffmgr.sdk;

import java.util.Map;

public class FlagExplanation {

    private String flagKey;
    private String appId;
    private boolean enabled;
    private boolean matched;
    private String matchedRuleName;
    private String reason;

    public FlagExplanation() {}

    public FlagExplanation(String flagKey, String appId, boolean enabled,
                           boolean matched, String matchedRuleName, String reason) {
        this.flagKey = flagKey;
        this.appId = appId;
        this.enabled = enabled;
        this.matched = matched;
        this.matchedRuleName = matchedRuleName;
        this.reason = reason;
    }

    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }
    public String getMatchedRuleName() { return matchedRuleName; }
    public void setMatchedRuleName(String matchedRuleName) { this.matchedRuleName = matchedRuleName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
