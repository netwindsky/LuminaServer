package com.whale.lumina.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 通知消息封装类
 * 用于封装发送给客户端的通知消息
 * 
 * @author Lumina Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationMessage {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private NotificationService.NotificationType type;
    private String message;
    private String timestamp;
    private Map<String, Object> data;
    
    public NotificationMessage() {
        this.timestamp = LocalDateTime.now().format(formatter);
    }
    
    public NotificationMessage(NotificationService.NotificationType type, String message) {
        this();
        this.type = type;
        this.message = message;
    }
    
    public NotificationMessage(NotificationService.NotificationType type, String message, Map<String, Object> data) {
        this(type, message);
        this.data = data;
    }
    
    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化通知消息失败", e);
        }
    }
    
    /**
     * 从JSON字符串创建对象
     */
    public static NotificationMessage fromJson(String json) {
        try {
            return objectMapper.readValue(json, NotificationMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化通知消息失败", e);
        }
    }
    
    // Getters and Setters
    public NotificationService.NotificationType getType() {
        return type;
    }
    
    public void setType(NotificationService.NotificationType type) {
        this.type = type;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    @Override
    public String toString() {
        return "NotificationMessage{" +
                "type=" + type +
                ", message='" + message + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", data=" + data +
                '}';
    }
}