package com.whale.lumina.room.event;

import java.util.HashSet;
import java.util.Set;

public class GameStartedEvent implements RoomEvent {
    private final String roomId;
    private final Set<String> players;
    private final long timestamp;

    public GameStartedEvent(String roomId, Set<String> players) {
        this.roomId = roomId;
        this.players = new HashSet<>(players);
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getRoomId() { return roomId; }
    public Set<String> getPlayers() { return players; }
    public long getTimestamp() { return timestamp; }
}