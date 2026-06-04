package com.ffmgr.feign;

import com.ffmgr.feign.dto.BatchEvaluationRequest;
import com.ffmgr.feign.dto.BatchEvaluationResponse;
import com.ffmgr.feign.dto.EvaluationRequest;
import com.ffmgr.feign.dto.EvaluationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "feature-management-service", path = "/api/evaluate")
public interface EvaluationFeignClient {

    @PostMapping
    EvaluationResponse evaluate(@RequestBody EvaluationRequest request);

    @PostMapping("/batch")
    BatchEvaluationResponse batchEvaluate(@RequestBody BatchEvaluationRequest request);
}
