package com.whale.lumina.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗会话类
 */
public class CombatSession {
    private final String combatId;
    private final String roomId;
    private final List<String> participants;
    private final Map<String, com.whale.lumina.logic.CombatState> playerStates;
    private final List<com.whale.lumina.logic.CombatEvent> combatEvents;
    private final long startTime;
    private long endTime;
    private boolean ended;

    public CombatSession(String combatId, String roomId, List<String> participants) {
        this.combatId = combatId;
        this.roomId = roomId;
        this.participants = new ArrayList<>(participants);
        this.playerStates = new ConcurrentHashMap<>();
        this.combatEvents = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
        this.ended = false;

        // 初始化玩家状态
        for (String playerId : participants) {
            playerStates.put(playerId, new com.whale.lumina.logic.CombatState(playerId));
        }
    }
    
    public String getCombatId() {
        return combatId;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public List<String> getParticipants() {
        return new ArrayList<>(participants);
    }
    
    public Map<String, com.whale.lumina.logic.CombatState> getPlayerStates() {
        return playerStates;
    }
    
    public List<com.whale.lumina.logic.CombatEvent> getCombatEvents() {
        return new ArrayList<>(combatEvents);
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public boolean isEnded() {
        return ended;
    }
    
    public void setEnded(boolean ended) {
        this.ended = ended;
        if (ended) {
            this.endTime = System.currentTimeMillis();
        }
    }
    
    public void addCombatEvent(com.whale.lumina.logic.CombatEvent event) {
        this.combatEvents.add(event);
    }
}