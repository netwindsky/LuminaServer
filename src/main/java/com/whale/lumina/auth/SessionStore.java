package com.whale.lumina.auth;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import com.whale.lumina.data.RedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话存储
 * 
 * 负责管理用户会话的存储、检索和清理
 * 支持Redis缓存和内存缓存的双重存储机制
 * 
 * @author Lumina Team
 */
@Component
public class SessionStore {

    private static final Logger logger = LoggerFactory.getLogger(SessionStore.class);

    @Autowired
    private RedisClient redisClient;

    // 配置参数
    @Value("${lumina.auth.session-expire-seconds:7200}")
    private int sessionExpireSeconds;

    @Value("${lumina.auth.enable-memory-cache:true}")
    private boolean enableMemoryCache;

    @Value("${lumina.auth.memory-cache-size:10000}")
    private int memoryCacheSize;

    // Redis键前缀
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String USER_SESSION_KEY_PREFIX = "user_session:";
    private static final String ACCESS_TOKEN_KEY_PREFIX = "access_token:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";

    // 内存缓存
    private final ConcurrentHashMap<String, AuthSession> memoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> accessTokenToSessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> refreshTokenToSessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userToSessionIds = new ConcurrentHashMap<>();

    // JSON序列化工具
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 统计信息
    private final AtomicLong totalSessions = new AtomicLong(0);
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final AtomicLong expiredSessions = new AtomicLong(0);

    // ========== 会话管理 ==========

    /**
     * 保存会话
     * 
     * @param session 会话对象
     */
    public void saveSession(AuthSession session) {
        try {
            String sessionId = session.getSessionId();
            String userId = session.getUserId();
            
            // 保存到Redis
            saveSessionToRedis(session);
            
            // 保存到内存缓存
            if (enableMemoryCache) {
                saveSessionToMemoryCache(session);
            }
            
            // 建立用户到会话的映射
            addUserSessionMapping(userId, sessionId);
            
            // 建立令牌到会话的映射
            addTokenSessionMapping(session.getAccessToken(), session.getRefreshToken(), sessionId);
            
            totalSessions.incrementAndGet();
            activeSessions.incrementAndGet();
            
            logger.debug("保存会话成功: sessionId={}, userId={}", sessionId, userId);
            
        } catch (Exception e) {
            logger.error("保存会话失败: sessionId={}", session.getSessionId(), e);
            throw new GameException(ErrorCodes.AUTH_SESSION_SAVE_FAILED, e);
        }
    }

    /**
     * 获取会话
     * 
     * @param sessionId 会话ID
     * @return 会话对象
     */
    public AuthSession getSession(String sessionId) {
        try {
            // 先从内存缓存获取
            if (enableMemoryCache) {
                AuthSession session = memoryCache.get(sessionId);
                if (session != null) {
                    if (!session.isExpired()) {
                        return session;
                    } else {
                        // 会话已过期，从缓存中移除
                        removeSessionFromMemoryCache(sessionId);
                    }
                }
            }
            
            // 从Redis获取
            AuthSession session = getSessionFromRedis(sessionId);
            if (session != null) {
                if (!session.isExpired()) {
                    // 更新内存缓存
                    if (enableMemoryCache) {
                        saveSessionToMemoryCache(session);
                    }
                    return session;
                } else {
                    // 会话已过期，删除
                    removeSession(sessionId);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("获取会话失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    /**
     * 通过访问令牌获取会话
     * 
     * @param accessToken 访问令牌
     * @return 会话对象
     */
    public AuthSession getSessionByAccessToken(String accessToken) {
        try {
            // 先从内存映射获取会话ID
            String sessionId = accessTokenToSessionId.get(accessToken);
            if (sessionId != null) {
                return getSession(sessionId);
            }
            
            // 从Redis获取会话ID
            sessionId = redisClient.get(ACCESS_TOKEN_KEY_PREFIX + accessToken);
            if (sessionId != null) {
                // 更新内存映射
                accessTokenToSessionId.put(accessToken, sessionId);
                return getSession(sessionId);
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("通过访问令牌获取会话失败: accessToken={}", accessToken, e);
            return null;
        }
    }

    /**
     * 通过刷新令牌获取会话
     * 
     * @param refreshToken 刷新令牌
     * @return 会话对象
     */
    public AuthSession getSessionByRefreshToken(String refreshToken) {
        try {
            // 先从内存映射获取会话ID
            String sessionId = refreshTokenToSessionId.get(refreshToken);
            if (sessionId != null) {
                return getSession(sessionId);
            }
            
            // 从Redis获取会话ID
            sessionId = redisClient.get(REFRESH_TOKEN_KEY_PREFIX + refreshToken);
            if (sessionId != null) {
                // 更新内存映射
                refreshTokenToSessionId.put(refreshToken, sessionId);
                return getSession(sessionId);
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("通过刷新令牌获取会话失败: refreshToken={}", refreshToken, e);
            return null;
        }
    }

    /**
     * 获取用户的所有会话
     * 
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<AuthSession> getUserSessions(String userId) {
        try {
            List<AuthSession> sessions = new ArrayList<>();
            
            // 从内存映射获取会话ID列表
            Set<String> sessionIds = userToSessionIds.get(userId);
            if (sessionIds != null) {
                for (String sessionId : new HashSet<>(sessionIds)) {
                    AuthSession session = getSession(sessionId);
                    if (session != null) {
                        sessions.add(session);
                    } else {
                        // 会话不存在，从映射中移除
                        sessionIds.remove(sessionId);
                    }
                }
            }
            
            // 如果内存映射为空，从Redis获取
            if (sessions.isEmpty()) {
                Set<String> redisSessionIds = redisClient.smembers(USER_SESSION_KEY_PREFIX + userId);
                if (redisSessionIds != null) {
                    for (String sessionId : redisSessionIds) {
                        AuthSession session = getSession(sessionId);
                        if (session != null) {
                            sessions.add(session);
                        } else {
                            // 会话不存在，从Redis集合中移除
                            redisClient.srem(USER_SESSION_KEY_PREFIX + userId, sessionId);
                        }
                    }
                }
            }
            
            return sessions;
            
        } catch (Exception e) {
            logger.error("获取用户会话失败: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 更新会话
     * 
     * @param session 会话对象
     */
    public void updateSession(AuthSession session) {
        try {
            String sessionId = session.getSessionId();
            
            // 更新Redis
            saveSessionToRedis(session);
            
            // 更新内存缓存
            if (enableMemoryCache) {
                saveSessionToMemoryCache(session);
            }
            
            logger.debug("更新会话成功: sessionId={}", sessionId);
            
        } catch (Exception e) {
            logger.error("更新会话失败: sessionId={}", session.getSessionId(), e);
            throw new GameException(ErrorCodes.AUTH_SESSION_UPDATE_FAILED, e);
        }
    }

    /**
     * 删除会话
     * 
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        try {
            // 获取会话信息
            AuthSession session = getSession(sessionId);
            if (session == null) {
                return;
            }
            
            String userId = session.getUserId();
            String accessToken = session.getAccessToken();
            String refreshToken = session.getRefreshToken();
            
            // 从Redis删除
            removeSessionFromRedis(sessionId, userId, accessToken, refreshToken);
            
            // 从内存缓存删除
            if (enableMemoryCache) {
                removeSessionFromMemoryCache(sessionId);
                removeTokenSessionMapping(accessToken, refreshToken);
                removeUserSessionMapping(userId, sessionId);
            }
            
            activeSessions.decrementAndGet();
            
            logger.debug("删除会话成功: sessionId={}, userId={}", sessionId, userId);
            
        } catch (Exception e) {
            logger.error("删除会话失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 删除用户的所有会话
     * 
     * @param userId 用户ID
     */
    public void removeUserSessions(String userId) {
        try {
            List<AuthSession> sessions = getUserSessions(userId);
            for (AuthSession session : sessions) {
                removeSession(session.getSessionId());
            }
            
            logger.info("删除用户所有会话成功: userId={}, sessionCount={}", userId, sessions.size());
            
        } catch (Exception e) {
            logger.error("删除用户所有会话失败: userId={}", userId, e);
        }
    }

    // ========== Redis操作 ==========

    /**
     * 保存会话到Redis
     */
    private void saveSessionToRedis(AuthSession session) throws Exception {
        String sessionId = session.getSessionId();
        String userId = session.getUserId();
        String accessToken = session.getAccessToken();
        String refreshToken = session.getRefreshToken();
        
        // 序列化会话对象
        String sessionJson = objectMapper.writeValueAsString(session);
        
        // 保存会话数据
        redisClient.setex(SESSION_KEY_PREFIX + sessionId, sessionExpireSeconds, sessionJson);
        
        // 保存用户到会话的映射
        redisClient.sadd(USER_SESSION_KEY_PREFIX + userId, sessionId);
        redisClient.expire(USER_SESSION_KEY_PREFIX + userId, sessionExpireSeconds);
        
        // 保存令牌到会话的映射
        redisClient.setex(ACCESS_TOKEN_KEY_PREFIX + accessToken, sessionExpireSeconds, sessionId);
        redisClient.setex(REFRESH_TOKEN_KEY_PREFIX + refreshToken, sessionExpireSeconds, sessionId);
    }

    /**
     * 从Redis获取会话
     */
    private AuthSession getSessionFromRedis(String sessionId) throws Exception {
        String sessionJson = redisClient.get(SESSION_KEY_PREFIX + sessionId);
        if (sessionJson != null) {
            return objectMapper.readValue(sessionJson, AuthSession.class);
        }
        return null;
    }

    /**
     * 从Redis删除会话
     */
    private void removeSessionFromRedis(String sessionId, String userId, String accessToken, String refreshToken) {
        try {
            // 删除会话数据
            redisClient.del(SESSION_KEY_PREFIX + sessionId);
            
            // 删除用户到会话的映射
            redisClient.srem(USER_SESSION_KEY_PREFIX + userId, sessionId);
            
            // 删除令牌到会话的映射
            redisClient.del(ACCESS_TOKEN_KEY_PREFIX + accessToken);
            redisClient.del(REFRESH_TOKEN_KEY_PREFIX + refreshToken);
            
        } catch (Exception e) {
            logger.error("从Redis删除会话失败: sessionId={}", sessionId, e);
        }
    }

    // ========== 内存缓存操作 ==========

    /**
     * 保存会话到内存缓存
     */
    private void saveSessionToMemoryCache(AuthSession session) {
        // 检查缓存大小限制
        if (memoryCache.size() >= memoryCacheSize) {
            // 清理过期会话
            cleanExpiredSessionsFromMemoryCache();
            
            // 如果仍然超过限制，移除最旧的会话
            if (memoryCache.size() >= memoryCacheSize) {
                removeOldestSessionFromMemoryCache();
            }
        }
        
        memoryCache.put(session.getSessionId(), session);
    }

    /**
     * 从内存缓存删除会话
     */
    private void removeSessionFromMemoryCache(String sessionId) {
        memoryCache.remove(sessionId);
    }

    /**
     * 清理内存缓存中的过期会话
     */
    private void cleanExpiredSessionsFromMemoryCache() {
        Iterator<Map.Entry<String, AuthSession>> iterator = memoryCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AuthSession> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                expiredSessions.incrementAndGet();
            }
        }
    }

    /**
     * 移除内存缓存中最旧的会话
     */
    private void removeOldestSessionFromMemoryCache() {
        AuthSession oldestSession = null;
        String oldestSessionId = null;
        
        for (Map.Entry<String, AuthSession> entry : memoryCache.entrySet()) {
            AuthSession session = entry.getValue();
            if (oldestSession == null || session.getCreatedTime() < oldestSession.getCreatedTime()) {
                oldestSession = session;
                oldestSessionId = entry.getKey();
            }
        }
        
        if (oldestSessionId != null) {
            memoryCache.remove(oldestSessionId);
        }
    }

    // ========== 映射管理 ==========

    /**
     * 添加用户到会话的映射
     */
    private void addUserSessionMapping(String userId, String sessionId) {
        userToSessionIds.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    /**
     * 删除用户到会话的映射
     */
    private void removeUserSessionMapping(String userId, String sessionId) {
        Set<String> sessionIds = userToSessionIds.get(userId);
        if (sessionIds != null) {
            sessionIds.remove(sessionId);
            if (sessionIds.isEmpty()) {
                userToSessionIds.remove(userId);
            }
        }
    }

    /**
     * 添加令牌到会话的映射
     */
    private void addTokenSessionMapping(String accessToken, String refreshToken, String sessionId) {
        accessTokenToSessionId.put(accessToken, sessionId);
        refreshTokenToSessionId.put(refreshToken, sessionId);
    }

    /**
     * 删除令牌到会话的映射
     */
    private void removeTokenSessionMapping(String accessToken, String refreshToken) {
        accessTokenToSessionId.remove(accessToken);
        refreshTokenToSessionId.remove(refreshToken);
    }

    // ========== 定时任务 ==========

    /**
     * 定时清理过期会话
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void cleanupExpiredSessions() {
        try {
            logger.debug("开始清理过期会话");
            
            long startTime = System.currentTimeMillis();
            int cleanedCount = 0;
            
            // 清理内存缓存中的过期会话
            if (enableMemoryCache) {
                Iterator<Map.Entry<String, AuthSession>> iterator = memoryCache.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, AuthSession> entry = iterator.next();
                    AuthSession session = entry.getValue();
                    if (session.isExpired()) {
                        String sessionId = entry.getKey();
                        iterator.remove();
                        
                        // 同时清理相关映射
                        removeTokenSessionMapping(session.getAccessToken(), session.getRefreshToken());
                        removeUserSessionMapping(session.getUserId(), sessionId);
                        
                        // 从Redis删除
                        removeSessionFromRedis(sessionId, session.getUserId(), 
                                             session.getAccessToken(), session.getRefreshToken());
                        
                        cleanedCount++;
                        expiredSessions.incrementAndGet();
                        activeSessions.decrementAndGet();
                    }
                }
            }
            
            long endTime = System.currentTimeMillis();
            
            if (cleanedCount > 0) {
                logger.info("清理过期会话完成: cleanedCount={}, duration={}ms", 
                           cleanedCount, endTime - startTime);
            }
            
        } catch (Exception e) {
            logger.error("清理过期会话失败", e);
        }
    }

    // ========== 统计信息 ==========

    /**
     * 获取活跃会话数量
     * 
     * @return 活跃会话数量
     */
    public long getActiveSessionCount() {
        return activeSessions.get();
    }

    /**
     * 获取会话统计信息
     * 
     * @return 统计信息
     */
    public SessionStats getSessionStats() {
        return new SessionStats(
            totalSessions.get(),
            activeSessions.get(),
            expiredSessions.get(),
            memoryCache.size(),
            accessTokenToSessionId.size(),
            refreshTokenToSessionId.size(),
            userToSessionIds.size()
        );
    }

    /**
     * 会话统计信息
     */
    public static class SessionStats {
        private final long totalSessions;
        private final long activeSessions;
        private final long expiredSessions;
        private final long memoryCacheSize;
        private final long accessTokenMappingSize;
        private final long refreshTokenMappingSize;
        private final long userMappingSize;

        public SessionStats(long totalSessions, long activeSessions, long expiredSessions,
                          long memoryCacheSize, long accessTokenMappingSize, 
                          long refreshTokenMappingSize, long userMappingSize) {
            this.totalSessions = totalSessions;
            this.activeSessions = activeSessions;
            this.expiredSessions = expiredSessions;
            this.memoryCacheSize = memoryCacheSize;
            this.accessTokenMappingSize = accessTokenMappingSize;
            this.refreshTokenMappingSize = refreshTokenMappingSize;
            this.userMappingSize = userMappingSize;
        }

        // Getters
        public long getTotalSessions() { return totalSessions; }
        public long getActiveSessions() { return activeSessions; }
        public long getExpiredSessions() { return expiredSessions; }
        public long getMemoryCacheSize() { return memoryCacheSize; }
        public long getAccessTokenMappingSize() { return accessTokenMappingSize; }
        public long getRefreshTokenMappingSize() { return refreshTokenMappingSize; }
        public long getUserMappingSize() { return userMappingSize; }
    }
}