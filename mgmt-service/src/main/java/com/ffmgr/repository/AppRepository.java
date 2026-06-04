package com.ffmgr.repository;

import com.ffmgr.entity.App;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRepository extends JpaRepository<App, Long> {
    Optional<App> findByAppId(String appId);
}
