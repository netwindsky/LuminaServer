package com.whale.lumina.signaling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 信令会话实体类
 * 
 * 表示一个WebRTC信令会话，包含会话的基本信息、参与者、状态等
 * 
 * @author Lumina Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignalingSession {

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("roomId")
    private String roomId;

    @JsonProperty("creatorId")
    private String creatorId;

    @JsonProperty("participants")
    private Set<String> participants;

    @JsonProperty("status")
    private SessionStatus status;

    @JsonProperty("sessionType")
    private SessionType sessionType;

    @JsonProperty("createdTime")
    private LocalDateTime createdTime;

    @JsonProperty("lastActiveTime")
    private LocalDateTime lastActiveTime;

    @JsonProperty("endTime")
    private LocalDateTime endTime;

    @JsonProperty("maxParticipants")
    private int maxParticipants;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("participantStates")
    private Map<String, ParticipantState> participantStates;

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        CREATED,        // 已创建
        ACTIVE,         // 活跃中
        PAUSED,         // 暂停
        ENDED,          // 已结束
        ERROR           // 错误状态
    }

    /**
     * 会话类型枚举
     */
    public enum SessionType {
        PEER_TO_PEER,   // 点对点
        MULTI_PARTY,    // 多方通话
        BROADCAST,      // 广播
        CONFERENCE      // 会议
    }

    /**
     * 参与者状态
     */
    public static class ParticipantState {
        @JsonProperty("userId")
        private String userId;

        @JsonProperty("joinTime")
        private LocalDateTime joinTime;

        @JsonProperty("lastActiveTime")
        private LocalDateTime lastActiveTime;

        @JsonProperty("connectionState")
        private ConnectionState connectionState;

        @JsonProperty("mediaState")
        private MediaState mediaState;

        @JsonProperty("metadata")
        private Map<String, Object> metadata;

        /**
         * 连接状态
         */
        public enum ConnectionState {
            CONNECTING,     // 连接中
            CONNECTED,      // 已连接
            DISCONNECTED,   // 已断开
            FAILED,         // 连接失败
            CLOSED          // 已关闭
        }

        /**
         * 媒体状态
         */
        public static class MediaState {
            @JsonProperty("audioEnabled")
            private boolean audioEnabled;

            @JsonProperty("videoEnabled")
            private boolean videoEnabled;

            @JsonProperty("screenShareEnabled")
            private boolean screenShareEnabled;

            public MediaState() {
                this.audioEnabled = true;
                this.videoEnabled = true;
                this.screenShareEnabled = false;
            }

            // Getters and Setters
            public boolean isAudioEnabled() { return audioEnabled; }
            public void setAudioEnabled(boolean audioEnabled) { this.audioEnabled = audioEnabled; }

            public boolean isVideoEnabled() { return videoEnabled; }
            public void setVideoEnabled(boolean videoEnabled) { this.videoEnabled = videoEnabled; }

            public boolean isScreenShareEnabled() { return screenShareEnabled; }
            public void setScreenShareEnabled(boolean screenShareEnabled) { this.screenShareEnabled = screenShareEnabled; }
        }

        /**
         * 默认构造函数
         */
        public ParticipantState() {
            this.joinTime = LocalDateTime.now();
            this.lastActiveTime = LocalDateTime.now();
            this.connectionState = ConnectionState.CONNECTING;
            this.mediaState = new MediaState();
            this.metadata = new HashMap<>();
        }

        /**
         * 构造函数
         */
        public ParticipantState(String userId) {
            this();
            this.userId = userId;
        }

        /**
         * 更新活跃时间
         */
        public void updateActiveTime() {
            this.lastActiveTime = LocalDateTime.now();
        }

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public LocalDateTime getJoinTime() { return joinTime; }
        public void setJoinTime(LocalDateTime joinTime) { this.joinTime = joinTime; }

        public LocalDateTime getLastActiveTime() { return lastActiveTime; }
        public void setLastActiveTime(LocalDateTime lastActiveTime) { this.lastActiveTime = lastActiveTime; }

        public ConnectionState getConnectionState() { return connectionState; }
        public void setConnectionState(ConnectionState connectionState) { this.connectionState = connectionState; }

        public MediaState getMediaState() { return mediaState; }
        public void setMediaState(MediaState mediaState) { this.mediaState = mediaState; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * 默认构造函数
     */
    public SignalingSession() {
        this.participants = new HashSet<>();
        this.status = SessionStatus.CREATED;
        this.sessionType = SessionType.MULTI_PARTY;
        this.createdTime = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
        this.maxParticipants = 10;
        this.metadata = new HashMap<>();
        this.participantStates = new ConcurrentHashMap<>();
    }

    /**
     * 构造函数
     */
    public SignalingSession(String sessionId, String roomId, String creatorId) {
        this();
        this.sessionId = sessionId;
        this.roomId = roomId;
        this.creatorId = creatorId;
        
        // 创建者自动加入会话
        addParticipant(creatorId);
    }

    /**
     * 添加参与者
     */
    public boolean addParticipant(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        if (participants.size() >= maxParticipants) {
            return false;
        }

        if (participants.add(userId)) {
            participantStates.put(userId, new ParticipantState(userId));
            updateActiveTime();
            return true;
        }

        return false;
    }

    /**
     * 移除参与者
     */
    public boolean removeParticipant(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        if (participants.remove(userId)) {
            participantStates.remove(userId);
            updateActiveTime();
            
            // 如果没有参与者了，结束会话
            if (participants.isEmpty()) {
                endSession();
            }
            
            return true;
        }

        return false;
    }

    /**
     * 检查用户是否是参与者
     */
    public boolean isParticipant(String userId) {
        return participants.contains(userId);
    }

    /**
     * 获取参与者数量
     */
    public int getParticipantCount() {
        return participants.size();
    }

    /**
     * 检查是否可以添加更多参与者
     */
    public boolean canAddParticipant() {
        return participants.size() < maxParticipants;
    }

    /**
     * 更新活跃时间
     */
    public void updateActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }

    /**
     * 更新参与者活跃时间
     */
    public void updateParticipantActiveTime(String userId) {
        ParticipantState state = participantStates.get(userId);
        if (state != null) {
            state.updateActiveTime();
        }
        updateActiveTime();
    }

    /**
     * 获取参与者状态
     */
    public ParticipantState getParticipantState(String userId) {
        return participantStates.get(userId);
    }

    /**
     * 更新参与者连接状态
     */
    public void updateParticipantConnectionState(String userId, ParticipantState.ConnectionState connectionState) {
        ParticipantState state = participantStates.get(userId);
        if (state != null) {
            state.setConnectionState(connectionState);
            state.updateActiveTime();
            updateActiveTime();
        }
    }

    /**
     * 更新参与者媒体状态
     */
    public void updateParticipantMediaState(String userId, ParticipantState.MediaState mediaState) {
        ParticipantState state = participantStates.get(userId);
        if (state != null) {
            state.setMediaState(mediaState);
            state.updateActiveTime();
            updateActiveTime();
        }
    }

    /**
     * 检查会话是否活跃
     */
    public boolean isActive() {
        return status == SessionStatus.ACTIVE && !participants.isEmpty();
    }

    /**
     * 检查会话是否已结束
     */
    public boolean isEnded() {
        return status == SessionStatus.ENDED || endTime != null;
    }

    /**
     * 激活会话
     */
    public void activateSession() {
        if (status == SessionStatus.CREATED || status == SessionStatus.PAUSED) {
            this.status = SessionStatus.ACTIVE;
            updateActiveTime();
        }
    }

    /**
     * 暂停会话
     */
    public void pauseSession() {
        if (status == SessionStatus.ACTIVE) {
            this.status = SessionStatus.PAUSED;
            updateActiveTime();
        }
    }

    /**
     * 结束会话
     */
    public void endSession() {
        this.status = SessionStatus.ENDED;
        this.endTime = LocalDateTime.now();
        updateActiveTime();
    }

    /**
     * 设置错误状态
     */
    public void setErrorStatus() {
        this.status = SessionStatus.ERROR;
        updateActiveTime();
    }

    /**
     * 获取会话持续时间（分钟）
     */
    public long getDurationMinutes() {
        LocalDateTime endTimeToUse = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(createdTime, endTimeToUse).toMinutes();
    }

    /**
     * 获取会话空闲时间（分钟）
     */
    public long getIdleMinutes() {
        return java.time.Duration.between(lastActiveTime, LocalDateTime.now()).toMinutes();
    }

    /**
     * 检查会话是否空闲超时
     */
    public boolean isIdleTimeout(long timeoutMinutes) {
        return getIdleMinutes() > timeoutMinutes;
    }

    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 移除元数据
     */
    public void removeMetadata(String key) {
        metadata.remove(key);
    }

    /**
     * 获取连接状态统计
     */
    public Map<ParticipantState.ConnectionState, Integer> getConnectionStateStats() {
        Map<ParticipantState.ConnectionState, Integer> stats = new HashMap<>();
        
        for (ParticipantState state : participantStates.values()) {
            ParticipantState.ConnectionState connectionState = state.getConnectionState();
            stats.put(connectionState, stats.getOrDefault(connectionState, 0) + 1);
        }
        
        return stats;
    }

    /**
     * 获取活跃参与者列表
     */
    public List<String> getActiveParticipants() {
        List<String> activeParticipants = new ArrayList<>();
        
        for (Map.Entry<String, ParticipantState> entry : participantStates.entrySet()) {
            ParticipantState.ConnectionState state = entry.getValue().getConnectionState();
            if (state == ParticipantState.ConnectionState.CONNECTED || 
                state == ParticipantState.ConnectionState.CONNECTING) {
                activeParticipants.add(entry.getKey());
            }
        }
        
        return activeParticipants;
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }

    public Set<String> getParticipants() { return new HashSet<>(participants); }
    public void setParticipants(Set<String> participants) { this.participants = participants; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public SessionType getSessionType() { return sessionType; }
    public void setSessionType(SessionType sessionType) { this.sessionType = sessionType; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getLastActiveTime() { return lastActiveTime; }
    public void setLastActiveTime(LocalDateTime lastActiveTime) { this.lastActiveTime = lastActiveTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Map<String, ParticipantState> getParticipantStates() { return participantStates; }
    public void setParticipantStates(Map<String, ParticipantState> participantStates) { this.participantStates = participantStates; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignalingSession that = (SignalingSession) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

    @Override
    public String toString() {
        return String.format("SignalingSession{sessionId='%s', roomId='%s', creatorId='%s', " +
                           "participantCount=%d, status=%s, sessionType=%s, createdTime=%s}",
                           sessionId, roomId, creatorId, participants.size(), status, sessionType, createdTime);
    }
}