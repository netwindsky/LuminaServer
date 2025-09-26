package com.whale.lumina.common;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Protocol Buffers 序列化工具类
 * 
 * 提供统一的序列化/反序列化接口，包含错误处理和性能优化
 * 
 * @author Lumina Team
 */
public final class ProtoUtils {

    private static final Logger logger = LoggerFactory.getLogger(ProtoUtils.class);

    // 消息头长度（4字节存储消息长度）
    private static final int HEADER_LENGTH = 4;
    
    // 最大消息长度（1MB）
    private static final int MAX_MESSAGE_LENGTH = 1024 * 1024;

    /**
     * 序列化Protobuf消息为字节数组
     * 
     * @param message Protobuf消息对象
     * @return 序列化后的字节数组
     * @throws IllegalArgumentException 如果消息为null或序列化失败
     */
    public static byte[] serialize(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        try {
            byte[] data = message.toByteArray();
            if (data.length > MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException("Message too large: " + data.length + " bytes");
            }
            return data;
        } catch (Exception e) {
            logger.error("Failed to serialize message: {}", message.getClass().getSimpleName(), e);
            throw new IllegalArgumentException("Serialization failed", e);
        }
    }

    /**
     * 反序列化字节数组为Protobuf消息
     * 
     * @param data 字节数组
     * @param parser Protobuf解析器
     * @param <T> 消息类型
     * @return 反序列化后的消息对象
     * @throws IllegalArgumentException 如果数据无效或反序列化失败
     */
    public static <T extends Message> T deserialize(byte[] data, Parser<T> parser) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        if (data.length > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Data too large: " + data.length + " bytes");
        }

        try {
            return parser.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            logger.error("Failed to deserialize data, length: {}", data.length, e);
            throw new IllegalArgumentException("Deserialization failed", e);
        }
    }

    /**
     * 序列化消息并添加长度头
     * 格式：[4字节长度][消息内容]
     * 
     * @param message Protobuf消息对象
     * @return 包含长度头的字节数组
     */
    public static byte[] serializeWithHeader(Message message) {
        byte[] data = serialize(message);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + data.length);
        buffer.putInt(data.length);
        buffer.put(data);
        return buffer.array();
    }

    /**
     * 从包含长度头的字节数组中反序列化消息
     * 
     * @param data 包含长度头的字节数组
     * @param parser Protobuf解析器
     * @param <T> 消息类型
     * @return 反序列化后的消息对象
     */
    public static <T extends Message> T deserializeWithHeader(byte[] data, Parser<T> parser) {
        if (data == null || data.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("Invalid data format");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int messageLength = buffer.getInt();
        
        if (messageLength < 0 || messageLength > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Invalid message length: " + messageLength);
        }

        if (buffer.remaining() < messageLength) {
            throw new IllegalArgumentException("Insufficient data for message");
        }

        byte[] messageData = new byte[messageLength];
        buffer.get(messageData);
        
        return deserialize(messageData, parser);
    }

    /**
     * 检查字节数组是否包含完整的消息（包含长度头）
     * 
     * @param data 字节数组
     * @return 如果包含完整消息返回true，否则返回false
     */
    public static boolean hasCompleteMessage(byte[] data) {
        if (data == null || data.length < HEADER_LENGTH) {
            return false;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int messageLength = buffer.getInt();
        
        return messageLength >= 0 && 
               messageLength <= MAX_MESSAGE_LENGTH && 
               buffer.remaining() >= messageLength;
    }

    /**
     * 获取消息长度（从长度头读取）
     * 
     * @param data 包含长度头的字节数组
     * @return 消息长度，如果数据无效返回-1
     */
    public static int getMessageLength(byte[] data) {
        if (data == null || data.length < HEADER_LENGTH) {
            return -1;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int messageLength = buffer.getInt();
        
        if (messageLength < 0 || messageLength > MAX_MESSAGE_LENGTH) {
            return -1;
        }

        return messageLength;
    }

    /**
     * 计算序列化后的消息大小（不包含长度头）
     * 
     * @param message Protobuf消息对象
     * @return 序列化后的字节数
     */
    public static int getSerializedSize(Message message) {
        if (message == null) {
            return 0;
        }
        return message.getSerializedSize();
    }

    /**
     * 验证消息是否有效（非null且可序列化）
     * 
     * @param message Protobuf消息对象
     * @return 如果消息有效返回true，否则返回false
     */
    public static boolean isValidMessage(Message message) {
        if (message == null) {
            return false;
        }

        try {
            // 尝试序列化以验证消息完整性
            message.toByteArray();
            return true;
        } catch (Exception e) {
            logger.debug("Invalid message: {}", message.getClass().getSimpleName(), e);
            return false;
        }
    }

    // 私有构造函数，防止实例化
    private ProtoUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}