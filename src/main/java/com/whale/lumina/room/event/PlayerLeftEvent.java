package com.whale.lumina.room.event;

/**
 * 玩家离开房间事件
 */
public class PlayerLeftEvent implements RoomEvent {
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