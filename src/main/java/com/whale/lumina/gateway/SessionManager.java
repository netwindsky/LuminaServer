package com.whale.lumina.gateway;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import com.whale.lumina.common.TimeUtils;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话管理器
 * 
 * 负责管理所有客户端会话，包括会话注册、查找、清理和统计
 * 
 * @author Lumina Team
 */
@Component
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    // 会话存储：sessionId -> IoSession
    private final Map<Long, IoSession> sessions = new ConcurrentHashMap<>();
    
    // 玩家会话映射：playerId -> sessionId
    private final Map<String, Long> playerSessions = new ConcurrentHashMap<>();
    
    // 会话玩家映射：sessionId -> playerId
    private final Map<Long, String> sessionPlayers = new ConcurrentHashMap<>();
    
    // 会话属性存储：sessionId -> attributes
    private final Map<Long, SessionAttributes> sessionAttributes = new ConcurrentHashMap<>();

    @Value("${game.gateway.session.cleanup-interval:60000}")
    private long cleanupInterval;

    @Value("${game.gateway.session.max-idle-time:300000}")
    private long maxIdleTime;

    // 统计信息
    private final AtomicLong totalSessionCount = new AtomicLong(0);
    private final AtomicLong activeSessionCount = new AtomicLong(0);

    // ========== 会话管理 ==========

    /**
     * 添加会话
     * 
     * @param session IO会话
     */
    public void addSession(IoSession session) {
        long sessionId = session.getId();
        
        sessions.put(sessionId, session);
        sessionAttributes.put(sessionId, new SessionAttributes(sessionId));
        
        totalSessionCount.incrementAndGet();
        activeSessionCount.incrementAndGet();
        
        logger.debug("会话已添加: sessionId={}, 当前活跃会话数: {}", sessionId, activeSessionCount.get());
    }

    /**
     * 移除会话
     * 
     * @param session IO会话
     */
    public void removeSession(IoSession session) {
        long sessionId = session.getId();
        
        // 移除会话
        sessions.remove(sessionId);
        SessionAttributes attributes = sessionAttributes.remove(sessionId);
        
        // 如果会话绑定了玩家，也要移除玩家映射
        if (attributes != null && attributes.getPlayerId() != null) {
            String playerId = attributes.getPlayerId();
            playerSessions.remove(playerId);
            sessionPlayers.remove(sessionId);
            logger.debug("会话解绑玩家: sessionId={}, playerId={}", sessionId, playerId);
        }
        
        activeSessionCount.decrementAndGet();
        
        logger.debug("会话已移除: sessionId={}, 当前活跃会话数: {}", sessionId, activeSessionCount.get());
    }

    /**
     * 获取会话
     * 
     * @param sessionId 会话ID
     * @return IO会话
     */
    public IoSession getSession(long sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 检查会话是否存在
     * 
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean hasSession(long sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 获取所有活跃会话ID
     * 
     * @return 会话ID集合
     */
    public java.util.Set<Long> getActiveSessionIds() {
        return sessions.keySet();
    }

    // ========== 玩家会话绑定 ==========

    /**
     * 绑定玩家到会话
     * 
     * @param sessionId 会话ID
     * @param playerId 玩家ID
     */
    public void bindPlayer(long sessionId, String playerId) {
        if (!sessions.containsKey(sessionId)) {
            throw new GameException(ErrorCodes.GATEWAY_SESSION_NOT_FOUND);
        }

        // 检查玩家是否已经绑定到其他会话
        Long existingSessionId = playerSessions.get(playerId);
        if (existingSessionId != null && !existingSessionId.equals(sessionId)) {
            // 踢掉旧会话
            logger.warn("玩家重复登录，踢掉旧会话: playerId={}, oldSessionId={}, newSessionId={}", 
                       playerId, existingSessionId, sessionId);
            closeSession(existingSessionId);
        }

        // 建立双向映射
        playerSessions.put(playerId, sessionId);
        sessionPlayers.put(sessionId, playerId);
        
        // 更新会话属性
        SessionAttributes attributes = sessionAttributes.get(sessionId);
        if (attributes != null) {
            attributes.setPlayerId(playerId);
            attributes.setBindTime(System.currentTimeMillis());
        }

        logger.debug("玩家绑定到会话: playerId={}, sessionId={}", playerId, sessionId);
    }

    /**
     * 解绑玩家会话
     * 
     * @param sessionId 会话ID
     */
    public void unbindPlayer(long sessionId) {
        String playerId = sessionPlayers.remove(sessionId);
        if (playerId != null) {
            playerSessions.remove(playerId);
            
            // 更新会话属性
            SessionAttributes attributes = sessionAttributes.get(sessionId);
            if (attributes != null) {
                attributes.setPlayerId(null);
                attributes.setBindTime(0);
            }
            
            logger.debug("玩家会话已解绑: playerId={}, sessionId={}", playerId, sessionId);
        }
    }

    /**
     * 根据玩家ID获取会话
     * 
     * @param playerId 玩家ID
     * @return IO会话
     */
    public IoSession getPlayerSession(String playerId) {
        Long sessionId = playerSessions.get(playerId);
        return sessionId != null ? sessions.get(sessionId) : null;
    }

    /**
     * 根据会话ID获取玩家ID
     * 
     * @param sessionId 会话ID
     * @return 玩家ID
     */
    public String getSessionPlayer(long sessionId) {
        return sessionPlayers.get(sessionId);
    }

    /**
     * 检查玩家是否在线
     * 
     * @param playerId 玩家ID
     * @return 是否在线
     */
    public boolean isPlayerOnline(String playerId) {
        Long sessionId = playerSessions.get(playerId);
        return sessionId != null && sessions.containsKey(sessionId);
    }

    // ========== 会话属性管理 ==========

    /**
     * 获取会话属性
     * 
     * @param sessionId 会话ID
     * @return 会话属性
     */
    public SessionAttributes getSessionAttributes(long sessionId) {
        return sessionAttributes.get(sessionId);
    }

    /**
     * 设置会话属性
     * 
     * @param sessionId 会话ID
     * @param key 属性键
     * @param value 属性值
     */
    public void setSessionAttribute(long sessionId, String key, Object value) {
        SessionAttributes attributes = sessionAttributes.get(sessionId);
        if (attributes != null) {
            attributes.setAttribute(key, value);
        }
    }

    /**
     * 获取会话属性值
     * 
     * @param sessionId 会话ID
     * @param key 属性键
     * @return 属性值
     */
    public Object getSessionAttribute(long sessionId, String key) {
        SessionAttributes attributes = sessionAttributes.get(sessionId);
        return attributes != null ? attributes.getAttribute(key) : null;
    }

    // ========== 消息发送 ==========

    /**
     * 向指定会话发送消息
     * 
     * @param sessionId 会话ID
     * @param message 消息对象
     * @return 是否发送成功
     */
    public boolean sendMessage(long sessionId, Object message) {
        IoSession session = sessions.get(sessionId);
        if (session != null && session.isConnected()) {
            try {
                session.write(message);
                return true;
            } catch (Exception e) {
                logger.error("发送消息失败: sessionId={}", sessionId, e);
                return false;
            }
        }
        return false;
    }

    /**
     * 向指定玩家发送消息
     * 
     * @param playerId 玩家ID
     * @param message 消息对象
     * @return 是否发送成功
     */
    public boolean sendMessageToPlayer(String playerId, Object message) {
        Long sessionId = playerSessions.get(playerId);
        return sessionId != null && sendMessage(sessionId, message);
    }

    /**
     * 广播消息给所有会话
     * 
     * @param message 消息对象
     */
    public void broadcast(Object message) {
        int successCount = 0;
        int failureCount = 0;
        
        for (IoSession session : sessions.values()) {
            if (session.isConnected()) {
                try {
                    session.write(message);
                    successCount++;
                } catch (Exception e) {
                    logger.error("广播消息失败: sessionId={}", session.getId(), e);
                    failureCount++;
                }
            }
        }
        
        logger.debug("广播消息完成: 成功={}, 失败={}", successCount, failureCount);
    }

    /**
     * 向指定玩家列表发送消息
     * 
     * @param playerIds 玩家ID列表
     * @param message 消息对象
     * @return 发送成功的数量
     */
    public int sendMessageToPlayers(java.util.List<String> playerIds, Object message) {
        int successCount = 0;
        
        for (String playerId : playerIds) {
            if (sendMessageToPlayer(playerId, message)) {
                successCount++;
            }
        }
        
        return successCount;
    }

    // ========== 会话控制 ==========

    /**
     * 关闭指定会话
     * 
     * @param sessionId 会话ID
     * @return 是否关闭成功
     */
    public boolean closeSession(long sessionId) {
        IoSession session = sessions.get(sessionId);
        if (session != null) {
            try {
                session.closeNow();
                return true;
            } catch (Exception e) {
                logger.error("关闭会话失败: sessionId={}", sessionId, e);
                return false;
            }
        }
        return false;
    }

    /**
     * 踢掉指定玩家
     * 
     * @param playerId 玩家ID
     * @return 是否踢掉成功
     */
    public boolean kickPlayer(String playerId) {
        Long sessionId = playerSessions.get(playerId);
        return sessionId != null && closeSession(sessionId);
    }

    // ========== 定时清理 ==========

    /**
     * 定时清理无效会话
     */
    @Scheduled(fixedDelayString = "${game.gateway.session.cleanup-interval:60000}")
    public void cleanupInactiveSessions() {
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;
        
        for (Map.Entry<Long, SessionAttributes> entry : sessionAttributes.entrySet()) {
            long sessionId = entry.getKey();
            SessionAttributes attributes = entry.getValue();
            
            // 检查会话是否还存在
            IoSession session = sessions.get(sessionId);
            if (session == null || !session.isConnected()) {
                sessionAttributes.remove(sessionId);
                cleanedCount++;
                continue;
            }
            
            // 检查会话是否空闲过久
            long lastActiveTime = attributes.getLastActiveTime();
            if (currentTime - lastActiveTime > maxIdleTime) {
                logger.warn("清理空闲会话: sessionId={}, 空闲时间: {}ms", 
                           sessionId, currentTime - lastActiveTime);
                closeSession(sessionId);
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            logger.info("会话清理完成，清理数量: {}", cleanedCount);
        }
    }

    // ========== 统计信息 ==========

    /**
     * 获取会话统计信息
     * 
     * @return 统计信息
     */
    public SessionStats getSessionStats() {
        return new SessionStats(
            totalSessionCount.get(),
            activeSessionCount.get(),
            playerSessions.size(),
            sessions.size()
        );
    }

    // ========== 内部类 ==========

    /**
     * 会话属性
     */
    public static class SessionAttributes {
        private final long sessionId;
        private final long createTime;
        private volatile long lastActiveTime;
        private volatile String playerId;
        private volatile long bindTime;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        public SessionAttributes(long sessionId) {
            this.sessionId = sessionId;
            this.createTime = System.currentTimeMillis();
            this.lastActiveTime = createTime;
        }

        // Getters and setters
        public long getSessionId() { return sessionId; }
        public long getCreateTime() { return createTime; }
        public long getLastActiveTime() { return lastActiveTime; }
        public void updateLastActiveTime() { this.lastActiveTime = System.currentTimeMillis(); }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public long getBindTime() { return bindTime; }
        public void setBindTime(long bindTime) { this.bindTime = bindTime; }
        
        public void setAttribute(String key, Object value) { attributes.put(key, value); }
        public Object getAttribute(String key) { return attributes.get(key); }
        public void removeAttribute(String key) { attributes.remove(key); }
        
        public String getCreateTimeFormatted() { return TimeUtils.formatTimestamp(createTime); }
        public String getLastActiveTimeFormatted() { return TimeUtils.formatTimestamp(lastActiveTime); }
        public String getBindTimeFormatted() { return bindTime > 0 ? TimeUtils.formatTimestamp(bindTime) : "未绑定"; }
    }

    /**
     * 会话统计信息
     */
    public static class SessionStats {
        private final long totalSessions;
        private final long activeSessions;
        private final long boundPlayers;
        private final long connectedSessions;

        public SessionStats(long totalSessions, long activeSessions, long boundPlayers, long connectedSessions) {
            this.totalSessions = totalSessions;
            this.activeSessions = activeSessions;
            this.boundPlayers = boundPlayers;
            this.connectedSessions = connectedSessions;
        }

        // Getters
        public long getTotalSessions() { return totalSessions; }
        public long getActiveSessions() { return activeSessions; }
        public long getBoundPlayers() { return boundPlayers; }
        public long getConnectedSessions() { return connectedSessions; }
    }
}