package com.whale.lumina.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 时间工具类
 * 
 * 提供游戏服务器常用的时间处理功能，包括时间戳转换、格式化、延迟计算等
 * 
 * @author Lumina Team
 */
public final class TimeUtils {

    // 常用时间格式
    public static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final DateTimeFormatter COMPACT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // 时间常量（毫秒）
    public static final long SECOND_MILLIS = 1000L;
    public static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
    public static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;
    public static final long DAY_MILLIS = 24 * HOUR_MILLIS;

    /**
     * 获取当前时间戳（毫秒）
     * 
     * @return 当前时间戳
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前时间戳（秒）
     * 
     * @return 当前时间戳（秒）
     */
    public static long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * 获取当前纳秒时间戳（用于高精度计时）
     * 
     * @return 当前纳秒时间戳
     */
    public static long currentNanoTime() {
        return System.nanoTime();
    }

    /**
     * 将毫秒时间戳转换为秒时间戳
     * 
     * @param millis 毫秒时间戳
     * @return 秒时间戳
     */
    public static long millisToSeconds(long millis) {
        return millis / 1000;
    }

    /**
     * 将秒时间戳转换为毫秒时间戳
     * 
     * @param seconds 秒时间戳
     * @return 毫秒时间戳
     */
    public static long secondsToMillis(long seconds) {
        return seconds * 1000;
    }

    /**
     * 格式化时间戳为字符串
     * 
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的时间字符串
     */
    public static String formatTimestamp(long timestamp) {
        return formatTimestamp(timestamp, DEFAULT_FORMATTER);
    }

    /**
     * 使用指定格式格式化时间戳
     * 
     * @param timestamp 时间戳（毫秒）
     * @param formatter 时间格式器
     * @return 格式化后的时间字符串
     */
    public static String formatTimestamp(long timestamp, DateTimeFormatter formatter) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneId.systemDefault()
        );
        return dateTime.format(formatter);
    }

    /**
     * 格式化持续时间为可读字符串
     * 
     * @param durationMillis 持续时间（毫秒）
     * @return 格式化后的持续时间字符串
     */
    public static String formatDuration(long durationMillis) {
        if (durationMillis < 0) {
            return "0ms";
        }
        
        long days = durationMillis / DAY_MILLIS;
        long hours = (durationMillis % DAY_MILLIS) / HOUR_MILLIS;
        long minutes = (durationMillis % HOUR_MILLIS) / MINUTE_MILLIS;
        long seconds = (durationMillis % MINUTE_MILLIS) / SECOND_MILLIS;
        long millis = durationMillis % SECOND_MILLIS;
        
        StringBuilder sb = new StringBuilder();
        
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0) {
            sb.append(seconds).append("s ");
        }
        if (millis > 0 || sb.length() == 0) {
            sb.append(millis).append("ms");
        }
        
        return sb.toString().trim();
    }

    /**
     * 计算两个时间戳之间的延迟（毫秒）
     * 
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 延迟时间（毫秒）
     */
    public static long calculateLatency(long startTime, long endTime) {
        return endTime - startTime;
    }

    /**
     * 计算从指定时间到现在的延迟（毫秒）
     * 
     * @param startTime 开始时间戳
     * @return 延迟时间（毫秒）
     */
    public static long calculateLatencyFromNow(long startTime) {
        return currentTimeMillis() - startTime;
    }

    /**
     * 检查时间戳是否过期
     * 
     * @param timestamp 时间戳（毫秒）
     * @param timeoutMillis 超时时间（毫秒）
     * @return 如果过期返回true，否则返回false
     */
    public static boolean isExpired(long timestamp, long timeoutMillis) {
        return currentTimeMillis() - timestamp > timeoutMillis;
    }

    /**
     * 检查时间戳是否在指定时间范围内
     * 
     * @param timestamp 时间戳（毫秒）
     * @param windowMillis 时间窗口（毫秒）
     * @return 如果在时间窗口内返回true，否则返回false
     */
    public static boolean isWithinTimeWindow(long timestamp, long windowMillis) {
        long now = currentTimeMillis();
        return Math.abs(now - timestamp) <= windowMillis;
    }

    /**
     * 将纳秒转换为毫秒
     * 
     * @param nanos 纳秒
     * @return 毫秒
     */
    public static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    /**
     * 将纳秒转换为微秒
     * 
     * @param nanos 纳秒
     * @return 微秒
     */
    public static long nanosToMicros(long nanos) {
        return TimeUnit.NANOSECONDS.toMicros(nanos);
    }

    /**
     * 计算游戏帧间隔时间（毫秒）
     * 
     * @param fps 帧率（每秒帧数）
     * @return 帧间隔时间（毫秒）
     */
    public static long calculateFrameInterval(int fps) {
        if (fps <= 0) {
            throw new IllegalArgumentException("FPS must be positive");
        }
        return 1000L / fps;
    }

    /**
     * 获取下一个游戏tick的时间戳
     * 
     * @param currentTick 当前tick时间戳
     * @param tickInterval tick间隔（毫秒）
     * @return 下一个tick时间戳
     */
    public static long getNextTickTime(long currentTick, long tickInterval) {
        return currentTick + tickInterval;
    }

    /**
     * 计算距离下一个tick还有多长时间
     * 
     * @param nextTickTime 下一个tick时间戳
     * @return 剩余时间（毫秒），如果已过期返回0
     */
    public static long getTimeUntilNextTick(long nextTickTime) {
        long remaining = nextTickTime - currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 创建超时检查器
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @return 超时检查器
     */
    public static TimeoutChecker createTimeoutChecker(long timeoutMillis) {
        return new TimeoutChecker(timeoutMillis);
    }

    /**
     * 超时检查器内部类
     */
    public static class TimeoutChecker {
        private final long startTime;
        private final long timeoutMillis;

        private TimeoutChecker(long timeoutMillis) {
            this.startTime = currentTimeMillis();
            this.timeoutMillis = timeoutMillis;
        }

        /**
         * 检查是否超时
         * 
         * @return 如果超时返回true，否则返回false
         */
        public boolean isTimeout() {
            return currentTimeMillis() - startTime > timeoutMillis;
        }

        /**
         * 获取已经过的时间
         * 
         * @return 已过时间（毫秒）
         */
        public long getElapsedTime() {
            return currentTimeMillis() - startTime;
        }

        /**
         * 获取剩余时间
         * 
         * @return 剩余时间（毫秒），如果已超时返回0
         */
        public long getRemainingTime() {
            long elapsed = getElapsedTime();
            return Math.max(0, timeoutMillis - elapsed);
        }
    }

    /**
     * 性能计时器
     */
    public static class PerformanceTimer {
        private long startTime;
        private long endTime;
        private long totalTime;
        private int count;

        public PerformanceTimer() {
            reset();
        }

        /**
         * 开始计时
         */
        public void start() {
            startTime = currentNanoTime();
        }

        /**
         * 停止计时
         */
        public void stop() {
            endTime = currentNanoTime();
            totalTime += (endTime - startTime);
            count++;
        }

        /**
         * 重置计时器
         */
        public void reset() {
            startTime = 0;
            endTime = 0;
            totalTime = 0;
            count = 0;
        }

        /**
         * 获取耗时（纳秒）
         * 
         * @return 耗时（纳秒）
         */
        public long getElapsedNanos() {
            return endTime - startTime;
        }

        /**
         * 获取耗时（毫秒）
         * 
         * @return 耗时（毫秒）
         */
        public long getElapsedMillis() {
            return nanosToMillis(getElapsedNanos());
        }

        /**
         * 获取耗时（微秒）
         * 
         * @return 耗时（微秒）
         */
        public long getElapsedMicros() {
            return nanosToMicros(getElapsedNanos());
        }

        /**
         * 获取平均时间（毫秒）
         * 
         * @return 平均时间（毫秒）
         */
        public double getAverageTime() {
            if (count == 0) {
                return 0.0;
            }
            return nanosToMillis(totalTime) / (double) count;
        }

        /**
         * 获取总计时次数
         * 
         * @return 计时次数
         */
        public int getCount() {
            return count;
        }
    }

    // 私有构造函数，防止实例化
    private TimeUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}