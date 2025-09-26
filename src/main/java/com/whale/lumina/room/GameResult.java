package com.whale.lumina.room;

import java.util.*;

/**
 * 游戏结果类
 * 
 * 记录游戏结束后的结果信息，包括获胜者、分数、统计数据等
 * 
 * @author Lumina Team
 */
public class GameResult {

    // 基本结果信息
    private final String gameId;
    private final String roomId;
    private final GameResultType resultType;
    private final long gameStartTime;
    private final long gameEndTime;
    private final long gameDuration;

    // 玩家结果
    private final List<String> winners;
    private final List<String> losers;
    private final Map<String, PlayerResult> playerResults;

    // 游戏统计
    private final Map<String, Object> gameStats;
    private final Map<String, Object> metadata;

    /**
     * 游戏结果类型枚举
     */
    public enum GameResultType {
        NORMAL_END,     // 正常结束
        TIME_OUT,       // 超时结束
        PLAYER_QUIT,    // 玩家退出
        SYSTEM_ERROR,   // 系统错误
        FORCE_END,      // 强制结束
        DRAW            // 平局
    }

    /**
     * 构造函数
     * 
     * @param gameId 游戏ID
     * @param roomId 房间ID
     * @param resultType 结果类型
     * @param gameStartTime 游戏开始时间
     * @param gameEndTime 游戏结束时间
     */
    public GameResult(String gameId, String roomId, GameResultType resultType, 
                     long gameStartTime, long gameEndTime) {
        this.gameId = gameId;
        this.roomId = roomId;
        this.resultType = resultType;
        this.gameStartTime = gameStartTime;
        this.gameEndTime = gameEndTime;
        this.gameDuration = gameEndTime - gameStartTime;
        
        this.winners = new ArrayList<>();
        this.losers = new ArrayList<>();
        this.playerResults = new HashMap<>();
        this.gameStats = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建正常结束的游戏结果
     * 
     * @param gameId 游戏ID
     * @param roomId 房间ID
     * @param gameStartTime 游戏开始时间
     * @param gameEndTime 游戏结束时间
     * @return 游戏结果对象
     */
    public static GameResult normalEnd(String gameId, String roomId, long gameStartTime, long gameEndTime) {
        return new GameResult(gameId, roomId, GameResultType.NORMAL_END, gameStartTime, gameEndTime);
    }

    /**
     * 创建超时结束的游戏结果
     * 
     * @param gameId 游戏ID
     * @param roomId 房间ID
     * @param gameStartTime 游戏开始时间
     * @param gameEndTime 游戏结束时间
     * @return 游戏结果对象
     */
    public static GameResult timeOut(String gameId, String roomId, long gameStartTime, long gameEndTime) {
        return new GameResult(gameId, roomId, GameResultType.TIME_OUT, gameStartTime, gameEndTime);
    }

    /**
     * 创建玩家退出的游戏结果
     * 
     * @param gameId 游戏ID
     * @param roomId 房间ID
     * @param gameStartTime 游戏开始时间
     * @param gameEndTime 游戏结束时间
     * @return 游戏结果对象
     */
    public static GameResult playerQuit(String gameId, String roomId, long gameStartTime, long gameEndTime) {
        return new GameResult(gameId, roomId, GameResultType.PLAYER_QUIT, gameStartTime, gameEndTime);
    }

    /**
     * 创建平局的游戏结果
     * 
     * @param gameId 游戏ID
     * @param roomId 房间ID
     * @param gameStartTime 游戏开始时间
     * @param gameEndTime 游戏结束时间
     * @return 游戏结果对象
     */
    public static GameResult draw(String gameId, String roomId, long gameStartTime, long gameEndTime) {
        return new GameResult(gameId, roomId, GameResultType.DRAW, gameStartTime, gameEndTime);
    }

    /**
     * 创建系统错误的游戏结果
     * 
     * @param gameId 游戏ID
     * @param roomId 房间ID
     * @param gameStartTime 游戏开始时间
     * @param gameEndTime 游戏结束时间
     * @return 游戏结果对象
     */
    public static GameResult systemError(String gameId, String roomId, long gameStartTime, long gameEndTime) {
        return new GameResult(gameId, roomId, GameResultType.SYSTEM_ERROR, gameStartTime, gameEndTime);
    }

    // ========== 玩家结果管理 ==========

    /**
     * 添加获胜者
     * 
     * @param playerId 玩家ID
     * @return 当前对象
     */
    public GameResult addWinner(String playerId) {
        if (!winners.contains(playerId)) {
            winners.add(playerId);
            // 从失败者列表中移除
            losers.remove(playerId);
        }
        return this;
    }

    /**
     * 添加失败者
     * 
     * @param playerId 玩家ID
     * @return 当前对象
     */
    public GameResult addLoser(String playerId) {
        if (!losers.contains(playerId)) {
            losers.add(playerId);
            // 从获胜者列表中移除
            winners.remove(playerId);
        }
        return this;
    }

    /**
     * 设置玩家结果
     * 
     * @param playerId 玩家ID
     * @param playerResult 玩家结果
     * @return 当前对象
     */
    public GameResult setPlayerResult(String playerId, PlayerResult playerResult) {
        playerResults.put(playerId, playerResult);
        
        // 根据玩家结果更新获胜者/失败者列表
        if (playerResult.isWinner()) {
            addWinner(playerId);
        } else {
            addLoser(playerId);
        }
        
        return this;
    }

    /**
     * 获取玩家结果
     * 
     * @param playerId 玩家ID
     * @return 玩家结果
     */
    public PlayerResult getPlayerResult(String playerId) {
        return playerResults.get(playerId);
    }

    /**
     * 检查玩家是否获胜
     * 
     * @param playerId 玩家ID
     * @return 是否获胜
     */
    public boolean isWinner(String playerId) {
        return winners.contains(playerId);
    }

    /**
     * 检查玩家是否失败
     * 
     * @param playerId 玩家ID
     * @return 是否失败
     */
    public boolean isLoser(String playerId) {
        return losers.contains(playerId);
    }

    /**
     * 获取所有参与玩家
     * 
     * @return 玩家ID列表
     */
    public List<String> getAllPlayers() {
        Set<String> allPlayers = new HashSet<>();
        allPlayers.addAll(winners);
        allPlayers.addAll(losers);
        allPlayers.addAll(playerResults.keySet());
        return new ArrayList<>(allPlayers);
    }

    // ========== 统计数据管理 ==========

    /**
     * 设置游戏统计数据
     * 
     * @param key 键
     * @param value 值
     * @return 当前对象
     */
    public GameResult setGameStat(String key, Object value) {
        gameStats.put(key, value);
        return this;
    }

    /**
     * 获取游戏统计数据
     * 
     * @param key 键
     * @return 值
     */
    public Object getGameStat(String key) {
        return gameStats.get(key);
    }

    /**
     * 获取游戏统计数据（带类型转换）
     * 
     * @param key 键
     * @param clazz 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getGameStat(String key, Class<T> clazz) {
        Object value = gameStats.get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 设置元数据
     * 
     * @param key 键
     * @param value 值
     * @return 当前对象
     */
    public GameResult setMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    /**
     * 获取元数据
     * 
     * @param key 键
     * @return 值
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 获取元数据（带类型转换）
     * 
     * @param key 键
     * @param clazz 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> clazz) {
        Object value = metadata.get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // ========== 便利方法 ==========

    /**
     * 检查是否有获胜者
     * 
     * @return 是否有获胜者
     */
    public boolean hasWinners() {
        return !winners.isEmpty();
    }

    /**
     * 检查是否为平局
     * 
     * @return 是否为平局
     */
    public boolean isDraw() {
        return resultType == GameResultType.DRAW || winners.isEmpty();
    }

    /**
     * 检查游戏是否正常结束
     * 
     * @return 是否正常结束
     */
    public boolean isNormalEnd() {
        return resultType == GameResultType.NORMAL_END;
    }

    /**
     * 检查游戏是否因超时结束
     * 
     * @return 是否超时结束
     */
    public boolean isTimeOut() {
        return resultType == GameResultType.TIME_OUT;
    }

    /**
     * 检查游戏是否因玩家退出结束
     * 
     * @return 是否因玩家退出结束
     */
    public boolean isPlayerQuit() {
        return resultType == GameResultType.PLAYER_QUIT;
    }

    /**
     * 检查游戏是否因系统错误结束
     * 
     * @return 是否因系统错误结束
     */
    public boolean isSystemError() {
        return resultType == GameResultType.SYSTEM_ERROR;
    }

    /**
     * 获取游戏时长（秒）
     * 
     * @return 游戏时长
     */
    public long getGameDurationSeconds() {
        return gameDuration / 1000;
    }

    /**
     * 获取游戏时长（分钟）
     * 
     * @return 游戏时长
     */
    public long getGameDurationMinutes() {
        return gameDuration / (1000 * 60);
    }

    /**
     * 获取参与玩家数量
     * 
     * @return 玩家数量
     */
    public int getPlayerCount() {
        return getAllPlayers().size();
    }

    /**
     * 获取获胜者数量
     * 
     * @return 获胜者数量
     */
    public int getWinnerCount() {
        return winners.size();
    }

    /**
     * 获取失败者数量
     * 
     * @return 失败者数量
     */
    public int getLoserCount() {
        return losers.size();
    }

    // ========== Getters ==========

    public String getGameId() {
        return gameId;
    }

    public String getRoomId() {
        return roomId;
    }

    public GameResultType getResultType() {
        return resultType;
    }

    public long getGameStartTime() {
        return gameStartTime;
    }

    public long getGameEndTime() {
        return gameEndTime;
    }

    public long getGameDuration() {
        return gameDuration;
    }

    public List<String> getWinners() {
        return new ArrayList<>(winners);
    }

    public List<String> getLosers() {
        return new ArrayList<>(losers);
    }

    public Map<String, PlayerResult> getPlayerResults() {
        return new HashMap<>(playerResults);
    }

    public Map<String, Object> getGameStats() {
        return new HashMap<>(gameStats);
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    // ========== Object方法重写 ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GameResult that = (GameResult) obj;
        return Objects.equals(gameId, that.gameId) &&
               Objects.equals(roomId, that.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameId, roomId);
    }

    @Override
    public String toString() {
        return String.format("GameResult{gameId='%s', roomId='%s', type=%s, duration=%dms, winners=%d, losers=%d}", 
                           gameId, roomId, resultType, gameDuration, winners.size(), losers.size());
    }

    /**
     * 获取详细信息
     * 
     * @return 详细信息字符串
     */
    public String getDetailedInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Game Result Details:\n");
        sb.append("  Game ID: ").append(gameId).append("\n");
        sb.append("  Room ID: ").append(roomId).append("\n");
        sb.append("  Result Type: ").append(resultType).append("\n");
        sb.append("  Duration: ").append(getGameDurationSeconds()).append(" seconds\n");
        sb.append("  Players: ").append(getPlayerCount()).append("\n");
        sb.append("  Winners: ").append(winners.size()).append(" - ").append(winners).append("\n");
        sb.append("  Losers: ").append(losers.size()).append(" - ").append(losers).append("\n");
        
        if (!gameStats.isEmpty()) {
            sb.append("  Game Stats: ").append(gameStats.size()).append(" items\n");
        }
        
        if (!metadata.isEmpty()) {
            sb.append("  Metadata: ").append(metadata.size()).append(" items\n");
        }
        
        return sb.toString();
    }

    /**
     * 玩家结果类
     */
    public static class PlayerResult {
        private final String playerId;
        private final boolean isWinner;
        private final int score;
        private final int rank;
        private final Map<String, Object> playerStats;

        public PlayerResult(String playerId, boolean isWinner, int score, int rank) {
            this.playerId = playerId;
            this.isWinner = isWinner;
            this.score = score;
            this.rank = rank;
            this.playerStats = new HashMap<>();
        }

        // 静态工厂方法
        public static PlayerResult winner(String playerId, int score, int rank) {
            return new PlayerResult(playerId, true, score, rank);
        }

        public static PlayerResult loser(String playerId, int score, int rank) {
            return new PlayerResult(playerId, false, score, rank);
        }

        // 统计数据管理
        public PlayerResult setStat(String key, Object value) {
            playerStats.put(key, value);
            return this;
        }

        public Object getStat(String key) {
            return playerStats.get(key);
        }

        @SuppressWarnings("unchecked")
        public <T> T getStat(String key, Class<T> clazz) {
            Object value = playerStats.get(key);
            if (value != null && clazz.isInstance(value)) {
                return (T) value;
            }
            return null;
        }

        // Getters
        public String getPlayerId() { return playerId; }
        public boolean isWinner() { return isWinner; }
        public int getScore() { return score; }
        public int getRank() { return rank; }
        public Map<String, Object> getPlayerStats() { return new HashMap<>(playerStats); }

        @Override
        public String toString() {
            return String.format("PlayerResult{playerId='%s', winner=%s, score=%d, rank=%d}", 
                               playerId, isWinner, score, rank);
        }
    }
}