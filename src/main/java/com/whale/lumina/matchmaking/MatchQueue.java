package com.whale.lumina.matchmaking;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.data.RedisClient;
import com.whale.lumina.player.Player;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 匹配队列管理器
 * 
 * 负责管理不同游戏模式的匹配队列，支持优先级匹配、技能匹配和快速匹配
 * 
 * @author Lumina Team
 */
@Component
public class MatchQueue {

    private static final Logger logger = LoggerFactory.getLogger(MatchQueue.class);

    @Autowired
    private RedisClient redisClient;

    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // 内存队列缓存
    private final Map<String, Queue<MatchRequest>> queueCache = new ConcurrentHashMap<>();
    private final Map<String, MatchRequest> requestCache = new ConcurrentHashMap<>();

    // Redis键前缀
    private static final String QUEUE_KEY_PREFIX = "match:queue:";
    private static final String REQUEST_KEY_PREFIX = "match:request:";
    private static final String QUEUE_STATS_KEY_PREFIX = "match:stats:";
    private static final String ACTIVE_QUEUES_KEY = "match:queues:active";

    // 队列配置
    private static final int MAX_QUEUE_SIZE = 1000;
    private static final int MAX_WAIT_TIME_MINUTES = 10;
    private static final int PRIORITY_BOOST_INTERVAL = 60; // 每60秒提升优先级

    /**
     * 构造函数
     */
    public MatchQueue() {
        this.objectMapper = new ObjectMapper();
    }

    // ========== 队列管理 ==========

    /**
     * 加入匹配队列
     * 
     * @param request 匹配请求
     * @throws GameException 加入失败时抛出
     */
    public void enqueue(MatchRequest request) throws GameException {
        if (request == null || request.getPlayerId() == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "匹配请求不能为空");
        }

        lock.writeLock().lock();
        try {
            String queueKey = getQueueKey(request.getGameMode(), request.getMatchType());
            
            // 检查玩家是否已在队列中
            if (isPlayerInQueue(request.getPlayerId())) {
                throw new GameException(ErrorCodes.PLAYER_ALREADY_IN_QUEUE, "玩家已在匹配队列中");
            }
            
            // 检查队列大小
            if (getQueueSize(queueKey) >= MAX_QUEUE_SIZE) {
                throw new GameException(ErrorCodes.QUEUE_FULL, "匹配队列已满");
            }
            
            // 设置请求时间和初始优先级
            request.setRequestTime(LocalDateTime.now());
            request.calculatePriority();
            
            // 加入内存队列
            Queue<MatchRequest> queue = getOrCreateQueue(queueKey);
            queue.offer(request);
            
            // 缓存请求
            requestCache.put(request.getPlayerId(), request);
            
            // 持久化到Redis
            saveRequestToRedis(request);
            saveQueueToRedis(queueKey, queue);
            
            // 更新统计
            updateQueueStatistics(queueKey, 1);
            
            logger.debug("玩家加入匹配队列: playerId={}, gameMode={}, matchType={}", 
                        request.getPlayerId(), request.getGameMode(), request.getMatchType());
            
        } catch (Exception e) {
            logger.error("加入匹配队列失败: playerId={}", request.getPlayerId(), e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "加入匹配队列失败: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 离开匹配队列
     * 
     * @param playerId 玩家ID
     * @throws GameException 离开失败时抛出
     */
    public void dequeue(String playerId) throws GameException {
        if (playerId == null || playerId.isEmpty()) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家ID不能为空");
        }

        lock.writeLock().lock();
        try {
            MatchRequest request = requestCache.get(playerId);
            if (request == null) {
                // 从Redis加载
                request = loadRequestFromRedis(playerId);
                if (request == null) {
                    return; // 玩家不在队列中
                }
            }
            
            String queueKey = getQueueKey(request.getGameMode(), request.getMatchType());
            
            // 从内存队列移除
            Queue<MatchRequest> queue = queueCache.get(queueKey);
            if (queue != null) {
                queue.removeIf(r -> playerId.equals(r.getPlayerId()));
            }
            
            // 从缓存移除
            requestCache.remove(playerId);
            
            // 从Redis移除
            removeRequestFromRedis(playerId);
            saveQueueToRedis(queueKey, queue);
            
            // 更新统计
            updateQueueStatistics(queueKey, -1);
            
            logger.debug("玩家离开匹配队列: playerId={}", playerId);
            
        } catch (Exception e) {
            logger.error("离开匹配队列失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "离开匹配队列失败: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取匹配候选者
     * 
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     * @param count 需要的玩家数量
     * @return 匹配候选者列表
     */
    public List<MatchRequest> getMatchCandidates(String gameMode, MatchType matchType, int count) {
        lock.readLock().lock();
        try {
            String queueKey = getQueueKey(gameMode, matchType);
            Queue<MatchRequest> queue = getOrCreateQueue(queueKey);
            
            List<MatchRequest> candidates = new ArrayList<>();
            Iterator<MatchRequest> iterator = queue.iterator();
            
            while (iterator.hasNext() && candidates.size() < count) {
                MatchRequest request = iterator.next();
                
                // 检查请求是否仍然有效
                if (isRequestValid(request)) {
                    candidates.add(request);
                } else {
                    // 移除无效请求
                    iterator.remove();
                    requestCache.remove(request.getPlayerId());
                    removeRequestFromRedis(request.getPlayerId());
                }
            }
            
            return candidates;
            
        } catch (Exception e) {
            logger.error("获取匹配候选者失败: gameMode={}, matchType={}", gameMode, matchType, e);
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 移除匹配候选者
     * 
     * @param candidates 候选者列表
     */
    public void removeCandidates(List<MatchRequest> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (MatchRequest candidate : candidates) {
                String playerId = candidate.getPlayerId();
                String queueKey = getQueueKey(candidate.getGameMode(), candidate.getMatchType());
                
                // 从内存队列移除
                Queue<MatchRequest> queue = queueCache.get(queueKey);
                if (queue != null) {
                    queue.removeIf(r -> playerId.equals(r.getPlayerId()));
                }
                
                // 从缓存移除
                requestCache.remove(playerId);
                
                // 从Redis移除
                removeRequestFromRedis(playerId);
                
                // 更新统计
                updateQueueStatistics(queueKey, -1);
            }
            
            logger.debug("移除匹配候选者: count={}", candidates.size());
            
        } catch (Exception e) {
            logger.error("移除匹配候选者失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== 队列查询 ==========

    /**
     * 检查玩家是否在队列中
     * 
     * @param playerId 玩家ID
     * @return 是否在队列中
     */
    public boolean isPlayerInQueue(String playerId) {
        return requestCache.containsKey(playerId) || loadRequestFromRedis(playerId) != null;
    }

    /**
     * 获取玩家的匹配请求
     * 
     * @param playerId 玩家ID
     * @return 匹配请求，如果不存在返回null
     */
    public MatchRequest getPlayerRequest(String playerId) {
        MatchRequest request = requestCache.get(playerId);
        if (request == null) {
            request = loadRequestFromRedis(playerId);
            if (request != null) {
                requestCache.put(playerId, request);
            }
        }
        return request;
    }

    /**
     * 获取队列大小
     * 
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     * @return 队列大小
     */
    public int getQueueSize(String gameMode, MatchType matchType) {
        String queueKey = getQueueKey(gameMode, matchType);
        return getQueueSize(queueKey);
    }

    /**
     * 获取所有队列的总大小
     * 
     * @return 所有队列的总大小
     */
    public int getQueueSize() {
        return queueCache.values().stream()
                .mapToInt(Queue::size)
                .sum();
    }

    /**
     * 获取队列统计信息
     * 
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     * @return 队列统计
     */
    public QueueStatistics getQueueStatistics(String gameMode, MatchType matchType) {
        try {
            String queueKey = getQueueKey(gameMode, matchType);
            String statsKey = QUEUE_STATS_KEY_PREFIX + queueKey;
            String statsJson = redisClient.get(statsKey);
            
            if (statsJson != null) {
                return objectMapper.readValue(statsJson, QueueStatistics.class);
            }
            
            // 生成默认统计
            QueueStatistics stats = new QueueStatistics();
            stats.setGameMode(gameMode);
            stats.setMatchType(matchType);
            stats.setCurrentSize(getQueueSize(queueKey));
            stats.setAverageWaitTime(0);
            stats.setTotalMatches(0);
            
            return stats;
            
        } catch (Exception e) {
            logger.error("获取队列统计失败: gameMode={}, matchType={}", gameMode, matchType, e);
            return new QueueStatistics();
        }
    }

    /**
     * 获取所有活跃队列
     * 
     * @return 活跃队列列表
     */
    public List<String> getActiveQueues() {
        try {
            Set<String> activeQueues = redisClient.smembers(ACTIVE_QUEUES_KEY);
            return new ArrayList<>(activeQueues);
        } catch (Exception e) {
            logger.error("获取活跃队列失败", e);
            return new ArrayList<>();
        }
    }

    // ========== 队列维护 ==========

    /**
     * 更新队列优先级
     * 
     * 定期调用此方法来提升等待时间较长的玩家的优先级
     */
    public void updateQueuePriorities() {
        lock.writeLock().lock();
        try {
            long currentTime = System.currentTimeMillis();
            
            for (Map.Entry<String, Queue<MatchRequest>> entry : queueCache.entrySet()) {
                Queue<MatchRequest> queue = entry.getValue();
                boolean updated = false;
                
                for (MatchRequest request : queue) {
                    long waitTime = currentTime - request.getRequestTimeMillis();
                    if (waitTime > PRIORITY_BOOST_INTERVAL * 1000) {
                        request.boostPriority();
                        updated = true;
                    }
                }
                
                if (updated) {
                    // 重新排序队列
                    List<MatchRequest> sortedRequests = new ArrayList<>(queue);
                    sortedRequests.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
                    
                    queue.clear();
                    queue.addAll(sortedRequests);
                    
                    // 保存到Redis
                    saveQueueToRedis(entry.getKey(), queue);
                }
            }
            
        } catch (Exception e) {
            logger.error("更新队列优先级失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清理过期请求
     */
    public void cleanupExpiredRequests() {
        lock.writeLock().lock();
        try {
            long currentTime = System.currentTimeMillis();
            long maxWaitTime = MAX_WAIT_TIME_MINUTES * 60 * 1000;
            
            List<String> expiredPlayerIds = new ArrayList<>();
            
            for (Map.Entry<String, MatchRequest> entry : requestCache.entrySet()) {
                MatchRequest request = entry.getValue();
                long waitTime = currentTime - request.getRequestTimeMillis();
                
                if (waitTime > maxWaitTime) {
                    expiredPlayerIds.add(entry.getKey());
                }
            }
            
            // 移除过期请求
            for (String playerId : expiredPlayerIds) {
                try {
                    dequeue(playerId);
                    logger.debug("清理过期匹配请求: playerId={}", playerId);
                } catch (Exception e) {
                    logger.error("清理过期请求失败: playerId={}", playerId, e);
                }
            }
            
        } catch (Exception e) {
            logger.error("清理过期请求失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== 私有方法 ==========

    /**
     * 获取队列键
     */
    private String getQueueKey(String gameMode, MatchType matchType) {
        return gameMode + ":" + matchType.name();
    }

    /**
     * 获取或创建队列
     */
    private Queue<MatchRequest> getOrCreateQueue(String queueKey) {
        return queueCache.computeIfAbsent(queueKey, k -> {
            // 尝试从Redis加载
            Queue<MatchRequest> queue = loadQueueFromRedis(k);
            if (queue == null) {
                queue = new PriorityQueue<>((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
                // 添加到活跃队列
                redisClient.sadd(ACTIVE_QUEUES_KEY, k);
            }
            return queue;
        });
    }

    /**
     * 获取队列大小
     */
    private int getQueueSize(String queueKey) {
        Queue<MatchRequest> queue = queueCache.get(queueKey);
        return queue != null ? queue.size() : 0;
    }

    /**
     * 检查请求是否有效
     */
    private boolean isRequestValid(MatchRequest request) {
        if (request == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long waitTime = currentTime - request.getRequestTimeMillis();
        
        return waitTime <= MAX_WAIT_TIME_MINUTES * 60 * 1000;
    }

    /**
     * 保存请求到Redis
     */
    private void saveRequestToRedis(MatchRequest request) {
        try {
            String requestKey = REQUEST_KEY_PREFIX + request.getPlayerId();
            String requestJson = objectMapper.writeValueAsString(request);
            redisClient.setWithExpire(requestKey, requestJson, MAX_WAIT_TIME_MINUTES * 60);
        } catch (Exception e) {
            logger.error("保存请求到Redis失败: playerId={}", request.getPlayerId(), e);
        }
    }

    /**
     * 从Redis加载请求
     */
    private MatchRequest loadRequestFromRedis(String playerId) {
        try {
            String requestKey = REQUEST_KEY_PREFIX + playerId;
            String requestJson = redisClient.get(requestKey);
            
            if (requestJson != null) {
                return objectMapper.readValue(requestJson, MatchRequest.class);
            }
        } catch (Exception e) {
            logger.error("从Redis加载请求失败: playerId={}", playerId, e);
        }
        return null;
    }

    /**
     * 从Redis移除请求
     */
    private void removeRequestFromRedis(String playerId) {
        try {
            String requestKey = REQUEST_KEY_PREFIX + playerId;
            redisClient.del(requestKey);
        } catch (Exception e) {
            logger.error("从Redis移除请求失败: playerId={}", playerId, e);
        }
    }

    /**
     * 保存队列到Redis
     */
    private void saveQueueToRedis(String queueKey, Queue<MatchRequest> queue) {
        try {
            String redisKey = QUEUE_KEY_PREFIX + queueKey;
            List<MatchRequest> queueList = new ArrayList<>(queue);
            String queueJson = objectMapper.writeValueAsString(queueList);
            redisClient.setWithExpire(redisKey, queueJson, MAX_WAIT_TIME_MINUTES * 60);
        } catch (Exception e) {
            logger.error("保存队列到Redis失败: queueKey={}", queueKey, e);
        }
    }

    /**
     * 从Redis加载队列
     */
    private Queue<MatchRequest> loadQueueFromRedis(String queueKey) {
        try {
            String redisKey = QUEUE_KEY_PREFIX + queueKey;
            String queueJson = redisClient.get(redisKey);
            
            if (queueJson != null) {
                List<MatchRequest> queueList = objectMapper.readValue(queueJson, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MatchRequest.class));
                
                Queue<MatchRequest> queue = new PriorityQueue<>((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
                queue.addAll(queueList);
                
                // 更新缓存
                for (MatchRequest request : queueList) {
                    requestCache.put(request.getPlayerId(), request);
                }
                
                return queue;
            }
        } catch (Exception e) {
            logger.error("从Redis加载队列失败: queueKey={}", queueKey, e);
        }
        return null;
    }

    /**
     * 更新队列统计
     */
    private void updateQueueStatistics(String queueKey, int delta) {
        try {
            String statsKey = QUEUE_STATS_KEY_PREFIX + queueKey;
            String statsJson = redisClient.get(statsKey);
            
            QueueStatistics stats;
            if (statsJson != null) {
                stats = objectMapper.readValue(statsJson, QueueStatistics.class);
            } else {
                stats = new QueueStatistics();
                String[] parts = queueKey.split(":");
                if (parts.length >= 2) {
                    stats.setGameMode(parts[0]);
                    stats.setMatchType(MatchType.valueOf(parts[1]));
                }
            }
            
            stats.setCurrentSize(Math.max(0, stats.getCurrentSize() + delta));
            stats.setLastUpdateTime(LocalDateTime.now());
            
            String updatedStatsJson = objectMapper.writeValueAsString(stats);
            redisClient.setWithExpire(statsKey, updatedStatsJson, 3600); // 1小时过期
            
        } catch (Exception e) {
            logger.error("更新队列统计失败: queueKey={}", queueKey, e);
        }
    }

    // ========== 内部类 ==========

    /**
     * 匹配类型枚举
     */
    public enum MatchType {
        QUICK_MATCH,    // 快速匹配
        RANKED_MATCH,   // 排位匹配
        CUSTOM_MATCH,   // 自定义匹配
        TOURNAMENT      // 锦标赛匹配
    }

    /**
     * 队列统计信息
     */
    public static class QueueStatistics {
        private String gameMode;
        private MatchType matchType;
        private int currentSize;
        private double averageWaitTime;
        private int totalMatches;
        private LocalDateTime lastUpdateTime;

        public QueueStatistics() {
            this.lastUpdateTime = LocalDateTime.now();
        }

        // Getters and Setters
        public String getGameMode() { return gameMode; }
        public void setGameMode(String gameMode) { this.gameMode = gameMode; }

        public MatchType getMatchType() { return matchType; }
        public void setMatchType(MatchType matchType) { this.matchType = matchType; }

        public int getCurrentSize() { return currentSize; }
        public void setCurrentSize(int currentSize) { this.currentSize = currentSize; }

        public double getAverageWaitTime() { return averageWaitTime; }
        public void setAverageWaitTime(double averageWaitTime) { this.averageWaitTime = averageWaitTime; }

        public int getTotalMatches() { return totalMatches; }
        public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }

        public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }

        @Override
        public String toString() {
            return String.format("QueueStatistics{gameMode='%s', matchType=%s, currentSize=%d, " +
                               "averageWaitTime=%.2f, totalMatches=%d}",
                               gameMode, matchType, currentSize, averageWaitTime, totalMatches);
        }
    }
}