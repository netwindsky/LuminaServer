package com.whale.lumina.signaling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 信令消息实体类
 * 
 * 表示WebRTC信令过程中的各种消息类型
 * 
 * @author Lumina Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignalingMessage {

    @JsonProperty("id")
    private String id;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("type")
    private Type type;

    @JsonProperty("fromUserId")
    private String fromUserId;

    @JsonProperty("toUserId")
    private String toUserId;

    @JsonProperty("data")
    private Map<String, Object> data;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("priority")
    private Priority priority;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * 消息类型枚举
     */
    public enum Type {
        // WebRTC 标准消息
        OFFER,              // SDP Offer
        ANSWER,             // SDP Answer
        CANDIDATE,          // ICE Candidate
        
        // 会话控制消息
        JOIN_SESSION,       // 加入会话
        LEAVE_SESSION,      // 离开会话
        SESSION_CREATED,    // 会话已创建
        SESSION_ENDED,      // 会话已结束
        
        // 媒体控制消息
        MEDIA_STATE_CHANGE, // 媒体状态变更
        AUDIO_TOGGLE,       // 音频开关
        VIDEO_TOGGLE,       // 视频开关
        SCREEN_SHARE_START, // 开始屏幕共享
        SCREEN_SHARE_STOP,  // 停止屏幕共享
        
        // 连接状态消息
        CONNECTION_STATE,   // 连接状态
        PEER_CONNECTED,     // 对等端已连接
        PEER_DISCONNECTED,  // 对等端已断开
        
        // 自定义消息
        CUSTOM,             // 自定义消息
        
        // 系统消息
        HEARTBEAT,          // 心跳
        ERROR,              // 错误消息
        ACK                 // 确认消息
    }

    /**
     * 消息优先级枚举
     */
    public enum Priority {
        LOW,        // 低优先级
        NORMAL,     // 普通优先级
        HIGH,       // 高优先级
        URGENT      // 紧急优先级
    }

    /**
     * 默认构造函数
     */
    public SignalingMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.priority = Priority.NORMAL;
        this.data = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    /**
     * 构造函数
     */
    public SignalingMessage(String sessionId, Type type, String fromUserId, String toUserId) {
        this();
        this.sessionId = sessionId;
        this.type = type;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
    }

    /**
     * 构造函数（带数据）
     */
    public SignalingMessage(String sessionId, Type type, String fromUserId, String toUserId, Map<String, Object> data) {
        this(sessionId, type, fromUserId, toUserId);
        if (data != null) {
            this.data.putAll(data);
        }
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建Offer消息
     */
    public static SignalingMessage createOffer(String sessionId, String fromUserId, String toUserId, String sdp) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.OFFER, fromUserId, toUserId);
        message.addData("sdp", sdp);
        message.setPriority(Priority.HIGH);
        return message;
    }

    /**
     * 创建Answer消息
     */
    public static SignalingMessage createAnswer(String sessionId, String fromUserId, String toUserId, String sdp) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.ANSWER, fromUserId, toUserId);
        message.addData("sdp", sdp);
        message.setPriority(Priority.HIGH);
        return message;
    }

    /**
     * 创建ICE Candidate消息
     */
    public static SignalingMessage createCandidate(String sessionId, String fromUserId, String toUserId, 
                                                  String candidate, String sdpMid, int sdpMLineIndex) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.CANDIDATE, fromUserId, toUserId);
        message.addData("candidate", candidate);
        message.addData("sdpMid", sdpMid);
        message.addData("sdpMLineIndex", sdpMLineIndex);
        message.setPriority(Priority.HIGH);
        return message;
    }

    /**
     * 创建加入会话消息
     */
    public static SignalingMessage createJoinSession(String sessionId, String userId) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.JOIN_SESSION, userId, null);
        message.setPriority(Priority.HIGH);
        return message;
    }

    /**
     * 创建离开会话消息
     */
    public static SignalingMessage createLeaveSession(String sessionId, String userId) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.LEAVE_SESSION, userId, null);
        message.setPriority(Priority.HIGH);
        return message;
    }

    /**
     * 创建媒体状态变更消息
     */
    public static SignalingMessage createMediaStateChange(String sessionId, String fromUserId, 
                                                         boolean audioEnabled, boolean videoEnabled, boolean screenShareEnabled) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.MEDIA_STATE_CHANGE, fromUserId, null);
        message.addData("audioEnabled", audioEnabled);
        message.addData("videoEnabled", videoEnabled);
        message.addData("screenShareEnabled", screenShareEnabled);
        return message;
    }

    /**
     * 创建连接状态消息
     */
    public static SignalingMessage createConnectionState(String sessionId, String fromUserId, String connectionState) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.CONNECTION_STATE, fromUserId, null);
        message.addData("connectionState", connectionState);
        return message;
    }

    /**
     * 创建心跳消息
     */
    public static SignalingMessage createHeartbeat(String sessionId, String userId) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.HEARTBEAT, userId, null);
        message.setPriority(Priority.LOW);
        return message;
    }

    /**
     * 创建错误消息
     */
    public static SignalingMessage createError(String sessionId, String fromUserId, String errorCode, String errorMessage) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.ERROR, fromUserId, null);
        message.addData("errorCode", errorCode);
        message.addData("errorMessage", errorMessage);
        message.setPriority(Priority.URGENT);
        return message;
    }

    /**
     * 创建确认消息
     */
    public static SignalingMessage createAck(String sessionId, String fromUserId, String toUserId, String originalMessageId) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.ACK, fromUserId, toUserId);
        message.addData("originalMessageId", originalMessageId);
        message.setPriority(Priority.LOW);
        return message;
    }

    /**
     * 创建自定义消息
     */
    public static SignalingMessage createCustom(String sessionId, String fromUserId, String toUserId, 
                                               String customType, Map<String, Object> customData) {
        SignalingMessage message = new SignalingMessage(sessionId, Type.CUSTOM, fromUserId, toUserId);
        message.addData("customType", customType);
        if (customData != null) {
            message.addData("customData", customData);
        }
        return message;
    }

    // ========== 数据操作方法 ==========

    /**
     * 添加数据
     */
    public void addData(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 获取数据
     */
    public Object getData(String key) {
        return data.get(key);
    }

    /**
     * 获取字符串数据
     */
    public String getStringData(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取整数数据
     */
    public Integer getIntData(String key) {
        Object value = data.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * 获取布尔数据
     */
    public Boolean getBooleanData(String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }

    /**
     * 移除数据
     */
    public void removeData(String key) {
        data.remove(key);
    }

    /**
     * 检查是否包含数据
     */
    public boolean hasData(String key) {
        return data.containsKey(key);
    }

    // ========== 元数据操作方法 ==========

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

    // ========== 消息验证方法 ==========

    /**
     * 验证消息是否有效
     */
    public boolean isValid() {
        return id != null && !id.isEmpty() &&
               sessionId != null && !sessionId.isEmpty() &&
               type != null &&
               timestamp != null;
    }

    /**
     * 检查是否是WebRTC相关消息
     */
    public boolean isWebRTCMessage() {
        return type == Type.OFFER || type == Type.ANSWER || type == Type.CANDIDATE;
    }

    /**
     * 检查是否是会话控制消息
     */
    public boolean isSessionControlMessage() {
        return type == Type.JOIN_SESSION || type == Type.LEAVE_SESSION ||
               type == Type.SESSION_CREATED || type == Type.SESSION_ENDED;
    }

    /**
     * 检查是否是媒体控制消息
     */
    public boolean isMediaControlMessage() {
        return type == Type.MEDIA_STATE_CHANGE || type == Type.AUDIO_TOGGLE ||
               type == Type.VIDEO_TOGGLE || type == Type.SCREEN_SHARE_START ||
               type == Type.SCREEN_SHARE_STOP;
    }

    /**
     * 检查是否是系统消息
     */
    public boolean isSystemMessage() {
        return type == Type.HEARTBEAT || type == Type.ERROR || type == Type.ACK;
    }

    /**
     * 检查是否是点对点消息
     */
    public boolean isPeerToPeerMessage() {
        return toUserId != null && !toUserId.isEmpty();
    }

    /**
     * 检查是否是广播消息
     */
    public boolean isBroadcastMessage() {
        return toUserId == null || toUserId.isEmpty();
    }

    /**
     * 检查消息是否过期
     */
    public boolean isExpired(long timeoutMinutes) {
        LocalDateTime expireTime = timestamp.plusMinutes(timeoutMinutes);
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 获取消息年龄（分钟）
     */
    public long getAgeMinutes() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
    }

    /**
     * 创建消息的副本
     */
    public SignalingMessage copy() {
        SignalingMessage copy = new SignalingMessage();
        copy.id = UUID.randomUUID().toString(); // 新的ID
        copy.sessionId = this.sessionId;
        copy.type = this.type;
        copy.fromUserId = this.fromUserId;
        copy.toUserId = this.toUserId;
        copy.data = new HashMap<>(this.data);
        copy.timestamp = LocalDateTime.now(); // 新的时间戳
        copy.priority = this.priority;
        copy.metadata = new HashMap<>(this.metadata);
        return copy;
    }

    // ========== Getters and Setters ==========

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignalingMessage that = (SignalingMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("SignalingMessage{id='%s', sessionId='%s', type=%s, fromUserId='%s', " +
                           "toUserId='%s', priority=%s, timestamp=%s}",
                           id, sessionId, type, fromUserId, toUserId, priority, timestamp);
    }
}