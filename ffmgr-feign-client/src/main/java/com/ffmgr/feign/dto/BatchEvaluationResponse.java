package com.ffmgr.feign.dto;

import java.util.List;

public class BatchEvaluationResponse {

    private String appId;
    private List<EvaluationResponse> results;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public List<EvaluationResponse> getResults() { return results; }
    public void setResults(List<EvaluationResponse> results) { this.results = results; }
}
