package com.whale.lumina.room;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import com.whale.lumina.common.TimeUtils;
import com.whale.lumina.data.RedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 房间管理器
 * 
 * 负责游戏房间的创建、管理、销毁和状态维护
 * 
 * @author Lumina Team
 */
@Component
public class RoomManager {

    private static final Logger logger = LoggerFactory.getLogger(RoomManager.class);

    @Autowired
    private RedisClient redisClient;

    // 配置参数
    @Value("${lumina.room.max-rooms:1000}")
    private int maxRooms;

    @Value("${lumina.room.max-players-per-room:10}")
    private int maxPlayersPerRoom;

    @Value("${lumina.room.room-timeout-seconds:1800}")
    private int roomTimeoutSeconds;

    @Value("${lumina.room.cleanup-interval-seconds:300}")
    private int cleanupIntervalSeconds;

    @Value("${lumina.room.enable-room-persistence:true}")
    private boolean enableRoomPersistence;

    // Redis键前缀
    private static final String ROOM_KEY_PREFIX = "room:";
    private static final String PLAYER_ROOM_KEY_PREFIX = "player_room:";
    private static final String ROOM_LIST_KEY = "room_list";

    // 房间存储
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerToRoom = new ConcurrentHashMap<>();

    // 房间ID生成器
    private final AtomicLong roomIdGenerator = new AtomicLong(1);

    // 统计信息
    private final AtomicLong totalRoomsCreated = new AtomicLong(0);
    private final AtomicLong totalRoomsDestroyed = new AtomicLong(0);

    // ========== 房间创建和管理 ==========

    /**
     * 创建房间
     * 
     * @param creatorId 创建者ID
     * @param roomConfig 房间配置
     * @return 房间对象
     */
    public Room createRoom(String creatorId, RoomConfig roomConfig) {
        try {
            // 检查房间数量限制
            if (rooms.size() >= maxRooms) {
                throw new GameException(ErrorCodes.ROOM_MAX_ROOMS_REACHED);
            }

            // 检查创建者是否已在其他房间
            if (playerToRoom.containsKey(creatorId)) {
                throw new GameException(ErrorCodes.ROOM_PLAYER_ALREADY_IN_ROOM);
            }

            // 生成房间ID
            String roomId = generateRoomId();

            // 创建房间对象
            Room room = new Room(roomId, creatorId, roomConfig);
            room.setMaxPlayers(Math.min(roomConfig.getMaxPlayers(), maxPlayersPerRoom));

            // 添加创建者到房间
            room.addPlayer(creatorId);

            // 保存房间
            rooms.put(roomId, room);
            playerToRoom.put(creatorId, roomId);

            // 持久化到Redis
            if (enableRoomPersistence) {
                saveRoomToRedis(room);
                addRoomToList(roomId);
                savePlayerRoomMapping(creatorId, roomId);
            }

            totalRoomsCreated.incrementAndGet();

            logger.info("创建房间成功: roomId={}, creatorId={}, maxPlayers={}", 
                       roomId, creatorId, room.getMaxPlayers());

            // 发布房间创建事件
            publishRoomEvent(new RoomCreatedEvent(roomId, creatorId, room.getConfig()));

            return room;

        } catch (GameException e) {
            logger.warn("创建房间失败: creatorId={}, error={}", creatorId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("创建房间异常: creatorId={}", creatorId, e);
            throw new GameException(ErrorCodes.ROOM_CREATE_FAILED, e);
        }
    }

    /**
     * 加入房间
     * 
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @return 是否成功
     */
    public boolean joinRoom(String roomId, String playerId) {
        try {
            // 检查玩家是否已在其他房间
            if (playerToRoom.containsKey(playerId)) {
                throw new GameException(ErrorCodes.ROOM_PLAYER_ALREADY_IN_ROOM);
            }

            // 获取房间
            Room room = getRoom(roomId);
            if (room == null) {
                throw new GameException(ErrorCodes.ROOM_NOT_FOUND);
            }

            // 检查房间状态
            if (room.getStatus() != Room.RoomStatus.WAITING) {
                throw new GameException(ErrorCodes.ROOM_NOT_JOINABLE);
            }

            // 检查房间是否已满
            if (room.isFull()) {
                throw new GameException(ErrorCodes.ROOM_FULL);
            }

            // 添加玩家到房间
            room.addPlayer(playerId);
            playerToRoom.put(playerId, roomId);

            // 更新Redis
            if (enableRoomPersistence) {
                saveRoomToRedis(room);
                savePlayerRoomMapping(playerId, roomId);
            }

            logger.info("玩家加入房间成功: roomId={}, playerId={}, playerCount={}", 
                       roomId, playerId, room.getPlayerCount());

            // 发布玩家加入事件
            publishRoomEvent(new PlayerJoinedEvent(roomId, playerId, room.getPlayerCount()));

            // 检查是否可以开始游戏
            checkRoomReadyToStart(room);

            return true;

        } catch (GameException e) {
            logger.warn("玩家加入房间失败: roomId={}, playerId={}, error={}", roomId, playerId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("玩家加入房间异常: roomId={}, playerId={}", roomId, playerId, e);
            throw new GameException(ErrorCodes.ROOM_JOIN_FAILED, e);
        }
    }

    /**
     * 离开房间
     * 
     * @param playerId 玩家ID
     * @return 是否成功
     */
    public boolean leaveRoom(String playerId) {
        try {
            String roomId = playerToRoom.get(playerId);
            if (roomId == null) {
                return false; // 玩家不在任何房间中
            }

            Room room = getRoom(roomId);
            if (room == null) {
                // 清理无效映射
                playerToRoom.remove(playerId);
                return false;
            }

            // 从房间移除玩家
            room.removePlayer(playerId);
            playerToRoom.remove(playerId);

            // 更新Redis
            if (enableRoomPersistence) {
                removePlayerRoomMapping(playerId);
                
                if (room.isEmpty()) {
                    // 房间为空，销毁房间
                    destroyRoom(roomId);
                } else {
                    // 更新房间信息
                    saveRoomToRedis(room);
                    
                    // 如果离开的是房主，转移房主权限
                    if (playerId.equals(room.getOwnerId()) && !room.isEmpty()) {
                        String newOwnerId = room.getPlayers().iterator().next();
                        room.setOwnerId(newOwnerId);
                        saveRoomToRedis(room);
                        
                        logger.info("房主转移: roomId={}, oldOwner={}, newOwner={}", 
                                   roomId, playerId, newOwnerId);
                        
                        publishRoomEvent(new OwnerChangedEvent(roomId, playerId, newOwnerId));
                    }
                }
            }

            logger.info("玩家离开房间成功: roomId={}, playerId={}, remainingPlayers={}", 
                       roomId, playerId, room.getPlayerCount());

            // 发布玩家离开事件
            publishRoomEvent(new PlayerLeftEvent(roomId, playerId, room.getPlayerCount()));

            return true;

        } catch (Exception e) {
            logger.error("玩家离开房间异常: playerId={}", playerId, e);
            return false;
        }
    }

    /**
     * 销毁房间
     * 
     * @param roomId 房间ID
     */
    public void destroyRoom(String roomId) {
        try {
            Room room = rooms.remove(roomId);
            if (room == null) {
                return;
            }

            // 移除所有玩家的房间映射
            for (String playerId : room.getPlayers()) {
                playerToRoom.remove(playerId);
                if (enableRoomPersistence) {
                    removePlayerRoomMapping(playerId);
                }
            }

            // 从Redis删除
            if (enableRoomPersistence) {
                removeRoomFromRedis(roomId);
                removeRoomFromList(roomId);
            }

            totalRoomsDestroyed.incrementAndGet();

            logger.info("销毁房间成功: roomId={}, playerCount={}", roomId, room.getPlayerCount());

            // 发布房间销毁事件
            publishRoomEvent(new RoomDestroyedEvent(roomId, room.getOwnerId()));

        } catch (Exception e) {
            logger.error("销毁房间异常: roomId={}", roomId, e);
        }
    }

    /**
     * 移除房间（destroyRoom的别名）
     * 
     * @param roomId 房间ID
     */
    public void removeRoom(String roomId) {
        destroyRoom(roomId);
    }

    // ========== 房间查询 ==========

    /**
     * 获取房间
     * 
     * @param roomId 房间ID
     * @return 房间对象
     */
    public Room getRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null && enableRoomPersistence) {
            // 尝试从Redis加载
            room = loadRoomFromRedis(roomId);
            if (room != null) {
                rooms.put(roomId, room);
            }
        }
        return room;
    }

    /**
     * 获取玩家所在房间
     * 
     * @param playerId 玩家ID
     * @return 房间对象
     */
    public Room getPlayerRoom(String playerId) {
        String roomId = playerToRoom.get(playerId);
        if (roomId != null) {
            return getRoom(roomId);
        }
        
        // 尝试从Redis获取
        if (enableRoomPersistence) {
            roomId = getPlayerRoomMapping(playerId);
            if (roomId != null) {
                playerToRoom.put(playerId, roomId);
                return getRoom(roomId);
            }
        }
        
        return null;
    }

    /**
     * 获取所有房间列表
     * 
     * @return 房间列表
     */
    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * 获取可加入的房间列表
     * 
     * @return 可加入的房间列表
     */
    public List<Room> getJoinableRooms() {
        return rooms.values().stream()
                .filter(room -> room.getStatus() == Room.RoomStatus.WAITING && !room.isFull())
                .collect(Collectors.toList());
    }

    /**
     * 搜索房间
     * 
     * @param criteria 搜索条件
     * @return 匹配的房间列表
     */
    public List<Room> searchRooms(RoomSearchCriteria criteria) {
        return rooms.values().stream()
                .filter(room -> matchesCriteria(room, criteria))
                .collect(Collectors.toList());
    }

    // ========== 房间状态管理 ==========

    /**
     * 开始游戏
     * 
     * @param roomId 房间ID
     * @return 是否成功
     */
    public boolean startGame(String roomId) {
        try {
            Room room = getRoom(roomId);
            if (room == null) {
                throw new GameException(ErrorCodes.ROOM_NOT_FOUND);
            }

            if (room.getStatus() != Room.RoomStatus.WAITING) {
                throw new GameException(ErrorCodes.ROOM_INVALID_STATUS);
            }

            if (room.getPlayerCount() < room.getConfig().getMinPlayers()) {
                throw new GameException(ErrorCodes.ROOM_NOT_ENOUGH_PLAYERS);
            }

            // 更改房间状态
            room.setStatus(Room.RoomStatus.PLAYING);
            room.setGameStartTime(System.currentTimeMillis());

            // 初始化游戏循环
            room.initializeGameLoop();

            // 更新Redis
            if (enableRoomPersistence) {
                saveRoomToRedis(room);
            }

            logger.info("游戏开始: roomId={}, playerCount={}", roomId, room.getPlayerCount());

            // 发布游戏开始事件
            publishRoomEvent(new GameStartedEvent(roomId, room.getPlayers()));

            return true;

        } catch (GameException e) {
            logger.warn("开始游戏失败: roomId={}, error={}", roomId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("开始游戏异常: roomId={}", roomId, e);
            throw new GameException(ErrorCodes.ROOM_START_GAME_FAILED, e);
        }
    }

    /**
     * 结束游戏
     * 
     * @param roomId 房间ID
     * @param gameResult 游戏结果
     */
    public void endGame(String roomId, GameResult gameResult) {
        try {
            Room room = getRoom(roomId);
            if (room == null) {
                return;
            }

            // 更改房间状态
            room.setStatus(Room.RoomStatus.FINISHED);
            room.setGameEndTime(System.currentTimeMillis());
            room.setGameResult(gameResult);

            // 停止游戏循环
            room.stopGameLoop();

            // 更新Redis
            if (enableRoomPersistence) {
                saveRoomToRedis(room);
            }

            logger.info("游戏结束: roomId={}, duration={}ms, result={}", 
                       roomId, room.getGameDuration(), gameResult);

            // 发布游戏结束事件
            publishRoomEvent(new GameEndedEvent(roomId, gameResult));

            // 延迟销毁房间
            scheduleRoomDestruction(roomId, 30000); // 30秒后销毁

        } catch (Exception e) {
            logger.error("结束游戏异常: roomId={}", roomId, e);
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 生成房间ID
     */
    private String generateRoomId() {
        return "ROOM_" + System.currentTimeMillis() + "_" + roomIdGenerator.getAndIncrement();
    }

    /**
     * 检查房间是否准备开始
     */
    private void checkRoomReadyToStart(Room room) {
        RoomConfig config = room.getConfig();
        if (config.isAutoStart() && 
            room.getPlayerCount() >= config.getMinPlayers() && 
            room.getStatus() == Room.RoomStatus.WAITING) {
            
            // 自动开始游戏
            startGame(room.getRoomId());
        }
    }

    /**
     * 检查房间搜索条件匹配
     */
    private boolean matchesCriteria(Room room, RoomSearchCriteria criteria) {
        if (criteria.getStatus() != null && room.getStatus() != criteria.getStatus()) {
            return false;
        }
        
        if (criteria.getGameMode() != null && !criteria.getGameMode().equals(room.getConfig().getGameMode())) {
            return false;
        }
        
        if (criteria.getMinPlayers() != null && room.getPlayerCount() < criteria.getMinPlayers()) {
            return false;
        }
        
        if (criteria.getMaxPlayers() != null && room.getPlayerCount() > criteria.getMaxPlayers()) {
            return false;
        }
        
        return true;
    }

    /**
     * 延迟销毁房间
     */
    private void scheduleRoomDestruction(String roomId, long delayMs) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                destroyRoom(roomId);
            }
        }, delayMs);
    }

    // ========== Redis操作 ==========

    private void saveRoomToRedis(Room room) {
        try {
            // 这里应该序列化Room对象到JSON
            // 暂时使用简化实现
            redisClient.setex(ROOM_KEY_PREFIX + room.getRoomId(), roomTimeoutSeconds, room.toString());
        } catch (Exception e) {
            logger.error("保存房间到Redis失败: roomId={}", room.getRoomId(), e);
        }
    }

    private Room loadRoomFromRedis(String roomId) {
        try {
            String roomData = redisClient.get(ROOM_KEY_PREFIX + roomId);
            if (roomData != null) {
                // 这里应该从JSON反序列化Room对象
                // 暂时返回null
                return null;
            }
        } catch (Exception e) {
            logger.error("从Redis加载房间失败: roomId={}", roomId, e);
        }
        return null;
    }

    private void removeRoomFromRedis(String roomId) {
        try {
            redisClient.del(ROOM_KEY_PREFIX + roomId);
        } catch (Exception e) {
            logger.error("从Redis删除房间失败: roomId={}", roomId, e);
        }
    }

    private void addRoomToList(String roomId) {
        try {
            redisClient.sadd(ROOM_LIST_KEY, roomId);
        } catch (Exception e) {
            logger.error("添加房间到列表失败: roomId={}", roomId, e);
        }
    }

    private void removeRoomFromList(String roomId) {
        try {
            redisClient.srem(ROOM_LIST_KEY, roomId);
        } catch (Exception e) {
            logger.error("从列表删除房间失败: roomId={}", roomId, e);
        }
    }

    private void savePlayerRoomMapping(String playerId, String roomId) {
        try {
            redisClient.setex(PLAYER_ROOM_KEY_PREFIX + playerId, roomTimeoutSeconds, roomId);
        } catch (Exception e) {
            logger.error("保存玩家房间映射失败: playerId={}, roomId={}", playerId, roomId, e);
        }
    }

    private String getPlayerRoomMapping(String playerId) {
        try {
            return redisClient.get(PLAYER_ROOM_KEY_PREFIX + playerId);
        } catch (Exception e) {
            logger.error("获取玩家房间映射失败: playerId={}", playerId, e);
            return null;
        }
    }

    private void removePlayerRoomMapping(String playerId) {
        try {
            redisClient.del(PLAYER_ROOM_KEY_PREFIX + playerId);
        } catch (Exception e) {
            logger.error("删除玩家房间映射失败: playerId={}", playerId, e);
        }
    }

    // ========== 定时任务 ==========

    /**
     * 定时清理超时房间
     */
    @Scheduled(fixedRateString = "${lumina.room.cleanup-interval-seconds:300}000")
    public void cleanupTimeoutRooms() {
        try {
            long currentTime = System.currentTimeMillis();
            long timeoutMs = roomTimeoutSeconds * 1000L;
            
            List<String> timeoutRooms = new ArrayList<>();
            
            for (Room room : rooms.values()) {
                if (currentTime - room.getLastActiveTime() > timeoutMs) {
                    timeoutRooms.add(room.getRoomId());
                }
            }
            
            for (String roomId : timeoutRooms) {
                logger.info("清理超时房间: roomId={}", roomId);
                destroyRoom(roomId);
            }
            
            if (!timeoutRooms.isEmpty()) {
                logger.info("清理超时房间完成: count={}", timeoutRooms.size());
            }
            
        } catch (Exception e) {
            logger.error("清理超时房间失败", e);
        }
    }

    // ========== 事件发布 ==========

    private void publishRoomEvent(RoomEvent event) {
        // 这里应该发布事件到Spring事件系统
        // 暂时只记录日志
        logger.debug("发布房间事件: {}", event);
    }

    // ========== 统计信息 ==========

    /**
     * 获取房间管理统计信息
     */
    public RoomManagerStats getStats() {
        return new RoomManagerStats(
            totalRoomsCreated.get(),
            totalRoomsDestroyed.get(),
            rooms.size(),
            playerToRoom.size(),
            getJoinableRooms().size()
        );
    }

    // ========== 事件类定义 ==========

    public interface RoomEvent {
        String getRoomId();
        long getTimestamp();
    }

    public static class RoomCreatedEvent implements RoomEvent {
        private final String roomId;
        private final String creatorId;
        private final RoomConfig config;
        private final long timestamp;

        public RoomCreatedEvent(String roomId, String creatorId, RoomConfig config) {
            this.roomId = roomId;
            this.creatorId = creatorId;
            this.config = config;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getRoomId() { return roomId; }
        public String getCreatorId() { return creatorId; }
        public RoomConfig getConfig() { return config; }
        public long getTimestamp() { return timestamp; }
    }

    public static class PlayerJoinedEvent implements RoomEvent {
        private final String roomId;
        private final String playerId;
        private final int playerCount;
        private final long timestamp;

        public PlayerJoinedEvent(String roomId, String playerId, int playerCount) {
            this.roomId = roomId;
            this.playerId = playerId;
            this.playerCount = playerCount;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getRoomId() { return roomId; }
        public String getPlayerId() { return playerId; }
        public int getPlayerCount() { return playerCount; }
        public long getTimestamp() { return timestamp; }
    }

    public static class PlayerLeftEvent implements RoomEvent {
        private final String roomId;
        private final String playerId;
        private final int playerCount;
        private final long timestamp;

        public PlayerLeftEvent(String roomId, String playerId, int playerCount) {
            this.roomId = roomId;
            this.playerId = playerId;
            this.playerCount = playerCount;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getRoomId() { return roomId; }
        public String getPlayerId() { return playerId; }
        public int getPlayerCount() { return playerCount; }
        public long getTimestamp() { return timestamp; }
    }

    public static class OwnerChangedEvent implements RoomEvent {
        private final String roomId;
        private final String oldOwnerId;
        private final String newOwnerId;
        private final long timestamp;

        public OwnerChangedEvent(String roomId, String oldOwnerId, String newOwnerId) {
            this.roomId = roomId;
            this.oldOwnerId = oldOwnerId;
            this.newOwnerId = newOwnerId;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getRoomId() { return roomId; }
        public String getOldOwnerId() { return oldOwnerId; }
        public String getNewOwnerId() { return newOwnerId; }
        public long getTimestamp() { return timestamp; }
    }

    public static class GameStartedEvent implements RoomEvent {
        private final String roomId;
        private final Set<String> players;
        private final long timestamp;

        public GameStartedEvent(String roomId, Set<String> players) {
            this.roomId = roomId;
            this.players = new HashSet<>(players);
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getRoomId() { return roomId; }
        public Set<String> getPlayers() { return players; }
        public long getTimestamp() { return timestamp; }
    }

    public static class GameEndedEvent implements RoomEvent {
        private final String roomId;
        private final GameResult gameResult;
        private final long timestamp;

        public GameEndedEvent(String roomId, GameResult gameResult) {
            this.roomId = roomId;
            this.gameResult = gameResult;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getRoomId() { return roomId; }
        public GameResult getGameResult() { return gameResult; }
        public long getTimestamp() { return timestamp; }
    }

    public static class RoomDestroyedEvent implements RoomEvent {
        private final String roomId;
        private final String ownerId;
        private final long timestamp;

        public RoomDestroyedEvent(String roomId, String ownerId) {
            this.roomId = roomId;
            this.ownerId = ownerId;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public String getRoomId() { return roomId; }
        public String getOwnerId() { return ownerId; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 房间管理统计信息
     */
    public static class RoomManagerStats {
        private final long totalRoomsCreated;
        private final long totalRoomsDestroyed;
        private final long activeRooms;
        private final long activePlayers;
        private final long joinableRooms;

        public RoomManagerStats(long totalRoomsCreated, long totalRoomsDestroyed, 
                              long activeRooms, long activePlayers, long joinableRooms) {
            this.totalRoomsCreated = totalRoomsCreated;
            this.totalRoomsDestroyed = totalRoomsDestroyed;
            this.activeRooms = activeRooms;
            this.activePlayers = activePlayers;
            this.joinableRooms = joinableRooms;
        }

        // Getters
        public long getTotalRoomsCreated() { return totalRoomsCreated; }
        public long getTotalRoomsDestroyed() { return totalRoomsDestroyed; }
        public long getActiveRooms() { return activeRooms; }
        public long getActivePlayers() { return activePlayers; }
        public long getJoinableRooms() { return joinableRooms; }
    }
}