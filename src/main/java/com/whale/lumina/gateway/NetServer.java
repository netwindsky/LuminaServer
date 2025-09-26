package com.whale.lumina.gateway;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网络服务器
 * 
 * 基于Apache MINA框架的TCP服务器，负责处理客户端连接和消息传输
 * 
 * @author Lumina Team
 */
@Component
public class NetServer extends IoHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(NetServer.class);

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private MessageCodec messageCodec;

    @Autowired
    private GatewayController gatewayController;

    @Value("${game.gateway.port:8080}")
    private int port;

    @Value("${game.gateway.bind-address:0.0.0.0}")
    private String bindAddress;

    @Value("${game.gateway.idle-timeout:300}")
    private int idleTimeout;

    @Value("${game.gateway.thread-pool.core-size:10}")
    private int corePoolSize;

    @Value("${game.gateway.thread-pool.max-size:50}")
    private int maxPoolSize;

    @Value("${game.gateway.buffer-size:8192}")
    private int bufferSize;

    private IoAcceptor acceptor;
    private ThreadPoolExecutor threadPool;
    
    // 连接统计
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalMessages = new AtomicLong(0);

    /**
     * 初始化网络服务器
     */
    @PostConstruct
    public void init() {
        try {
            // 创建线程池
            threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxPoolSize);
            
            // 创建NIO Socket Acceptor
            acceptor = new NioSocketAcceptor();
            
            // 配置过滤器链
            acceptor.getFilterChain().addLast("logger", new LoggingFilter());
            acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(messageCodec));
            acceptor.getFilterChain().addLast("executor", new ExecutorFilter(threadPool));
            
            // 设置处理器
            acceptor.setHandler(this);
            
            // 配置会话
            acceptor.getSessionConfig().setReadBufferSize(bufferSize);
            acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, idleTimeout);
            acceptor.getSessionConfig().setKeepAlive(true);
            acceptor.getSessionConfig().setTcpNoDelay(true);
            
            // 绑定端口并启动
            acceptor.bind(new InetSocketAddress(bindAddress, port));
            
            logger.info("网络服务器启动成功，监听地址: {}:{}", bindAddress, port);
            logger.info("服务器配置 - 空闲超时: {}s, 缓冲区大小: {}bytes, 线程池: {}-{}", 
                       idleTimeout, bufferSize, corePoolSize, maxPoolSize);
            
        } catch (IOException e) {
            logger.error("网络服务器启动失败", e);
            throw new GameException(ErrorCodes.GATEWAY_SERVER_START_FAILED, e);
        }
    }

    /**
     * 关闭网络服务器
     */
    @PreDestroy
    public void shutdown() {
        try {
            if (acceptor != null) {
                acceptor.unbind();
                acceptor.dispose(true);
                logger.info("网络服务器已关闭");
            }
            
            if (threadPool != null) {
                threadPool.shutdown();
                logger.info("网络服务器线程池已关闭");
            }
        } catch (Exception e) {
            logger.error("关闭网络服务器时发生异常", e);
        }
    }

    // ========== MINA事件处理 ==========

    @Override
    public void sessionCreated(IoSession session) throws Exception {
        long sessionId = session.getId();
        String remoteAddress = session.getRemoteAddress().toString();
        
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        
        logger.info("新连接建立: sessionId={}, remoteAddress={}, 当前活跃连接数: {}", 
                   sessionId, remoteAddress, activeConnections.get());
        
        // 注册会话到会话管理器
        sessionManager.addSession(session);
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        long sessionId = session.getId();
        logger.debug("会话打开: sessionId={}", sessionId);
        
        // 发送欢迎消息或进行初始化
        gatewayController.onSessionOpened(session);
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        long sessionId = session.getId();
        activeConnections.decrementAndGet();
        
        logger.info("连接关闭: sessionId={}, 当前活跃连接数: {}", sessionId, activeConnections.get());
        
        // 从会话管理器移除会话
        sessionManager.removeSession(session);
        
        // 处理会话关闭事件
        gatewayController.onSessionClosed(session);
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        long sessionId = session.getId();
        logger.debug("会话空闲: sessionId={}, status={}", sessionId, status);
        
        // 处理会话空闲事件
        gatewayController.onSessionIdle(session, status);
        
        // 如果空闲时间过长，关闭连接
        if (status == IdleStatus.BOTH_IDLE) {
            logger.warn("会话空闲超时，关闭连接: sessionId={}", sessionId);
            session.closeNow();
        }
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        long sessionId = session.getId();
        totalMessages.incrementAndGet();
        
        logger.debug("收到消息: sessionId={}, messageType={}", sessionId, message.getClass().getSimpleName());
        
        try {
            // 委托给网关控制器处理消息
            gatewayController.handleMessage(session, message);
        } catch (Exception e) {
            logger.error("处理消息时发生异常: sessionId={}", sessionId, e);
            // 发送错误响应
            gatewayController.sendErrorResponse(session, ErrorCodes.GATEWAY_MESSAGE_PROCESS_FAILED, e.getMessage());
        }
    }

    @Override
    public void messageSent(IoSession session, Object message) throws Exception {
        long sessionId = session.getId();
        logger.debug("消息发送完成: sessionId={}, messageType={}", sessionId, message.getClass().getSimpleName());
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        long sessionId = session.getId();
        logger.error("会话异常: sessionId={}", sessionId, cause);
        
        // 处理异常
        gatewayController.onSessionException(session, cause);
        
        // 如果是严重异常，关闭连接
        if (cause instanceof IOException || cause instanceof GameException) {
            logger.warn("严重异常，关闭连接: sessionId={}", sessionId);
            session.closeNow();
        }
    }

    // ========== 服务器管理 ==========

    /**
     * 获取服务器状态
     * 
     * @return 服务器状态
     */
    public ServerStatus getServerStatus() {
        return new ServerStatus(
            acceptor != null && acceptor.isActive(),
            totalConnections.get(),
            activeConnections.get(),
            totalMessages.get(),
            threadPool != null ? threadPool.getActiveCount() : 0,
            threadPool != null ? threadPool.getPoolSize() : 0
        );
    }

    /**
     * 广播消息给所有连接的客户端
     * 
     * @param message 消息对象
     */
    public void broadcast(Object message) {
        sessionManager.broadcast(message);
    }

    /**
     * 向指定会话发送消息
     * 
     * @param sessionId 会话ID
     * @param message 消息对象
     * @return 是否发送成功
     */
    public boolean sendMessage(long sessionId, Object message) {
        return sessionManager.sendMessage(sessionId, message);
    }

    /**
     * 关闭指定会话
     * 
     * @param sessionId 会话ID
     * @return 是否关闭成功
     */
    public boolean closeSession(long sessionId) {
        return sessionManager.closeSession(sessionId);
    }

    /**
     * 获取当前活跃连接数
     * 
     * @return 活跃连接数
     */
    public long getActiveConnectionCount() {
        return activeConnections.get();
    }

    /**
     * 获取总连接数
     * 
     * @return 总连接数
     */
    public long getTotalConnectionCount() {
        return totalConnections.get();
    }

    /**
     * 获取总消息数
     * 
     * @return 总消息数
     */
    public long getTotalMessageCount() {
        return totalMessages.get();
    }

    // ========== 内部类 ==========

    /**
     * 服务器状态
     */
    public static class ServerStatus {
        private final boolean active;
        private final long totalConnections;
        private final long activeConnections;
        private final long totalMessages;
        private final int activeThreads;
        private final int poolSize;

        public ServerStatus(boolean active, long totalConnections, long activeConnections, 
                          long totalMessages, int activeThreads, int poolSize) {
            this.active = active;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.totalMessages = totalMessages;
            this.activeThreads = activeThreads;
            this.poolSize = poolSize;
        }

        // Getters
        public boolean isActive() { return active; }
        public long getTotalConnections() { return totalConnections; }
        public long getActiveConnections() { return activeConnections; }
        public long getTotalMessages() { return totalMessages; }
        public int getActiveThreads() { return activeThreads; }
        public int getPoolSize() { return poolSize; }
    }
}