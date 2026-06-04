package com.ffmgr.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_flags")
public class Flag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "flag_key", nullable = false, length = 128)
    private String flagKey;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false, columnDefinition = "JSON")
    private String targeting;

    @Column(nullable = false, columnDefinition = "JSON")
    private String metadata;

    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = 1;
        }
        if (version == null) {
            version = 1L;
        }
        if (enabled == null) {
            enabled = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getTargeting() { return targeting; }
    public void setTargeting(String targeting) { this.targeting = targeting; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
