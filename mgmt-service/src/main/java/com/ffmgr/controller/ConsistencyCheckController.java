package com.ffmgr.controller;

import com.ffmgr.service.ConsistencyCheckService;
import com.ffmgr.dto.ConsistencyCheckResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flags")
public class ConsistencyCheckController {

    private final ConsistencyCheckService consistencyCheckService;

    public ConsistencyCheckController(ConsistencyCheckService consistencyCheckService) {
        this.consistencyCheckService = consistencyCheckService;
    }

    @GetMapping("/consistency-check")
    public ResponseEntity<ConsistencyCheckResponse> consistencyCheck(@RequestParam String appId) {
        return ResponseEntity.ok(consistencyCheckService.check(appId));
    }
}
