package com.whale.lumina.logic;

/**
 * 游戏循环配置类
 */
public class GameLoopConfig {
    private static final int DEFAULT_TARGET_FPS = 60;
    private static final int DEFAULT_MAX_FRAME_TIME = 100;
    
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