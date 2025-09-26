package com.whale.lumina.logic;

/**
 * 游戏事件类
 */
public class GameEvent {
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