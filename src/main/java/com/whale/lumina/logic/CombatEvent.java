package com.whale.lumina.logic;

/**
 * 战斗事件类
 */
public class CombatEvent {
    private final String type;
    private final String sourceId;
    private final String targetId;
    private final int value;
    private final long timestamp;

    public CombatEvent(String type, String sourceId, String targetId, int value) {
        this.type = type;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() { return type; }
    public String getSourceId() { return sourceId; }
    public String getTargetId() { return targetId; }
    public int getValue() { return value; }
    public long getTimestamp() { return timestamp; }
}