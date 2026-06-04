package com.ffmgr.controller;

import com.ffmgr.dto.CreateAppRequest;
import com.ffmgr.dto.UpdateAppRequest;
import com.ffmgr.entity.App;
import com.ffmgr.repository.AppRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
public class AppController {

    private final AppRepository appRepository;

    public AppController(AppRepository appRepository) {
        this.appRepository = appRepository;
    }

    @PostMapping
    public ResponseEntity<App> create(@RequestBody CreateAppRequest req) {
        App app = new App();
        app.setAppId(req.getAppId());
        app.setName(req.getName() != null ? req.getName() : "");
        app.setOwner(req.getOwner() != null ? req.getOwner() : "");
        app.setStatus(1);
        return ResponseEntity.ok(appRepository.save(app));
    }

    @PutMapping("/{appId}")
    public ResponseEntity<App> update(@PathVariable String appId,
                                      @RequestBody UpdateAppRequest req) {
        return appRepository.findByAppId(appId)
                .map(app -> {
                    if (req.getName() != null) app.setName(req.getName());
                    if (req.getOwner() != null) app.setOwner(req.getOwner());
                    return ResponseEntity.ok(appRepository.save(app));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{appId}")
    public ResponseEntity<Void> delete(@PathVariable String appId) {
        appRepository.findByAppId(appId).ifPresent(appRepository::delete);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<App>> list() {
        return ResponseEntity.ok(appRepository.findAll());
    }

    @GetMapping("/{appId}")
    public ResponseEntity<App> get(@PathVariable String appId) {
        return appRepository.findByAppId(appId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
