package com.whale.lumina.signaling;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.data.RedisClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 信令存储服务
 * 
 * 负责信令会话和消息的持久化存储，使用Redis作为存储后端
 * 
 * @author Lumina Team
 */
@Component
public class SignalingStore {

    private static final Logger logger = LoggerFactory.getLogger(SignalingStore.class);

    @Autowired
    private RedisClient redisClient;

    private final ObjectMapper objectMapper;

    // Redis键前缀
    private static final String SESSION_KEY_PREFIX = "signaling:session:";
    private static final String MESSAGE_KEY_PREFIX = "signaling:message:";
    private static final String ROOM_SESSION_KEY_PREFIX = "signaling:room:";
    private static final String USER_SESSION_KEY_PREFIX = "signaling:user:";

    // 默认过期时间（秒）
    private static final int DEFAULT_SESSION_EXPIRE_TIME = 3600; // 1小时
    private static final int DEFAULT_MESSAGE_EXPIRE_TIME = 1800; // 30分钟

    /**
     * 构造函数
     */
    public SignalingStore() {
        this.objectMapper = new ObjectMapper();
    }

    // ========== 会话存储 ==========

    /**
     * 保存信令会话
     * 
     * @param session 信令会话
     * @throws GameException 保存失败时抛出
     */
    public void saveSession(SignalingSession session) throws GameException {
        if (session == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "会话不能为空");
        }

        try {
            String sessionKey = SESSION_KEY_PREFIX + session.getSessionId();
            String sessionJson = objectMapper.writeValueAsString(session);
            
            // 保存会话数据
            redisClient.setWithExpire(sessionKey, sessionJson, DEFAULT_SESSION_EXPIRE_TIME);
            
            // 建立房间到会话的映射
            String roomSessionKey = ROOM_SESSION_KEY_PREFIX + session.getRoomId();
            redisClient.setWithExpire(roomSessionKey, session.getSessionId(), DEFAULT_SESSION_EXPIRE_TIME);
            
            // 建立用户到会话的映射
            for (String participantId : session.getParticipants()) {
                String userSessionKey = USER_SESSION_KEY_PREFIX + participantId;
                redisClient.sadd(userSessionKey, session.getSessionId());
                redisClient.expire(userSessionKey, DEFAULT_SESSION_EXPIRE_TIME);
            }
            
            logger.debug("保存信令会话: sessionId={}, roomId={}", 
                        session.getSessionId(), session.getRoomId());
            
        } catch (Exception e) {
            logger.error("保存信令会话失败: sessionId={}", session.getSessionId(), e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "保存信令会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取信令会话
     * 
     * @param sessionId 会话ID
     * @return 信令会话，如果不存在返回null
     */
    public SignalingSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }

        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            String sessionJson = redisClient.get(sessionKey);
            
            if (sessionJson != null) {
                return objectMapper.readValue(sessionJson, SignalingSession.class);
            }
            
        } catch (Exception e) {
            logger.error("获取信令会话失败: sessionId={}", sessionId, e);
        }
        
        return null;
    }

    /**
     * 根据房间ID获取信令会话
     * 
     * @param roomId 房间ID
     * @return 信令会话，如果不存在返回null
     */
    public SignalingSession getSessionByRoom(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }

        try {
            String roomSessionKey = ROOM_SESSION_KEY_PREFIX + roomId;
            String sessionId = redisClient.get(roomSessionKey);
            
            if (sessionId != null) {
                return getSession(sessionId);
            }
            
        } catch (Exception e) {
            logger.error("根据房间ID获取信令会话失败: roomId={}", roomId, e);
        }
        
        return null;
    }

    /**
     * 获取用户参与的所有会话
     * 
     * @param userId 用户ID
     * @return 会话ID列表
     */
    public List<String> getUserSessions(String userId) {
        if (userId == null || userId.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
            Set<String> sessionIds = redisClient.smembers(userSessionKey);
            
            return new ArrayList<>(sessionIds);
            
        } catch (Exception e) {
            logger.error("获取用户会话失败: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 更新会话
     * 
     * @param session 信令会话
     * @throws GameException 更新失败时抛出
     */
    public void updateSession(SignalingSession session) throws GameException {
        if (session == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "会话不能为空");
        }

        try {
            String sessionKey = SESSION_KEY_PREFIX + session.getSessionId();
            String sessionJson = objectMapper.writeValueAsString(session);
            
            // 更新会话数据，保持原有过期时间
            Long ttl = redisClient.ttl(sessionKey);
            if (ttl != null && ttl > 0) {
                redisClient.setWithExpire(sessionKey, sessionJson, ttl.intValue());
            } else {
                redisClient.setWithExpire(sessionKey, sessionJson, DEFAULT_SESSION_EXPIRE_TIME);
            }
            
            logger.debug("更新信令会话: sessionId={}", session.getSessionId());
            
        } catch (Exception e) {
            logger.error("更新信令会话失败: sessionId={}", session.getSessionId(), e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "更新信令会话失败: " + e.getMessage());
        }
    }

    /**
     * 删除信令会话
     * 
     * @param sessionId 会话ID
     * @throws GameException 删除失败时抛出
     */
    public void removeSession(String sessionId) throws GameException {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }

        try {
            // 先获取会话信息
            SignalingSession session = getSession(sessionId);
            if (session == null) {
                return;
            }

            // 删除会话数据
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            redisClient.del(sessionKey);
            
            // 删除房间映射
            String roomSessionKey = ROOM_SESSION_KEY_PREFIX + session.getRoomId();
            redisClient.del(roomSessionKey);
            
            // 删除用户映射
            for (String participantId : session.getParticipants()) {
                String userSessionKey = USER_SESSION_KEY_PREFIX + participantId;
                redisClient.srem(userSessionKey, sessionId);
            }
            
            // 删除相关消息
            removeSessionMessages(sessionId);
            
            logger.debug("删除信令会话: sessionId={}", sessionId);
            
        } catch (Exception e) {
            logger.error("删除信令会话失败: sessionId={}", sessionId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "删除信令会话失败: " + e.getMessage());
        }
    }

    // ========== 消息存储 ==========

    /**
     * 保存信令消息
     * 
     * @param sessionId 会话ID
     * @param message 信令消息
     * @throws GameException 保存失败时抛出
     */
    public void saveMessage(String sessionId, SignalingMessage message) throws GameException {
        if (sessionId == null || message == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "会话ID和消息不能为空");
        }

        try {
            String messageKey = MESSAGE_KEY_PREFIX + sessionId + ":" + message.getId();
            String messageJson = objectMapper.writeValueAsString(message);
            
            // 保存消息
            redisClient.setWithExpire(messageKey, messageJson, DEFAULT_MESSAGE_EXPIRE_TIME);
            
            // 添加到会话消息列表
            String sessionMessageListKey = MESSAGE_KEY_PREFIX + sessionId + ":list";
            redisClient.lpush(sessionMessageListKey, message.getId());
            redisClient.expire(sessionMessageListKey, DEFAULT_MESSAGE_EXPIRE_TIME);
            
            // 限制消息列表长度（保留最近1000条）
            redisClient.ltrim(sessionMessageListKey, 0, 999);
            
            logger.debug("保存信令消息: sessionId={}, messageId={}, type={}", 
                        sessionId, message.getId(), message.getType());
            
        } catch (Exception e) {
            logger.error("保存信令消息失败: sessionId={}, messageId={}", 
                        sessionId, message.getId(), e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "保存信令消息失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话的消息列表
     * 
     * @param sessionId 会话ID
     * @param limit 限制数量
     * @return 消息列表
     */
    public List<SignalingMessage> getSessionMessages(String sessionId, int limit) {
        if (sessionId == null || sessionId.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String sessionMessageListKey = MESSAGE_KEY_PREFIX + sessionId + ":list";
            List<String> messageIds = redisClient.lrange(sessionMessageListKey, 0, limit - 1);
            
            List<SignalingMessage> messages = new ArrayList<>();
            for (String messageId : messageIds) {
                String messageKey = MESSAGE_KEY_PREFIX + sessionId + ":" + messageId;
                String messageJson = redisClient.get(messageKey);
                
                if (messageJson != null) {
                    SignalingMessage message = objectMapper.readValue(messageJson, SignalingMessage.class);
                    messages.add(message);
                }
            }
            
            return messages;
            
        } catch (Exception e) {
            logger.error("获取会话消息失败: sessionId={}", sessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取特定类型的消息
     * 
     * @param sessionId 会话ID
     * @param messageType 消息类型
     * @param limit 限制数量
     * @return 消息列表
     */
    public List<SignalingMessage> getMessagesByType(String sessionId, SignalingMessage.Type messageType, int limit) {
        List<SignalingMessage> allMessages = getSessionMessages(sessionId, limit * 2); // 获取更多消息以便过滤
        List<SignalingMessage> filteredMessages = new ArrayList<>();
        
        for (SignalingMessage message : allMessages) {
            if (message.getType() == messageType) {
                filteredMessages.add(message);
                if (filteredMessages.size() >= limit) {
                    break;
                }
            }
        }
        
        return filteredMessages;
    }

    /**
     * 获取用户相关的消息
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 消息列表
     */
    public List<SignalingMessage> getUserMessages(String sessionId, String userId, int limit) {
        List<SignalingMessage> allMessages = getSessionMessages(sessionId, limit * 2);
        List<SignalingMessage> userMessages = new ArrayList<>();
        
        for (SignalingMessage message : allMessages) {
            if (userId.equals(message.getFromUserId()) || userId.equals(message.getToUserId())) {
                userMessages.add(message);
                if (userMessages.size() >= limit) {
                    break;
                }
            }
        }
        
        return userMessages;
    }

    /**
     * 删除会话的所有消息
     * 
     * @param sessionId 会话ID
     */
    private void removeSessionMessages(String sessionId) {
        try {
            String sessionMessageListKey = MESSAGE_KEY_PREFIX + sessionId + ":list";
            List<String> messageIds = redisClient.lrange(sessionMessageListKey, 0, -1);
            
            // 删除所有消息
            for (String messageId : messageIds) {
                String messageKey = MESSAGE_KEY_PREFIX + sessionId + ":" + messageId;
                redisClient.del(messageKey);
            }
            
            // 删除消息列表
            redisClient.del(sessionMessageListKey);
            
            logger.debug("删除会话消息: sessionId={}, messageCount={}", sessionId, messageIds.size());
            
        } catch (Exception e) {
            logger.error("删除会话消息失败: sessionId={}", sessionId, e);
        }
    }

    // ========== 统计和查询 ==========

    /**
     * 获取活跃会话数量
     * 
     * @return 活跃会话数量
     */
    public long getActiveSessionCount() {
        try {
            Set<String> sessionKeys = redisClient.keys(SESSION_KEY_PREFIX + "*");
            return sessionKeys.size();
        } catch (Exception e) {
            logger.error("获取活跃会话数量失败", e);
            return 0;
        }
    }

    /**
     * 获取会话的消息数量
     * 
     * @param sessionId 会话ID
     * @return 消息数量
     */
    public long getSessionMessageCount(String sessionId) {
        try {
            String sessionMessageListKey = MESSAGE_KEY_PREFIX + sessionId + ":list";
            return redisClient.llen(sessionMessageListKey);
        } catch (Exception e) {
            logger.error("获取会话消息数量失败: sessionId={}", sessionId, e);
            return 0;
        }
    }

    /**
     * 检查会话是否存在
     * 
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean sessionExists(String sessionId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            return redisClient.exists(sessionKey);
        } catch (Exception e) {
            logger.error("检查会话存在性失败: sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * 获取会话的剩余过期时间
     * 
     * @param sessionId 会话ID
     * @return 剩余时间（秒），-1表示永不过期，-2表示不存在
     */
    public long getSessionTTL(String sessionId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            Long ttl = redisClient.ttl(sessionKey);
            return ttl != null ? ttl : -2;
        } catch (Exception e) {
            logger.error("获取会话TTL失败: sessionId={}", sessionId, e);
            return -2;
        }
    }

    /**
     * 延长会话过期时间
     * 
     * @param sessionId 会话ID
     * @param expireSeconds 过期时间（秒）
     * @throws GameException 延长失败时抛出
     */
    public void extendSessionTTL(String sessionId, int expireSeconds) throws GameException {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            if (redisClient.exists(sessionKey)) {
                redisClient.expire(sessionKey, expireSeconds);
                
                // 同时延长相关映射的过期时间
                SignalingSession session = getSession(sessionId);
                if (session != null) {
                    String roomSessionKey = ROOM_SESSION_KEY_PREFIX + session.getRoomId();
                    redisClient.expire(roomSessionKey, expireSeconds);
                    
                    for (String participantId : session.getParticipants()) {
                        String userSessionKey = USER_SESSION_KEY_PREFIX + participantId;
                        redisClient.expire(userSessionKey, expireSeconds);
                    }
                }
                
                logger.debug("延长会话过期时间: sessionId={}, expireSeconds={}", sessionId, expireSeconds);
            }
        } catch (Exception e) {
            logger.error("延长会话过期时间失败: sessionId={}", sessionId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "延长会话过期时间失败: " + e.getMessage());
        }
    }

    // ========== 清理和维护 ==========

    /**
     * 清理过期的数据
     */
    public void cleanupExpiredData() {
        try {
            // Redis会自动清理过期的键，这里主要是清理一些可能的孤立数据
            
            // 清理空的用户会话集合
            Set<String> userSessionKeys = redisClient.keys(USER_SESSION_KEY_PREFIX + "*");
            for (String userSessionKey : userSessionKeys) {
                if (redisClient.scard(userSessionKey) == 0) {
                    redisClient.del(userSessionKey);
                }
            }
            
            logger.debug("清理过期数据完成");
            
        } catch (Exception e) {
            logger.error("清理过期数据失败", e);
        }
    }

    /**
     * 获取存储统计信息
     * 
     * @return 统计信息
     */
    public SignalingStoreStatistics getStatistics() {
        try {
            long sessionCount = getActiveSessionCount();
            
            Set<String> messageListKeys = redisClient.keys(MESSAGE_KEY_PREFIX + "*:list");
            long totalMessages = 0;
            for (String messageListKey : messageListKeys) {
                totalMessages += redisClient.llen(messageListKey);
            }
            
            Set<String> userSessionKeys = redisClient.keys(USER_SESSION_KEY_PREFIX + "*");
            long activeUsers = userSessionKeys.size();
            
            return new SignalingStoreStatistics(sessionCount, totalMessages, activeUsers);
            
        } catch (Exception e) {
            logger.error("获取存储统计信息失败", e);
            return new SignalingStoreStatistics(0, 0, 0);
        }
    }

    /**
     * 信令存储统计信息
     */
    public static class SignalingStoreStatistics {
        private final long sessionCount;
        private final long messageCount;
        private final long activeUserCount;

        public SignalingStoreStatistics(long sessionCount, long messageCount, long activeUserCount) {
            this.sessionCount = sessionCount;
            this.messageCount = messageCount;
            this.activeUserCount = activeUserCount;
        }

        public long getSessionCount() { return sessionCount; }
        public long getMessageCount() { return messageCount; }
        public long getActiveUserCount() { return activeUserCount; }

        @Override
        public String toString() {
            return String.format("SignalingStoreStatistics{sessions=%d, messages=%d, activeUsers=%d}",
                               sessionCount, messageCount, activeUserCount);
        }
    }
}