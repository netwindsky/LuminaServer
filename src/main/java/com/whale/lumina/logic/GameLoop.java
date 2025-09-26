package com.whale.lumina.logic;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.TimeUtils;
import com.whale.lumina.room.Room;
import com.whale.lumina.room.GameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏循环管理器
 * 
 * 负责管理游戏的主循环，包括游戏状态更新、帧率控制、游戏逻辑处理等
 * 
 * @author Lumina Team
 */
@Component
public class GameLoop {

    private static final Logger logger = LoggerFactory.getLogger(GameLoop.class);

    // 默认配置
    private static final int DEFAULT_TARGET_FPS = 60;
    private static final int DEFAULT_MAX_FRAME_TIME = 50; // 最大帧时间（毫秒）
    private static final int DEFAULT_THREAD_POOL_SIZE = 10;

    // 线程池
    private final ScheduledExecutorService gameLoopExecutor;
    private final ExecutorService gameLogicExecutor;

    // 游戏循环管理
    private final Map<String, GameLoopInstance> activeLoops;
    private final AtomicLong nextLoopId;

    // 统计信息
    private final AtomicLong totalFramesProcessed;
    private final AtomicLong totalGameLoops;

    /**
     * 构造函数
     */
    public GameLoop() {
        this.gameLoopExecutor = Executors.newScheduledThreadPool(DEFAULT_THREAD_POOL_SIZE, 
            r -> new Thread(r, "GameLoop-" + Thread.currentThread().getId()));
        this.gameLogicExecutor = Executors.newCachedThreadPool(
            r -> new Thread(r, "GameLogic-" + Thread.currentThread().getId()));
        
        this.activeLoops = new ConcurrentHashMap<>();
        this.nextLoopId = new AtomicLong(1);
        this.totalFramesProcessed = new AtomicLong(0);
        this.totalGameLoops = new AtomicLong(0);
    }

    // ========== 游戏循环管理 ==========

    /**
     * 启动游戏循环
     * 
     * @param room 房间对象
     * @param config 循环配置
     * @return 循环ID
     * @throws GameException 启动失败时抛出
     */
    public String startGameLoop(Room room, GameLoopConfig config) throws GameException {
        if (room == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "房间不能为空");
        }

        String loopId = "loop_" + nextLoopId.getAndIncrement();
        
        try {
            GameLoopInstance instance = new GameLoopInstance(loopId, room, config);
            activeLoops.put(loopId, instance);
            
            // 启动循环
            instance.start();
            totalGameLoops.incrementAndGet();
            
            logger.info("游戏循环已启动: loopId={}, roomId={}, targetFPS={}", 
                       loopId, room.getRoomId(), config.getTargetFPS());
            
            return loopId;
            
        } catch (Exception e) {
            logger.error("启动游戏循环失败: loopId={}, roomId={}", loopId, room.getRoomId(), e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "启动游戏循环失败: " + e.getMessage());
        }
    }

    /**
     * 启动游戏循环（使用默认配置）
     * 
     * @param room 房间对象
     * @return 循环ID
     * @throws GameException 启动失败时抛出
     */
    public String startGameLoop(Room room) throws GameException {
        return startGameLoop(room, GameLoopConfig.defaultConfig());
    }

    /**
     * 停止游戏循环
     * 
     * @param loopId 循环ID
     * @return 游戏结果
     * @throws GameException 停止失败时抛出
     */
    public GameResult stopGameLoop(String loopId) throws GameException {
        GameLoopInstance instance = activeLoops.get(loopId);
        if (instance == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "游戏循环不存在: " + loopId);
        }

        try {
            GameResult result = instance.stop();
            activeLoops.remove(loopId);
            
            logger.info("游戏循环已停止: loopId={}, roomId={}, duration={}ms", 
                       loopId, instance.getRoom().getRoomId(), result.getGameDuration());
            
            return result;
            
        } catch (Exception e) {
            logger.error("停止游戏循环失败: loopId={}", loopId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "停止游戏循环失败: " + e.getMessage());
        }
    }

    /**
     * 暂停游戏循环
     * 
     * @param loopId 循环ID
     * @throws GameException 暂停失败时抛出
     */
    public void pauseGameLoop(String loopId) throws GameException {
        GameLoopInstance instance = activeLoops.get(loopId);
        if (instance == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "游戏循环不存在: " + loopId);
        }

        instance.pause();
        logger.info("游戏循环已暂停: loopId={}", loopId);
    }

    /**
     * 恢复游戏循环
     * 
     * @param loopId 循环ID
     * @throws GameException 恢复失败时抛出
     */
    public void resumeGameLoop(String loopId) throws GameException {
        GameLoopInstance instance = activeLoops.get(loopId);
        if (instance == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "游戏循环不存在: " + loopId);
        }

        instance.resume();
        logger.info("游戏循环已恢复: loopId={}", loopId);
    }

    /**
     * 获取游戏循环状态
     * 
     * @param loopId 循环ID
     * @return 循环状态
     */
    public GameLoopStatus getGameLoopStatus(String loopId) {
        GameLoopInstance instance = activeLoops.get(loopId);
        if (instance == null) {
            return null;
        }
        return instance.getStatus();
    }

    /**
     * 检查游戏循环是否存在
     * 
     * @param loopId 循环ID
     * @return 是否存在
     */
    public boolean hasGameLoop(String loopId) {
        return activeLoops.containsKey(loopId);
    }

    /**
     * 获取房间的游戏循环ID
     * 
     * @param roomId 房间ID
     * @return 循环ID，如果不存在返回null
     */
    public String getGameLoopByRoom(String roomId) {
        for (GameLoopInstance instance : activeLoops.values()) {
            if (instance.getRoom().getRoomId().equals(roomId)) {
                return instance.getLoopId();
            }
        }
        return null;
    }

    // ========== 游戏逻辑处理 ==========

    /**
     * 处理游戏输入
     * 
     * @param loopId 循环ID
     * @param playerId 玩家ID
     * @param inputData 输入数据
     * @throws GameException 处理失败时抛出
     */
    public void processGameInput(String loopId, String playerId, Object inputData) throws GameException {
        GameLoopInstance instance = activeLoops.get(loopId);
        if (instance == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "游戏循环不存在: " + loopId);
        }

        instance.addInput(playerId, inputData);
    }

    /**
     * 添加游戏事件
     * 
     * @param loopId 循环ID
     * @param event 游戏事件
     * @throws GameException 添加失败时抛出
     */
    public void addGameEvent(String loopId, GameEvent event) throws GameException {
        GameLoopInstance instance = activeLoops.get(loopId);
        if (instance == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "游戏循环不存在: " + loopId);
        }

        instance.addEvent(event);
    }

    // ========== 统计信息 ==========

    /**
     * 获取活跃循环数量
     * 
     * @return 活跃循环数量
     */
    public int getActiveLoopCount() {
        return activeLoops.size();
    }

    /**
     * 获取总处理帧数
     * 
     * @return 总处理帧数
     */
    public long getTotalFramesProcessed() {
        return totalFramesProcessed.get();
    }

    /**
     * 获取总游戏循环数
     * 
     * @return 总游戏循环数
     */
    public long getTotalGameLoops() {
        return totalGameLoops.get();
    }

    /**
     * 获取所有活跃循环的状态
     * 
     * @return 循环状态列表
     */
    public List<GameLoopStatus> getAllLoopStatus() {
        List<GameLoopStatus> statusList = new ArrayList<>();
        for (GameLoopInstance instance : activeLoops.values()) {
            statusList.add(instance.getStatus());
        }
        return statusList;
    }

    // ========== 生命周期管理 ==========

    /**
     * 关闭游戏循环管理器
     */
    public void shutdown() {
        logger.info("正在关闭游戏循环管理器...");

        // 停止所有活跃的游戏循环
        for (String loopId : new ArrayList<>(activeLoops.keySet())) {
            try {
                stopGameLoop(loopId);
            } catch (Exception e) {
                logger.error("停止游戏循环失败: loopId={}", loopId, e);
            }
        }

        // 关闭线程池
        gameLoopExecutor.shutdown();
        gameLogicExecutor.shutdown();

        try {
            if (!gameLoopExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                gameLoopExecutor.shutdownNow();
            }
            if (!gameLogicExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                gameLogicExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            gameLoopExecutor.shutdownNow();
            gameLogicExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("游戏循环管理器已关闭");
    }

    // ========== 内部类 ==========

    /**
     * 游戏循环实例
     */
    private class GameLoopInstance {
        private final String loopId;
        private final Room room;
        private final GameLoopConfig config;
        private final AtomicBoolean running;
        private final AtomicBoolean paused;
        
        private ScheduledFuture<?> loopTask;
        private long startTime;
        private long lastFrameTime;
        private long frameCount;
        private final Queue<GameInput> inputQueue;
        private final Queue<GameEvent> eventQueue;
        
        // 性能统计
        private final TimeUtils.PerformanceTimer frameTimer;
        private double averageFrameTime;
        private double currentFPS;

        public GameLoopInstance(String loopId, Room room, GameLoopConfig config) {
            this.loopId = loopId;
            this.room = room;
            this.config = config;
            this.running = new AtomicBoolean(false);
            this.paused = new AtomicBoolean(false);
            
            this.inputQueue = new ConcurrentLinkedQueue<>();
            this.eventQueue = new ConcurrentLinkedQueue<>();
            this.frameTimer = new TimeUtils.PerformanceTimer();
        }

        public void start() {
            if (running.get()) {
                return;
            }

            running.set(true);
            startTime = System.currentTimeMillis();
            lastFrameTime = startTime;
            frameCount = 0;

            // 计算帧间隔
            long frameInterval = 1000 / config.getTargetFPS();
            
            loopTask = gameLoopExecutor.scheduleAtFixedRate(
                this::gameLoopTick, 0, frameInterval, TimeUnit.MILLISECONDS);
        }

        public GameResult stop() {
            if (!running.get()) {
                return createGameResult();
            }

            running.set(false);
            if (loopTask != null) {
                loopTask.cancel(false);
            }

            return createGameResult();
        }

        public void pause() {
            paused.set(true);
        }

        public void resume() {
            paused.set(false);
        }

        public void addInput(String playerId, Object inputData) {
            inputQueue.offer(new GameInput(playerId, inputData, System.currentTimeMillis()));
        }

        public void addEvent(GameEvent event) {
            eventQueue.offer(event);
        }

        private void gameLoopTick() {
            if (!running.get() || paused.get()) {
                return;
            }

            frameTimer.start();
            
            try {
                // 处理输入
                processInputs();
                
                // 处理事件
                processEvents();
                
                // 更新游戏逻辑
                updateGameLogic();
                
                // 发送游戏状态
                sendGameState();
                
                // 更新统计信息
                updateStatistics();
                
            } catch (Exception e) {
                logger.error("游戏循环处理异常: loopId={}, roomId={}", loopId, room.getRoomId(), e);
            } finally {
                frameTimer.stop();
                frameCount++;
                totalFramesProcessed.incrementAndGet();
            }
        }

        private void processInputs() {
            GameInput input;
            while ((input = inputQueue.poll()) != null) {
                try {
                    // 这里应该调用具体的游戏逻辑处理器
                    // 暂时只记录日志
                    logger.debug("处理游戏输入: playerId={}, loopId={}", input.getPlayerId(), loopId);
                } catch (Exception e) {
                    logger.error("处理游戏输入失败: playerId={}, loopId={}", input.getPlayerId(), loopId, e);
                }
            }
        }

        private void processEvents() {
            GameEvent event;
            while ((event = eventQueue.poll()) != null) {
                try {
                    // 这里应该调用具体的事件处理器
                    // 暂时只记录日志
                    logger.debug("处理游戏事件: type={}, loopId={}", event.getType(), loopId);
                } catch (Exception e) {
                    logger.error("处理游戏事件失败: type={}, loopId={}", event.getType(), loopId, e);
                }
            }
        }

        private void updateGameLogic() {
            // 这里应该调用具体的游戏逻辑更新
            // 暂时只更新房间状态
            room.updateLastActiveTime();
        }

        private void sendGameState() {
            // 这里应该发送游戏状态给所有玩家
            // 暂时只记录日志
            if (frameCount % (config.getTargetFPS() * 5) == 0) { // 每5秒记录一次
                logger.debug("发送游戏状态: loopId={}, frame={}", loopId, frameCount);
            }
        }

        private void updateStatistics() {
            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastFrameTime;
            
            if (timeDiff > 0) {
                currentFPS = 1000.0 / timeDiff;
                averageFrameTime = frameTimer.getAverageTime();
            }
            
            lastFrameTime = currentTime;
        }

        private GameResult createGameResult() {
            long endTime = System.currentTimeMillis();
            GameResult result = GameResult.normalEnd(
                room.getGameId(), room.getRoomId(), startTime, endTime);
            
            // 添加统计信息
            result.setGameStat("totalFrames", frameCount);
            result.setGameStat("averageFPS", currentFPS);
            result.setGameStat("averageFrameTime", averageFrameTime);
            
            return result;
        }

        public GameLoopStatus getStatus() {
            return new GameLoopStatus(loopId, room.getRoomId(), running.get(), paused.get(),
                                    frameCount, currentFPS, averageFrameTime, 
                                    System.currentTimeMillis() - startTime);
        }

        public String getLoopId() { return loopId; }
        public Room getRoom() { return room; }
    }

    /**
     * 游戏输入类
     */
    private static class GameInput {
        private final String playerId;
        private final Object inputData;
        private final long timestamp;

        public GameInput(String playerId, Object inputData, long timestamp) {
            this.playerId = playerId;
            this.inputData = inputData;
            this.timestamp = timestamp;
        }

        public String getPlayerId() { return playerId; }
        public Object getInputData() { return inputData; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 游戏事件类
     */
    public static class GameEvent {
        private final String type;
        private final Object data;
        private final long timestamp;

        public GameEvent(String type, Object data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getType() { return type; }
        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 游戏循环配置类
     */
    public static class GameLoopConfig {
        private final int targetFPS;
        private final int maxFrameTime;
        private final boolean enableStatistics;

        public GameLoopConfig(int targetFPS, int maxFrameTime, boolean enableStatistics) {
            this.targetFPS = targetFPS;
            this.maxFrameTime = maxFrameTime;
            this.enableStatistics = enableStatistics;
        }

        public static GameLoopConfig defaultConfig() {
            return new GameLoopConfig(DEFAULT_TARGET_FPS, DEFAULT_MAX_FRAME_TIME, true);
        }

        public static GameLoopConfig customConfig(int targetFPS) {
            return new GameLoopConfig(targetFPS, DEFAULT_MAX_FRAME_TIME, true);
        }

        public int getTargetFPS() { return targetFPS; }
        public int getMaxFrameTime() { return maxFrameTime; }
        public boolean isEnableStatistics() { return enableStatistics; }
    }

    /**
     * 游戏循环状态类
     */
    public static class GameLoopStatus {
        private final String loopId;
        private final String roomId;
        private final boolean running;
        private final boolean paused;
        private final long frameCount;
        private final double currentFPS;
        private final double averageFrameTime;
        private final long runningTime;

        public GameLoopStatus(String loopId, String roomId, boolean running, boolean paused,
                            long frameCount, double currentFPS, double averageFrameTime, long runningTime) {
            this.loopId = loopId;
            this.roomId = roomId;
            this.running = running;
            this.paused = paused;
            this.frameCount = frameCount;
            this.currentFPS = currentFPS;
            this.averageFrameTime = averageFrameTime;
            this.runningTime = runningTime;
        }

        // Getters
        public String getLoopId() { return loopId; }
        public String getRoomId() { return roomId; }
        public boolean isRunning() { return running; }
        public boolean isPaused() { return paused; }
        public long getFrameCount() { return frameCount; }
        public double getCurrentFPS() { return currentFPS; }
        public double getAverageFrameTime() { return averageFrameTime; }
        public long getRunningTime() { return runningTime; }

        @Override
        public String toString() {
            return String.format("GameLoopStatus{loopId='%s', roomId='%s', running=%s, paused=%s, " +
                               "frames=%d, fps=%.1f, avgFrameTime=%.2fms, runningTime=%dms}",
                               loopId, roomId, running, paused, frameCount, currentFPS, 
                               averageFrameTime, runningTime);
        }
    }
}