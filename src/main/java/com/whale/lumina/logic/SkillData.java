package com.whale.lumina.logic;

/**
 * 技能数据类
 */
public class SkillData {
    private final String type;
    private final int value;
    private final String effectId;
    private final int duration;

    public SkillData(String type, int value, String effectId, int duration) {
        this.type = type;
        this.value = value;
        this.effectId = effectId;
        this.duration = duration;
    }

    public String getType() { return type; }
    public int getValue() { return value; }
    public String getEffectId() { return effectId; }
    public int getDuration() { return duration; }
}