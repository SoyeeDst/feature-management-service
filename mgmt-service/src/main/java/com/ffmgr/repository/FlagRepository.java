package com.ffmgr.repository;

import com.ffmgr.entity.Flag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlagRepository extends JpaRepository<Flag, Long> {
    List<Flag> findByAppIdAndStatus(String appId, Integer status);

    Optional<Flag> findByAppIdAndFlagKey(String appId, String flagKey);

    List<Flag> findByAppId(String appId);
}
