package com.whale.lumina.room.event;

import com.whale.lumina.room.RoomConfig;

/**
 * 房间创建事件
 */
public class RoomCreatedEvent implements RoomEvent {
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