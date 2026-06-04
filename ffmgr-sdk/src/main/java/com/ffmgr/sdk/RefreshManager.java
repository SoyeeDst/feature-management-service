package com.ffmgr.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class RefreshManager {

    private static final Logger log = LoggerFactory.getLogger(RefreshManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final FeatureFlagConfig config;
    private final InMemoryStore store;
    private final ScheduledExecutorService scheduler;

    RefreshManager(FeatureFlagConfig config, InMemoryStore store) {
        this.config = config;
        this.store = store;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ffmgr-refresh-" + config.getAppId());
            t.setDaemon(true);
            return t;
        });
    }

    void start() {
        scheduler.scheduleAtFixedRate(
                this::refresh,
                0,
                config.getRefreshIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    void stop() {
        scheduler.shutdown();
    }

    void refresh() {
        try {
            String url = config.getManagementServiceUrl()
                    + "/api/allFlags?appId=" + config.getAppId();

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + config.getJwtToken());
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status != 200) {
                log.warn("refresh failed: HTTP {}", status);
                return;
            }

            try (InputStream is = conn.getInputStream()) {
                AllFlagsResponse response = objectMapper.readValue(is, AllFlagsResponse.class);
                if (response != null && response.getFlags() != null) {
                    store.putAll(response.getFlags());
                    log.info("refreshed {} flags for appId={}", response.getFlags().size(), config.getAppId());
                }
            }
        } catch (Exception e) {
            log.warn("refresh error for appId={}: {}", config.getAppId(), e.getMessage());
        }
    }
}
