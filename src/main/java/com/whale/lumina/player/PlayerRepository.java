package com.whale.lumina.player;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.data.RedisClient;
import com.whale.lumina.data.DataSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 玩家数据访问层
 * 
 * 负责玩家数据的存储、检索和管理，支持Redis缓存和MySQL持久化
 * 
 * @author Lumina Team
 */
@Repository
public class PlayerRepository {

    private static final Logger logger = LoggerFactory.getLogger(PlayerRepository.class);

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private DataSyncService dataSyncService;

    private final ObjectMapper objectMapper;

    // Redis键前缀
    private static final String PLAYER_KEY_PREFIX = "player:";
    private static final String PLAYER_INDEX_KEY_PREFIX = "player:index:";
    private static final String ONLINE_PLAYERS_KEY = "players:online";
    private static final String PLAYER_STATS_KEY_PREFIX = "player:stats:";
    private static final String PLAYER_RANKING_KEY = "players:ranking";

    // 缓存过期时间
    private static final int PLAYER_CACHE_EXPIRE_TIME = 3600; // 1小时
    private static final int STATS_CACHE_EXPIRE_TIME = 1800; // 30分钟

    /**
     * 构造函数
     */
    public PlayerRepository() {
        this.objectMapper = new ObjectMapper();
    }

    // ========== 基本CRUD操作 ==========

    /**
     * 保存玩家
     * 
     * @param player 玩家对象
     * @throws GameException 保存失败时抛出
     */
    public void savePlayer(Player player) throws GameException {
        if (player == null || player.getPlayerId() == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家对象或ID不能为空");
        }

        try {
            String playerKey = PLAYER_KEY_PREFIX + player.getPlayerId();
            String playerJson = objectMapper.writeValueAsString(player);
            
            // 保存到Redis缓存
            redisClient.setWithExpire(playerKey, playerJson, PLAYER_CACHE_EXPIRE_TIME);
            
            // 更新索引
            updatePlayerIndexes(player);
            
            // 异步同步到数据库
            dataSyncService.updateData("players", player.getPlayerId(), playerJson);
            
            logger.debug("保存玩家: playerId={}, username={}", player.getPlayerId(), player.getUsername());
            
        } catch (Exception e) {
            logger.error("保存玩家失败: playerId={}", player.getPlayerId(), e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "保存玩家失败: " + e.getMessage());
        }
    }

    /**
     * 根据玩家ID获取玩家
     * 
     * @param playerId 玩家ID
     * @return 玩家对象，如果不存在返回null
     */
    public Player getPlayerById(String playerId) {
        if (playerId == null || playerId.isEmpty()) {
            return null;
        }

        try {
            String playerKey = PLAYER_KEY_PREFIX + playerId;
            String playerJson = redisClient.get(playerKey);
            
            if (playerJson != null) {
                Player player = objectMapper.readValue(playerJson, Player.class);
                // 延长缓存时间
                redisClient.expire(playerKey, PLAYER_CACHE_EXPIRE_TIME);
                return player;
            }
            
            // 从数据库加载
            return loadPlayerFromDatabase(playerId);
            
        } catch (Exception e) {
            logger.error("获取玩家失败: playerId={}", playerId, e);
            return null;
        }
    }

    /**
     * 根据用户ID获取玩家
     * 
     * @param userId 用户ID
     * @return 玩家对象，如果不存在返回null
     */
    public Player getPlayerByUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }

        try {
            // 先从索引中查找玩家ID
            String indexKey = PLAYER_INDEX_KEY_PREFIX + "userId:" + userId;
            String playerId = redisClient.get(indexKey);
            
            if (playerId != null) {
                return getPlayerById(playerId);
            }
            
            // 从数据库查找
            return loadPlayerByUserIdFromDatabase(userId);
            
        } catch (Exception e) {
            logger.error("根据用户ID获取玩家失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 根据用户名获取玩家
     * 
     * @param username 用户名
     * @return 玩家对象，如果不存在返回null
     */
    public Player getPlayerByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }

        try {
            // 先从索引中查找玩家ID
            String indexKey = PLAYER_INDEX_KEY_PREFIX + "username:" + username.toLowerCase();
            String playerId = redisClient.get(indexKey);
            
            if (playerId != null) {
                return getPlayerById(playerId);
            }
            
            // 从数据库查找
            return loadPlayerByUsernameFromDatabase(username);
            
        } catch (Exception e) {
            logger.error("根据用户名获取玩家失败: username={}", username, e);
            return null;
        }
    }

    /**
     * 更新玩家
     * 
     * @param player 玩家对象
     * @throws GameException 更新失败时抛出
     */
    public void updatePlayer(Player player) throws GameException {
        if (player == null || player.getPlayerId() == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家对象或ID不能为空");
        }

        // 检查玩家是否存在
        if (!playerExists(player.getPlayerId())) {
            throw new GameException(ErrorCodes.PLAYER_NOT_FOUND, "玩家不存在");
        }

        savePlayer(player);
    }

    /**
     * 删除玩家
     * 
     * @param playerId 玩家ID
     * @throws GameException 删除失败时抛出
     */
    public void deletePlayer(String playerId) throws GameException {
        if (playerId == null || playerId.isEmpty()) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家ID不能为空");
        }

        try {
            // 先获取玩家信息以便清理索引
            Player player = getPlayerById(playerId);
            if (player == null) {
                return; // 玩家不存在，直接返回
            }

            // 删除玩家数据
            String playerKey = PLAYER_KEY_PREFIX + playerId;
            redisClient.del(playerKey);
            
            // 清理索引
            removePlayerIndexes(player);
            
            // 从在线玩家列表中移除
            redisClient.srem(ONLINE_PLAYERS_KEY, playerId);
            
            // 删除统计数据
            String statsKey = PLAYER_STATS_KEY_PREFIX + playerId;
            redisClient.del(statsKey);
            
            // 从排行榜中移除
            redisClient.zrem(PLAYER_RANKING_KEY, playerId);
            
            // 异步从数据库删除
            dataSyncService.deleteData("players", playerId);
            
            logger.debug("删除玩家: playerId={}", playerId);
            
        } catch (Exception e) {
            logger.error("删除玩家失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "删除玩家失败: " + e.getMessage());
        }
    }

    // ========== 玩家状态管理 ==========

    /**
     * 玩家上线
     * 
     * @param playerId 玩家ID
     * @throws GameException 操作失败时抛出
     */
    public void playerGoOnline(String playerId) throws GameException {
        Player player = getPlayerById(playerId);
        if (player == null) {
            throw new GameException(ErrorCodes.PLAYER_NOT_FOUND, "玩家不存在");
        }

        try {
            player.goOnline();
            savePlayer(player);
            
            // 添加到在线玩家列表
            redisClient.sadd(ONLINE_PLAYERS_KEY, playerId);
            
            logger.debug("玩家上线: playerId={}", playerId);
            
        } catch (Exception e) {
            logger.error("玩家上线失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家上线失败: " + e.getMessage());
        }
    }

    /**
     * 玩家下线
     * 
     * @param playerId 玩家ID
     * @throws GameException 操作失败时抛出
     */
    public void playerGoOffline(String playerId) throws GameException {
        Player player = getPlayerById(playerId);
        if (player == null) {
            return; // 玩家不存在，直接返回
        }

        try {
            player.goOffline();
            savePlayer(player);
            
            // 从在线玩家列表中移除
            redisClient.srem(ONLINE_PLAYERS_KEY, playerId);
            
            logger.debug("玩家下线: playerId={}", playerId);
            
        } catch (Exception e) {
            logger.error("玩家下线失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "玩家下线失败: " + e.getMessage());
        }
    }

    /**
     * 更新玩家状态
     * 
     * @param playerId 玩家ID
     * @param status 新状态
     * @throws GameException 更新失败时抛出
     */
    public void updatePlayerStatus(String playerId, Player.PlayerStatus status) throws GameException {
        Player player = getPlayerById(playerId);
        if (player == null) {
            throw new GameException(ErrorCodes.PLAYER_NOT_FOUND, "玩家不存在");
        }

        try {
            player.setStatus(status);
            player.updateActiveTime();
            savePlayer(player);
            
            logger.debug("更新玩家状态: playerId={}, status={}", playerId, status);
            
        } catch (Exception e) {
            logger.error("更新玩家状态失败: playerId={}", playerId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "更新玩家状态失败: " + e.getMessage());
        }
    }

    // ========== 查询和搜索 ==========

    /**
     * 获取在线玩家列表
     * 
     * @return 在线玩家ID列表
     */
    public List<String> getOnlinePlayerIds() {
        try {
            Set<String> onlinePlayerIds = redisClient.smembers(ONLINE_PLAYERS_KEY);
            return new ArrayList<>(onlinePlayerIds);
        } catch (Exception e) {
            logger.error("获取在线玩家列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取在线玩家对象列表
     * 
     * @return 在线玩家列表
     */
    public List<Player> getOnlinePlayers() {
        List<String> onlinePlayerIds = getOnlinePlayerIds();
        List<Player> onlinePlayers = new ArrayList<>();
        
        for (String playerId : onlinePlayerIds) {
            Player player = getPlayerById(playerId);
            if (player != null && player.isOnline()) {
                onlinePlayers.add(player);
            } else {
                // 清理无效的在线玩家记录
                redisClient.srem(ONLINE_PLAYERS_KEY, playerId);
            }
        }
        
        return onlinePlayers;
    }

    /**
     * 搜索玩家
     * 
     * @param keyword 关键词
     * @param limit 限制数量
     * @return 匹配的玩家列表
     */
    public List<Player> searchPlayers(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 这里简化实现，实际应该使用更复杂的搜索逻辑
            List<Player> results = new ArrayList<>();
            
            // 精确匹配用户名
            Player exactMatch = getPlayerByUsername(keyword.trim());
            if (exactMatch != null) {
                results.add(exactMatch);
            }
            
            // 如果需要更多结果，可以从数据库进行模糊搜索
            if (results.size() < limit) {
                List<Player> fuzzyResults = searchPlayersFromDatabase(keyword, limit - results.size());
                results.addAll(fuzzyResults);
            }
            
            return results.stream().limit(limit).collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("搜索玩家失败: keyword={}", keyword, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取玩家排行榜
     * 
     * @param start 开始位置
     * @param end 结束位置
     * @return 排行榜玩家列表
     */
    public List<Player> getPlayerRanking(int start, int end) {
        try {
            // 按分数降序获取排行榜
            Set<String> rankedPlayerIds = redisClient.zrevrange(PLAYER_RANKING_KEY, start, end);
            
            List<Player> rankedPlayers = new ArrayList<>();
            for (String playerId : rankedPlayerIds) {
                Player player = getPlayerById(playerId);
                if (player != null) {
                    rankedPlayers.add(player);
                }
            }
            
            return rankedPlayers;
            
        } catch (Exception e) {
            logger.error("获取玩家排行榜失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 更新玩家排行榜
     * 
     * @param playerId 玩家ID
     * @param score 分数
     */
    public void updatePlayerRanking(String playerId, long score) {
        try {
            redisClient.zadd(PLAYER_RANKING_KEY, score, playerId);
            logger.debug("更新玩家排行榜: playerId={}, score={}", playerId, score);
        } catch (Exception e) {
            logger.error("更新玩家排行榜失败: playerId={}", playerId, e);
        }
    }

    // ========== 统计和分析 ==========

    /**
     * 获取在线玩家数量
     * 
     * @return 在线玩家数量
     */
    public long getOnlinePlayerCount() {
        try {
            return redisClient.scard(ONLINE_PLAYERS_KEY);
        } catch (Exception e) {
            logger.error("获取在线玩家数量失败", e);
            return 0;
        }
    }

    /**
     * 获取玩家统计信息
     * 
     * @param playerId 玩家ID
     * @return 统计信息
     */
    public PlayerStatistics getPlayerStatistics(String playerId) {
        try {
            String statsKey = PLAYER_STATS_KEY_PREFIX + playerId;
            String statsJson = redisClient.get(statsKey);
            
            if (statsJson != null) {
                return objectMapper.readValue(statsJson, PlayerStatistics.class);
            }
            
            // 从玩家对象生成统计信息
            Player player = getPlayerById(playerId);
            if (player != null) {
                PlayerStatistics stats = generatePlayerStatistics(player);
                
                // 缓存统计信息
                String statsJsonToCache = objectMapper.writeValueAsString(stats);
                redisClient.setWithExpire(statsKey, statsJsonToCache, STATS_CACHE_EXPIRE_TIME);
                
                return stats;
            }
            
        } catch (Exception e) {
            logger.error("获取玩家统计信息失败: playerId={}", playerId, e);
        }
        
        return null;
    }

    // ========== 辅助方法 ==========

    /**
     * 检查玩家是否存在
     * 
     * @param playerId 玩家ID
     * @return 是否存在
     */
    public boolean playerExists(String playerId) {
        try {
            String playerKey = PLAYER_KEY_PREFIX + playerId;
            return redisClient.exists(playerKey) || checkPlayerExistsInDatabase(playerId);
        } catch (Exception e) {
            logger.error("检查玩家存在性失败: playerId={}", playerId, e);
            return false;
        }
    }

    /**
     * 更新玩家索引
     */
    private void updatePlayerIndexes(Player player) {
        try {
            // 用户ID索引
            if (player.getUserId() != null) {
                String userIdIndexKey = PLAYER_INDEX_KEY_PREFIX + "userId:" + player.getUserId();
                redisClient.setWithExpire(userIdIndexKey, player.getPlayerId(), PLAYER_CACHE_EXPIRE_TIME);
            }
            
            // 用户名索引
            if (player.getUsername() != null) {
                String usernameIndexKey = PLAYER_INDEX_KEY_PREFIX + "username:" + player.getUsername().toLowerCase();
                redisClient.setWithExpire(usernameIndexKey, player.getPlayerId(), PLAYER_CACHE_EXPIRE_TIME);
            }
            
            // 更新排行榜
            if (player.getGameStats() != null) {
                updatePlayerRanking(player.getPlayerId(), player.getGameStats().getScore());
            }
            
        } catch (Exception e) {
            logger.error("更新玩家索引失败: playerId={}", player.getPlayerId(), e);
        }
    }

    /**
     * 移除玩家索引
     */
    private void removePlayerIndexes(Player player) {
        try {
            // 移除用户ID索引
            if (player.getUserId() != null) {
                String userIdIndexKey = PLAYER_INDEX_KEY_PREFIX + "userId:" + player.getUserId();
                redisClient.del(userIdIndexKey);
            }
            
            // 移除用户名索引
            if (player.getUsername() != null) {
                String usernameIndexKey = PLAYER_INDEX_KEY_PREFIX + "username:" + player.getUsername().toLowerCase();
                redisClient.del(usernameIndexKey);
            }
            
        } catch (Exception e) {
            logger.error("移除玩家索引失败: playerId={}", player.getPlayerId(), e);
        }
    }

    /**
     * 从数据库加载玩家
     */
    private Player loadPlayerFromDatabase(String playerId) {
        // TODO: 实现从数据库加载玩家的逻辑
        // 这里应该调用数据库查询方法
        logger.debug("从数据库加载玩家: playerId={}", playerId);
        return null;
    }

    /**
     * 从数据库根据用户ID加载玩家
     */
    private Player loadPlayerByUserIdFromDatabase(String userId) {
        // TODO: 实现从数据库根据用户ID加载玩家的逻辑
        logger.debug("从数据库根据用户ID加载玩家: userId={}", userId);
        return null;
    }

    /**
     * 从数据库根据用户名加载玩家
     */
    private Player loadPlayerByUsernameFromDatabase(String username) {
        // TODO: 实现从数据库根据用户名加载玩家的逻辑
        logger.debug("从数据库根据用户名加载玩家: username={}", username);
        return null;
    }

    /**
     * 从数据库搜索玩家
     */
    private List<Player> searchPlayersFromDatabase(String keyword, int limit) {
        // TODO: 实现从数据库搜索玩家的逻辑
        logger.debug("从数据库搜索玩家: keyword={}, limit={}", keyword, limit);
        return new ArrayList<>();
    }

    /**
     * 检查玩家在数据库中是否存在
     */
    private boolean checkPlayerExistsInDatabase(String playerId) {
        // TODO: 实现检查玩家在数据库中是否存在的逻辑
        logger.debug("检查玩家在数据库中是否存在: playerId={}", playerId);
        return false;
    }

    /**
     * 生成玩家统计信息
     */
    private PlayerStatistics generatePlayerStatistics(Player player) {
        PlayerStatistics stats = new PlayerStatistics();
        stats.setPlayerId(player.getPlayerId());
        stats.setUsername(player.getUsername());
        stats.setLevel(player.getLevel());
        stats.setExperience(player.getExperience());
        stats.setOnlineTime(player.getOnlineTime());
        
        if (player.getGameStats() != null) {
            Player.GameStats gameStats = player.getGameStats();
            stats.setTotalGames(gameStats.getTotalGames());
            stats.setWins(gameStats.getWins());
            stats.setLosses(gameStats.getLosses());
            stats.setDraws(gameStats.getDraws());
            stats.setWinRate(gameStats.getWinRate());
            stats.setTotalScore(gameStats.getScore());
            stats.setRanking(gameStats.getRanking());
        }
        
        return stats;
    }

    /**
     * 玩家统计信息类
     */
    public static class PlayerStatistics {
        private String playerId;
        private String username;
        private int level;
        private long experience;
        private long onlineTime;
        private int totalGames;
        private int wins;
        private int losses;
        private int draws;
        private double winRate;
        private long totalScore;
        private int ranking;

        // Getters and Setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }

        public long getExperience() { return experience; }
        public void setExperience(long experience) { this.experience = experience; }

        public long getOnlineTime() { return onlineTime; }
        public void setOnlineTime(long onlineTime) { this.onlineTime = onlineTime; }

        public int getTotalGames() { return totalGames; }
        public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }

        public int getLosses() { return losses; }
        public void setLosses(int losses) { this.losses = losses; }

        public int getDraws() { return draws; }
        public void setDraws(int draws) { this.draws = draws; }

        public double getWinRate() { return winRate; }
        public void setWinRate(double winRate) { this.winRate = winRate; }

        public long getTotalScore() { return totalScore; }
        public void setTotalScore(long totalScore) { this.totalScore = totalScore; }

        public int getRanking() { return ranking; }
        public void setRanking(int ranking) { this.ranking = ranking; }

        @Override
        public String toString() {
            return String.format("PlayerStatistics{playerId='%s', username='%s', level=%d, " +
                               "totalGames=%d, winRate=%.2f, ranking=%d}",
                               playerId, username, level, totalGames, winRate, ranking);
        }
    }
}