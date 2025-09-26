package com.whale.lumina.monitoring;

import com.whale.lumina.player.PlayerRepository;
import com.whale.lumina.room.RoomManager;
import com.whale.lumina.matchmaking.MatchQueue;
import com.whale.lumina.matchmaking.MatchMaker;
import com.whale.lumina.signaling.SignalingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标导出器
 * 
 * 负责收集和导出系统性能指标，包括业务指标、系统指标和自定义指标
 * 
 * @author Lumina Team
 */
@Service
public class MetricsExporter {

    private static final Logger logger = LoggerFactory.getLogger(MetricsExporter.class);

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private MatchQueue matchQueue;

    @Autowired
    private MatchMaker matchMaker;

    @Autowired
    private SignalingHandler signalingHandler;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> histograms = new ConcurrentHashMap<>();

    // JVM监控Bean
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    // 指标收集配置
    private static final int METRICS_COLLECTION_INTERVAL = 30; // 秒
    private static final int HISTOGRAM_MAX_SIZE = 1000;

    /**
     * 初始化指标导出器
     */
    public void initialize() {
        // 初始化计数器
        initializeCounters();
        
        // 启动定期指标收集
        scheduler.scheduleAtFixedRate(this::collectMetrics, 0, METRICS_COLLECTION_INTERVAL, TimeUnit.SECONDS);
        
        // 启动指标清理任务
        scheduler.scheduleAtFixedRate(this::cleanupOldMetrics, 0, 300, TimeUnit.SECONDS); // 5分钟
        
        logger.info("指标导出器已初始化");
    }

    /**
     * 关闭指标导出器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("指标导出器已关闭");
    }

    // ========== 指标收集 ==========

    /**
     * 收集所有指标
     */
    public void collectMetrics() {
        try {
            collectSystemMetrics();
            collectBusinessMetrics();
            collectPerformanceMetrics();
            
            logger.debug("指标收集完成，当前指标数量: {}", metrics.size());
            
        } catch (Exception e) {
            logger.error("收集指标失败", e);
        }
    }

    /**
     * 收集系统指标
     */
    private void collectSystemMetrics() {
        LocalDateTime now = LocalDateTime.now();
        
        // JVM内存指标
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
        
        setGaugeMetric("jvm.memory.heap.used", heapUsed, now);
        setGaugeMetric("jvm.memory.heap.max", heapMax, now);
        setGaugeMetric("jvm.memory.nonheap.used", nonHeapUsed, now);
        
        // JVM运行时指标
        long uptime = runtimeBean.getUptime();
        setGaugeMetric("jvm.uptime", uptime, now);
        
        // 系统指标 - 修复getProcessCpuLoad()方法调用
        try {
            // 使用反射调用getProcessCpuLoad()方法，因为它在某些JVM实现中可能不可用
            java.lang.reflect.Method method = osBean.getClass().getMethod("getProcessCpuLoad");
            Object result = method.invoke(osBean);
            if (result instanceof Double) {
                double cpuLoad = (Double) result;
                if (cpuLoad >= 0) {
                    setGaugeMetric("system.cpu.usage", cpuLoad * 100, now);
                }
            }
        } catch (Exception e) {
            logger.debug("无法获取CPU使用率: {}", e.getMessage());
            // 设置默认值
            setGaugeMetric("system.cpu.usage", 0.0, now);
        }
        
        int availableProcessors = osBean.getAvailableProcessors();
        setGaugeMetric("system.cpu.count", availableProcessors, now);
        
        // 线程指标
        int activeThreads = Thread.activeCount();
        setGaugeMetric("jvm.threads.active", activeThreads, now);
    }

    /**
     * 收集业务指标
     */
    private void collectBusinessMetrics() {
        LocalDateTime now = LocalDateTime.now();
        
        try {
            // 玩家指标 - 修复getTotalPlayerCount()方法调用
            long onlinePlayerCount = playerRepository.getOnlinePlayerCount();
            // 使用在线玩家数作为总玩家数的近似值，或者添加新的方法
            setGaugeMetric("players.online", onlinePlayerCount, now);
            setGaugeMetric("players.total", onlinePlayerCount, now); // 暂时使用在线玩家数
            
            // 房间指标 - 修复getActiveRoomCount()方法调用
            int activeRoomCount = roomManager.getActiveRoomCount();
            int totalRoomCount = roomManager.getTotalRoomCount();
            
            setGaugeMetric("rooms.active", activeRoomCount, now);
            setGaugeMetric("rooms.total", totalRoomCount, now);
            
            // 匹配队列指标
            List<String> activeQueues = matchQueue.getActiveQueues();
            int totalQueueSize = 0;
            for (String queue : activeQueues) {
                String[] parts = queue.split(":");
                if (parts.length >= 2) {
                    String gameMode = parts[0];
                    MatchQueue.MatchType matchType = MatchQueue.MatchType.valueOf(parts[1]);
                    int queueSize = matchQueue.getQueueSize(gameMode, matchType);
                    totalQueueSize += queueSize;
                    
                    setGaugeMetric("matchmaking.queue.size." + gameMode + "." + matchType.name().toLowerCase(), 
                                 queueSize, now);
                }
            }
            
            setGaugeMetric("matchmaking.queue.total_size", totalQueueSize, now);
            setGaugeMetric("matchmaking.queue.count", activeQueues.size(), now);
            
            // 匹配制造器指标
            MatchMaker.MatchingStatistics matchingStats = matchMaker.getMatchingStatistics();
            setGaugeMetric("matchmaking.active_sessions", matchingStats.getActiveSessionCount(), now);
            setGaugeMetric("matchmaking.total_matches", matchingStats.getTotalMatches(), now);
            setGaugeMetric("matchmaking.success_rate", matchingStats.getMatchingSuccessRate(), now);
            setGaugeMetric("matchmaking.average_quality", matchingStats.getAverageMatchQuality(), now);
            
            // 信令指标
            // TODO: 从SignalingHandler获取统计信息
            // SignalingHandler.SignalingStatistics signalingStats = signalingHandler.getStatistics();
            // setGaugeMetric("signaling.active_sessions", signalingStats.getActiveSessionCount(), now);
            
        } catch (Exception e) {
            logger.error("收集业务指标失败", e);
        }
    }

    /**
     * 收集性能指标
     */
    private void collectPerformanceMetrics() {
        LocalDateTime now = LocalDateTime.now();
        
        // 垃圾回收指标
        // TODO: 实现GC指标收集
        
        // 网络指标
        // TODO: 实现网络指标收集
        
        // 数据库连接池指标
        // TODO: 实现数据库连接池指标收集
        
        // Redis连接池指标
        // TODO: 实现Redis连接池指标收集
    }

    // ========== 指标操作方法 ==========

    /**
     * 设置计量指标
     * 
     * @param name 指标名称
     * @param value 指标值
     * @param timestamp 时间戳
     */
    public void setGaugeMetric(String name, double value, LocalDateTime timestamp) {
        MetricValue metric = new MetricValue(MetricType.GAUGE, value, timestamp);
        metrics.put(name, metric);
    }

    /**
     * 增加计数器指标
     * 
     * @param name 指标名称
     * @param increment 增量
     */
    public void incrementCounter(String name, long increment) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(increment);
        
        // 同时更新到metrics中
        LocalDateTime now = LocalDateTime.now();
        MetricValue metric = new MetricValue(MetricType.COUNTER, counters.get(name).get(), now);
        metrics.put(name, metric);
    }

    /**
     * 增加计数器指标（增量为1）
     * 
     * @param name 指标名称
     */
    public void incrementCounter(String name) {
        incrementCounter(name, 1);
    }

    /**
     * 记录直方图指标
     * 
     * @param name 指标名称
     * @param value 值
     */
    public void recordHistogram(String name, double value) {
        histograms.computeIfAbsent(name, k -> Collections.synchronizedList(new ArrayList<>()));
        List<Double> values = histograms.get(name);
        
        // 限制直方图大小
        if (values.size() >= HISTOGRAM_MAX_SIZE) {
            values.remove(0);
        }
        values.add(value);
        
        // 计算统计值并更新到metrics中
        updateHistogramMetrics(name, values);
    }

    /**
     * 更新直方图统计指标
     * 
     * @param name 指标名称
     * @param values 值列表
     */
    private void updateHistogramMetrics(String name, List<Double> values) {
        if (values.isEmpty()) {
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 计算统计值
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double avg = sum / values.size();
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        
        // 计算百分位数
        List<Double> sortedValues = new ArrayList<>(values);
        Collections.sort(sortedValues);
        double p50 = getPercentile(sortedValues, 0.5);
        double p95 = getPercentile(sortedValues, 0.95);
        double p99 = getPercentile(sortedValues, 0.99);
        
        // 更新指标
        setGaugeMetric(name + ".count", values.size(), now);
        setGaugeMetric(name + ".sum", sum, now);
        setGaugeMetric(name + ".avg", avg, now);
        setGaugeMetric(name + ".min", min, now);
        setGaugeMetric(name + ".max", max, now);
        setGaugeMetric(name + ".p50", p50, now);
        setGaugeMetric(name + ".p95", p95, now);
        setGaugeMetric(name + ".p99", p99, now);
    }

    /**
     * 计算百分位数
     * 
     * @param sortedValues 已排序的值列表
     * @param percentile 百分位（0-1）
     * @return 百分位数值
     */
    private double getPercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        
        return sortedValues.get(index);
    }

    // ========== 业务指标记录方法 ==========

    /**
     * 记录玩家登录
     */
    public void recordPlayerLogin() {
        incrementCounter("players.login.total");
    }

    /**
     * 记录玩家登出
     */
    public void recordPlayerLogout() {
        incrementCounter("players.logout.total");
    }

    /**
     * 记录房间创建
     */
    public void recordRoomCreated() {
        incrementCounter("rooms.created.total");
    }

    /**
     * 记录房间销毁
     */
    public void recordRoomDestroyed() {
        incrementCounter("rooms.destroyed.total");
    }

    /**
     * 记录匹配请求
     */
    public void recordMatchRequest() {
        incrementCounter("matchmaking.requests.total");
    }

    /**
     * 记录匹配成功
     */
    public void recordMatchSuccess() {
        incrementCounter("matchmaking.success.total");
    }

    /**
     * 记录匹配失败
     */
    public void recordMatchFailure() {
        incrementCounter("matchmaking.failure.total");
    }

    /**
     * 记录匹配时间
     * 
     * @param timeSeconds 匹配时间（秒）
     */
    public void recordMatchTime(double timeSeconds) {
        recordHistogram("matchmaking.time", timeSeconds);
    }

    /**
     * 记录游戏时长
     * 
     * @param durationSeconds 游戏时长（秒）
     */
    public void recordGameDuration(double durationSeconds) {
        recordHistogram("game.duration", durationSeconds);
    }

    /**
     * 记录API请求
     * 
     * @param endpoint 端点
     * @param method HTTP方法
     * @param statusCode 状态码
     * @param responseTime 响应时间（毫秒）
     */
    public void recordApiRequest(String endpoint, String method, int statusCode, double responseTime) {
        String metricName = String.format("api.requests.%s.%s", method.toLowerCase(), endpoint.replace("/", "_"));
        incrementCounter(metricName + ".total");
        
        if (statusCode >= 200 && statusCode < 300) {
            incrementCounter(metricName + ".success");
        } else if (statusCode >= 400) {
            incrementCounter(metricName + ".error");
        }
        
        recordHistogram(metricName + ".response_time", responseTime);
    }

    // ========== 查询方法 ==========

    /**
     * 获取所有指标
     * 
     * @return 指标映射
     */
    public Map<String, MetricValue> getAllMetrics() {
        return new HashMap<>(metrics);
    }

    /**
     * 获取指定指标
     * 
     * @param name 指标名称
     * @return 指标值
     */
    public MetricValue getMetric(String name) {
        return metrics.get(name);
    }

    /**
     * 获取指标名称列表
     * 
     * @return 指标名称列表
     */
    public Set<String> getMetricNames() {
        return new HashSet<>(metrics.keySet());
    }

    /**
     * 获取按类型分组的指标
     * 
     * @param type 指标类型
     * @return 指标映射
     */
    public Map<String, MetricValue> getMetricsByType(MetricType type) {
        Map<String, MetricValue> result = new HashMap<>();
        
        for (Map.Entry<String, MetricValue> entry : metrics.entrySet()) {
            if (entry.getValue().getType() == type) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }

    /**
     * 获取指标摘要
     * 
     * @return 指标摘要
     */
    public MetricsSummary getMetricsSummary() {
        MetricsSummary summary = new MetricsSummary();
        summary.setTotalMetrics(metrics.size());
        summary.setCounterMetrics(getMetricsByType(MetricType.COUNTER).size());
        summary.setGaugeMetrics(getMetricsByType(MetricType.GAUGE).size());
        summary.setHistogramMetrics(histograms.size());
        summary.setLastCollectionTime(getLastCollectionTime());
        
        return summary;
    }

    // ========== 辅助方法 ==========

    /**
     * 初始化计数器
     */
    private void initializeCounters() {
        // 初始化基础计数器
        String[] counterNames = {
            "players.login.total",
            "players.logout.total",
            "rooms.created.total",
            "rooms.destroyed.total",
            "matchmaking.requests.total",
            "matchmaking.success.total",
            "matchmaking.failure.total"
        };
        
        for (String name : counterNames) {
            counters.put(name, new AtomicLong(0));
        }
    }

    /**
     * 清理过期指标
     */
    private void cleanupOldMetrics() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24); // 保留24小时的指标
            
            metrics.entrySet().removeIf(entry -> 
                entry.getValue().getTimestamp().isBefore(cutoffTime));
            
            logger.debug("清理过期指标完成，当前指标数量: {}", metrics.size());
            
        } catch (Exception e) {
            logger.error("清理过期指标失败", e);
        }
    }

    /**
     * 获取最后收集时间
     * 
     * @return 最后收集时间
     */
    private LocalDateTime getLastCollectionTime() {
        return metrics.values().stream()
                .map(MetricValue::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    // ========== 内部类 ==========

    /**
     * 指标值类
     */
    public static class MetricValue {
        private final MetricType type;
        private final double value;
        private final LocalDateTime timestamp;

        public MetricValue(MetricType type, double value, LocalDateTime timestamp) {
            this.type = type;
            this.value = value;
            this.timestamp = timestamp;
        }

        // Getters
        public MetricType getType() { return type; }
        public double getValue() { return value; }
        public LocalDateTime getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("MetricValue{type=%s, value=%.2f, timestamp=%s}", 
                               type, value, timestamp);
        }
    }

    /**
     * 指标类型枚举
     */
    public enum MetricType {
        COUNTER,    // 计数器
        GAUGE,      // 计量器
        HISTOGRAM   // 直方图
    }

    /**
     * 指标摘要类
     */
    public static class MetricsSummary {
        private int totalMetrics;
        private int counterMetrics;
        private int gaugeMetrics;
        private int histogramMetrics;
        private LocalDateTime lastCollectionTime;

        // Getters and Setters
        public int getTotalMetrics() { return totalMetrics; }
        public void setTotalMetrics(int totalMetrics) { this.totalMetrics = totalMetrics; }

        public int getCounterMetrics() { return counterMetrics; }
        public void setCounterMetrics(int counterMetrics) { this.counterMetrics = counterMetrics; }

        public int getGaugeMetrics() { return gaugeMetrics; }
        public void setGaugeMetrics(int gaugeMetrics) { this.gaugeMetrics = gaugeMetrics; }

        public int getHistogramMetrics() { return histogramMetrics; }
        public void setHistogramMetrics(int histogramMetrics) { this.histogramMetrics = histogramMetrics; }

        public LocalDateTime getLastCollectionTime() { return lastCollectionTime; }
        public void setLastCollectionTime(LocalDateTime lastCollectionTime) { this.lastCollectionTime = lastCollectionTime; }

        @Override
        public String toString() {
            return String.format("MetricsSummary{totalMetrics=%d, counterMetrics=%d, " +
                               "gaugeMetrics=%d, histogramMetrics=%d, lastCollectionTime=%s}",
                               totalMetrics, counterMetrics, gaugeMetrics, histogramMetrics, lastCollectionTime);
        }
    }
}