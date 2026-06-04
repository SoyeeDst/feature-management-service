package com.ffmgr.dto;

import java.util.Map;

public class ExplainResponse {

    private String flagKey;
    private String appId;
    private Boolean enabled;
    private EvaluationDetail evaluation;
    private Map<String, String> targetingSummary;
    private Map<String, Object> metadata;

    public static class EvaluationDetail {
        private Boolean result;
        private MatchedRule matchedRule;
        private Boolean defaultResult;

        public Boolean getResult() { return result; }
        public void setResult(Boolean result) { this.result = result; }
        public MatchedRule getMatchedRule() { return matchedRule; }
        public void setMatchedRule(MatchedRule matchedRule) { this.matchedRule = matchedRule; }
        public Boolean getDefaultResult() { return defaultResult; }
        public void setDefaultResult(Boolean defaultResult) { this.defaultResult = defaultResult; }
    }

    public static class MatchedRule {
        private int index;
        private String condition;
        private int priority;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }

    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public EvaluationDetail getEvaluation() { return evaluation; }
    public void setEvaluation(EvaluationDetail evaluation) { this.evaluation = evaluation; }
    public Map<String, String> getTargetingSummary() { return targetingSummary; }
    public void setTargetingSummary(Map<String, String> targetingSummary) { this.targetingSummary = targetingSummary; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
