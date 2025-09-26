package com.whale.lumina.room.event;

public class RoomDestroyedEvent implements RoomEvent {
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