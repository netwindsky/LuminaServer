package com.whale.lumina.notification;

import com.whale.lumina.websocket.WebSocketManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 通知服务
 * 负责向用户发送各种类型的通知消息
 * 
 * @author Lumina Team
 */
@Service
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    @Autowired
    private WebSocketManager webSocketManager;
    
    /**
     * 通知类型枚举
     */
    public enum NotificationType {
        MATCH_FOUND,        // 匹配成功
        GAME_START,         // 游戏开始
        GAME_END,           // 游戏结束
        PLAYER_JOIN,        // 玩家加入
        PLAYER_LEAVE,       // 玩家离开
        ROOM_UPDATE,        // 房间更新
        SYSTEM_MESSAGE,     // 系统消息
        ERROR_MESSAGE       // 错误消息
    }
    
    /**
     * 发送通知给指定用户
     */
    public void sendNotification(String userId, NotificationType type, String message) {
        sendNotification(userId, type, message, null);
    }
    
    /**
     * 发送通知给指定用户（带额外数据）
     */
    public void sendNotification(String userId, NotificationType type, String message, Map<String, Object> data) {
        try {
            NotificationMessage notification = new NotificationMessage(type, message, data);
            String jsonMessage = notification.toJson();
            
            boolean sent = webSocketManager.sendMessageToUser(userId, jsonMessage);
            if (sent) {
                logger.debug("通知发送成功: {} -> {}", userId, type);
            } else {
                logger.warn("通知发送失败，用户可能不在线: {} -> {}", userId, type);
            }
        } catch (Exception e) {
            logger.error("发送通知异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 批量发送通知
     */
    public void sendNotificationToUsers(List<String> userIds, NotificationType type, String message) {
        sendNotificationToUsers(userIds, type, message, null);
    }
    
    /**
     * 批量发送通知（带额外数据）
     */
    public void sendNotificationToUsers(List<String> userIds, NotificationType type, String message, Map<String, Object> data) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        
        // 异步批量发送
        CompletableFuture.runAsync(() -> {
            NotificationMessage notification = new NotificationMessage(type, message, data);
            String jsonMessage = notification.toJson();
            
            int successCount = 0;
            for (String userId : userIds) {
                try {
                    if (webSocketManager.sendMessageToUser(userId, jsonMessage)) {
                        successCount++;
                    }
                } catch (Exception e) {
                    logger.error("发送通知给用户 {} 失败: {}", userId, e.getMessage());
                }
            }
            
            logger.info("批量通知发送完成: {}/{} 成功", successCount, userIds.size());
        });
    }
    
    /**
     * 发送匹配成功通知
     */
    public void notifyMatchFound(List<String> playerIds, String roomId) {
        Map<String, Object> data = Map.of("roomId", roomId);
        sendNotificationToUsers(playerIds, NotificationType.MATCH_FOUND, 
            "匹配成功！正在进入游戏房间...", data);
    }
    
    /**
     * 发送游戏开始通知
     */
    public void notifyGameStart(List<String> playerIds, String roomId) {
        Map<String, Object> data = Map.of("roomId", roomId);
        sendNotificationToUsers(playerIds, NotificationType.GAME_START, 
            "游戏开始！", data);
    }
    
    /**
     * 发送游戏结束通知
     */
    public void notifyGameEnd(List<String> playerIds, String roomId, Map<String, Object> gameResult) {
        Map<String, Object> data = Map.of(
            "roomId", roomId,
            "result", gameResult
        );
        sendNotificationToUsers(playerIds, NotificationType.GAME_END, 
            "游戏结束！", data);
    }
    
    /**
     * 发送玩家加入房间通知
     */
    public void notifyPlayerJoin(List<String> existingPlayerIds, String newPlayerId, String playerName) {
        Map<String, Object> data = Map.of(
            "playerId", newPlayerId,
            "playerName", playerName
        );
        sendNotificationToUsers(existingPlayerIds, NotificationType.PLAYER_JOIN, 
            playerName + " 加入了房间", data);
    }
    
    /**
     * 发送玩家离开房间通知
     */
    public void notifyPlayerLeave(List<String> remainingPlayerIds, String leftPlayerId, String playerName) {
        Map<String, Object> data = Map.of(
            "playerId", leftPlayerId,
            "playerName", playerName
        );
        sendNotificationToUsers(remainingPlayerIds, NotificationType.PLAYER_LEAVE, 
            playerName + " 离开了房间", data);
    }
    
    /**
     * 发送房间更新通知
     */
    public void notifyRoomUpdate(List<String> playerIds, String roomId, Map<String, Object> updateData) {
        Map<String, Object> data = Map.of(
            "roomId", roomId,
            "updates", updateData
        );
        sendNotificationToUsers(playerIds, NotificationType.ROOM_UPDATE, 
            "房间信息已更新", data);
    }
    
    /**
     * 发送系统消息
     */
    public void sendSystemMessage(String userId, String message) {
        sendNotification(userId, NotificationType.SYSTEM_MESSAGE, message);
    }
    
    /**
     * 发送错误消息
     */
    public void sendErrorMessage(String userId, String errorMessage) {
        sendNotification(userId, NotificationType.ERROR_MESSAGE, errorMessage);
    }
    
    /**
     * 广播系统公告
     */
    public void broadcastSystemAnnouncement(String announcement) {
        try {
            NotificationMessage notification = new NotificationMessage(
                NotificationType.SYSTEM_MESSAGE, announcement, null);
            webSocketManager.broadcastMessage(notification.toJson());
            logger.info("系统公告已广播: {}", announcement);
        } catch (Exception e) {
            logger.error("广播系统公告失败: {}", e.getMessage(), e);
        }
    }
}