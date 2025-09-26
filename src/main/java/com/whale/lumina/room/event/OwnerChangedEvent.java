package com.whale.lumina.room.event;

public class OwnerChangedEvent implements RoomEvent {
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