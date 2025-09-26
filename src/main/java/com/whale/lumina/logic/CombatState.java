package com.whale.lumina.logic;

import java.util.HashMap;
import java.util.Map;

/**
 * 战斗状态类
 */
public class CombatState {
    private final String playerId;
    private int currentHP;
    private int maxHP;
    private int attackPower;
    private int defense;
    private final Map<String, Integer> buffs;
    private final Map<String, Integer> debuffs;

    public CombatState(String playerId) {
        this.playerId = playerId;
        this.maxHP = 100;
        this.currentHP = maxHP;
        this.attackPower = 10;
        this.defense = 5;
        this.buffs = new HashMap<>();
        this.debuffs = new HashMap<>();
    }

    public void takeDamage(int damage) {
        currentHP = Math.max(0, currentHP - damage);
    }

    public void heal(int amount) {
        currentHP = Math.min(maxHP, currentHP + amount);
    }

    public void applyBuff(String buffId, int duration) {
        buffs.put(buffId, duration);
    }

    public void applyDebuff(String debuffId, int duration) {
        debuffs.put(debuffId, duration);
    }

    public boolean applyEffect(String effectId, EffectData effectData) {
        // 根据效果类型应用不同逻辑
        switch (effectData.getType()) {
            case "POISON":
                takeDamage(effectData.getValue());
                return true;
            case "REGENERATION":
                heal(effectData.getValue());
                return true;
            default:
                return false;
        }
    }

    // Getters
    public String getPlayerId() { return playerId; }
    public int getCurrentHP() { return currentHP; }
    public int getMaxHP() { return maxHP; }
    public int getAttackPower() { return attackPower; }
    public int getDefense() { return defense; }
    public Map<String, Integer> getBuffs() { return new HashMap<>(buffs); }
    public Map<String, Integer> getDebuffs() { return new HashMap<>(debuffs); }
}