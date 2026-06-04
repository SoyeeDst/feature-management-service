CREATE DATABASE IF NOT EXISTS feature_management DEFAULT CHARACTER SET utf8mb4;

USE feature_management;

CREATE TABLE IF NOT EXISTS t_apps (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    app_id      VARCHAR(64)     NOT NULL,
    name        VARCHAR(256)    NOT NULL DEFAULT '',
    owner       VARCHAR(128)    NOT NULL DEFAULT '',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1=active, 0=disabled',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_app_id (app_id),
    INDEX idx_owner (owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_flags (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    app_id      VARCHAR(64)     NOT NULL,
    flag_key    VARCHAR(128)    NOT NULL,
    enabled     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'global enable/disable switch',
    targeting   JSON            NOT NULL COMMENT 'targeting rule tree',
    metadata    JSON            NOT NULL COMMENT '{"owner","release","description","tags","type"}',
    version     BIGINT          NOT NULL COMMENT 'monotonic version, per app_id monotonically increasing',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1=active, 0=archived',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_app_flag (app_id, flag_key),
    INDEX idx_app_version (app_id, version),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_audit_log (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    app_id      VARCHAR(64)     NOT NULL,
    flag_key    VARCHAR(128)    NOT NULL,
    event_type  VARCHAR(32)     NOT NULL COMMENT 'CREATE | UPDATE | DELETE | TOGGLE',
    diff        JSON            NOT NULL COMMENT '{"before":{...},"after":{...}}',
    changed_by  VARCHAR(128)    NOT NULL,
    changed_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     BIGINT          NOT NULL COMMENT 'flag version after this change',

    INDEX idx_app_flag_time (app_id, flag_key, changed_at),
    INDEX idx_changed_by (changed_by),
    INDEX idx_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
