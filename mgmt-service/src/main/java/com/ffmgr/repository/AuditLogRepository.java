package com.ffmgr.repository;

import com.ffmgr.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByAppIdAndFlagKeyOrderByChangedAtDesc(String appId, String flagKey);
}
