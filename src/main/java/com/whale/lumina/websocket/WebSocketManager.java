package com.whale.lumina.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket连接管理器
 * 负责管理所有WebSocket连接的生命周期
 * 
 * @author Lumina Team
 */
@Component
public class WebSocketManager extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketManager.class);
    
    // 存储所有活跃的WebSocket会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // 存储用户ID到会话ID的映射
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        logger.info("WebSocket连接建立: {}", sessionId);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        
        // 清理用户映射
        userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
        
        logger.info("WebSocket连接关闭: {}, 状态: {}", sessionId, status);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.debug("收到WebSocket消息: {}", message.getPayload());
        // 这里可以添加消息处理逻辑
    }
    
    /**
     * 绑定用户到WebSocket会话
     */
    public void bindUser(String userId, String sessionId) {
        userSessionMap.put(userId, sessionId);
        logger.debug("用户绑定到WebSocket会话: {} -> {}", userId, sessionId);
    }
    
    /**
     * 解绑用户
     */
    public void unbindUser(String userId) {
        userSessionMap.remove(userId);
        logger.debug("用户解绑WebSocket会话: {}", userId);
    }
    
    /**
     * 向指定用户发送消息
     */
    public boolean sendMessageToUser(String userId, String message) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId != null) {
            return sendMessageToSession(sessionId, message);
        }
        return false;
    }
    
    /**
     * 向指定会话发送消息
     */
    public boolean sendMessageToSession(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                return true;
            } catch (Exception e) {
                logger.error("发送WebSocket消息失败: {}", e.getMessage());
            }
        }
        return false;
    }
    
    /**
     * 广播消息给所有连接的用户
     */
    public void broadcastMessage(String message) {
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (Exception e) {
                    logger.error("广播消息失败: {}", e.getMessage());
                }
            }
        });
    }
    
    /**
     * 获取在线用户数量
     */
    public int getOnlineUserCount() {
        return userSessionMap.size();
    }
    
    /**
     * 获取活跃连接数量
     */
    public int getActiveConnectionCount() {
        return sessions.size();
    }
    
    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String userId) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId != null) {
            WebSocketSession session = sessions.get(sessionId);
            return session != null && session.isOpen();
        }
        return false;
    }
    
    /**
     * 关闭指定用户的连接
     */
    public void closeUserConnection(String userId) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId != null) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                } catch (Exception e) {
                    logger.error("关闭用户连接失败: {}", e.getMessage());
                }
            }
        }
    }
}