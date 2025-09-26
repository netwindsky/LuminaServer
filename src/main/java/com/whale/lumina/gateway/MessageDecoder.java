package com.whale.lumina.gateway;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息解码器
 */
public class MessageDecoder extends CumulativeProtocolDecoder {
    private static final Logger logger = LoggerFactory.getLogger(MessageDecoder.class);
    private static final int HEADER_LENGTH = 4;
    private static final int MIN_MESSAGE_LENGTH = 1;
    private static final int MAX_MESSAGE_LENGTH = 1024 * 1024; // 1MB

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