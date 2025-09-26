package com.whale.lumina.matchmaking;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.player.Player;
import com.whale.lumina.player.PlayerRepository;
import com.whale.lumina.room.RoomManager;
import com.whale.lumina.room.RoomConfig;
import com.whale.lumina.websocket.WebSocketManager;
import com.whale.lumina.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 匹配分发器
 * 
 * 负责将匹配结果分发给相关玩家和系统组件
 * 
 * @author Lumina Team
 */
@Service
public class MatchDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(MatchDispatcher.class);

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private WebSocketManager webSocketManager;

    @Autowired
    private NotificationService notificationService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, DispatchSession> activeSessions = new ConcurrentHashMap<>();

    // 分发配置
    private static final int DISPATCH_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_SECONDS = 5;

    /**
     * 初始化分发器
     */
    public void initialize() {
        // 启动清理任务
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 0, 60, TimeUnit.SECONDS);
        
        logger.info("匹配分发器已初始化");
    }

    /**
     * 关闭分发器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("匹配分发器已关闭");
    }

    // ========== 主要分发方法 ==========

    /**
     * 分发匹配结果
     * 
     * @param matchResult 匹配结果
     * @return 分发结果的异步任务
     */
    public CompletableFuture<DispatchResult> dispatchMatch(MatchResult matchResult) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("开始分发匹配结果: matchId={}", matchResult.getMatchId());
                
                // 创建分发会话
                DispatchSession session = createDispatchSession(matchResult);
                activeSessions.put(matchResult.getMatchId(), session);
                
                // 执行分发流程
                DispatchResult result = executeDispatchFlow(session);
                
                // 更新会话状态
                session.setResult(result);
                session.setStatus(result.isSuccess() ? DispatchStatus.COMPLETED : DispatchStatus.FAILED);
                
                logger.debug("匹配结果分发完成: matchId={}, success={}", 
                           matchResult.getMatchId(), result.isSuccess());
                
                return result;
                
            } catch (Exception e) {
                logger.error("分发匹配结果失败: matchId={}", matchResult.getMatchId(), e);
                return DispatchResult.failure("分发失败: " + e.getMessage());
            }
        });
    }

    /**
     * 批量分发匹配结果
     * 
     * @param matchResults 匹配结果列表
     * @return 分发结果列表的异步任务
     */
    public CompletableFuture<List<DispatchResult>> dispatchMatches(List<MatchResult> matchResults) {
        List<CompletableFuture<DispatchResult>> futures = new ArrayList<>();
        
        for (MatchResult matchResult : matchResults) {
            futures.add(dispatchMatch(matchResult));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }

    // ========== 分发流程 ==========

    /**
     * 执行分发流程
     * 
     * @param session 分发会话
     * @return 分发结果
     */
    private DispatchResult executeDispatchFlow(DispatchSession session) {
        MatchResult matchResult = session.getMatchResult();
        
        try {
            // 1. 验证匹配结果
            if (!validateMatchResult(matchResult)) {
                return DispatchResult.failure("匹配结果验证失败");
            }
            
            // 2. 创建游戏房间
            String roomId = createGameRoom(matchResult);
            if (roomId == null) {
                return DispatchResult.failure("创建游戏房间失败");
            }
            matchResult.setRoomId(roomId);
            
            // 3. 通知玩家
            boolean notificationSuccess = notifyPlayers(matchResult);
            if (!notificationSuccess) {
                // 清理已创建的房间
                cleanupGameRoom(roomId);
                return DispatchResult.failure("通知玩家失败");
            }
            
            // 4. 等待玩家响应
            boolean acceptanceSuccess = waitForPlayerAcceptance(session);
            if (!acceptanceSuccess) {
                // 清理已创建的房间
                cleanupGameRoom(roomId);
                return DispatchResult.failure("玩家接受超时或拒绝");
            }
            
            // 5. 更新玩家状态
            updatePlayerStates(matchResult);
            
            // 6. 记录分发统计
            recordDispatchStatistics(session);
            
            return DispatchResult.success(roomId);
            
        } catch (Exception e) {
            logger.error("执行分发流程失败: matchId={}", matchResult.getMatchId(), e);
            return DispatchResult.failure("分发流程异常: " + e.getMessage());
        }
    }

    /**
     * 验证匹配结果
     * 
     * @param matchResult 匹配结果
     * @return 是否有效
     */
    private boolean validateMatchResult(MatchResult matchResult) {
        if (matchResult == null || !matchResult.isValid()) {
            return false;
        }
        
        // 验证玩家是否都在线
        for (MatchRequest request : matchResult.getPlayers()) {
            Player player = playerRepository.getPlayerById(request.getPlayerId());
            if (player == null || !player.isOnline()) {
                logger.warn("玩家不在线: playerId={}", request.getPlayerId());
                return false;
            }
        }
        
        return true;
    }

    /**
     * 创建游戏房间
     * 
     * @param matchResult 匹配结果
     * @return 房间ID，失败返回null
     */
    private String createGameRoom(MatchResult matchResult) {
        try {
            RoomConfig roomConfig = createRoomConfig(matchResult);
            String roomId = roomManager.createRoom(roomConfig);
            
            logger.debug("为匹配创建房间: matchId={}, roomId={}", 
                        matchResult.getMatchId(), roomId);
            
            return roomId;
            
        } catch (Exception e) {
            logger.error("创建游戏房间失败: matchId={}", matchResult.getMatchId(), e);
            return null;
        }
    }

    /**
     * 创建房间配置
     * 
     * @param matchResult 匹配结果
     * @return 房间配置
     */
    private RoomConfig createRoomConfig(MatchResult matchResult) {
        RoomConfig config = new RoomConfig();
        config.setGameMode(matchResult.getGameMode());
        config.setMaxPlayers(matchResult.getPlayerCount());
        config.setPrivate(false);
        config.setAutoStart(true);
        
        // 根据匹配类型设置房间属性
        switch (matchResult.getMatchType()) {
            case RANKED_MATCH:
                config.setRanked(true);
                break;
            case TOURNAMENT:
                config.setTournament(true);
                break;
            case CUSTOM_MATCH:
                // 从元数据中获取自定义配置
                Map<String, Object> metadata = matchResult.getMetadata();
                if (metadata != null) {
                    applyCustomRoomConfig(config, metadata);
                }
                break;
        }
        
        return config;
    }

    /**
     * 应用自定义房间配置
     * 
     * @param config 房间配置
     * @param metadata 元数据
     */
    private void applyCustomRoomConfig(RoomConfig config, Map<String, Object> metadata) {
        // 从元数据中提取自定义配置
        if (metadata.containsKey("mapName")) {
            config.setMapName((String) metadata.get("mapName"));
        }
        if (metadata.containsKey("gameTime")) {
            config.setGameTime((Integer) metadata.get("gameTime"));
        }
        if (metadata.containsKey("allowSpectators")) {
            config.setAllowSpectators((Boolean) metadata.get("allowSpectators"));
        }
    }

    /**
     * 通知玩家
     * 
     * @param matchResult 匹配结果
     * @return 是否成功
     */
    private boolean notifyPlayers(MatchResult matchResult) {
        try {
            MatchNotification notification = createMatchNotification(matchResult);
            
            for (MatchRequest request : matchResult.getPlayers()) {
                String playerId = request.getPlayerId();
                
                // 通过WebSocket发送实时通知
                webSocketManager.sendToUser(playerId, "match_found", notification);
                
                // 发送推送通知
                notificationService.sendMatchFoundNotification(playerId, notification);
                
                logger.debug("已通知玩家匹配结果: playerId={}, matchId={}", 
                           playerId, matchResult.getMatchId());
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("通知玩家失败: matchId={}", matchResult.getMatchId(), e);
            return false;
        }
    }

    /**
     * 创建匹配通知
     * 
     * @param matchResult 匹配结果
     * @return 匹配通知
     */
    private MatchNotification createMatchNotification(MatchResult matchResult) {
        MatchNotification notification = new MatchNotification();
        notification.setMatchId(matchResult.getMatchId());
        notification.setGameMode(matchResult.getGameMode());
        notification.setMatchType(matchResult.getMatchType());
        notification.setRoomId(matchResult.getRoomId());
        notification.setPlayerCount(matchResult.getPlayerCount());
        notification.setQualityScore(matchResult.getQualityScore());
        notification.setEstimatedWaitTime(matchResult.getEstimatedWaitTime());
        notification.setExpireTime(LocalDateTime.now().plusSeconds(DISPATCH_TIMEOUT_SECONDS));
        
        return notification;
    }

    /**
     * 等待玩家接受
     * 
     * @param session 分发会话
     * @return 是否所有玩家都接受
     */
    private boolean waitForPlayerAcceptance(DispatchSession session) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = DISPATCH_TIMEOUT_SECONDS * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (session.areAllPlayersAccepted()) {
                return true;
            }
            
            if (session.hasAnyPlayerRejected()) {
                logger.debug("有玩家拒绝匹配: matchId={}", session.getMatchResult().getMatchId());
                return false;
            }
            
            try {
                Thread.sleep(1000); // 每秒检查一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        logger.debug("等待玩家接受超时: matchId={}", session.getMatchResult().getMatchId());
        return false;
    }

    /**
     * 更新玩家状态
     * 
     * @param matchResult 匹配结果
     */
    private void updatePlayerStates(MatchResult matchResult) {
        for (MatchRequest request : matchResult.getPlayers()) {
            try {
                Player player = playerRepository.getPlayerById(request.getPlayerId());
                if (player != null) {
                    player.setCurrentRoomId(matchResult.getRoomId());
                    player.updateStatus(Player.PlayerStatus.IN_GAME);
                    playerRepository.updatePlayer(player);
                }
            } catch (Exception e) {
                logger.error("更新玩家状态失败: playerId={}", request.getPlayerId(), e);
            }
        }
    }

    /**
     * 清理游戏房间
     * 
     * @param roomId 房间ID
     */
    private void cleanupGameRoom(String roomId) {
        try {
            if (roomId != null) {
                roomManager.removeRoom(roomId);
                logger.debug("已清理游戏房间: roomId={}", roomId);
            }
        } catch (Exception e) {
            logger.error("清理游戏房间失败: roomId={}", roomId, e);
        }
    }

    /**
     * 记录分发统计
     * 
     * @param session 分发会话
     */
    private void recordDispatchStatistics(DispatchSession session) {
        // TODO: 实现分发统计记录
        logger.debug("记录分发统计: matchId={}, duration={}ms", 
                    session.getMatchResult().getMatchId(), 
                    System.currentTimeMillis() - session.getCreateTime());
    }

    // ========== 玩家响应处理 ==========

    /**
     * 处理玩家接受匹配
     * 
     * @param matchId 匹配ID
     * @param playerId 玩家ID
     * @return 是否成功
     */
    public boolean handlePlayerAcceptance(String matchId, String playerId) {
        DispatchSession session = activeSessions.get(matchId);
        if (session == null) {
            logger.warn("未找到分发会话: matchId={}, playerId={}", matchId, playerId);
            return false;
        }
        
        session.setPlayerAccepted(playerId, true);
        logger.debug("玩家接受匹配: matchId={}, playerId={}", matchId, playerId);
        
        return true;
    }

    /**
     * 处理玩家拒绝匹配
     * 
     * @param matchId 匹配ID
     * @param playerId 玩家ID
     * @return 是否成功
     */
    public boolean handlePlayerRejection(String matchId, String playerId) {
        DispatchSession session = activeSessions.get(matchId);
        if (session == null) {
            logger.warn("未找到分发会话: matchId={}, playerId={}", matchId, playerId);
            return false;
        }
        
        session.setPlayerAccepted(playerId, false);
        logger.debug("玩家拒绝匹配: matchId={}, playerId={}", matchId, playerId);
        
        return true;
    }

    // ========== 辅助方法 ==========

    /**
     * 创建分发会话
     * 
     * @param matchResult 匹配结果
     * @return 分发会话
     */
    private DispatchSession createDispatchSession(MatchResult matchResult) {
        DispatchSession session = new DispatchSession(matchResult);
        
        // 初始化玩家接受状态
        for (MatchRequest request : matchResult.getPlayers()) {
            session.setPlayerAccepted(request.getPlayerId(), null); // null表示未响应
        }
        
        return session;
    }

    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions() {
        try {
            long currentTime = System.currentTimeMillis();
            
            activeSessions.entrySet().removeIf(entry -> {
                DispatchSession session = entry.getValue();
                return session.isExpired(currentTime);
            });
            
        } catch (Exception e) {
            logger.error("清理过期分发会话失败", e);
        }
    }

    // ========== 查询方法 ==========

    /**
     * 获取分发会话
     * 
     * @param matchId 匹配ID
     * @return 分发会话
     */
    public DispatchSession getDispatchSession(String matchId) {
        return activeSessions.get(matchId);
    }

    /**
     * 获取活跃分发会话列表
     * 
     * @return 活跃会话列表
     */
    public List<DispatchSession> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }

    /**
     * 获取分发统计信息
     * 
     * @return 分发统计
     */
    public DispatchStatistics getDispatchStatistics() {
        DispatchStatistics stats = new DispatchStatistics();
        stats.setActiveSessionCount(activeSessions.size());
        stats.setTotalDispatches(getTotalDispatchCount());
        stats.setSuccessRate(getDispatchSuccessRate());
        stats.setAverageDispatchTime(getAverageDispatchTime());
        
        return stats;
    }

    // ========== 私有统计方法 ==========

    private long getTotalDispatchCount() {
        // TODO: 从统计服务获取总分发数
        return 0;
    }

    private double getDispatchSuccessRate() {
        // TODO: 计算分发成功率
        return 0.0;
    }

    private double getAverageDispatchTime() {
        // TODO: 计算平均分发时间
        return 0.0;
    }

    // ========== 内部类 ==========

    /**
     * 分发会话类
     */
    public static class DispatchSession {
        private final MatchResult matchResult;
        private final long createTime;
        private final long expireTime;
        private final Map<String, Boolean> playerAcceptances;
        private DispatchStatus status;
        private DispatchResult result;

        public DispatchSession(MatchResult matchResult) {
            this.matchResult = matchResult;
            this.createTime = System.currentTimeMillis();
            this.expireTime = createTime + TimeUnit.SECONDS.toMillis(DISPATCH_TIMEOUT_SECONDS);
            this.playerAcceptances = new ConcurrentHashMap<>();
            this.status = DispatchStatus.PENDING;
        }

        public boolean isExpired(long currentTime) {
            return currentTime > expireTime;
        }

        public boolean areAllPlayersAccepted() {
            return playerAcceptances.values().stream().allMatch(accepted -> accepted != null && accepted);
        }

        public boolean hasAnyPlayerRejected() {
            return playerAcceptances.values().stream().anyMatch(accepted -> accepted != null && !accepted);
        }

        public void setPlayerAccepted(String playerId, Boolean accepted) {
            playerAcceptances.put(playerId, accepted);
        }

        // Getters and Setters
        public MatchResult getMatchResult() { return matchResult; }
        public long getCreateTime() { return createTime; }
        public long getExpireTime() { return expireTime; }
        public Map<String, Boolean> getPlayerAcceptances() { return playerAcceptances; }
        public DispatchStatus getStatus() { return status; }
        public void setStatus(DispatchStatus status) { this.status = status; }
        public DispatchResult getResult() { return result; }
        public void setResult(DispatchResult result) { this.result = result; }
    }

    /**
     * 分发状态枚举
     */
    public enum DispatchStatus {
        PENDING,    // 等待中
        NOTIFYING,  // 通知中
        WAITING,    // 等待响应
        COMPLETED,  // 已完成
        FAILED,     // 失败
        EXPIRED     // 过期
    }

    /**
     * 分发结果类
     */
    public static class DispatchResult {
        private final boolean success;
        private final String message;
        private final String roomId;
        private final LocalDateTime timestamp;

        private DispatchResult(boolean success, String message, String roomId) {
            this.success = success;
            this.message = message;
            this.roomId = roomId;
            this.timestamp = LocalDateTime.now();
        }

        public static DispatchResult success(String roomId) {
            return new DispatchResult(true, "分发成功", roomId);
        }

        public static DispatchResult failure(String message) {
            return new DispatchResult(false, message, null);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getRoomId() { return roomId; }
        public LocalDateTime getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("DispatchResult{success=%s, message='%s', roomId='%s'}", 
                               success, message, roomId);
        }
    }

    /**
     * 匹配通知类
     */
    public static class MatchNotification {
        private String matchId;
        private String gameMode;
        private MatchQueue.MatchType matchType;
        private String roomId;
        private int playerCount;
        private int qualityScore;
        private int estimatedWaitTime;
        private LocalDateTime expireTime;

        // Getters and Setters
        public String getMatchId() { return matchId; }
        public void setMatchId(String matchId) { this.matchId = matchId; }

        public String getGameMode() { return gameMode; }
        public void setGameMode(String gameMode) { this.gameMode = gameMode; }

        public MatchQueue.MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchQueue.MatchType matchType) { this.matchType = matchType; }

        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }

        public int getPlayerCount() { return playerCount; }
        public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }

        public int getQualityScore() { return qualityScore; }
        public void setQualityScore(int qualityScore) { this.qualityScore = qualityScore; }

        public int getEstimatedWaitTime() { return estimatedWaitTime; }
        public void setEstimatedWaitTime(int estimatedWaitTime) { this.estimatedWaitTime = estimatedWaitTime; }

        public LocalDateTime getExpireTime() { return expireTime; }
        public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    }

    /**
     * 分发统计信息类
     */
    public static class DispatchStatistics {
        private int activeSessionCount;
        private long totalDispatches;
        private double successRate;
        private double averageDispatchTime;
        private LocalDateTime statisticsTime;

        public DispatchStatistics() {
            this.statisticsTime = LocalDateTime.now();
        }

        // Getters and Setters
        public int getActiveSessionCount() { return activeSessionCount; }
        public void setActiveSessionCount(int activeSessionCount) { this.activeSessionCount = activeSessionCount; }

        public long getTotalDispatches() { return totalDispatches; }
        public void setTotalDispatches(long totalDispatches) { this.totalDispatches = totalDispatches; }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }

        public double getAverageDispatchTime() { return averageDispatchTime; }
        public void setAverageDispatchTime(double averageDispatchTime) { this.averageDispatchTime = averageDispatchTime; }

        public LocalDateTime getStatisticsTime() { return statisticsTime; }
        public void setStatisticsTime(LocalDateTime statisticsTime) { this.statisticsTime = statisticsTime; }

        @Override
        public String toString() {
            return String.format("DispatchStatistics{activeSessionCount=%d, totalDispatches=%d, " +
                               "successRate=%.2f, averageDispatchTime=%.2f}",
                               activeSessionCount, totalDispatches, successRate, averageDispatchTime);
        }
    }
}