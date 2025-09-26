package com.whale.lumina.room.event;

/**
 * 房间事件接口
 * 定义所有房间相关事件的基本接口
 */
public interface RoomEvent {
    /**
     * 获取房间ID
     * @return 房间ID
     */
    String getRoomId();
    
    /**
     * 获取事件发生时间戳
     * @return 时间戳（毫秒）
     */
    long getTimestamp();
}