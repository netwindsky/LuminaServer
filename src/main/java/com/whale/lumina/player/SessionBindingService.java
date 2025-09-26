package com.whale.lumina.player;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.data.RedisClient;
import com.whale.lumina.auth.AuthSession;
import com.whale.lumina.room.Room;
import com.whale.lumina.signaling.SignalingSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 会话绑定服务
 * 
 * 负责管理玩家与各种会话（认证会话、房间会话、信令会话）的绑定关系
 * 
 * @author Lumina Team
 */
@Service
public class SessionBindingService {

    private static final Logger logger = LoggerFactory.getLogger(SessionBindingService.class);

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private PlayerRepository playerRepository;

    private final ObjectMapper objectMapper;

    // 内存缓存，用于快速查找
    private final Map<String, SessionBinding> bindingCache = new ConcurrentHashMap<>();

    // Redis键前缀
    private static final String BINDING_KEY_PREFIX = "session:binding:";
    private static final String PLAYER_SESSIONS_KEY_PREFIX = "player:sessions:";
    private static final String SESSION_PLAYER_KEY_PREFIX = "session:player:";
    private static final String ACTIVE_BINDINGS_KEY = "bindings:active";

    // 缓存过期时间
    private static final int BINDING_CACHE_EXPIRE_TIME = 1800; // 30分钟
    private static final int MEMORY_CACHE_SIZE = 10000; // 内存缓存最大大小

    /**
     * 构造函数
     */
    public SessionBindingService() {
        this.objectMapper = new ObjectMapper();
    }

    // ========== 会话绑定管理 ==========

    /**
     * 绑定玩家与认证会话
     * 
     * @param playerId 玩家ID
     * @param authSession 认证会话
     * @throws GameException 绑定失败时抛出
     */
    public void bindAuthSession(String playerId, AuthSession authSession) throws GameException {
        if (playerId == null || authSession == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家ID和认证会话不能为空");
        }

        try {
            SessionBinding binding = getOrCreateBinding(playerId);
            binding.setAuthSessionId(authSession.getSessionId());
            binding.setAuthSession(authSession);
            binding.updateActiveTime();
            
            saveBinding(binding);
            
            // 更新玩家状态为在线
            playerRepository.playerGoOnline(playerId);
            
            logger.debug("绑定认证会话: playerId={}, sessionId={}", playerId, authSession.getSessionId());
            
        } catch (Exception e) {
            logger.error("绑定认证会话失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "绑定认证会话失败: " + e.getMessage());
        }
    }

    /**
     * 解绑玩家与认证会话
     * 
     * @param playerId 玩家ID
     * @throws GameException 解绑失败时抛出
     */
    public void unbindAuthSession(String playerId) throws GameException {
        try {
            SessionBinding binding = getBinding(playerId);
            if (binding != null) {
                binding.setAuthSessionId(null);
                binding.setAuthSession(null);
                binding.updateActiveTime();
                
                saveBinding(binding);
                
                // 更新玩家状态为离线
                playerRepository.playerGoOffline(playerId);
                
                logger.debug("解绑认证会话: playerId={}", playerId);
            }
            
        } catch (Exception e) {
            logger.error("解绑认证会话失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "解绑认证会话失败: " + e.getMessage());
        }
    }

    /**
     * 绑定玩家与房间会话
     * 
     * @param playerId 玩家ID
     * @param roomId 房间ID
     * @throws GameException 绑定失败时抛出
     */
    public void bindRoomSession(String playerId, String roomId) throws GameException {
        if (playerId == null || roomId == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家ID和房间ID不能为空");
        }

        try {
            SessionBinding binding = getOrCreateBinding(playerId);
            binding.setRoomId(roomId);
            binding.updateActiveTime();
            
            saveBinding(binding);
            
            // 更新玩家状态
            playerRepository.updatePlayerStatus(playerId, Player.PlayerStatus.IN_ROOM);
            
            logger.debug("绑定房间会话: playerId={}, roomId={}", playerId, roomId);
            
        } catch (Exception e) {
            logger.error("绑定房间会话失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "绑定房间会话失败: " + e.getMessage());
        }
    }

    /**
     * 解绑玩家与房间会话
     * 
     * @param playerId 玩家ID
     * @throws GameException 解绑失败时抛出
     */
    public void unbindRoomSession(String playerId) throws GameException {
        try {
            SessionBinding binding = getBinding(playerId);
            if (binding != null) {
                binding.setRoomId(null);
                binding.updateActiveTime();
                
                saveBinding(binding);
                
                // 更新玩家状态
                playerRepository.updatePlayerStatus(playerId, Player.PlayerStatus.ONLINE);
                
                logger.debug("解绑房间会话: playerId={}", playerId);
            }
            
        } catch (Exception e) {
            logger.error("解绑房间会话失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "解绑房间会话失败: " + e.getMessage());
        }
    }

    /**
     * 绑定玩家与信令会话
     * 
     * @param playerId 玩家ID
     * @param signalingSession 信令会话
     * @throws GameException 绑定失败时抛出
     */
    public void bindSignalingSession(String playerId, SignalingSession signalingSession) throws GameException {
        if (playerId == null || signalingSession == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家ID和信令会话不能为空");
        }

        try {
            SessionBinding binding = getOrCreateBinding(playerId);
            binding.setSignalingSessionId(signalingSession.getSessionId());
            binding.setSignalingSession(signalingSession);
            binding.updateActiveTime();
            
            saveBinding(binding);
            
            logger.debug("绑定信令会话: playerId={}, sessionId={}", playerId, signalingSession.getSessionId());
            
        } catch (Exception e) {
            logger.error("绑定信令会话失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "绑定信令会话失败: " + e.getMessage());
        }
    }

    /**
     * 解绑玩家与信令会话
     * 
     * @param playerId 玩家ID
     * @throws GameException 解绑失败时抛出
     */
    public void unbindSignalingSession(String playerId) throws GameException {
        try {
            SessionBinding binding = getBinding(playerId);
            if (binding != null) {
                binding.setSignalingSessionId(null);
                binding.setSignalingSession(null);
                binding.updateActiveTime();
                
                saveBinding(binding);
                
                logger.debug("解绑信令会话: playerId={}", playerId);
            }
            
        } catch (Exception e) {
            logger.error("解绑信令会话失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "解绑信令会话失败: " + e.getMessage());
        }
    }

    // ========== 查询方法 ==========

    /**
     * 获取玩家的会话绑定
     * 
     * @param playerId 玩家ID
     * @return 会话绑定对象，如果不存在返回null
     */
    public SessionBinding getPlayerBinding(String playerId) {
        return getBinding(playerId);
    }

    /**
     * 根据认证会话ID获取玩家ID
     * 
     * @param authSessionId 认证会话ID
     * @return 玩家ID，如果不存在返回null
     */
    public String getPlayerByAuthSession(String authSessionId) {
        try {
            String sessionPlayerKey = SESSION_PLAYER_KEY_PREFIX + "auth:" + authSessionId;
            return redisClient.get(sessionPlayerKey);
        } catch (Exception e) {
            logger.error("根据认证会话ID获取玩家失败: authSessionId={}", authSessionId, e);
            return null;
        }
    }

    /**
     * 根据房间ID获取玩家列表
     * 
     * @param roomId 房间ID
     * @return 玩家ID列表
     */
    public List<String> getPlayersByRoom(String roomId) {
        List<String> playerIds = new ArrayList<>();
        
        try {
            // 遍历所有活跃绑定
            Set<String> activeBindings = redisClient.smembers(ACTIVE_BINDINGS_KEY);
            
            for (String playerId : activeBindings) {
                SessionBinding binding = getBinding(playerId);
                if (binding != null && roomId.equals(binding.getRoomId())) {
                    playerIds.add(playerId);
                }
            }
            
        } catch (Exception e) {
            logger.error("根据房间ID获取玩家列表失败: roomId={}", roomId, e);
        }
        
        return playerIds;
    }

    /**
     * 根据信令会话ID获取玩家ID
     * 
     * @param signalingSessionId 信令会话ID
     * @return 玩家ID，如果不存在返回null
     */
    public String getPlayerBySignalingSession(String signalingSessionId) {
        try {
            String sessionPlayerKey = SESSION_PLAYER_KEY_PREFIX + "signaling:" + signalingSessionId;
            return redisClient.get(sessionPlayerKey);
        } catch (Exception e) {
            logger.error("根据信令会话ID获取玩家失败: signalingSessionId={}", signalingSessionId, e);
            return null;
        }
    }

    /**
     * 检查玩家是否在线
     * 
     * @param playerId 玩家ID
     * @return 是否在线
     */
    public boolean isPlayerOnline(String playerId) {
        SessionBinding binding = getBinding(playerId);
        return binding != null && binding.getAuthSessionId() != null;
    }

    /**
     * 检查玩家是否在房间中
     * 
     * @param playerId 玩家ID
     * @return 是否在房间中
     */
    public boolean isPlayerInRoom(String playerId) {
        SessionBinding binding = getBinding(playerId);
        return binding != null && binding.getRoomId() != null;
    }

    /**
     * 获取玩家当前房间ID
     * 
     * @param playerId 玩家ID
     * @return 房间ID，如果不在房间中返回null
     */
    public String getPlayerCurrentRoom(String playerId) {
        SessionBinding binding = getBinding(playerId);
        return binding != null ? binding.getRoomId() : null;
    }

    // ========== 批量操作 ==========

    /**
     * 批量解绑房间会话
     * 
     * @param playerIds 玩家ID列表
     */
    public void batchUnbindRoomSessions(List<String> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return;
        }

        for (String playerId : playerIds) {
            try {
                unbindRoomSession(playerId);
            } catch (Exception e) {
                logger.error("批量解绑房间会话失败: playerId={}", playerId, e);
            }
        }
    }

    /**
     * 清理过期的会话绑定
     */
    public void cleanupExpiredBindings() {
        try {
            Set<String> activeBindings = redisClient.smembers(ACTIVE_BINDINGS_KEY);
            long currentTime = System.currentTimeMillis();
            
            for (String playerId : activeBindings) {
                SessionBinding binding = getBinding(playerId);
                if (binding != null && binding.isExpired(currentTime)) {
                    removeBinding(playerId);
                    logger.debug("清理过期绑定: playerId={}", playerId);
                }
            }
            
        } catch (Exception e) {
            logger.error("清理过期绑定失败", e);
        }
    }

    /**
     * 获取活跃会话统计
     * 
     * @return 统计信息
     */
    public SessionStatistics getSessionStatistics() {
        SessionStatistics stats = new SessionStatistics();
        
        try {
            Set<String> activeBindings = redisClient.smembers(ACTIVE_BINDINGS_KEY);
            stats.setTotalActiveSessions(activeBindings.size());
            
            int onlineCount = 0;
            int inRoomCount = 0;
            int signalingCount = 0;
            
            for (String playerId : activeBindings) {
                SessionBinding binding = getBinding(playerId);
                if (binding != null) {
                    if (binding.getAuthSessionId() != null) {
                        onlineCount++;
                    }
                    if (binding.getRoomId() != null) {
                        inRoomCount++;
                    }
                    if (binding.getSignalingSessionId() != null) {
                        signalingCount++;
                    }
                }
            }
            
            stats.setOnlinePlayerCount(onlineCount);
            stats.setInRoomPlayerCount(inRoomCount);
            stats.setSignalingSessionCount(signalingCount);
            
        } catch (Exception e) {
            logger.error("获取会话统计失败", e);
        }
        
        return stats;
    }

    // ========== 私有方法 ==========

    /**
     * 获取或创建会话绑定
     */
    private SessionBinding getOrCreateBinding(String playerId) {
        SessionBinding binding = getBinding(playerId);
        if (binding == null) {
            binding = new SessionBinding(playerId);
        }
        return binding;
    }

    /**
     * 获取会话绑定
     */
    private SessionBinding getBinding(String playerId) {
        if (playerId == null) {
            return null;
        }

        // 先从内存缓存查找
        SessionBinding binding = bindingCache.get(playerId);
        if (binding != null) {
            return binding;
        }

        try {
            // 从Redis查找
            String bindingKey = BINDING_KEY_PREFIX + playerId;
            String bindingJson = redisClient.get(bindingKey);
            
            if (bindingJson != null) {
                binding = objectMapper.readValue(bindingJson, SessionBinding.class);
                
                // 加入内存缓存
                if (bindingCache.size() < MEMORY_CACHE_SIZE) {
                    bindingCache.put(playerId, binding);
                }
                
                return binding;
            }
            
        } catch (Exception e) {
            logger.error("获取会话绑定失败: playerId={}", playerId, e);
        }
        
        return null;
    }

    /**
     * 保存会话绑定
     */
    private void saveBinding(SessionBinding binding) throws Exception {
        String playerId = binding.getPlayerId();
        String bindingKey = BINDING_KEY_PREFIX + playerId;
        String bindingJson = objectMapper.writeValueAsString(binding);
        
        // 保存到Redis
        redisClient.setWithExpire(bindingKey, bindingJson, BINDING_CACHE_EXPIRE_TIME);
        
        // 更新内存缓存
        if (bindingCache.size() < MEMORY_CACHE_SIZE) {
            bindingCache.put(playerId, binding);
        }
        
        // 添加到活跃绑定集合
        redisClient.sadd(ACTIVE_BINDINGS_KEY, playerId);
        
        // 更新会话到玩家的映射
        updateSessionPlayerMappings(binding);
    }

    /**
     * 移除会话绑定
     */
    private void removeBinding(String playerId) {
        try {
            SessionBinding binding = getBinding(playerId);
            
            // 从Redis删除
            String bindingKey = BINDING_KEY_PREFIX + playerId;
            redisClient.del(bindingKey);
            
            // 从内存缓存删除
            bindingCache.remove(playerId);
            
            // 从活跃绑定集合删除
            redisClient.srem(ACTIVE_BINDINGS_KEY, playerId);
            
            // 清理会话到玩家的映射
            if (binding != null) {
                removeSessionPlayerMappings(binding);
            }
            
        } catch (Exception e) {
            logger.error("移除会话绑定失败: playerId={}", playerId, e);
        }
    }

    /**
     * 更新会话到玩家的映射
     */
    private void updateSessionPlayerMappings(SessionBinding binding) {
        try {
            String playerId = binding.getPlayerId();
            
            // 认证会话映射
            if (binding.getAuthSessionId() != null) {
                String authKey = SESSION_PLAYER_KEY_PREFIX + "auth:" + binding.getAuthSessionId();
                redisClient.setWithExpire(authKey, playerId, BINDING_CACHE_EXPIRE_TIME);
            }
            
            // 信令会话映射
            if (binding.getSignalingSessionId() != null) {
                String signalingKey = SESSION_PLAYER_KEY_PREFIX + "signaling:" + binding.getSignalingSessionId();
                redisClient.setWithExpire(signalingKey, playerId, BINDING_CACHE_EXPIRE_TIME);
            }
            
        } catch (Exception e) {
            logger.error("更新会话映射失败: playerId={}", binding.getPlayerId(), e);
        }
    }

    /**
     * 移除会话到玩家的映射
     */
    private void removeSessionPlayerMappings(SessionBinding binding) {
        try {
            // 移除认证会话映射
            if (binding.getAuthSessionId() != null) {
                String authKey = SESSION_PLAYER_KEY_PREFIX + "auth:" + binding.getAuthSessionId();
                redisClient.del(authKey);
            }
            
            // 移除信令会话映射
            if (binding.getSignalingSessionId() != null) {
                String signalingKey = SESSION_PLAYER_KEY_PREFIX + "signaling:" + binding.getSignalingSessionId();
                redisClient.del(signalingKey);
            }
            
        } catch (Exception e) {
            logger.error("移除会话映射失败: playerId={}", binding.getPlayerId(), e);
        }
    }

    // ========== 内部类 ==========

    /**
     * 会话绑定类
     */
    public static class SessionBinding {
        private String playerId;
        private String authSessionId;
        private String roomId;
        private String signalingSessionId;
        private LocalDateTime createTime;
        private LocalDateTime lastActiveTime;
        private long expireTime; // 过期时间戳
        
        // 会话对象缓存（不持久化）
        private transient AuthSession authSession;
        private transient SignalingSession signalingSession;

        public SessionBinding() {
            this.createTime = LocalDateTime.now();
            this.lastActiveTime = LocalDateTime.now();
            this.expireTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24); // 24小时过期
        }

        public SessionBinding(String playerId) {
            this();
            this.playerId = playerId;
        }

        /**
         * 更新活跃时间
         */
        public void updateActiveTime() {
            this.lastActiveTime = LocalDateTime.now();
            this.expireTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24);
        }

        /**
         * 检查是否过期
         */
        public boolean isExpired(long currentTime) {
            return currentTime > expireTime;
        }

        /**
         * 检查是否有活跃会话
         */
        public boolean hasActiveSession() {
            return authSessionId != null || roomId != null || signalingSessionId != null;
        }

        // Getters and Setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getAuthSessionId() { return authSessionId; }
        public void setAuthSessionId(String authSessionId) { this.authSessionId = authSessionId; }

        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }

        public String getSignalingSessionId() { return signalingSessionId; }
        public void setSignalingSessionId(String signalingSessionId) { this.signalingSessionId = signalingSessionId; }

        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

        public LocalDateTime getLastActiveTime() { return lastActiveTime; }
        public void setLastActiveTime(LocalDateTime lastActiveTime) { this.lastActiveTime = lastActiveTime; }

        public long getExpireTime() { return expireTime; }
        public void setExpireTime(long expireTime) { this.expireTime = expireTime; }

        public AuthSession getAuthSession() { return authSession; }
        public void setAuthSession(AuthSession authSession) { this.authSession = authSession; }

        public SignalingSession getSignalingSession() { return signalingSession; }
        public void setSignalingSession(SignalingSession signalingSession) { this.signalingSession = signalingSession; }

        @Override
        public String toString() {
            return String.format("SessionBinding{playerId='%s', authSessionId='%s', roomId='%s', " +
                               "signalingSessionId='%s', createTime=%s, lastActiveTime=%s}",
                               playerId, authSessionId, roomId, signalingSessionId, createTime, lastActiveTime);
        }
    }

    /**
     * 会话统计信息类
     */
    public static class SessionStatistics {
        private int totalActiveSessions;
        private int onlinePlayerCount;
        private int inRoomPlayerCount;
        private int signalingSessionCount;
        private LocalDateTime statisticsTime;

        public SessionStatistics() {
            this.statisticsTime = LocalDateTime.now();
        }

        // Getters and Setters
        public int getTotalActiveSessions() { return totalActiveSessions; }
        public void setTotalActiveSessions(int totalActiveSessions) { this.totalActiveSessions = totalActiveSessions; }

        public int getOnlinePlayerCount() { return onlinePlayerCount; }
        public void setOnlinePlayerCount(int onlinePlayerCount) { this.onlinePlayerCount = onlinePlayerCount; }

        public int getInRoomPlayerCount() { return inRoomPlayerCount; }
        public void setInRoomPlayerCount(int inRoomPlayerCount) { this.inRoomPlayerCount = inRoomPlayerCount; }

        public int getSignalingSessionCount() { return signalingSessionCount; }
        public void setSignalingSessionCount(int signalingSessionCount) { this.signalingSessionCount = signalingSessionCount; }

        public LocalDateTime getStatisticsTime() { return statisticsTime; }
        public void setStatisticsTime(LocalDateTime statisticsTime) { this.statisticsTime = statisticsTime; }

        @Override
        public String toString() {
            return String.format("SessionStatistics{totalActiveSessions=%d, onlinePlayerCount=%d, " +
                               "inRoomPlayerCount=%d, signalingSessionCount=%d, statisticsTime=%s}",
                               totalActiveSessions, onlinePlayerCount, inRoomPlayerCount, 
                               signalingSessionCount, statisticsTime);
        }
    }
}