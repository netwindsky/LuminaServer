package com.whale.lumina.matchmaking;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.player.Player;
import com.whale.lumina.player.PlayerRepository;
import com.whale.lumina.room.RoomManager;
import com.whale.lumina.room.RoomConfig;
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
import java.util.stream.Collectors;

/**
 * 匹配制造器
 * 
 * 负责执行匹配算法，将合适的玩家组合成匹配结果
 * 
 * @author Lumina Team
 */
@Service
public class MatchMaker {

    private static final Logger logger = LoggerFactory.getLogger(MatchMaker.class);

    @Autowired
    private MatchQueue matchQueue;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private MatchDispatcher matchDispatcher;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, MatchingSession> activeSessions = new ConcurrentHashMap<>();

    // 匹配配置
    private static final int MIN_PLAYERS_PER_MATCH = 2;
    private static final int MAX_PLAYERS_PER_MATCH = 10;
    private static final int MATCH_ATTEMPT_INTERVAL = 5; // 秒
    private static final int MAX_MATCH_ATTEMPTS = 3;

    /**
     * 初始化匹配制造器
     */
    public void initialize() {
        // 启动定期匹配任务
        scheduler.scheduleAtFixedRate(this::performMatching, 0, MATCH_ATTEMPT_INTERVAL, TimeUnit.SECONDS);
        
        // 启动清理任务
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 0, 60, TimeUnit.SECONDS);
        
        logger.info("匹配制造器已初始化");
    }

    /**
     * 关闭匹配制造器
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
        logger.info("匹配制造器已关闭");
    }

    // ========== 主要匹配方法 ==========

    /**
     * 执行匹配
     * 
     * 定期调用此方法来处理所有活跃队列的匹配
     */
    public void performMatching() {
        try {
            List<String> activeQueues = matchQueue.getActiveQueues();
            
            for (String queueKey : activeQueues) {
                String[] parts = queueKey.split(":");
                if (parts.length >= 2) {
                    String gameMode = parts[0];
                    MatchQueue.MatchType matchType = MatchQueue.MatchType.valueOf(parts[1]);
                    
                    performMatchingForQueue(gameMode, matchType);
                }
            }
            
        } catch (Exception e) {
            logger.error("执行匹配失败", e);
        }
    }

    /**
     * 为特定队列执行匹配
     * 
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     */
    public void performMatchingForQueue(String gameMode, MatchQueue.MatchType matchType) {
        try {
            int queueSize = matchQueue.getQueueSize(gameMode, matchType);
            if (queueSize < MIN_PLAYERS_PER_MATCH) {
                return; // 队列中玩家不足
            }
            
            // 获取匹配候选者
            List<MatchRequest> candidates = matchQueue.getMatchCandidates(gameMode, matchType, 
                Math.min(queueSize, MAX_PLAYERS_PER_MATCH * 2));
            
            if (candidates.size() < MIN_PLAYERS_PER_MATCH) {
                return;
            }
            
            // 执行匹配算法
            List<MatchResult> matches = executeMatchingAlgorithm(candidates, gameMode, matchType);
            
            // 处理匹配结果
            for (MatchResult match : matches) {
                processMatchResult(match);
            }
            
        } catch (Exception e) {
            logger.error("为队列执行匹配失败: gameMode={}, matchType={}", gameMode, matchType, e);
        }
    }

    /**
     * 手动触发匹配
     * 
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     * @return 匹配结果的异步任务
     */
    public CompletableFuture<List<MatchResult>> triggerMatching(String gameMode, MatchQueue.MatchType matchType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<MatchRequest> candidates = matchQueue.getMatchCandidates(gameMode, matchType, MAX_PLAYERS_PER_MATCH * 2);
                return executeMatchingAlgorithm(candidates, gameMode, matchType);
            } catch (Exception e) {
                logger.error("手动触发匹配失败: gameMode={}, matchType={}", gameMode, matchType, e);
                return new ArrayList<>();
            }
        });
    }

    // ========== 匹配算法 ==========

    /**
     * 执行匹配算法
     * 
     * @param candidates 候选者列表
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     * @return 匹配结果列表
     */
    private List<MatchResult> executeMatchingAlgorithm(List<MatchRequest> candidates, 
                                                      String gameMode, MatchQueue.MatchType matchType) {
        List<MatchResult> results = new ArrayList<>();
        
        switch (matchType) {
            case QUICK_MATCH:
                results = performQuickMatching(candidates, gameMode);
                break;
            case RANKED_MATCH:
                results = performRankedMatching(candidates, gameMode);
                break;
            case CUSTOM_MATCH:
                results = performCustomMatching(candidates, gameMode);
                break;
            case TOURNAMENT:
                results = performTournamentMatching(candidates, gameMode);
                break;
        }
        
        return results;
    }

    /**
     * 快速匹配算法
     * 
     * 优先考虑匹配速度，降低匹配质量要求
     */
    private List<MatchResult> performQuickMatching(List<MatchRequest> candidates, String gameMode) {
        List<MatchResult> results = new ArrayList<>();
        List<MatchRequest> available = new ArrayList<>(candidates);
        
        while (available.size() >= MIN_PLAYERS_PER_MATCH) {
            List<MatchRequest> matchGroup = new ArrayList<>();
            MatchRequest primary = available.remove(0);
            matchGroup.add(primary);
            
            // 寻找兼容的玩家
            Iterator<MatchRequest> iterator = available.iterator();
            while (iterator.hasNext() && matchGroup.size() < getMaxPlayersForMode(gameMode)) {
                MatchRequest candidate = iterator.next();
                
                if (primary.getCompatibilityScore(candidate) >= 50) { // 快速匹配降低兼容性要求
                    matchGroup.add(candidate);
                    iterator.remove();
                }
            }
            
            if (matchGroup.size() >= MIN_PLAYERS_PER_MATCH) {
                MatchResult result = createMatchResult(matchGroup, gameMode, MatchQueue.MatchType.QUICK_MATCH);
                results.add(result);
            } else {
                // 如果无法形成匹配，将主要玩家放回队列
                available.add(0, primary);
                break;
            }
        }
        
        return results;
    }

    /**
     * 排位匹配算法
     * 
     * 优先考虑技能平衡和匹配质量
     */
    private List<MatchResult> performRankedMatching(List<MatchRequest> candidates, String gameMode) {
        List<MatchResult> results = new ArrayList<>();
        
        // 按技能等级分组
        Map<Integer, List<MatchRequest>> skillGroups = candidates.stream()
            .collect(Collectors.groupingBy(this::getPlayerSkillLevel));
        
        for (Map.Entry<Integer, List<MatchRequest>> entry : skillGroups.entrySet()) {
            List<MatchRequest> skillGroup = entry.getValue();
            
            while (skillGroup.size() >= MIN_PLAYERS_PER_MATCH) {
                List<MatchRequest> matchGroup = selectBestMatchGroup(skillGroup, gameMode);
                
                if (matchGroup.size() >= MIN_PLAYERS_PER_MATCH) {
                    MatchResult result = createMatchResult(matchGroup, gameMode, MatchQueue.MatchType.RANKED_MATCH);
                    results.add(result);
                    
                    // 从技能组中移除已匹配的玩家
                    skillGroup.removeAll(matchGroup);
                } else {
                    break;
                }
            }
        }
        
        return results;
    }

    /**
     * 自定义匹配算法
     * 
     * 基于玩家自定义条件进行匹配
     */
    private List<MatchResult> performCustomMatching(List<MatchRequest> candidates, String gameMode) {
        List<MatchResult> results = new ArrayList<>();
        List<MatchRequest> available = new ArrayList<>(candidates);
        
        while (available.size() >= MIN_PLAYERS_PER_MATCH) {
            MatchRequest primary = available.remove(0);
            List<MatchRequest> matchGroup = new ArrayList<>();
            matchGroup.add(primary);
            
            // 基于自定义条件寻找匹配
            Iterator<MatchRequest> iterator = available.iterator();
            while (iterator.hasNext() && matchGroup.size() < getMaxPlayersForMode(gameMode)) {
                MatchRequest candidate = iterator.next();
                
                if (isCustomCompatible(primary, candidate)) {
                    matchGroup.add(candidate);
                    iterator.remove();
                }
            }
            
            if (matchGroup.size() >= MIN_PLAYERS_PER_MATCH) {
                MatchResult result = createMatchResult(matchGroup, gameMode, MatchQueue.MatchType.CUSTOM_MATCH);
                results.add(result);
            } else {
                available.add(0, primary);
                break;
            }
        }
        
        return results;
    }

    /**
     * 锦标赛匹配算法
     * 
     * 为锦标赛创建平衡的匹配
     */
    private List<MatchResult> performTournamentMatching(List<MatchRequest> candidates, String gameMode) {
        List<MatchResult> results = new ArrayList<>();
        
        // 按优先级排序
        candidates.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
        
        int tournamentSize = getTournamentSizeForMode(gameMode);
        
        while (candidates.size() >= tournamentSize) {
            List<MatchRequest> tournamentGroup = new ArrayList<>();
            
            for (int i = 0; i < tournamentSize && !candidates.isEmpty(); i++) {
                tournamentGroup.add(candidates.remove(0));
            }
            
            MatchResult result = createMatchResult(tournamentGroup, gameMode, MatchQueue.MatchType.TOURNAMENT);
            results.add(result);
        }
        
        return results;
    }

    // ========== 匹配结果处理 ==========

    /**
     * 处理匹配结果
     * 
     * @param matchResult 匹配结果
     */
    private void processMatchResult(MatchResult matchResult) {
        try {
            // 创建匹配会话
            MatchingSession session = new MatchingSession(matchResult);
            activeSessions.put(matchResult.getMatchId(), session);
            
            // 从队列中移除已匹配的玩家
            matchQueue.removeCandidates(matchResult.getPlayers());
            
            // 更新玩家状态
            for (MatchRequest request : matchResult.getPlayers()) {
                request.updateStatus(MatchRequest.MatchStatus.MATCHED);
            }
            
            // 分发匹配结果
            matchDispatcher.dispatchMatch(matchResult);
            
            logger.debug("处理匹配结果: matchId={}, playerCount={}", 
                        matchResult.getMatchId(), matchResult.getPlayers().size());
            
        } catch (Exception e) {
            logger.error("处理匹配结果失败: matchId={}", matchResult.getMatchId(), e);
        }
    }

    /**
     * 创建匹配结果
     * 
     * @param players 玩家列表
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     * @return 匹配结果
     */
    private MatchResult createMatchResult(List<MatchRequest> players, String gameMode, MatchQueue.MatchType matchType) {
        MatchResult result = new MatchResult();
        result.setMatchId(generateMatchId());
        result.setGameMode(gameMode);
        result.setMatchType(matchType);
        result.setPlayers(new ArrayList<>(players));
        result.setMatchTime(LocalDateTime.now());
        result.setQualityScore(calculateMatchQuality(players));
        
        return result;
    }

    // ========== 辅助方法 ==========

    /**
     * 选择最佳匹配组
     * 
     * @param candidates 候选者列表
     * @param gameMode 游戏模式
     * @return 最佳匹配组
     */
    private List<MatchRequest> selectBestMatchGroup(List<MatchRequest> candidates, String gameMode) {
        int maxPlayers = getMaxPlayersForMode(gameMode);
        int targetSize = Math.min(candidates.size(), maxPlayers);
        
        if (targetSize < MIN_PLAYERS_PER_MATCH) {
            return new ArrayList<>();
        }
        
        // 简单实现：选择前N个玩家
        List<MatchRequest> bestGroup = new ArrayList<>();
        for (int i = 0; i < targetSize; i++) {
            bestGroup.add(candidates.get(i));
        }
        
        return bestGroup;
    }

    /**
     * 获取玩家技能等级
     * 
     * @param request 匹配请求
     * @return 技能等级
     */
    private int getPlayerSkillLevel(MatchRequest request) {
        Player player = playerRepository.getPlayerById(request.getPlayerId());
        return player != null ? player.getLevel() : 1;
    }

    /**
     * 检查自定义兼容性
     * 
     * @param primary 主要请求
     * @param candidate 候选请求
     * @return 是否兼容
     */
    private boolean isCustomCompatible(MatchRequest primary, MatchRequest candidate) {
        return primary.getCompatibilityScore(candidate) >= primary.getCriteria().getMinCompatibilityScore();
    }

    /**
     * 获取游戏模式的最大玩家数
     * 
     * @param gameMode 游戏模式
     * @return 最大玩家数
     */
    private int getMaxPlayersForMode(String gameMode) {
        // 根据游戏模式返回不同的最大玩家数
        switch (gameMode.toLowerCase()) {
            case "1v1":
                return 2;
            case "2v2":
                return 4;
            case "3v3":
                return 6;
            case "5v5":
                return 10;
            case "battle_royale":
                return 100;
            default:
                return 8;
        }
    }

    /**
     * 获取锦标赛模式的参赛人数
     * 
     * @param gameMode 游戏模式
     * @return 锦标赛人数
     */
    private int getTournamentSizeForMode(String gameMode) {
        // 根据游戏模式返回锦标赛人数
        switch (gameMode.toLowerCase()) {
            case "1v1":
                return 8; // 8人单淘汰
            case "2v2":
                return 8; // 4队双淘汰
            case "3v3":
                return 6; // 2队三淘汰
            default:
                return 16;
        }
    }

    /**
     * 计算匹配质量
     * 
     * @param players 玩家列表
     * @return 质量分数（0-100）
     */
    private int calculateMatchQuality(List<MatchRequest> players) {
        if (players.size() < 2) {
            return 0;
        }
        
        int totalScore = 0;
        int comparisons = 0;
        
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                totalScore += players.get(i).getCompatibilityScore(players.get(j));
                comparisons++;
            }
        }
        
        return comparisons > 0 ? totalScore / comparisons : 0;
    }

    /**
     * 生成匹配ID
     * 
     * @return 匹配ID
     */
    private String generateMatchId() {
        return "match_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    /**
     * 清理过期会话
     */
    private void cleanupExpiredSessions() {
        try {
            long currentTime = System.currentTimeMillis();
            
            activeSessions.entrySet().removeIf(entry -> {
                MatchingSession session = entry.getValue();
                return session.isExpired(currentTime);
            });
            
        } catch (Exception e) {
            logger.error("清理过期会话失败", e);
        }
    }

    // ========== 查询方法 ==========

    /**
     * 获取匹配统计信息
     * 
     * @return 匹配统计
     */
    public MatchingStatistics getMatchingStatistics() {
        MatchingStatistics stats = new MatchingStatistics();
        stats.setActiveSessionCount(activeSessions.size());
        stats.setTotalMatches(getTotalMatchCount());
        stats.setAverageMatchQuality(getAverageMatchQuality());
        stats.setMatchingSuccessRate(getMatchingSuccessRate());
        
        return stats;
    }

    /**
     * 获取活跃匹配会话
     * 
     * @return 活跃会话列表
     */
    public List<MatchingSession> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }

    /**
     * 获取匹配会话
     * 
     * @param matchId 匹配ID
     * @return 匹配会话
     */
    public MatchingSession getMatchingSession(String matchId) {
        return activeSessions.get(matchId);
    }

    // ========== 私有统计方法 ==========

    private long getTotalMatchCount() {
        // TODO: 从统计服务获取总匹配数
        return 0;
    }

    private double getAverageMatchQuality() {
        // TODO: 计算平均匹配质量
        return 0.0;
    }

    private double getMatchingSuccessRate() {
        // TODO: 计算匹配成功率
        return 0.0;
    }

    // ========== 内部类 ==========

    /**
     * 匹配会话类
     */
    public static class MatchingSession {
        private final MatchResult matchResult;
        private final long createTime;
        private final long expireTime;
        private MatchingStatus status;

        public MatchingSession(MatchResult matchResult) {
            this.matchResult = matchResult;
            this.createTime = System.currentTimeMillis();
            this.expireTime = createTime + TimeUnit.MINUTES.toMillis(10); // 10分钟过期
            this.status = MatchingStatus.CREATED;
        }

        public boolean isExpired(long currentTime) {
            return currentTime > expireTime;
        }

        // Getters and Setters
        public MatchResult getMatchResult() { return matchResult; }
        public long getCreateTime() { return createTime; }
        public long getExpireTime() { return expireTime; }
        public MatchingStatus getStatus() { return status; }
        public void setStatus(MatchingStatus status) { this.status = status; }

        public enum MatchingStatus {
            CREATED,    // 已创建
            DISPATCHED, // 已分发
            ACCEPTED,   // 已接受
            REJECTED,   // 已拒绝
            EXPIRED     // 已过期
        }
    }

    /**
     * 匹配统计信息类
     */
    public static class MatchingStatistics {
        private int activeSessionCount;
        private long totalMatches;
        private double averageMatchQuality;
        private double matchingSuccessRate;
        private LocalDateTime statisticsTime;

        public MatchingStatistics() {
            this.statisticsTime = LocalDateTime.now();
        }

        // Getters and Setters
        public int getActiveSessionCount() { return activeSessionCount; }
        public void setActiveSessionCount(int activeSessionCount) { this.activeSessionCount = activeSessionCount; }

        public long getTotalMatches() { return totalMatches; }
        public void setTotalMatches(long totalMatches) { this.totalMatches = totalMatches; }

        public double getAverageMatchQuality() { return averageMatchQuality; }
        public void setAverageMatchQuality(double averageMatchQuality) { this.averageMatchQuality = averageMatchQuality; }

        public double getMatchingSuccessRate() { return matchingSuccessRate; }
        public void setMatchingSuccessRate(double matchingSuccessRate) { this.matchingSuccessRate = matchingSuccessRate; }

        public LocalDateTime getStatisticsTime() { return statisticsTime; }
        public void setStatisticsTime(LocalDateTime statisticsTime) { this.statisticsTime = statisticsTime; }

        @Override
        public String toString() {
            return String.format("MatchingStatistics{activeSessionCount=%d, totalMatches=%d, " +
                               "averageMatchQuality=%.2f, matchingSuccessRate=%.2f}",
                               activeSessionCount, totalMatches, averageMatchQuality, matchingSuccessRate);
        }
    }
}