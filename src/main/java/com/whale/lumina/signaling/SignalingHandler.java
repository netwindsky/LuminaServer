package com.whale.lumina.signaling;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebRTC信令处理器
 * 
 * 负责处理WebRTC相关的信令消息，包括offer、answer、candidate等
 * 
 * @author Lumina Team
 */
@Component
@SuppressWarnings("unchecked")
public class SignalingHandler {

    private static final Logger logger = LoggerFactory.getLogger(SignalingHandler.class);

    @Autowired
    private SignalingStore signalingStore;

    // 信令会话管理
    private final Map<String, SignalingSession> activeSessions;
    private final AtomicLong nextSessionId;

    // 统计信息
    private final AtomicLong totalSignalingMessages;
    private final AtomicLong totalOffers;
    private final AtomicLong totalAnswers;
    private final AtomicLong totalCandidates;

    /**
     * 构造函数
     */
    public SignalingHandler() {
        this.activeSessions = new ConcurrentHashMap<>();
        this.nextSessionId = new AtomicLong(1);
        this.totalSignalingMessages = new AtomicLong(0);
        this.totalOffers = new AtomicLong(0);
        this.totalAnswers = new AtomicLong(0);
        this.totalCandidates = new AtomicLong(0);
    }

    // ========== 信令会话管理 ==========
    /**
     * 创建信令会话
     *
     * @param roomId 房间ID
     * @param participants 参与者列表
     * @return 会话ID
     * @throws GameException 创建失败时抛出
     */
    public String createSignalingSession(String roomId, List<String> participants) throws GameException {
        if (roomId == null || roomId.isEmpty()) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "房间ID不能为空");
        }

        if (participants == null || participants.isEmpty()) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "参与者列表不能为空");
        }

        String sessionId = "signaling_" + nextSessionId.getAndIncrement();

        try {
            SignalingSession session = new SignalingSession(sessionId, roomId, participants.get(0));
            // 添加所有参与者
            for (String participant : participants) {
                session.addParticipant(participant);
            }
            activeSessions.put(sessionId, session);

            // 保存到存储
            signalingStore.saveSession(session);

            logger.info("信令会话已创建: sessionId={}, roomId={}, participants={}",
                       sessionId, roomId, participants.size());

            return sessionId;

        } catch (Exception e) {
            logger.error("创建信令会话失败: roomId={}, participants={}", roomId, participants, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "创建信令会话失败: " + e.getMessage());
        }
    }


    /**
     * 结束信令会话
     * 
     * @param sessionId 会话ID
     * @throws GameException 结束失败时抛出
     */
    public void endSignalingSession(String sessionId) throws GameException {
        SignalingSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "信令会话不存在: " + sessionId);
        }

        try {
            session.end();
            activeSessions.remove(sessionId);
            
            // 从存储中删除
            signalingStore.removeSession(sessionId);
            
            logger.info("信令会话已结束: sessionId={}, duration={}ms", 
                       sessionId, session.getDuration());
            
        } catch (Exception e) {
            logger.error("结束信令会话失败: sessionId={}", sessionId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "结束信令会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取信令会话
     * 
     * @param sessionId 会话ID
     * @return 信令会话
     */
    public SignalingSession getSignalingSession(String sessionId) {
        SignalingSession session = activeSessions.get(sessionId);
        if (session == null) {
            // 尝试从存储中加载
            session = signalingStore.getSession(sessionId);
            if (session != null) {
                activeSessions.put(sessionId, session);
            }
        }
        return session;
    }

    /**
     * 根据房间ID获取信令会话
     * 
     * @param roomId 房间ID
     * @return 信令会话
     */
    public SignalingSession getSignalingSessionByRoom(String roomId) {
        for (SignalingSession session : activeSessions.values()) {
            if (session.getRoomId().equals(roomId)) {
                return session;
            }
        }
        
        // 尝试从存储中查找
        return signalingStore.getSessionByRoom(roomId);
    }

    // ========== 信令消息处理 ==========
    /**
     * 处理Offer消息
     *
     * @param sessionId 会话ID
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param offer Offer数据
     * @throws GameException 处理失败时抛出
     */
    public void handleOffer(String sessionId, String fromUserId, String toUserId,
                           OfferMessage offer) throws GameException {
        SignalingSession session = getSignalingSession(sessionId);
        if (session == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "信令会话不存在: " + sessionId);
        }

        try {
            // 验证参与者
            if (!session.hasParticipant(fromUserId) || !session.hasParticipant(toUserId)) {
                throw new GameException(ErrorCodes.SYSTEM_ERROR, "参与者不在会话中");
            }

            // 创建信令消息
            Map<String, Object> offerData = new HashMap<>();
            offerData.put("sdp", offer.getSdp());
            offerData.put("type", offer.getType());
            SignalingMessage message = new SignalingMessage(
                sessionId, SignalingMessage.Type.OFFER, fromUserId, toUserId, offerData);
            // 类型已在构造函数中设置，无需再次设置

            // 处理消息
            session.addMessage(message);

            // 转发给目标用户
            forwardMessage(sessionId, message);

            totalSignalingMessages.incrementAndGet();
            totalOffers.incrementAndGet();

            logger.debug("处理Offer消息: sessionId={}, from={}, to={}",
                        sessionId, fromUserId, toUserId);

        } catch (Exception e) {
            logger.error("处理Offer消息失败: sessionId={}, from={}, to={}",
                        sessionId, fromUserId, toUserId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "处理Offer消息失败: " + e.getMessage());
        }
    }


    /**
     * 处理Answer消息
     * 
     * @param sessionId 会话ID
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param answer Answer数据
     * @throws GameException 处理失败时抛出
     */
    public void handleAnswer(String sessionId, String fromUserId, String toUserId, 
                            AnswerMessage answer) throws GameException {
        SignalingSession session = getSignalingSession(sessionId);
        if (session == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "信令会话不存在: " + sessionId);
        }

        try {
            // 验证参与者
            if (!session.hasParticipant(fromUserId) || !session.hasParticipant(toUserId)) {
                throw new GameException(ErrorCodes.SYSTEM_ERROR, "参与者不在会话中");
            }

            // 创建信令消息
            Map<String, Object> answerData = new HashMap<>();
            answerData.put("sdp", answer.getSdp());
            answerData.put("type", answer.getType());
            SignalingMessage message = new SignalingMessage(
                sessionId, SignalingMessage.Type.ANSWER, fromUserId, toUserId, answerData);
            // 类型已在构造函数中设置，无需再次设置
            
            // 处理消息
            session.addMessage(message);
            
            // 转发给目标用户
            forwardMessage(sessionId, message);
            
            totalSignalingMessages.incrementAndGet();
            totalAnswers.incrementAndGet();
            
            logger.debug("处理Answer消息: sessionId={}, from={}, to={}", 
                        sessionId, fromUserId, toUserId);
            
        } catch (Exception e) {
            logger.error("处理Answer消息失败: sessionId={}, from={}, to={}", 
                        sessionId, fromUserId, toUserId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "处理Answer消息失败: " + e.getMessage());
        }
    }


    /**
     * 处理ICE Candidate消息
     * 
     * @param sessionId 会话ID
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param candidate Candidate数据
     * @throws GameException 处理失败时抛出
     */
    public void handleCandidate(String sessionId, String fromUserId, String toUserId, 
                               CandidateMessage candidate) throws GameException {
        SignalingSession session = getSignalingSession(sessionId);
        if (session == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "信令会话不存在: " + sessionId);
        }

        try {
            // 验证参与者
            if (!session.hasParticipant(fromUserId) || !session.hasParticipant(toUserId)) {
                throw new GameException(ErrorCodes.SYSTEM_ERROR, "参与者不在会话中");
            }

            // 创建信令消息
            Map<String, Object> candidateData = new HashMap<>();
            candidateData.put("candidate", candidate.getCandidate());
            candidateData.put("sdpMid", candidate.getSdpMid());
            candidateData.put("sdpMLineIndex", candidate.getSdpMLineIndex());
            SignalingMessage message = new SignalingMessage(
                sessionId, SignalingMessage.Type.CANDIDATE, fromUserId, toUserId, candidateData);
            // 类型已在构造函数中设置，无需再次设置
            
            // 处理消息
            session.addMessage(message);
            
            // 转发给目标用户
            forwardMessage(sessionId, message);
            
            totalSignalingMessages.incrementAndGet();
            totalCandidates.incrementAndGet();
            
            logger.debug("处理Candidate消息: sessionId={}, from={}, to={}", 
                        sessionId, fromUserId, toUserId);
            
        } catch (Exception e) {
            logger.error("处理Candidate消息失败: sessionId={}, from={}, to={}", 
                        sessionId, fromUserId, toUserId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "处理Candidate消息失败: " + e.getMessage());
        }
    }

    /**
     * 处理自定义信令消息
     * 
     * @param sessionId 会话ID
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID（可为null表示广播）
     * @param customData 自定义数据
     * @throws GameException 处理失败时抛出
     */
    public void handleCustomMessage(String sessionId, String fromUserId, String toUserId, 
                                  Object customData) throws GameException {
        SignalingSession session = getSignalingSession(sessionId);
        if (session == null) {
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "信令会话不存在: " + sessionId);
        }

        try {
            // 验证发送者
            if (!session.hasParticipant(fromUserId)) {
                throw new GameException(ErrorCodes.SYSTEM_ERROR, "发送者不在会话中");
            }

            // 如果指定了接收者，验证接收者
            if (toUserId != null && !session.hasParticipant(toUserId)) {
                throw new GameException(ErrorCodes.SYSTEM_ERROR, "接收者不在会话中");
            }

            // 创建信令消息
            Map<String, Object> dataMap = new HashMap<>();
            if (customData instanceof Map) {
                dataMap.putAll((Map<? extends String, ?>) customData);
            } else {
                dataMap.put("data", customData);
            }
            SignalingMessage message = new SignalingMessage(
                sessionId, SignalingMessage.Type.CUSTOM, fromUserId, toUserId, dataMap);
            // 类型已在构造函数中设置，无需再次设置
            
            // 处理消息
            session.addMessage(message);
            
            // 转发消息
            if (toUserId != null) {
                // 单播
                forwardMessage(sessionId, message);
            } else {
                // 广播给所有参与者
                for (String participantId : session.getParticipants()) {
                    if (!participantId.equals(message.getFromUserId())) {
                        // 这里应该通过WebSocket或其他方式发送消息
                        logger.debug("广播消息给参与者: sessionId={}, participantId={}, messageType={}", 
                                   sessionId, participantId, message.getType().name());
                    }
                }
            }
            
            totalSignalingMessages.incrementAndGet();
            
            logger.debug("处理自定义消息: sessionId={}, from={}, to={}", 
                        sessionId, fromUserId, toUserId);
            
        } catch (Exception e) {
            logger.error("处理自定义消息失败: sessionId={}, from={}, to={}", 
                        sessionId, fromUserId, toUserId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "处理自定义消息失败: " + e.getMessage());
        }
    }

    // ========== 消息转发 ==========

    /**
     * 转发消息给指定用户
     * 
     * @param sessionId 会话ID
     * @param message 信令消息
     */
    private void forwardMessage(String sessionId, SignalingMessage message) {
        try {
            // 这里应该通过WebSocket或其他方式发送给客户端
            // 暂时只记录日志
            logger.debug("转发信令消息: sessionId={}, type={}, from={}, to={}", 
                        sessionId, message.getType(), message.getFromUserId(), message.getToUserId());
            
            // 保存消息到存储
            signalingStore.saveMessage(sessionId, message);
            
        } catch (Exception e) {
            logger.error("转发消息失败: sessionId={}, messageId={}", sessionId, message.getId(), e);
        }
    }

    /**
     * 广播消息给会话中的其他参与者
     * 
     * @param sessionId 会话ID
     * @param message 信令消息
     */
    private void broadcastMessage(String sessionId, SignalingMessage message) {
        SignalingSession session = activeSessions.get(sessionId);
        if (session == null) {
            return;
        }

        try {
            for (String participantId : session.getParticipants()) {
                if (!participantId.equals(message.getFromUserId())) {
                    // 创建针对每个参与者的消息副本
                    SignalingMessage broadcastMessage = new SignalingMessage(
                        sessionId, message.getType(), message.getFromUserId(), participantId, message.getData());
                    
                    forwardMessage(sessionId, broadcastMessage);
                }
            }
            
        } catch (Exception e) {
            logger.error("广播消息失败: sessionId={}, messageId={}", sessionId, message.getId(), e);
        }
    }

    // ========== 会话状态查询 ==========

    /**
     * 获取会话中的所有参与者
     * 
     * @param sessionId 会话ID
     * @return 参与者列表
     * @throws GameException 查询失败时抛出
     */
    public List<String> getSessionParticipants(String sessionId) throws GameException {
        SignalingSession session = getSignalingSession(sessionId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "信令会话不存在: " + sessionId);
        }
        
        return new ArrayList<>(session.getParticipants());
    }

    /**
     * 获取会话的消息历史
     * 
     * @param sessionId 会话ID
     * @param limit 限制数量
     * @return 消息列表
     * @throws GameException 查询失败时抛出
     */
    public List<SignalingMessage> getSessionMessages(String sessionId, int limit) throws GameException {
        SignalingSession session = getSignalingSession(sessionId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "信令会话不存在: " + sessionId);
        }
        
        return session.getRecentMessages(limit);
    }

    /**
     * 检查用户是否在会话中
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 是否在会话中
     */
    public boolean isUserInSession(String sessionId, String userId) {
        SignalingSession session = getSignalingSession(sessionId);
        return session != null && session.hasParticipant(userId);
    }

    // ========== 统计信息 ==========

    /**
     * 获取活跃连接数量
     * 
     * @return 活跃连接数量
     */
    public int getActiveConnections() {
        return activeSessions.values().stream()
                .mapToInt(session -> session.getParticipants().size())
                .sum();
    }

    /**
     * 获取活跃会话数量
     * 
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * 获取总信令消息数量
     * 
     * @return 总消息数量
     */
    public long getTotalSignalingMessages() {
        return totalSignalingMessages.get();
    }

    /**
     * 获取总Offer数量
     * 
     * @return 总Offer数量
     */
    public long getTotalOffers() {
        return totalOffers.get();
    }

    /**
     * 获取总Answer数量
     * 
     * @return 总Answer数量
     */
    public long getTotalAnswers() {
        return totalAnswers.get();
    }

    /**
     * 获取总Candidate数量
     * 
     * @return 总Candidate数量
     */
    public long getTotalCandidates() {
        return totalCandidates.get();
    }

    /**
     * 获取信令统计信息
     * 
     * @return 统计信息
     */
    public SignalingStatistics getSignalingStatistics() {
        return new SignalingStatistics(
            getActiveSessionCount(),
            getTotalSignalingMessages(),
            getTotalOffers(),
            getTotalAnswers(),
            getTotalCandidates()
        );
    }

    // ========== 清理和维护 ==========

    /**
     * 清理过期的会话
     */
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredSessions = new ArrayList<>();
        
        for (SignalingSession session : activeSessions.values()) {
            if (session.isExpired(currentTime)) {
                expiredSessions.add(session.getSessionId());
            }
        }
        
        for (String sessionId : expiredSessions) {
            try {
                endSignalingSession(sessionId);
                logger.info("清理过期信令会话: sessionId={}", sessionId);
            } catch (Exception e) {
                logger.error("清理过期会话失败: sessionId={}", sessionId, e);
            }
        }
    }

    // ========== 消息数据类 ==========

    /**
     * Offer消息数据
     */
    public static class OfferMessage {
        private final String sdp;
        private final String type;

        public OfferMessage(String sdp, String type) {
            this.sdp = sdp;
            this.type = type;
        }

        public String getSdp() { return sdp; }
        public String getType() { return type; }
    }

    /**
     * Answer消息数据
     */
    public static class AnswerMessage {
        private final String sdp;
        private final String type;

        public AnswerMessage(String sdp, String type) {
            this.sdp = sdp;
            this.type = type;
        }

        public String getSdp() { return sdp; }
        public String getType() { return type; }
    }

    /**
     * Candidate消息数据
     */
    public static class CandidateMessage {
        private final String candidate;
        private final String sdpMid;
        private final int sdpMLineIndex;

        public CandidateMessage(String candidate, String sdpMid, int sdpMLineIndex) {
            this.candidate = candidate;
            this.sdpMid = sdpMid;
            this.sdpMLineIndex = sdpMLineIndex;
        }

        public String getCandidate() { return candidate; }
        public String getSdpMid() { return sdpMid; }
        public int getSdpMLineIndex() { return sdpMLineIndex; }
    }

    /**
     * 信令统计信息
     */
    public static class SignalingStatistics {
        private final int activeSessions;
        private final long totalMessages;
        private final long totalOffers;
        private final long totalAnswers;
        private final long totalCandidates;

        public SignalingStatistics(int activeSessions, long totalMessages, long totalOffers, 
                                 long totalAnswers, long totalCandidates) {
            this.activeSessions = activeSessions;
            this.totalMessages = totalMessages;
            this.totalOffers = totalOffers;
            this.totalAnswers = totalAnswers;
            this.totalCandidates = totalCandidates;
        }

        public int getActiveSessions() { return activeSessions; }
        public long getTotalMessages() { return totalMessages; }
        public long getTotalOffers() { return totalOffers; }
        public long getTotalAnswers() { return totalAnswers; }
        public long getTotalCandidates() { return totalCandidates; }

        @Override
        public String toString() {
            return String.format("SignalingStatistics{activeSessions=%d, totalMessages=%d, " +
                               "totalOffers=%d, totalAnswers=%d, totalCandidates=%d}",
                               activeSessions, totalMessages, totalOffers, totalAnswers, totalCandidates);
        }
    }
    /**
     * 获取消息类型的字符串表示
     * @param type 消息类型
     * @return 字符串表示
     */
    private String getMessageTypeString(SignalingMessage.Type type) {
        return type.name();
    }
}

