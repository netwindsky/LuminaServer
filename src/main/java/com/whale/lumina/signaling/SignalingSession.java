package com.whale.lumina.signaling;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 信令会话类
 *
 * 代表一个WebRTC信令会话，管理参与者、消息和会话状态
 *
 * @author Lumina Team
 */
public class SignalingSession {

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        PENDING,    // 等待中
        ACTIVE,     // 活跃中
        PAUSED,     // 已暂停
        ENDED,      // 已结束
        CLOSED,     // 已关闭
        CONNECTING  // 连接中
    }

    /**
     * 会话类型枚举
     */
    public enum SessionType {
        PEER_TO_PEER,       // 点对点
        MESH,               // 网状
        SFU,                // 选择性转发单元
        MCU                 // 多点控制单元
    }

    // 会话基本信息
    private String sessionId;           // 会话ID
    private String roomId;              // 房间ID
    private String creatorId;           // 创建者ID
    private Set<String> participants;   // 参与者ID集合
    private SessionStatus status;       // 会话状态
    private SessionType sessionType;    // 会话类型
    private long createdTime;           // 创建时间戳
    private long activeTime;            // 最后活跃时间戳

    // 信令消息队列
    private Queue<SignalingMessage> messages;
    private static final int MAX_MESSAGES = 1000; // 最大消息数量限制

    // 会话配置
    private Map<String, Object> config; // 会话配置参数

    // 统计信息
    private long messageCount;          // 消息总数
    private long dataTransferBytes;     // 数据传输字节数

    /**
     * 构造函数
     */
    public SignalingSession() {
        this.participants = new HashSet<>();
        this.messages = new ConcurrentLinkedQueue<>();
        this.config = new HashMap<>();
        this.createdTime = System.currentTimeMillis();
        this.activeTime = this.createdTime;
        this.status = SessionStatus.PENDING;
        this.sessionType = SessionType.PEER_TO_PEER;
    }

    /**
     * 构造函数
     *
     * @param sessionId 会话ID
     * @param roomId 房间ID
     * @param creatorId 创建者ID
     */
    public SignalingSession(String sessionId, String roomId, String creatorId) {
        this();
        this.sessionId = sessionId;
        this.roomId = roomId;
        this.creatorId = creatorId;
        if (creatorId != null) {
            this.participants.add(creatorId);
        }
    }

    // ========== Getters and Setters ==========

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public Set<String> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<String> participants) {
        this.participants = participants != null ? participants : new HashSet<>();
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(SessionType sessionType) {
        this.sessionType = sessionType;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getActiveTime() {
        return activeTime;
    }

    public void setActiveTime(long activeTime) {
        this.activeTime = activeTime;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config != null ? config : new HashMap<>();
    }

    public long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(long messageCount) {
        this.messageCount = messageCount;
    }

    public long getDataTransferBytes() {
        return dataTransferBytes;
    }

    public void setDataTransferBytes(long dataTransferBytes) {
        this.dataTransferBytes = dataTransferBytes;
    }

    // ========== 业务方法 ==========

    /**
     * 添加参与者
     *
     * @param userId 用户ID
     * @return 是否添加成功
     */
    public boolean addParticipant(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        boolean added = participants.add(userId);
        if (added) {
            updateActiveTime();
        }
        return added;
    }

    /**
     * 移除参与者
     *
     * @param userId 用户ID
     * @return 是否移除成功
     */
    public boolean removeParticipant(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        boolean removed = participants.remove(userId);
        if (removed) {
            updateActiveTime();
        }
        return removed;
    }

    /**
     * 检查是否为参与者
     *
     * @param userId 用户ID
     * @return 是否为参与者
     */
    public boolean isParticipant(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        return participants.contains(userId);
    }

    /**
     * 获取参与者数量
     *
     * @return 参与者数量
     */
    @JsonIgnore
    public int getParticipantCount() {
        return participants.size();
    }

    /**
     * 更新活跃时间
     */
    public void updateActiveTime() {
        this.activeTime = System.currentTimeMillis();
    }

    /**
     * 获取会话持续时间（分钟）
     *
     * @return 持续时间（分钟）
     */
    @JsonIgnore
    public long getDurationMinutes() {
        return (System.currentTimeMillis() - createdTime) / 60000;
    }

    /**
     * 检查是否超时
     *
     * @param timeoutMinutes 超时时间（分钟）
     * @return 是否超时
     */
    public boolean isIdleTimeout(long timeoutMinutes) {
        return getDurationMinutes() > timeoutMinutes;
    }

    /**
     * 检查会话是否活跃
     *
     * @return 是否活跃
     */
    @JsonIgnore
    public boolean isActive() {
        return status == SessionStatus.ACTIVE || status == SessionStatus.CONNECTING;
    }

    /**
     * 开始会话
     */
    public void startSession() {
        this.status = SessionStatus.ACTIVE;
        updateActiveTime();
    }

    /**
     * 结束会话
     */
    public void endSession() {
        this.status = SessionStatus.CLOSED;
        clearMessages();
        updateActiveTime();
    }

    /**
     * 添加消息到会话
     */
    public void addMessage(SignalingMessage message) {
        if (message == null) {
            return;
        }

        messages.offer(message);

        // 保持消息数量在限制内
        while (messages.size() > MAX_MESSAGES) {
            messages.poll();
        }

        updateActiveTime();
    }

    /**
     * 获取最近的消息
     */
    public List<SignalingMessage> getRecentMessages(int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }

        List<SignalingMessage> messageList = new ArrayList<>(messages);
        int size = messageList.size();

        if (size <= count) {
            return messageList;
        }

        return messageList.subList(size - count, size);
    }

    /**
     * 获取所有消息
     */
    public List<SignalingMessage> getAllMessages() {
        return new ArrayList<>(messages);
    }

    /**
     * 清空消息
     */
    public void clearMessages() {
        messages.clear();
    }

    /**
     * 获取消息数量
     */
    public int getMessageCountInternal() {
        return messages.size();
    }

    // 方法别名，用于兼容SignalingHandler中的调用

    /**
     * 结束会话 - endSession的别名
     */
    public void end() {
        endSession();
    }

    /**
     * 获取持续时间 - getDurationMinutes的别名
     */
    public long getDuration() {
        return getDurationMinutes();
    }

    /**
     * 检查是否有参与者 - isParticipant的别名
     */
    public boolean hasParticipant(String userId) {
        return isParticipant(userId);
    }

    /**
     * 检查是否过期 - isIdleTimeout的别名
     */
    public boolean isExpired(long timeoutMinutes) {
        return isIdleTimeout(timeoutMinutes);
    }

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