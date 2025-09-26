package com.whale.lumina.room;

import com.whale.lumina.common.TimeUtils;
import com.whale.lumina.logic.GameLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏房间实体类
 * 
 * 表示一个游戏房间，包含房间状态、玩家信息、游戏配置等
 * 
 * @author Lumina Team
 */
public class Room {

    private static final Logger logger = LoggerFactory.getLogger(Room.class);

    // 房间基本信息
    private final String roomId;
    private String ownerId;
    private final RoomConfig config;
    private RoomStatus status;
    private int maxPlayers;

    // 时间信息
    private final long createTime;
    private long lastActiveTime;
    private long gameStartTime;
    private long gameEndTime;

    // 玩家管理
    private final Set<String> players;
    private final Map<String, PlayerRoomInfo> playerInfos;

    // 游戏相关
    private GameLoop gameLoop;
    private GameResult gameResult;
    private final Map<String, Object> gameData;

    // 统计信息
    private final AtomicLong messageCount = new AtomicLong(0);
    private final AtomicLong totalPlayersJoined = new AtomicLong(0);

    /**
     * 房间状态枚举
     */
    public enum RoomStatus {
        WAITING,    // 等待玩家
        PLAYING,    // 游戏中
        PAUSED,     // 暂停
        FINISHED    // 已结束
    }

    /**
     * 构造函数
     * 
     * @param roomId 房间ID
     * @param ownerId 房主ID
     * @param config 房间配置
     */
    public Room(String roomId, String ownerId, RoomConfig config) {
        this.roomId = roomId;
        this.ownerId = ownerId;
        this.config = config;
        this.status = RoomStatus.WAITING;
        this.maxPlayers = config.getMaxPlayers();
        
        this.createTime = System.currentTimeMillis();
        this.lastActiveTime = createTime;
        this.gameStartTime = 0;
        this.gameEndTime = 0;
        
        this.players = ConcurrentHashMap.newKeySet();
        this.playerInfos = new ConcurrentHashMap<>();
        this.gameData = new ConcurrentHashMap<>();
        
        logger.debug("创建房间: roomId={}, ownerId={}, gameMode={}", 
                    roomId, ownerId, config.getGameMode());
    }

    // ========== 玩家管理 ==========

    /**
     * 添加玩家
     * 
     * @param playerId 玩家ID
     * @return 是否成功
     */
    public synchronized boolean addPlayer(String playerId) {
        if (players.contains(playerId)) {
            return false; // 玩家已在房间中
        }
        
        if (players.size() >= maxPlayers) {
            return false; // 房间已满
        }
        
        if (status != RoomStatus.WAITING) {
            return false; // 房间状态不允许加入
        }
        
        players.add(playerId);
        playerInfos.put(playerId, new PlayerRoomInfo(playerId, System.currentTimeMillis()));
        totalPlayersJoined.incrementAndGet();
        updateLastActiveTime();
        
        logger.debug("玩家加入房间: roomId={}, playerId={}, playerCount={}", 
                    roomId, playerId, players.size());
        
        return true;
    }

    /**
     * 移除玩家
     * 
     * @param playerId 玩家ID
     * @return 是否成功
     */
    public synchronized boolean removePlayer(String playerId) {
        if (!players.contains(playerId)) {
            return false; // 玩家不在房间中
        }
        
        players.remove(playerId);
        PlayerRoomInfo playerInfo = playerInfos.remove(playerId);
        
        if (playerInfo != null) {
            playerInfo.setLeaveTime(System.currentTimeMillis());
        }
        
        updateLastActiveTime();
        
        logger.debug("玩家离开房间: roomId={}, playerId={}, playerCount={}", 
                    roomId, playerId, players.size());
        
        return true;
    }

    /**
     * 检查玩家是否在房间中
     * 
     * @param playerId 玩家ID
     * @return 是否在房间中
     */
    public boolean containsPlayer(String playerId) {
        return players.contains(playerId);
    }

    /**
     * 获取玩家信息
     * 
     * @param playerId 玩家ID
     * @return 玩家房间信息
     */
    public PlayerRoomInfo getPlayerInfo(String playerId) {
        return playerInfos.get(playerId);
    }

    /**
     * 更新玩家活跃时间
     * 
     * @param playerId 玩家ID
     */
    public void updatePlayerActiveTime(String playerId) {
        PlayerRoomInfo playerInfo = playerInfos.get(playerId);
        if (playerInfo != null) {
            playerInfo.setLastActiveTime(System.currentTimeMillis());
        }
        updateLastActiveTime();
    }

    // ========== 房间状态管理 ==========

    /**
     * 更新房间最后活跃时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * 增加消息计数
     */
    public void incrementMessageCount() {
        messageCount.incrementAndGet();
        updateLastActiveTime();
    }

    /**
     * 检查房间是否为空
     * 
     * @return 是否为空
     */
    public boolean isEmpty() {
        return players.isEmpty();
    }

    /**
     * 检查房间是否已满
     * 
     * @return 是否已满
     */
    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    /**
     * 检查房间是否可以开始游戏
     * 
     * @return 是否可以开始
     */
    public boolean canStartGame() {
        return status == RoomStatus.WAITING && 
               players.size() >= config.getMinPlayers() && 
               players.size() <= maxPlayers;
    }

    /**
     * 检查房间是否处于游戏中
     * 
     * @return 是否在游戏中
     */
    public boolean isPlaying() {
        return status == RoomStatus.PLAYING;
    }

    /**
     * 检查房间是否已结束
     * 
     * @return 是否已结束
     */
    public boolean isFinished() {
        return status == RoomStatus.FINISHED;
    }

    // ========== 游戏循环管理 ==========

    /**
     * 初始化游戏循环
     */
    public void initializeGameLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
        
        gameLoop = new GameLoop(this);
        gameLoop.start();
        
        logger.info("初始化游戏循环: roomId={}", roomId);
    }

    /**
     * 停止游戏循环
     */
    public void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
        
        logger.info("停止游戏循环: roomId={}", roomId);
    }

    /**
     * 暂停游戏
     */
    public synchronized void pauseGame() {
        if (status == RoomStatus.PLAYING) {
            status = RoomStatus.PAUSED;
            if (gameLoop != null) {
                gameLoop.pause();
            }
            updateLastActiveTime();
            
            logger.info("暂停游戏: roomId={}", roomId);
        }
    }

    /**
     * 恢复游戏
     */
    public synchronized void resumeGame() {
        if (status == RoomStatus.PAUSED) {
            status = RoomStatus.PLAYING;
            if (gameLoop != null) {
                gameLoop.resume();
            }
            updateLastActiveTime();
            
            logger.info("恢复游戏: roomId={}", roomId);
        }
    }

    // ========== 游戏数据管理 ==========

    /**
     * 设置游戏数据
     * 
     * @param key 键
     * @param value 值
     */
    public void setGameData(String key, Object value) {
        gameData.put(key, value);
        updateLastActiveTime();
    }

    /**
     * 获取游戏数据
     * 
     * @param key 键
     * @return 值
     */
    public Object getGameData(String key) {
        return gameData.get(key);
    }

    /**
     * 获取游戏数据（带类型转换）
     * 
     * @param key 键
     * @param clazz 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getGameData(String key, Class<T> clazz) {
        Object value = gameData.get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 移除游戏数据
     * 
     * @param key 键
     * @return 被移除的值
     */
    public Object removeGameData(String key) {
        return gameData.remove(key);
    }

    /**
     * 清空游戏数据
     */
    public void clearGameData() {
        gameData.clear();
    }

    // ========== 时间计算 ==========

    /**
     * 获取房间存在时长（毫秒）
     * 
     * @return 存在时长
     */
    public long getRoomDuration() {
        return System.currentTimeMillis() - createTime;
    }

    /**
     * 获取游戏时长（毫秒）
     * 
     * @return 游戏时长
     */
    public long getGameDuration() {
        if (gameStartTime == 0) {
            return 0;
        }
        
        long endTime = gameEndTime > 0 ? gameEndTime : System.currentTimeMillis();
        return endTime - gameStartTime;
    }

    /**
     * 获取空闲时长（毫秒）
     * 
     * @return 空闲时长
     */
    public long getIdleDuration() {
        return System.currentTimeMillis() - lastActiveTime;
    }

    // ========== Getters and Setters ==========

    public String getRoomId() {
        return roomId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
        updateLastActiveTime();
    }

    public RoomConfig getConfig() {
        return config;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
        updateLastActiveTime();
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = Math.min(maxPlayers, config.getMaxPlayers());
        updateLastActiveTime();
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public long getGameStartTime() {
        return gameStartTime;
    }

    public void setGameStartTime(long gameStartTime) {
        this.gameStartTime = gameStartTime;
        updateLastActiveTime();
    }

    public long getGameEndTime() {
        return gameEndTime;
    }

    public void setGameEndTime(long gameEndTime) {
        this.gameEndTime = gameEndTime;
        updateLastActiveTime();
    }

    public Set<String> getPlayers() {
        return new HashSet<>(players);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public Map<String, PlayerRoomInfo> getPlayerInfos() {
        return new HashMap<>(playerInfos);
    }

    public GameLoop getGameLoop() {
        return gameLoop;
    }

    public GameResult getGameResult() {
        return gameResult;
    }

    public void setGameResult(GameResult gameResult) {
        this.gameResult = gameResult;
        updateLastActiveTime();
    }

    public Map<String, Object> getGameData() {
        return new HashMap<>(gameData);
    }

    public long getMessageCount() {
        return messageCount.get();
    }

    public long getTotalPlayersJoined() {
        return totalPlayersJoined.get();
    }

    // ========== Object方法重写 ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Room room = (Room) obj;
        return Objects.equals(roomId, room.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId);
    }

    @Override
    public String toString() {
        return String.format("Room{roomId='%s', ownerId='%s', status=%s, players=%d/%d, gameMode='%s'}", 
                           roomId, ownerId, status, players.size(), maxPlayers, config.getGameMode());
    }

    /**
     * 获取房间详细信息
     * 
     * @return 详细信息字符串
     */
    public String getDetailedInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Room Details:\n");
        sb.append("  ID: ").append(roomId).append("\n");
        sb.append("  Owner: ").append(ownerId).append("\n");
        sb.append("  Status: ").append(status).append("\n");
        sb.append("  Players: ").append(players.size()).append("/").append(maxPlayers).append("\n");
        sb.append("  Game Mode: ").append(config.getGameMode()).append("\n");
        sb.append("  Created: ").append(TimeUtils.formatTimestamp(createTime)).append("\n");
        sb.append("  Last Active: ").append(TimeUtils.formatTimestamp(lastActiveTime)).append("\n");
        
        if (gameStartTime > 0) {
            sb.append("  Game Started: ").append(TimeUtils.formatTimestamp(gameStartTime)).append("\n");
            sb.append("  Game Duration: ").append(TimeUtils.formatDuration(getGameDuration())).append("\n");
        }
        
        if (gameEndTime > 0) {
            sb.append("  Game Ended: ").append(TimeUtils.formatTimestamp(gameEndTime)).append("\n");
        }
        
        sb.append("  Messages: ").append(messageCount.get()).append("\n");
        sb.append("  Total Players Joined: ").append(totalPlayersJoined.get()).append("\n");
        
        return sb.toString();
    }

    /**
     * 玩家房间信息类
     */
    public static class PlayerRoomInfo {
        private final String playerId;
        private final long joinTime;
        private long lastActiveTime;
        private long leaveTime;
        private final Map<String, Object> playerData;

        public PlayerRoomInfo(String playerId, long joinTime) {
            this.playerId = playerId;
            this.joinTime = joinTime;
            this.lastActiveTime = joinTime;
            this.leaveTime = 0;
            this.playerData = new ConcurrentHashMap<>();
        }

        // Getters and Setters
        public String getPlayerId() { return playerId; }
        public long getJoinTime() { return joinTime; }
        public long getLastActiveTime() { return lastActiveTime; }
        public void setLastActiveTime(long lastActiveTime) { this.lastActiveTime = lastActiveTime; }
        public long getLeaveTime() { return leaveTime; }
        public void setLeaveTime(long leaveTime) { this.leaveTime = leaveTime; }

        public void setPlayerData(String key, Object value) {
            playerData.put(key, value);
        }

        public Object getPlayerData(String key) {
            return playerData.get(key);
        }

        @SuppressWarnings("unchecked")
        public <T> T getPlayerData(String key, Class<T> clazz) {
            Object value = playerData.get(key);
            if (value != null && clazz.isInstance(value)) {
                return (T) value;
            }
            return null;
        }

        public long getSessionDuration() {
            long endTime = leaveTime > 0 ? leaveTime : System.currentTimeMillis();
            return endTime - joinTime;
        }

        public long getIdleDuration() {
            return System.currentTimeMillis() - lastActiveTime;
        }

        @Override
        public String toString() {
            return String.format("PlayerRoomInfo{playerId='%s', joinTime=%d, sessionDuration=%d}", 
                               playerId, joinTime, getSessionDuration());
        }
    }
}