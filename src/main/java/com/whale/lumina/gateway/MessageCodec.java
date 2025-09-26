package com.whale.lumina.gateway;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ProtoUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 消息编解码器
 * 
 * 基于Protocol Buffers的消息编解码器，处理TCP消息的编码和解码
 * 消息格式：[4字节长度][消息内容]
 * 
 * @author Lumina Team
 */
@Component
public class MessageCodec implements ProtocolCodecFactory {

    private static final Logger logger = LoggerFactory.getLogger(MessageCodec.class);

    // 消息头长度（4字节）
    private static final int HEADER_LENGTH = 4;
    
    // 最大消息长度（1MB）
    private static final int MAX_MESSAGE_LENGTH = 1024 * 1024;
    
    // 最小消息长度
    private static final int MIN_MESSAGE_LENGTH = 1;

    @Override
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
        return new MessageEncoder();
    }

    @Override
    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
        return new MessageDecoder();
    }

    /**
     * 消息编码器
     */
    private static class MessageEncoder implements ProtocolEncoder {

        @Override
        public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
            try {
                byte[] data;
                
                if (message instanceof byte[]) {
                    data = (byte[]) message;
                } else if (message instanceof com.google.protobuf.MessageLite) {
                    // Protocol Buffers消息
                    data = ((com.google.protobuf.MessageLite) message).toByteArray();
                } else if (message instanceof String) {
                    // 字符串消息
                    data = ((String) message).getBytes("UTF-8");
                } else {
                    throw new GameException(ErrorCodes.GATEWAY_UNSUPPORTED_MESSAGE_TYPE);
                }

                // 检查消息长度
                if (data.length > MAX_MESSAGE_LENGTH) {
                    throw new GameException(ErrorCodes.GATEWAY_MESSAGE_TOO_LARGE);
                }

                // 创建输出缓冲区：4字节长度 + 消息内容
                IoBuffer buffer = IoBuffer.allocate(HEADER_LENGTH + data.length);
                buffer.putInt(data.length);  // 写入消息长度
                buffer.put(data);           // 写入消息内容
                buffer.flip();

                out.write(buffer);
                
                logger.debug("消息编码完成: sessionId={}, messageLength={}", 
                           session.getId(), data.length);
                
            } catch (Exception e) {
                logger.error("消息编码失败: sessionId={}", session.getId(), e);
                throw new GameException(ErrorCodes.GATEWAY_MESSAGE_ENCODE_FAILED, e);
            }
        }

        @Override
        public void dispose(IoSession session) throws Exception {
            // 清理资源
        }
    }

    /**
     * 消息解码器
     */
    private static class MessageDecoder extends CumulativeProtocolDecoder {

        @Override
        protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
            try {
                // 检查是否有足够的数据读取消息头
                if (in.remaining() < HEADER_LENGTH) {
                    return false; // 需要更多数据
                }

                // 标记当前位置
                in.mark();

                // 读取消息长度
                int messageLength = in.getInt();

                // 验证消息长度
                if (messageLength < MIN_MESSAGE_LENGTH || messageLength > MAX_MESSAGE_LENGTH) {
                    logger.error("无效的消息长度: sessionId={}, messageLength={}", 
                               session.getId(), messageLength);
                    throw new GameException(ErrorCodes.GATEWAY_INVALID_MESSAGE_LENGTH);
                }

                // 检查是否有足够的数据读取完整消息
                if (in.remaining() < messageLength) {
                    in.reset(); // 重置到标记位置
                    return false; // 需要更多数据
                }

                // 读取消息内容
                byte[] messageData = new byte[messageLength];
                in.get(messageData);

                // 创建消息对象
                GameMessage gameMessage = new GameMessage(messageData);
                out.write(gameMessage);

                logger.debug("消息解码完成: sessionId={}, messageLength={}", 
                           session.getId(), messageLength);

                return true; // 解码成功

            } catch (Exception e) {
                logger.error("消息解码失败: sessionId={}", session.getId(), e);
                throw new GameException(ErrorCodes.GATEWAY_MESSAGE_DECODE_FAILED, e);
            }
        }

        @Override
        public void dispose(IoSession session) throws Exception {
            // 清理资源
        }
    }

    /**
     * 游戏消息包装类
     */
    public static class GameMessage {
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

        /**
         * 解析为字符串
         * 
         * @return 字符串内容
         */
        public String parseAsString() throws Exception {
            return new String(data, "UTF-8");
        }

        /**
         * 验证消息完整性
         * 
         * @return 是否有效
         */
        public boolean isValid() {
            return data != null && data.length > 0 && data.length <= MAX_MESSAGE_LENGTH;
        }

        @Override
        public String toString() {
            return String.format("GameMessage{length=%d, timestamp=%d}", getLength(), timestamp);
        }
    }

    // ========== 工具方法 ==========

    /**
     * 创建Protocol Buffers消息
     * 
     * @param message Protocol Buffers消息
     * @return 字节数组
     */
    public static byte[] createProtobufMessage(com.google.protobuf.MessageLite message) {
        try {
            return message.toByteArray();
        } catch (Exception e) {
            logger.error("创建Protocol Buffers消息失败", e);
            throw new GameException(ErrorCodes.GATEWAY_MESSAGE_CREATE_FAILED, e);
        }
    }

    /**
     * 创建字符串消息
     * 
     * @param message 字符串消息
     * @return 字节数组
     */
    public static byte[] createStringMessage(String message) {
        try {
            return message.getBytes("UTF-8");
        } catch (Exception e) {
            logger.error("创建字符串消息失败", e);
            throw new GameException(ErrorCodes.GATEWAY_MESSAGE_CREATE_FAILED, e);
        }
    }

    /**
     * 创建错误响应消息
     * 
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return 字节数组
     */
    public static byte[] createErrorMessage(String errorCode, String errorMessage) {
        try {
            // 这里应该使用实际的Protocol Buffers错误消息定义
            // 暂时使用JSON格式作为示例
            String jsonError = String.format(
                "{\"error\":{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}}", 
                errorCode, errorMessage, System.currentTimeMillis()
            );
            return jsonError.getBytes("UTF-8");
        } catch (Exception e) {
            logger.error("创建错误消息失败", e);
            throw new GameException(ErrorCodes.GATEWAY_MESSAGE_CREATE_FAILED, e);
        }
    }

    /**
     * 验证消息格式
     * 
     * @param data 消息数据
     * @return 是否有效
     */
    public static boolean validateMessage(byte[] data) {
        return data != null && 
               data.length >= MIN_MESSAGE_LENGTH && 
               data.length <= MAX_MESSAGE_LENGTH;
    }

    /**
     * 获取消息类型（从消息头部解析）
     * 
     * @param data 消息数据
     * @return 消息类型
     */
    public static String getMessageType(byte[] data) {
        try {
            // 这里应该根据实际的Protocol Buffers消息格式来解析
            // 暂时返回默认类型
            return "UNKNOWN";
        } catch (Exception e) {
            logger.warn("解析消息类型失败", e);
            return "UNKNOWN";
        }
    }

    // ========== 常量定义 ==========

    /**
     * 消息类型常量
     */
    public static class MessageType {
        public static final String LOGIN_REQUEST = "LOGIN_REQUEST";
        public static final String LOGIN_RESPONSE = "LOGIN_RESPONSE";
        public static final String HEARTBEAT = "HEARTBEAT";
        public static final String MATCH_REQUEST = "MATCH_REQUEST";
        public static final String MATCH_RESPONSE = "MATCH_RESPONSE";
        public static final String GAME_INPUT = "GAME_INPUT";
        public static final String GAME_STATE = "GAME_STATE";
        public static final String WEBRTC_SIGNAL = "WEBRTC_SIGNAL";
        public static final String ERROR_RESPONSE = "ERROR_RESPONSE";
    }
}