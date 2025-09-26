package com.whale.lumina.room;

/**
 * 房间管理统计信息
 */
public class RoomManagerStats {
    private final long totalRoomsCreated;
    private final long totalRoomsDestroyed;
    private final long activeRooms;
    private final long activePlayers;
    private final long joinableRooms;

    public RoomManagerStats(long totalRoomsCreated, long totalRoomsDestroyed, 
                          long activeRooms, long activePlayers, long joinableRooms) {
        this.totalRoomsCreated = totalRoomsCreated;
        this.totalRoomsDestroyed = totalRoomsDestroyed;
        this.activeRooms = activeRooms;
        this.activePlayers = activePlayers;
        this.joinableRooms = joinableRooms;
    }

    // Getters
    public long getTotalRoomsCreated() { return totalRoomsCreated; }
    public long getTotalRoomsDestroyed() { return totalRoomsDestroyed; }
    public long getActiveRooms() { return activeRooms; }
    public long getActivePlayers() { return activePlayers; }
    public long getJoinableRooms() { return joinableRooms; }
}