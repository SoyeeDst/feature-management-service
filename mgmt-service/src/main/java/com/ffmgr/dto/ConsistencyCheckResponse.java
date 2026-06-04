package com.ffmgr.dto;

import java.util.List;

public class ConsistencyCheckResponse {

    private String appId;
    private String checkedAt;
    private int mysqlCount;
    private int redisCount;
    private List<FlagDiff> diff;
    private List<String> onlyInMysql;
    private List<String> onlyInRedis;

    public static class FlagDiff {
        private String flagKey;
        private FlagState mysql;
        private FlagState redis;
        private String status;

        public String getFlagKey() { return flagKey; }
        public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
        public FlagState getMysql() { return mysql; }
        public void setMysql(FlagState mysql) { this.mysql = mysql; }
        public FlagState getRedis() { return redis; }
        public void setRedis(FlagState redis) { this.redis = redis; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class FlagState {
        private Boolean enabled;
        private Long version;
        private String updatedAt;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Long getVersion() { return version; }
        public void setVersion(Long version) { this.version = version; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getCheckedAt() { return checkedAt; }
    public void setCheckedAt(String checkedAt) { this.checkedAt = checkedAt; }
    public int getMysqlCount() { return mysqlCount; }
    public void setMysqlCount(int mysqlCount) { this.mysqlCount = mysqlCount; }
    public int getRedisCount() { return redisCount; }
    public void setRedisCount(int redisCount) { this.redisCount = redisCount; }
    public List<FlagDiff> getDiff() { return diff; }
    public void setDiff(List<FlagDiff> diff) { this.diff = diff; }
    public List<String> getOnlyInMysql() { return onlyInMysql; }
    public void setOnlyInMysql(List<String> onlyInMysql) { this.onlyInMysql = onlyInMysql; }
    public List<String> getOnlyInRedis() { return onlyInRedis; }
    public void setOnlyInRedis(List<String> onlyInRedis) { this.onlyInRedis = onlyInRedis; }
}
