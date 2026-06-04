package com.ffmgr.controller;

import com.ffmgr.dto.BatchEvaluationRequest;
import com.ffmgr.dto.BatchEvaluationResponse;
import com.ffmgr.dto.EvaluationRequest;
import com.ffmgr.dto.EvaluationResponse;
import com.ffmgr.service.EvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluate")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public ResponseEntity<EvaluationResponse> evaluate(@RequestBody EvaluationRequest request) {
        EvaluationResponse result = evaluationService.evaluate(
                request.getAppId(), request.getFlagKey(), request.getContext());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchEvaluationResponse> batchEvaluate(@RequestBody BatchEvaluationRequest request) {
        List<EvaluationResponse> results = evaluationService.batchEvaluate(
                request.getAppId(), request.getFlagKeys(), request.getContext());
        BatchEvaluationResponse response = new BatchEvaluationResponse();
        response.setAppId(request.getAppId());
        response.setResults(results);
        return ResponseEntity.ok(response);
    }

}
