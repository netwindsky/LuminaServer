package com.whale.lumina.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 认证会话
 * 
 * 表示用户的登录会话信息
 * 
 * @author Lumina Team
 */
public class AuthSession {

    private String sessionId;
    private String userId;
    private String accessToken;
    private String refreshToken;
    private String clientIP;
    private String userAgent;
    private long createdTime;
    private long lastActiveTime;
    private long expireTime;
    private SessionStatus status;

    public AuthSession() {
        this.status = SessionStatus.ACTIVE;
        this.createdTime = System.currentTimeMillis();
        this.lastActiveTime = this.createdTime;
    }

    // ========== Getters and Setters ==========

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getClientIP() {
        return clientIP;
    }

    public void setClientIP(String clientIP) {
        this.clientIP = clientIP;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    // ========== 业务方法 ==========

    /**
     * 检查会话是否已过期
     * 
     * @return 是否过期
     */
    @JsonIgnore
    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime || status == SessionStatus.EXPIRED;
    }

    /**
     * 检查会话是否活跃
     * 
     * @return 是否活跃
     */
    @JsonIgnore
    public boolean isActive() {
        return status == SessionStatus.ACTIVE && !isExpired();
    }

    /**
     * 更新最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * 延长会话过期时间
     * 
     * @param extendSeconds 延长的秒数
     */
    public void extendExpireTime(int extendSeconds) {
        this.expireTime = System.currentTimeMillis() + (extendSeconds * 1000L);
    }

    /**
     * 获取会话剩余时间（秒）
     * 
     * @return 剩余时间
     */
    @JsonIgnore
    public long getRemainingTimeSeconds() {
        long remaining = (expireTime - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * 获取会话持续时间（秒）
     * 
     * @return 持续时间
     */
    @JsonIgnore
    public long getDurationSeconds() {
        return (lastActiveTime - createdTime) / 1000;
    }

    /**
     * 获取空闲时间（秒）
     * 
     * @return 空闲时间
     */
    @JsonIgnore
    public long getIdleTimeSeconds() {
        return (System.currentTimeMillis() - lastActiveTime) / 1000;
    }

    // ========== 枚举定义 ==========

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        ACTIVE,     // 活跃
        INACTIVE,   // 非活跃
        EXPIRED,    // 已过期
        REVOKED     // 已撤销
    }

    // ========== Object方法重写 ==========

    @Override
    public String toString() {
        return "AuthSession{" +
                "sessionId='" + sessionId + '\'' +
                ", userId='" + userId + '\'' +
                ", clientIP='" + clientIP + '\'' +
                ", createdTime=" + createdTime +
                ", lastActiveTime=" + lastActiveTime +
                ", expireTime=" + expireTime +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuthSession that = (AuthSession) o;

        return sessionId != null ? sessionId.equals(that.sessionId) : that.sessionId == null;
    }

    @Override
    public int hashCode() {
        return sessionId != null ? sessionId.hashCode() : 0;
    }
}