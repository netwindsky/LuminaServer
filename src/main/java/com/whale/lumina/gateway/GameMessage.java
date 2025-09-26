package com.whale.lumina.gateway;

/**
 * 游戏消息包装类
 */
public class GameMessage {
    private final byte[] data;
    private final long timestamp;

    public GameMessage(byte[] data) {
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取消息数据
     * 
     * @return 消息数据
     */
    public byte[] getData() {
        return data;
    }

    /**
     * 获取消息时间戳
     * 
     * @return 时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 获取消息长度
     * 
     * @return 消息长度
     */
    public int getLength() {
        return data != null ? data.length : 0;
    }

    /**
     * 解析为Protocol Buffers消息
     * 
     * @param parser 消息解析器
     * @param <T> 消息类型
     * @return 解析后的消息
     */
    public <T extends com.google.protobuf.MessageLite> T parseAs(
            com.google.protobuf.Parser<T> parser) throws Exception {
        return parser.parseFrom(data);
    }
}