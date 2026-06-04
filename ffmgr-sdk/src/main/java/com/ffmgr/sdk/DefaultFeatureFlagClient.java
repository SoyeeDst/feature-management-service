package com.ffmgr.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultFeatureFlagClient implements FeatureFlagClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultFeatureFlagClient.class);

    private final FeatureFlagConfig config;
    private final InMemoryStore store;
    private final EvaluationEngine engine;
    private final RefreshManager refreshManager;

    DefaultFeatureFlagClient(FeatureFlagConfig config) {
        this.config = config;
        this.store = new InMemoryStore();
        this.engine = new EvaluationEngine();
        this.refreshManager = new RefreshManager(config, store);
        this.refreshManager.start();
    }

    @Override
    public boolean isEnabled(String flagKey, EvaluationContext context) {
        FlagConfig flagConfig = store.get(flagKey);
        if (flagConfig == null) {
            log.warn("flag not found in local store: {}", flagKey);
            return false;
        }
        EvaluationEngine.EvaluationResult result = engine.evaluate(flagConfig, context);
        return result.enabled;
    }

    @Override
    public String getVariant(String flagKey, EvaluationContext context) {
        FlagConfig flagConfig = store.get(flagKey);
        if (flagConfig == null) {
            log.warn("flag not found in local store: {}", flagKey);
            return null;
        }
        EvaluationEngine.EvaluationResult result = engine.evaluate(flagConfig, context);
        return result.variant;
    }

    @Override
    public FlagExplanation explain(String flagKey, EvaluationContext context) {
        FlagConfig flagConfig = store.get(flagKey);
        if (flagConfig == null) {
            return new FlagExplanation(flagKey, config.getAppId(), false, false, null, "flag_not_found");
        }
        EvaluationEngine.EvaluationResult result = engine.evaluate(flagConfig, context);
        return new FlagExplanation(
                flagKey,
                config.getAppId(),
                result.enabled,
                result.matched,
                result.matchedRuleName,
                result.reason);
    }

    @Override
    public void refresh() {
        refreshManager.refresh();
    }

    @Override
    public void close() {
        refreshManager.stop();
        store.clear();
        log.info("feature flag client closed for appId={}", config.getAppId());
    }
}
