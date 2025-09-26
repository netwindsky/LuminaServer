package com.whale.lumina.logic;

/**
 * 游戏输入类
 */
public class GameInput {
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