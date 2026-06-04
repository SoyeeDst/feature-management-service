package com.ffmgr.controller;

import com.ffmgr.dto.CreateFlagRequest;
import com.ffmgr.dto.PatchFlagRequest;
import com.ffmgr.dto.UpdateFlagRequest;
import com.ffmgr.entity.AuditLog;
import com.ffmgr.entity.Flag;
import com.ffmgr.service.FlagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flags")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    @PostMapping
    public ResponseEntity<Flag> create(@RequestBody CreateFlagRequest req) {
        Flag flag = flagService.createFlag(
                req.getAppId(),
                req.getFlagKey(),
                req.getEnabled(),
                req.getTargetingJson(),
                req.getMetadataJson(),
                req.getChangedBy() != null ? req.getChangedBy() : "admin"
        );
        return ResponseEntity.ok(flag);
    }

    @PutMapping("/{flagKey}")
    public ResponseEntity<Flag> update(@PathVariable String flagKey,
                                       @RequestBody UpdateFlagRequest req) {
        Flag flag = flagService.updateFlag(
                req.getAppId(),
                flagKey,
                req.getEnabled(),
                req.getTargetingJson(),
                req.getMetadataJson(),
                req.getChangedBy() != null ? req.getChangedBy() : "admin"
        );
        return ResponseEntity.ok(flag);
    }

    @PatchMapping("/{flagKey}")
    public ResponseEntity<Flag> patch(@PathVariable String flagKey,
                                      @RequestBody PatchFlagRequest req) {
        String changedBy = req.getChangedBy() != null ? req.getChangedBy() : "admin";
        Flag flag = flagService.patchFlag(
                req.getAppId(),
                flagKey,
                req.toChangeMap(),
                changedBy
        );
        return ResponseEntity.ok(flag);
    }

    @DeleteMapping("/{flagKey}")
    public ResponseEntity<Void> delete(@PathVariable String flagKey,
                                       @RequestParam String appId,
                                       @RequestParam(defaultValue = "admin") String changedBy) {
        flagService.deleteFlag(appId, flagKey, changedBy);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Flag>> list(@RequestParam(required = false) String appId) {
        if (appId != null) {
            List<Flag> flags = flagService.listFlags(appId);
            return ResponseEntity.ok(flags);
        }
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }

    @GetMapping("/{flagKey}")
    public ResponseEntity<Flag> get(@PathVariable String flagKey,
                                    @RequestParam String appId) {
        return flagService.getFlag(appId, flagKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{flagKey}/history")
    public ResponseEntity<List<AuditLog>> history(@PathVariable String flagKey,
                                                   @RequestParam String appId) {
        return ResponseEntity.ok(flagService.getFlagHistory(appId, flagKey));
    }
}
