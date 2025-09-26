package com.whale.lumina.monitoring;

import com.whale.lumina.player.PlayerRepository;
import com.whale.lumina.room.RoomManager;
import com.whale.lumina.matchmaking.MatchQueue;
import com.whale.lumina.signaling.SignalingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 健康检查端点
 * 
 * 提供系统健康状态检查功能，包括各个组件的健康状态和整体系统状态
 * 
 * @author Lumina Team
 */
@RestController
@RequestMapping("/health")
public class HealthEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(HealthEndpoint.class);

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private MatchQueue matchQueue;

    @Autowired
    private SignalingHandler signalingHandler;

    @Autowired
    private MetricsExporter metricsExporter;

    @Autowired(required = false)
    private DataSource dataSource;

    // 健康检查配置
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;
    private static final double CPU_THRESHOLD = 90.0; // CPU使用率阈值
    private static final double MEMORY_THRESHOLD = 90.0; // 内存使用率阈值

    // ========== 主要健康检查端点 ==========

    /**
     * 基础健康检查
     * 
     * @return 健康状态
     */
    @GetMapping
    public ResponseEntity<HealthStatus> health() {
        try {
            HealthStatus status = performBasicHealthCheck();
            
            HttpStatus httpStatus = status.getStatus() == HealthStatus.Status.UP 
                                  ? HttpStatus.OK 
                                  : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(httpStatus).body(status);
            
        } catch (Exception e) {
            logger.error("健康检查失败", e);
            
            HealthStatus errorStatus = new HealthStatus();
            errorStatus.setStatus(HealthStatus.Status.DOWN);
            errorStatus.setMessage("健康检查异常: " + e.getMessage());
            errorStatus.setTimestamp(LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStatus);
        }
    }

    /**
     * 详细健康检查
     * 
     * @return 详细健康状态
     */
    @GetMapping("/detailed")
    public ResponseEntity<DetailedHealthStatus> detailedHealth() {
        try {
            DetailedHealthStatus status = performDetailedHealthCheck();
            
            HttpStatus httpStatus = status.getOverallStatus() == HealthStatus.Status.UP 
                                  ? HttpStatus.OK 
                                  : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(httpStatus).body(status);
            
        } catch (Exception e) {
            logger.error("详细健康检查失败", e);
            
            DetailedHealthStatus errorStatus = new DetailedHealthStatus();
            errorStatus.setOverallStatus(HealthStatus.Status.DOWN);
            errorStatus.setMessage("详细健康检查异常: " + e.getMessage());
            errorStatus.setTimestamp(LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStatus);
        }
    }

    /**
     * 就绪检查
     * 
     * @return 就绪状态
     */
    @GetMapping("/ready")
    public ResponseEntity<ReadinessStatus> readiness() {
        try {
            ReadinessStatus status = performReadinessCheck();
            
            HttpStatus httpStatus = status.isReady() 
                                  ? HttpStatus.OK 
                                  : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(httpStatus).body(status);
            
        } catch (Exception e) {
            logger.error("就绪检查失败", e);
            
            ReadinessStatus errorStatus = new ReadinessStatus();
            errorStatus.setReady(false);
            errorStatus.setMessage("就绪检查异常: " + e.getMessage());
            errorStatus.setTimestamp(LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStatus);
        }
    }

    /**
     * 存活检查
     * 
     * @return 存活状态
     */
    @GetMapping("/live")
    public ResponseEntity<LivenessStatus> liveness() {
        try {
            LivenessStatus status = performLivenessCheck();
            
            HttpStatus httpStatus = status.isAlive() 
                                  ? HttpStatus.OK 
                                  : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(httpStatus).body(status);
            
        } catch (Exception e) {
            logger.error("存活检查失败", e);
            
            LivenessStatus errorStatus = new LivenessStatus();
            errorStatus.setAlive(false);
            errorStatus.setMessage("存活检查异常: " + e.getMessage());
            errorStatus.setTimestamp(LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStatus);
        }
    }

    /**
     * 组件健康检查
     * 
     * @param component 组件名称
     * @return 组件健康状态
     */
    @GetMapping("/component")
    public ResponseEntity<ComponentHealthStatus> componentHealth(@RequestParam String component) {
        try {
            ComponentHealthStatus status = checkComponentHealth(component);
            
            HttpStatus httpStatus = status.getStatus() == HealthStatus.Status.UP 
                                  ? HttpStatus.OK 
                                  : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(httpStatus).body(status);
            
        } catch (Exception e) {
            logger.error("组件健康检查失败: component={}", component, e);
            
            ComponentHealthStatus errorStatus = new ComponentHealthStatus();
            errorStatus.setComponentName(component);
            errorStatus.setStatus(HealthStatus.Status.DOWN);
            errorStatus.setMessage("组件健康检查异常: " + e.getMessage());
            errorStatus.setTimestamp(LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStatus);
        }
    }

    // ========== 健康检查实现 ==========

    /**
     * 执行基础健康检查
     * 
     * @return 健康状态
     */
    private HealthStatus performBasicHealthCheck() {
        HealthStatus status = new HealthStatus();
        status.setTimestamp(LocalDateTime.now());
        
        try {
            // 检查关键组件
            boolean allHealthy = true;
            StringBuilder messageBuilder = new StringBuilder();
            
            // 检查数据库连接
            if (!checkDatabaseHealth()) {
                allHealthy = false;
                messageBuilder.append("数据库连接异常; ");
            }
            
            // 检查Redis连接
            if (!checkRedisHealth()) {
                allHealthy = false;
                messageBuilder.append("Redis连接异常; ");
            }
            
            // 检查系统资源
            if (!checkSystemResources()) {
                allHealthy = false;
                messageBuilder.append("系统资源不足; ");
            }
            
            status.setStatus(allHealthy ? HealthStatus.Status.UP : HealthStatus.Status.DOWN);
            status.setMessage(allHealthy ? "系统健康" : messageBuilder.toString());
            
        } catch (Exception e) {
            status.setStatus(HealthStatus.Status.DOWN);
            status.setMessage("健康检查异常: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 执行详细健康检查
     * 
     * @return 详细健康状态
     */
    private DetailedHealthStatus performDetailedHealthCheck() {
        DetailedHealthStatus status = new DetailedHealthStatus();
        status.setTimestamp(LocalDateTime.now());
        
        Map<String, ComponentHealthStatus> components = new HashMap<>();
        
        // 检查各个组件
        components.put("database", checkDatabaseComponent());
        components.put("redis", checkRedisComponent());
        components.put("player_repository", checkPlayerRepositoryComponent());
        components.put("room_manager", checkRoomManagerComponent());
        components.put("match_queue", checkMatchQueueComponent());
        components.put("signaling_handler", checkSignalingHandlerComponent());
        components.put("metrics_exporter", checkMetricsExporterComponent());
        components.put("system_resources", checkSystemResourcesComponent());
        
        status.setComponents(components);
        
        // 计算整体状态
        boolean allUp = components.values().stream()
                .allMatch(comp -> comp.getStatus() == HealthStatus.Status.UP);
        
        status.setOverallStatus(allUp ? HealthStatus.Status.UP : HealthStatus.Status.DOWN);
        status.setMessage(allUp ? "所有组件健康" : "部分组件异常");
        
        return status;
    }

    /**
     * 执行就绪检查
     * 
     * @return 就绪状态
     */
    private ReadinessStatus performReadinessCheck() {
        ReadinessStatus status = new ReadinessStatus();
        status.setTimestamp(LocalDateTime.now());
        
        try {
            // 检查应用是否准备好接收流量
            boolean ready = true;
            List<String> issues = new ArrayList<>();
            
            // 检查数据库连接
            if (!checkDatabaseHealth()) {
                ready = false;
                issues.add("数据库未就绪");
            }
            
            // 检查Redis连接
            if (!checkRedisHealth()) {
                ready = false;
                issues.add("Redis未就绪");
            }
            
            // 检查关键服务
            if (!checkCriticalServices()) {
                ready = false;
                issues.add("关键服务未就绪");
            }
            
            status.setReady(ready);
            status.setMessage(ready ? "应用已就绪" : "应用未就绪: " + String.join(", ", issues));
            status.setIssues(issues);
            
        } catch (Exception e) {
            status.setReady(false);
            status.setMessage("就绪检查异常: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 执行存活检查
     * 
     * @return 存活状态
     */
    private LivenessStatus performLivenessCheck() {
        LivenessStatus status = new LivenessStatus();
        status.setTimestamp(LocalDateTime.now());
        
        try {
            // 检查应用是否存活（基本功能是否正常）
            boolean alive = true;
            List<String> issues = new ArrayList<>();
            
            // 检查JVM状态
            if (!checkJvmHealth()) {
                alive = false;
                issues.add("JVM状态异常");
            }
            
            // 检查线程状态
            if (!checkThreadHealth()) {
                alive = false;
                issues.add("线程状态异常");
            }
            
            // 检查内存状态
            if (!checkMemoryHealth()) {
                alive = false;
                issues.add("内存状态异常");
            }
            
            status.setAlive(alive);
            status.setMessage(alive ? "应用存活" : "应用异常: " + String.join(", ", issues));
            status.setIssues(issues);
            
        } catch (Exception e) {
            status.setAlive(false);
            status.setMessage("存活检查异常: " + e.getMessage());
        }
        
        return status;
    }

    // ========== 组件健康检查 ==========

    /**
     * 检查组件健康状态
     * 
     * @param componentName 组件名称
     * @return 组件健康状态
     */
    private ComponentHealthStatus checkComponentHealth(String componentName) {
        switch (componentName.toLowerCase()) {
            case "database":
                return checkDatabaseComponent();
            case "redis":
                return checkRedisComponent();
            case "player_repository":
                return checkPlayerRepositoryComponent();
            case "room_manager":
                return checkRoomManagerComponent();
            case "match_queue":
                return checkMatchQueueComponent();
            case "signaling_handler":
                return checkSignalingHandlerComponent();
            case "metrics_exporter":
                return checkMetricsExporterComponent();
            case "system_resources":
                return checkSystemResourcesComponent();
            default:
                ComponentHealthStatus status = new ComponentHealthStatus();
                status.setComponentName(componentName);
                status.setStatus(HealthStatus.Status.UNKNOWN);
                status.setMessage("未知组件");
                status.setTimestamp(LocalDateTime.now());
                return status;
        }
    }

    /**
     * 检查数据库组件
     */
    private ComponentHealthStatus checkDatabaseComponent() {
        ComponentHealthStatus status = new ComponentHealthStatus();
        status.setComponentName("database");
        status.setTimestamp(LocalDateTime.now());
        
        try {
            boolean healthy = checkDatabaseHealth();
            status.setStatus(healthy ? HealthStatus.Status.UP : HealthStatus.Status.DOWN);
            status.setMessage(healthy ? "数据库连接正常" : "数据库连接异常");
            
            if (healthy && dataSource != null) {
                // 添加数据库详细信息
                Map<String, Object> details = new HashMap<>();
                details.put("driver", dataSource.getClass().getSimpleName());
                status.setDetails(details);
            }
            
        } catch (Exception e) {
            status.setStatus(HealthStatus.Status.DOWN);
            status.setMessage("数据库检查异常: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 检查Redis组件
     */
    private ComponentHealthStatus checkRedisComponent() {
        ComponentHealthStatus status = new ComponentHealthStatus();
        status.setComponentName("redis");
        status.setTimestamp(LocalDateTime.now());
        
        try {
            boolean healthy = checkRedisHealth();
            status.setStatus(healthy ? HealthStatus.Status.UP : HealthStatus.Status.DOWN);
            status.setMessage(healthy ? "Redis连接正常" : "Redis连接异常");
            
        } catch (Exception e) {
            status.setStatus(HealthStatus.Status.DOWN);
            status.setMessage("Redis检查异常: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 检查玩家仓库组件
     */
    private ComponentHealthStatus checkPlayerRepositoryComponent() {
        ComponentHealthStatus status = new ComponentHealthStatus();
        status.setComponentName("player_repository");
        status.setTimestamp(LocalDateTime.now());
        
        try {
            // 尝试获取在线玩家数量
            long onlineCount = playerRepository.getOnlinePlayerCount();
            
            status.setStatus(HealthStatus.Status.UP);
            status.setMessage("玩家仓库正常");
            
            Map<String, Object> details = new HashMap<>();
            details.put("online_players", onlineCount);
            status.setDetails(details);
            
        } catch (Exception e) {
            status.setStatus(HealthStatus.Status.DOWN);
            status.setMessage("玩家仓库异常: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 检查房间管理器组件
     */
    private ComponentHealthStatus checkRoomManagerComponent() {
        ComponentHealthStatus status = new ComponentHealthStatus();
        status.setComponentName("room_manager");
        status.setTimestamp(LocalDateTime.now());
        
        try {
            // 尝试获取活跃房间数量
            int activeRooms = roomManager.getActiveRoomCount();
            
            status.setStatus(HealthStatus.Status.UP);
            status.setMessage("房间管理器正常");
            
            Map<String, Object> details = new HashMap<>();
            details.put("active_rooms", activeRooms);
            status.setDetails(details);
            
        } catch (Exception e) {
            status.setStatus(HealthStatus.Status.DOWN);
            status.setMessage("房间管理器异常: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 检查匹配队列组件
     */
    private ComponentHealthStatus checkMatchQueueComponent() {
        ComponentHealthStatus status = new ComponentHealthStatus();
        status.setComponentName("match_queue");
        status.setTimestamp(LocalDateTime.now());
        
        try {
            // 尝试获取活跃队列
            List<String> activeQueues = matchQueue.getActiveQueues();
            
            status.setStatus(HealthStatus.Status.UP);
            status.setMessage("匹配队列正常");
            
            Map<String, Object> details = new HashMap<>();
            details.put("active_queues", activeQueues.size());
            status.setDetails(details);
            
        } catch (Exception e) {
            status.setStatus(HealthStatus.Status.DOWN);
            status.setMessage("匹配队列异常: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 检查信令处理器组件
     */
    private ComponentHealthStatus checkSignalingHandlerComponent() {
        try {
            int activeConnections = signalingHandler.getActiveConnections();
            int activeSessions = signalingHandler.getActiveSessionCount();
            
            Map<String, Object> details = new HashMap<>();
            details.put("activeConnections", activeConnections);
            details.put("activeSessions", activeSessions);
            details.put("totalMessages", signalingHandler.getTotalSignalingMessages());
            
            return new ComponentHealthStatus("SignalingHandler", HealthStatus.Status.UP, details);
        } catch (Exception e) {
            logger.error("检查信令处理器组件健康状态失败", e);
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            return new ComponentHealthStatus("SignalingHandler", HealthStatus.Status.DOWN, details);
        }
    }

    /**
     * 检查指标导出器组件
     */
    private ComponentHealthStatus checkMetricsExporterComponent() {
        ComponentHealthStatus status = new ComponentHealthStatus();
        status.setComponentName("metrics_exporter");
        status.setTimestamp(LocalDateTime.now());
        
        try {
            // 检查指标导出器
            MetricsExporter.MetricsSummary summary = metricsExporter.getMetricsSummary();
            
            status.setStatus(HealthStatus.Status.UP);
            status.setMessage("指标导出器正常");
            
            Map<String, Object> details = new HashMap<>();
            details.put("total_metrics", summary.getTotalMetrics());
            details.put("last_collection", summary.getLastCollectionTime());
            status.setDetails(details);
            
        } catch (Exception e) {
            status.setStatus(HealthStatus.Status.DOWN);
            status.setMessage("指标导出器异常: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * 检查系统资源组件
     */
    private ComponentHealthStatus checkSystemResourcesComponent() {
        ComponentHealthStatus status = new ComponentHealthStatus();
        status.setComponentName("system_resources");
        status.setTimestamp(LocalDateTime.now());
        
        try {
            boolean healthy = checkSystemResources();
            status.setStatus(healthy ? HealthStatus.Status.UP : HealthStatus.Status.DOWN);
            status.setMessage(healthy ? "系统资源正常" : "系统资源不足");
            
            // 添加系统资源详细信息
            Map<String, Object> details = getSystemResourceDetails();
            status.setDetails(details);
            
        } catch (Exception e) {
            status.setStatus(HealthStatus.Status.DOWN);
            status.setMessage("系统资源检查异常: " + e.getMessage());
        }
        
        return status;
    }

    // ========== 底层健康检查方法 ==========

    /**
     * 检查数据库健康状态
     */
    private boolean checkDatabaseHealth() {
        if (dataSource == null) {
            return false;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(HEALTH_CHECK_TIMEOUT_SECONDS);
        } catch (Exception e) {
            logger.warn("数据库健康检查失败", e);
            return false;
        }
    }

    /**
     * 检查Redis健康状态
     */
    private boolean checkRedisHealth() {
        try {
            // TODO: 实现Redis健康检查
            // 可以通过PlayerRepository或其他使用Redis的组件来检查
            return true;
        } catch (Exception e) {
            logger.warn("Redis健康检查失败", e);
            return false;
        }
    }

    /**
     * 检查系统资源
     */
    private boolean checkSystemResources() {
        try {
            // 检查CPU使用率
            MetricsExporter.MetricValue cpuMetric = metricsExporter.getMetric("system.cpu.usage");
            if (cpuMetric != null && cpuMetric.getValue() > CPU_THRESHOLD) {
                return false;
            }
            
            // 检查内存使用率
            MetricsExporter.MetricValue memoryMetric = metricsExporter.getMetric("jvm.memory.heap.usage");
            if (memoryMetric != null && memoryMetric.getValue() > MEMORY_THRESHOLD) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            logger.warn("系统资源检查失败", e);
            return false;
        }
    }

    /**
     * 检查关键服务
     */
    private boolean checkCriticalServices() {
        try {
            // 检查关键服务是否正常运行
            return playerRepository != null && 
                   roomManager != null && 
                   matchQueue != null;
        } catch (Exception e) {
            logger.warn("关键服务检查失败", e);
            return false;
        }
    }

    /**
     * 检查JVM健康状态
     */
    private boolean checkJvmHealth() {
        try {
            // 检查JVM基本状态
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            
            double memoryUsage = (double) (totalMemory - freeMemory) / maxMemory * 100;
            
            return memoryUsage < MEMORY_THRESHOLD;
            
        } catch (Exception e) {
            logger.warn("JVM健康检查失败", e);
            return false;
        }
    }

    /**
     * 检查线程健康状态
     */
    private boolean checkThreadHealth() {
        try {
            // 检查活跃线程数是否正常
            int activeThreads = Thread.activeCount();
            
            // 简单检查：活跃线程数不应该过多
            return activeThreads < 1000;
            
        } catch (Exception e) {
            logger.warn("线程健康检查失败", e);
            return false;
        }
    }

    /**
     * 检查内存健康状态
     */
    private boolean checkMemoryHealth() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            
            double memoryUsage = (double) (totalMemory - freeMemory) / maxMemory * 100;
            
            return memoryUsage < MEMORY_THRESHOLD;
            
        } catch (Exception e) {
            logger.warn("内存健康检查失败", e);
            return false;
        }
    }

    /**
     * 获取系统资源详细信息
     */
    private Map<String, Object> getSystemResourceDetails() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            Runtime runtime = Runtime.getRuntime();
            
            // 内存信息
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            details.put("memory_max_mb", maxMemory / 1024 / 1024);
            details.put("memory_total_mb", totalMemory / 1024 / 1024);
            details.put("memory_used_mb", usedMemory / 1024 / 1024);
            details.put("memory_free_mb", freeMemory / 1024 / 1024);
            details.put("memory_usage_percent", (double) usedMemory / maxMemory * 100);
            
            // CPU信息
            details.put("cpu_cores", runtime.availableProcessors());
            
            // 线程信息
            details.put("active_threads", Thread.activeCount());
            
        } catch (Exception e) {
            logger.warn("获取系统资源详细信息失败", e);
        }
        
        return details;
    }

    // ========== 内部类 ==========

    /**
     * 基础健康状态类
     */
    public static class HealthStatus {
        private Status status;
        private String message;
        private LocalDateTime timestamp;

        public enum Status {
            UP, DOWN, UNKNOWN
        }

        // Getters and Setters
        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return String.format("HealthStatus{status=%s, message='%s', timestamp=%s}", 
                               status, message, timestamp);
        }
    }

    /**
     * 详细健康状态类
     */
    public static class DetailedHealthStatus extends HealthStatus {
        private HealthStatus.Status overallStatus;
        private Map<String, ComponentHealthStatus> components;

        public DetailedHealthStatus() {
            this.components = new HashMap<>();
        }

        // Getters and Setters
        public HealthStatus.Status getOverallStatus() { return overallStatus; }
        public void setOverallStatus(HealthStatus.Status overallStatus) { this.overallStatus = overallStatus; }

        public Map<String, ComponentHealthStatus> getComponents() { return components; }
        public void setComponents(Map<String, ComponentHealthStatus> components) { this.components = components; }
    }

    /**
     * 组件健康状态类
     */
    public static class ComponentHealthStatus extends HealthStatus {
        private String componentName;
        private Map<String, Object> details;

        public ComponentHealthStatus() {
            this.details = new HashMap<>();
        }

        public ComponentHealthStatus(String componentName, HealthStatus.Status status, Map<String, Object> details) {
            this.componentName = componentName;
            this.setStatus(status);
            this.details = details != null ? details : new HashMap<>();
            this.setTimestamp(LocalDateTime.now());
        }

        // Getters and Setters
        public String getComponentName() { return componentName; }
        public void setComponentName(String componentName) { this.componentName = componentName; }

        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
    }

    /**
     * 就绪状态类
     */
    public static class ReadinessStatus {
        private boolean ready;
        private String message;
        private List<String> issues;
        private LocalDateTime timestamp;

        public ReadinessStatus() {
            this.issues = new ArrayList<>();
        }

        // Getters and Setters
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return String.format("ReadinessStatus{ready=%s, message='%s', issues=%s, timestamp=%s}", 
                               ready, message, issues, timestamp);
        }
    }

    /**
     * 存活状态类
     */
    public static class LivenessStatus {
        private boolean alive;
        private String message;
        private List<String> issues;
        private LocalDateTime timestamp;

        public LivenessStatus() {
            this.issues = new ArrayList<>();
        }

        // Getters and Setters
        public boolean isAlive() { return alive; }
        public void setAlive(boolean alive) { this.alive = alive; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return String.format("LivenessStatus{alive=%s, message='%s', issues=%s, timestamp=%s}", 
                               alive, message, issues, timestamp);
        }
    }

}


