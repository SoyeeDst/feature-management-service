package com.ffmgr.controller;

import com.ffmgr.dto.AllFlagsResponse;
import com.ffmgr.service.FlagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AllFlagsController {

    private final FlagService flagService;

    public AllFlagsController(FlagService flagService) {
        this.flagService = flagService;
    }

    @GetMapping("/allFlags")
    public ResponseEntity<AllFlagsResponse> getAllFlags(@RequestParam String appId) {
        return ResponseEntity.ok(flagService.getAllFlags(appId));
    }
}
