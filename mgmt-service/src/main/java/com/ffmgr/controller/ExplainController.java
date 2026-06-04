package com.ffmgr.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ffmgr.dto.EvaluationResponse;
import com.ffmgr.dto.ExplainResponse;
import com.ffmgr.entity.Flag;
import com.ffmgr.service.EvaluationService;
import com.ffmgr.service.FlagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/flags")
public class ExplainController {

    private final FlagService flagService;
    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    public ExplainController(FlagService flagService,
                             EvaluationService evaluationService,
                             ObjectMapper objectMapper) {
        this.flagService = flagService;
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{flagKey}/explain")
    public ResponseEntity<ExplainResponse> explain(@PathVariable String flagKey,
                                                    @RequestParam String appId,
                                                    @RequestParam Map<String, String> params) {
        ExplainResponse response = new ExplainResponse();
        response.setFlagKey(flagKey);
        response.setAppId(appId);

        EvaluationResponse eval = evaluationService.evaluate(appId, flagKey, params);
        response.setEnabled(eval.getEnabled());

        ExplainResponse.EvaluationDetail detail = new ExplainResponse.EvaluationDetail();
        detail.setResult(eval.getEnabled());
        detail.setDefaultResult("default".equals(eval.getReason()));

        if (eval.getReason() != null && eval.getReason().startsWith("rule_match:")) {
            ExplainResponse.MatchedRule matchedRule = new ExplainResponse.MatchedRule();
            String ruleName = eval.getReason().substring("rule_match:".length());
            matchedRule.setCondition("matched rule: " + ruleName);
            matchedRule.setIndex(-1);
            matchedRule.setPriority(0);

            flagService.getFlag(appId, flagKey).ifPresent(flag -> {
                try {
                    JsonNode targeting = objectMapper.readTree(flag.getTargeting());
                    JsonNode rules = targeting.get("rules");
                    if (rules != null && rules.isArray()) {
                        for (int i = 0; i < rules.size(); i++) {
                            JsonNode rule = rules.get(i);
                            String name = rule.has("name") ? rule.get("name").asText() : "rule_" + i;
                            if (name.equals(ruleName)) {
                                matchedRule.setIndex(i);
                                matchedRule.setPriority(rule.has("priority") ? rule.get("priority").asInt() : 0);

                                StringBuilder conditionStr = new StringBuilder();
                                JsonNode conditions = rule.get("conditions");
                                if (conditions != null) {
                                    for (int j = 0; j < conditions.size(); j++) {
                                        JsonNode cond = conditions.get(j);
                                        if (j > 0) conditionStr.append(" AND ");
                                        conditionStr.append(cond.get("attribute").asText())
                                                .append(" ").append(cond.get("op").asText())
                                                .append(" ");
                                        if (cond.has("value")) {
                                            conditionStr.append(cond.get("value").asText());
                                        } else if (cond.has("values")) {
                                            conditionStr.append(cond.get("values").toString());
                                        }
                                    }
                                }
                                matchedRule.setCondition(conditionStr.toString());
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            });

            detail.setMatchedRule(matchedRule);
        }

        response.setEvaluation(detail);

        Map<String, String> summary = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!e.getKey().equals("appId") && !e.getKey().equals("flagKey")) {
                summary.put(e.getKey(), e.getValue());
            }
        }
        response.setTargetingSummary(summary);

        flagService.getFlag(appId, flagKey).ifPresent(flag -> {
            Map<String, Object> meta = new HashMap<>();
            meta.put("version", flag.getVersion());
            meta.put("created_at", flag.getCreatedAt() != null ? flag.getCreatedAt().toString() : null);
            meta.put("updated_at", flag.getUpdatedAt() != null ? flag.getUpdatedAt().toString() : null);
            try {
                JsonNode metadataNode = objectMapper.readTree(flag.getMetadata());
                if (metadataNode.has("owner")) meta.put("owner", metadataNode.get("owner").asText());
                if (metadataNode.has("description")) meta.put("description", metadataNode.get("description").asText());
                if (metadataNode.has("release")) meta.put("release", metadataNode.get("release").asText());
                if (metadataNode.has("tags")) meta.put("tags", metadataNode.get("tags").toString());
                if (metadataNode.has("type")) meta.put("type", metadataNode.get("type").asText());
            } catch (Exception ignored) {}
            response.setMetadata(meta);
        });

        return ResponseEntity.ok(response);
    }
}
