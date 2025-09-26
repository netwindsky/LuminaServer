package com.whale.lumina.room.event;

import com.whale.lumina.room.GameResult;

public class GameEndedEvent implements RoomEvent {
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