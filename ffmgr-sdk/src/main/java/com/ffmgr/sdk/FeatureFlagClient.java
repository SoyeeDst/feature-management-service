package com.ffmgr.sdk;

public interface FeatureFlagClient extends AutoCloseable {

    boolean isEnabled(String flagKey, EvaluationContext context);

    String getVariant(String flagKey, EvaluationContext context);

    FlagExplanation explain(String flagKey, EvaluationContext context);

    void refresh();

    void close();
}
