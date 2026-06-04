package com.ffmgr.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "flag_key", nullable = false, length = 128)
    private String flagKey;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false, columnDefinition = "JSON")
    private String diff;

    @Column(name = "changed_by", nullable = false, length = 128)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDiff() { return diff; }
    public void setDiff(String diff) { this.diff = diff; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
