package com.whale.lumina.logic;

/**
 * 效果数据类
 */
public class EffectData {
    private final String type;
    private final int value;
    private final int duration;

    public EffectData(String type, int value, int duration) {
        this.type = type;
        this.value = value;
        this.duration = duration;
    }

    public String getType() { return type; }
    public int getValue() { return value; }
    public int getDuration() { return duration; }
}