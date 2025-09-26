package com.whale.lumina.gateway;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import com.whale.lumina.common.TimeUtils;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关控制器
 * 
 * 负责处理网关层的所有业务逻辑，包括消息路由、会话管理和事件处理
 * 
 * @author Lumina Team
 */
@Component
public class GatewayController {

    private static final Logger logger = LoggerFactory.getLogger(GatewayController.class);

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // 消息统计
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong processedMessages = new AtomicLong(0);
    private final AtomicLong errorMessages = new AtomicLong(0);

    // ========== 会话事件处理 ==========

    /**
     * 处理会话打开事件
     * 
     * @param session IO会话
     */
    public void onSessionOpened(IoSession session) {
        long sessionId = session.getId();
        String remoteAddress = session.getRemoteAddress().toString();
        
        logger.info("会话打开: sessionId={}, remoteAddress={}", sessionId, remoteAddress);
        
        try {
            // 发送欢迎消息
            sendWelcomeMessage(session);
            
            // 发布会话打开事件
            eventPublisher.publishEvent(new SessionOpenedEvent(sessionId, remoteAddress));
            
        } catch (Exception e) {
            logger.error("处理会话打开事件失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 处理会话关闭事件
     * 
     * @param session IO会话
     */
    public void onSessionClosed(IoSession session) {
        long sessionId = session.getId();
        
        logger.info("会话关闭: sessionId={}", sessionId);
        
        try {
            // 获取会话绑定的玩家
            String playerId = sessionManager.getSessionPlayer(sessionId);
            
            // 发布会话关闭事件
            eventPublisher.publishEvent(new SessionClosedEvent(sessionId, playerId));
            
            // 如果有绑定玩家，处理玩家下线逻辑
            if (playerId != null) {
                handlePlayerOffline(sessionId, playerId);
            }
            
        } catch (Exception e) {
            logger.error("处理会话关闭事件失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 处理会话空闲事件
     * 
     * @param session IO会话
     * @param status 空闲状态
     */
    public void onSessionIdle(IoSession session, IdleStatus status) {
        long sessionId = session.getId();
        
        logger.debug("会话空闲: sessionId={}, status={}", sessionId, status);
        
        try {
            // 发送心跳请求
            if (status == IdleStatus.READER_IDLE || status == IdleStatus.BOTH_IDLE) {
                sendHeartbeatRequest(session);
            }
            
            // 发布会话空闲事件
            eventPublisher.publishEvent(new SessionIdleEvent(sessionId, status));
            
        } catch (Exception e) {
            logger.error("处理会话空闲事件失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 处理会话异常事件
     * 
     * @param session IO会话
     * @param cause 异常原因
     */
    public void onSessionException(IoSession session, Throwable cause) {
        long sessionId = session.getId();
        
        logger.error("会话异常: sessionId={}", sessionId, cause);
        
        try {
            // 发布会话异常事件
            eventPublisher.publishEvent(new SessionExceptionEvent(sessionId, cause));
            
            // 根据异常类型决定是否关闭会话
            if (shouldCloseSessionOnException(cause)) {
                logger.warn("严重异常，关闭会话: sessionId={}", sessionId);
                session.closeNow();
            }
            
        } catch (Exception e) {
            logger.error("处理会话异常事件失败: sessionId={}", sessionId, e);
        }
    }

    // ========== 消息处理 ==========

    /**
     * 处理接收到的消息
     * 
     * @param session IO会话
     * @param message 消息对象
     */
    public void handleMessage(IoSession session, Object message) {
        long sessionId = session.getId();
        totalMessages.incrementAndGet();
        
        try {
            // 更新会话活跃时间
            SessionManager.SessionAttributes attributes = sessionManager.getSessionAttributes(sessionId);
            if (attributes != null) {
                attributes.updateLastActiveTime();
            }
            
            // 处理不同类型的消息
            if (message instanceof MessageCodec.GameMessage) {
                handleGameMessage(session, (MessageCodec.GameMessage) message);
            } else {
                logger.warn("未知消息类型: sessionId={}, messageType={}", 
                           sessionId, message.getClass().getSimpleName());
                sendErrorResponse(session, ErrorCodes.GATEWAY_UNSUPPORTED_MESSAGE_TYPE, "不支持的消息类型");
            }
            
            processedMessages.incrementAndGet();
            
        } catch (GameException e) {
            logger.error("处理游戏消息失败: sessionId={}, errorCode={}", sessionId, e.getErrorCode(), e);
            sendErrorResponse(session, e.getErrorCode(), e.getMessage());
            errorMessages.incrementAndGet();
        } catch (Exception e) {
            logger.error("处理消息时发生未知异常: sessionId={}", sessionId, e);
            sendErrorResponse(session, ErrorCodes.GATEWAY_MESSAGE_PROCESS_FAILED, "消息处理失败");
            errorMessages.incrementAndGet();
        }
    }

    /**
     * 处理游戏消息
     * 
     * @param session IO会话
     * @param gameMessage 游戏消息
     */
    private void handleGameMessage(IoSession session, MessageCodec.GameMessage gameMessage) {
        long sessionId = session.getId();
        
        try {
            // 验证消息
            if (!gameMessage.isValid()) {
                throw new GameException(ErrorCodes.GATEWAY_INVALID_MESSAGE_FORMAT);
            }
            
            // 获取消息类型（这里需要根据实际的Protocol Buffers定义来解析）
            String messageType = MessageCodec.getMessageType(gameMessage.getData());
            
            logger.debug("处理游戏消息: sessionId={}, messageType={}, length={}", 
                        sessionId, messageType, gameMessage.getLength());
            
            // 根据消息类型路由到相应的处理器
            switch (messageType) {
                case MessageCodec.MessageType.LOGIN_REQUEST:
                    handleLoginRequest(session, gameMessage);
                    break;
                case MessageCodec.MessageType.HEARTBEAT:
                    handleHeartbeat(session, gameMessage);
                    break;
                case MessageCodec.MessageType.MATCH_REQUEST:
                    handleMatchRequest(session, gameMessage);
                    break;
                case MessageCodec.MessageType.GAME_INPUT:
                    handleGameInput(session, gameMessage);
                    break;
                case MessageCodec.MessageType.WEBRTC_SIGNAL:
                    handleWebRTCSignal(session, gameMessage);
                    break;
                default:
                    logger.warn("未处理的消息类型: sessionId={}, messageType={}", sessionId, messageType);
                    sendErrorResponse(session, ErrorCodes.GATEWAY_UNSUPPORTED_MESSAGE_TYPE, 
                                    "不支持的消息类型: " + messageType);
            }
            
        } catch (Exception e) {
            logger.error("处理游戏消息失败: sessionId={}", sessionId, e);
            throw new GameException(ErrorCodes.GATEWAY_MESSAGE_PROCESS_FAILED, e);
        }
    }

    // ========== 具体消息处理器 ==========

    /**
     * 处理登录请求
     */
    private void handleLoginRequest(IoSession session, MessageCodec.GameMessage message) {
        long sessionId = session.getId();
        
        try {
            // 这里应该解析实际的登录请求Protocol Buffers消息
            // 暂时使用模拟逻辑
            logger.info("处理登录请求: sessionId={}", sessionId);
            
            // 发布登录请求事件，由Auth模块处理
            eventPublisher.publishEvent(new LoginRequestEvent(sessionId, message.getData()));
            
        } catch (Exception e) {
            logger.error("处理登录请求失败: sessionId={}", sessionId, e);
            sendErrorResponse(session, ErrorCodes.AUTH_LOGIN_FAILED, "登录处理失败");
        }
    }

    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(IoSession session, MessageCodec.GameMessage message) {
        long sessionId = session.getId();
        
        try {
            logger.debug("处理心跳消息: sessionId={}", sessionId);
            
            // 发送心跳响应
            sendHeartbeatResponse(session);
            
        } catch (Exception e) {
            logger.error("处理心跳消息失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 处理匹配请求
     */
    private void handleMatchRequest(IoSession session, MessageCodec.GameMessage message) {
        long sessionId = session.getId();
        String playerId = sessionManager.getSessionPlayer(sessionId);
        
        try {
            if (playerId == null) {
                throw new GameException(ErrorCodes.AUTH_NOT_AUTHENTICATED);
            }
            
            logger.info("处理匹配请求: sessionId={}, playerId={}", sessionId, playerId);
            
            // 发布匹配请求事件，由Matchmaking模块处理
            eventPublisher.publishEvent(new MatchRequestEvent(sessionId, playerId, message.getData()));
            
        } catch (Exception e) {
            logger.error("处理匹配请求失败: sessionId={}", sessionId, e);
            sendErrorResponse(session, ErrorCodes.MATCH_REQUEST_FAILED, "匹配请求处理失败");
        }
    }

    /**
     * 处理游戏输入
     */
    private void handleGameInput(IoSession session, MessageCodec.GameMessage message) {
        long sessionId = session.getId();
        String playerId = sessionManager.getSessionPlayer(sessionId);
        
        try {
            if (playerId == null) {
                throw new GameException(ErrorCodes.AUTH_NOT_AUTHENTICATED);
            }
            
            logger.debug("处理游戏输入: sessionId={}, playerId={}", sessionId, playerId);
            
            // 发布游戏输入事件，由Room模块处理
            eventPublisher.publishEvent(new GameInputEvent(sessionId, playerId, message.getData()));
            
        } catch (Exception e) {
            logger.error("处理游戏输入失败: sessionId={}", sessionId, e);
            sendErrorResponse(session, ErrorCodes.ROOM_INPUT_PROCESS_FAILED, "游戏输入处理失败");
        }
    }

    /**
     * 处理WebRTC信令
     */
    private void handleWebRTCSignal(IoSession session, MessageCodec.GameMessage message) {
        long sessionId = session.getId();
        String playerId = sessionManager.getSessionPlayer(sessionId);
        
        try {
            if (playerId == null) {
                throw new GameException(ErrorCodes.AUTH_NOT_AUTHENTICATED);
            }
            
            logger.debug("处理WebRTC信令: sessionId={}, playerId={}", sessionId, playerId);
            
            // 发布WebRTC信令事件，由Signaling模块处理
            eventPublisher.publishEvent(new WebRTCSignalEvent(sessionId, playerId, message.getData()));
            
        } catch (Exception e) {
            logger.error("处理WebRTC信令失败: sessionId={}", sessionId, e);
            sendErrorResponse(session, ErrorCodes.WEBRTC_SIGNAL_FAILED, "WebRTC信令处理失败");
        }
    }

    // ========== 消息发送 ==========

    /**
     * 发送欢迎消息
     */
    private void sendWelcomeMessage(IoSession session) {
        try {
            String welcomeMsg = String.format(
                "{\"type\":\"WELCOME\",\"timestamp\":%d,\"message\":\"欢迎连接到Lumina游戏服务器\"}", 
                System.currentTimeMillis()
            );
            session.write(MessageCodec.createStringMessage(welcomeMsg));
        } catch (Exception e) {
            logger.error("发送欢迎消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 发送心跳请求
     */
    private void sendHeartbeatRequest(IoSession session) {
        try {
            String heartbeatMsg = String.format(
                "{\"type\":\"HEARTBEAT_REQUEST\",\"timestamp\":%d}", 
                System.currentTimeMillis()
            );
            session.write(MessageCodec.createStringMessage(heartbeatMsg));
        } catch (Exception e) {
            logger.error("发送心跳请求失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 发送心跳响应
     */
    private void sendHeartbeatResponse(IoSession session) {
        try {
            String heartbeatMsg = String.format(
                "{\"type\":\"HEARTBEAT_RESPONSE\",\"timestamp\":%d}", 
                System.currentTimeMillis()
            );
            session.write(MessageCodec.createStringMessage(heartbeatMsg));
        } catch (Exception e) {
            logger.error("发送心跳响应失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 发送错误响应
     * 
     * @param session IO会话
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    public void sendErrorResponse(IoSession session, String errorCode, String errorMessage) {
        try {
            byte[] errorData = MessageCodec.createErrorMessage(errorCode, errorMessage);
            session.write(errorData);
            logger.debug("发送错误响应: sessionId={}, errorCode={}", session.getId(), errorCode);
        } catch (Exception e) {
            logger.error("发送错误响应失败: sessionId={}, errorCode={}", session.getId(), errorCode, e);
        }
    }

    // ========== 业务逻辑处理 ==========

    /**
     * 处理玩家下线
     */
    private void handlePlayerOffline(long sessionId, String playerId) {
        try {
            logger.info("处理玩家下线: sessionId={}, playerId={}", sessionId, playerId);
            
            // 发布玩家下线事件
            eventPublisher.publishEvent(new PlayerOfflineEvent(sessionId, playerId));
            
        } catch (Exception e) {
            logger.error("处理玩家下线失败: sessionId={}, playerId={}", sessionId, playerId, e);
        }
    }

    /**
     * 判断是否应该因异常关闭会话
     */
    private boolean shouldCloseSessionOnException(Throwable cause) {
        return cause instanceof java.io.IOException || 
               cause instanceof java.net.SocketException ||
               (cause instanceof GameException && 
                ((GameException) cause).getErrorCode().startsWith("SYSTEM"));
    }

    // ========== 统计信息 ==========

    /**
     * 获取网关统计信息
     * 
     * @return 统计信息
     */
    public GatewayStats getGatewayStats() {
        return new GatewayStats(
            totalMessages.get(),
            processedMessages.get(),
            errorMessages.get(),
            sessionManager.getSessionStats()
        );
    }

    // ========== 事件类定义 ==========

    public static class SessionOpenedEvent {
        private final long sessionId;
        private final String remoteAddress;
        private final long timestamp;

        public SessionOpenedEvent(long sessionId, String remoteAddress) {
            this.sessionId = sessionId;
            this.remoteAddress = remoteAddress;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public String getRemoteAddress() { return remoteAddress; }
        public long getTimestamp() { return timestamp; }
    }

    public static class SessionClosedEvent {
        private final long sessionId;
        private final String playerId;
        private final long timestamp;

        public SessionClosedEvent(long sessionId, String playerId) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public String getPlayerId() { return playerId; }
        public long getTimestamp() { return timestamp; }
    }

    public static class SessionIdleEvent {
        private final long sessionId;
        private final IdleStatus status;
        private final long timestamp;

        public SessionIdleEvent(long sessionId, IdleStatus status) {
            this.sessionId = sessionId;
            this.status = status;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public IdleStatus getStatus() { return status; }
        public long getTimestamp() { return timestamp; }
    }

    public static class SessionExceptionEvent {
        private final long sessionId;
        private final Throwable cause;
        private final long timestamp;

        public SessionExceptionEvent(long sessionId, Throwable cause) {
            this.sessionId = sessionId;
            this.cause = cause;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public Throwable getCause() { return cause; }
        public long getTimestamp() { return timestamp; }
    }

    public static class LoginRequestEvent {
        private final long sessionId;
        private final byte[] data;
        private final long timestamp;

        public LoginRequestEvent(long sessionId, byte[] data) {
            this.sessionId = sessionId;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public byte[] getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    public static class MatchRequestEvent {
        private final long sessionId;
        private final String playerId;
        private final byte[] data;
        private final long timestamp;

        public MatchRequestEvent(long sessionId, String playerId, byte[] data) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public String getPlayerId() { return playerId; }
        public byte[] getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    public static class GameInputEvent {
        private final long sessionId;
        private final String playerId;
        private final byte[] data;
        private final long timestamp;

        public GameInputEvent(long sessionId, String playerId, byte[] data) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public String getPlayerId() { return playerId; }
        public byte[] getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    public static class WebRTCSignalEvent {
        private final long sessionId;
        private final String playerId;
        private final byte[] data;
        private final long timestamp;

        public WebRTCSignalEvent(long sessionId, String playerId, byte[] data) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public String getPlayerId() { return playerId; }
        public byte[] getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    public static class PlayerOfflineEvent {
        private final long sessionId;
        private final String playerId;
        private final long timestamp;

        public PlayerOfflineEvent(long sessionId, String playerId) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public long getSessionId() { return sessionId; }
        public String getPlayerId() { return playerId; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 网关统计信息
     */
    public static class GatewayStats {
        private final long totalMessages;
        private final long processedMessages;
        private final long errorMessages;
        private final SessionManager.SessionStats sessionStats;

        public GatewayStats(long totalMessages, long processedMessages, long errorMessages, 
                          SessionManager.SessionStats sessionStats) {
            this.totalMessages = totalMessages;
            this.processedMessages = processedMessages;
            this.errorMessages = errorMessages;
            this.sessionStats = sessionStats;
        }

        // Getters
        public long getTotalMessages() { return totalMessages; }
        public long getProcessedMessages() { return processedMessages; }
        public long getErrorMessages() { return errorMessages; }
        public SessionManager.SessionStats getSessionStats() { return sessionStats; }
        public double getSuccessRate() { 
            return totalMessages > 0 ? (double) processedMessages / totalMessages : 0.0; 
        }
        public double getErrorRate() { 
            return totalMessages > 0 ? (double) errorMessages / totalMessages : 0.0; 
        }
    }
}