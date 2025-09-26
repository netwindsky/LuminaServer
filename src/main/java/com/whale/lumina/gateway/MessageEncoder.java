package com.whale.lumina.gateway;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息编码器
 */
public class MessageEncoder implements ProtocolEncoder {
    private static final Logger logger = LoggerFactory.getLogger(MessageEncoder.class);
    private static final int HEADER_LENGTH = 4;
    private static final int MAX_MESSAGE_LENGTH = 1024 * 1024; // 1MB

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