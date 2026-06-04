package com.ffmgr.controller;

import com.ffmgr.service.WarmupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class WarmupController {

    private final WarmupService warmupService;

    public WarmupController(WarmupService warmupService) {
        this.warmupService = warmupService;
    }

    @PostMapping("/warmup")
    public ResponseEntity<Map<String, String>> warmup() {
        warmupService.warmup();
        Map<String, String> body = new HashMap<>();
        body.put("status", "ok");
        body.put("message", "cache warmup completed");
        return ResponseEntity.ok(body);
    }

}
