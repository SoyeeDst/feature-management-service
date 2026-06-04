package com.ffmgr.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class EvaluationEngine {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public EvaluationResult evaluate(FlagConfig config, EvaluationContext context) {
        if (config == null) {
            return new EvaluationResult(false, null, "flag_not_found", false, null);
        }
        if (!config.isEnabled()) {
            return new EvaluationResult(false, null, "disabled", false, null);
        }

        try {
            JsonNode targeting = objectMapper.readTree(config.getTargeting());
            JsonNode rules = targeting.get("rules");
            JsonNode defaultNode = targeting.get("default");

            if (rules != null && rules.isArray()) {
                for (int i = 0; i < rules.size(); i++) {
                    JsonNode rule = rules.get(i);
                    if (rule.has("enabled") && !rule.get("enabled").asBoolean()) {
                        continue;
                    }
                    if (evaluateRule(rule, context)) {
                        String variant = rule.has("variant") && !rule.get("variant").isNull()
                                ? rule.get("variant").asText() : null;
                        String ruleName = rule.has("name") ? rule.get("name").asText() : "rule_" + i;
                        return new EvaluationResult(true, variant, "rule_match:" + ruleName, true, ruleName);
                    }
                }
            }

            if (defaultNode != null) {
                boolean defaultEnabled = defaultNode.get("enabled") != null
                        && defaultNode.get("enabled").asBoolean();
                return new EvaluationResult(defaultEnabled,
                        defaultNode.has("variant") ? defaultNode.get("variant").asText() : null,
                        "default", false, null);
            }

            return new EvaluationResult(false, null, "default", false, null);
        } catch (Exception e) {
            return new EvaluationResult(false, null, "error:" + e.getMessage(), false, null);
        }
    }

    private boolean evaluateRule(JsonNode rule, EvaluationContext context) {
        JsonNode conditions = rule.get("conditions");
        if (conditions == null || !conditions.isArray()) {
            return true;
        }
        for (JsonNode condition : conditions) {
            String attr = condition.get("attribute").asText();
            String op = condition.get("op").asText();
            String contextValue = context.get(attr);
            if (contextValue == null) {
                return false;
            }
            switch (op) {
                case "EQ":
                    if (!contextValue.equals(condition.get("value").asText())) return false;
                    break;
                case "IN":
                    boolean matched = false;
                    for (JsonNode v : condition.get("values")) {
                        if (contextValue.equals(v.asText())) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) return false;
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    static class EvaluationResult {
        final boolean enabled;
        final String variant;
        final String reason;
        final boolean matched;
        final String matchedRuleName;

        EvaluationResult(boolean enabled, String variant, String reason,
                         boolean matched, String matchedRuleName) {
            this.enabled = enabled;
            this.variant = variant;
            this.reason = reason;
            this.matched = matched;
            this.matchedRuleName = matchedRuleName;
        }
    }
}
