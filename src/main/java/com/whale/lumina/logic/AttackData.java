package com.whale.lumina.logic;

/**
 * 攻击数据类
 */
public class AttackData {
    private final int baseDamage;
    private final String attackType;

    public AttackData(int baseDamage, String attackType) {
        this.baseDamage = baseDamage;
        this.attackType = attackType;
    }

    public int getBaseDamage() { return baseDamage; }
    public String getAttackType() { return attackType; }
}