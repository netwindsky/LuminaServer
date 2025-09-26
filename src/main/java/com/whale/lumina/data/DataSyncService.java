package com.whale.lumina.data;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import com.whale.lumina.common.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据同步服务
 * 
 * 负责Redis和MySQL之间的数据同步，包括缓存更新、持久化和数据一致性保证
 * 
 * @author Lumina Team
 */
@Service
public class DataSyncService {

    private static final Logger logger = LoggerFactory.getLogger(DataSyncService.class);

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private MySQLClient mysqlClient;

    @Value("${game.data.sync.batch-size:100}")
    private int batchSize;

    @Value("${game.data.sync.flush-interval:30000}")
    private long flushInterval;

    @Value("${game.data.sync.max-retry:3}")
    private int maxRetry;

    // 待同步数据队列
    private final Map<String, SyncData> pendingSyncData = new ConcurrentHashMap<>();
    
    // 同步统计
    private final AtomicLong syncSuccessCount = new AtomicLong(0);
    private final AtomicLong syncFailureCount = new AtomicLong(0);
    private final AtomicLong lastSyncTime = new AtomicLong(0);

    @PostConstruct
    public void init() {
        logger.info("数据同步服务初始化完成，批量大小: {}, 刷新间隔: {}ms", batchSize, flushInterval);
    }

    // ========== 缓存操作 ==========

    /**
     * 从缓存获取数据，如果不存在则从数据库加载
     * 
     * @param key 缓存键
     * @param loader 数据加载器
     * @param expireSeconds 过期时间（秒）
     * @return 数据值
     */
    public String getFromCacheOrLoad(String key, DataLoader loader, int expireSeconds) {
        try {
            // 先从Redis获取
            String value = redisClient.get(key);
            if (value != null) {
                return value;
            }

            // 从数据库加载
            value = loader.load();
            if (value != null) {
                // 写入缓存
                redisClient.setex(key, expireSeconds, value);
            }

            return value;
        } catch (Exception e) {
            logger.error("从缓存或数据库获取数据失败: {}", key, e);
            throw new GameException(ErrorCodes.DATA_SYNC_LOAD_FAILED, e);
        }
    }

    /**
     * 更新缓存并标记为需要同步到数据库
     * 
     * @param key 缓存键
     * @param value 数据值
     * @param expireSeconds 过期时间（秒）
     * @param syncToDb 是否需要同步到数据库
     */
    public void updateCache(String key, String value, int expireSeconds, boolean syncToDb) {
        try {
            // 更新Redis缓存
            redisClient.setex(key, expireSeconds, value);

            // 如果需要同步到数据库，添加到待同步队列
            if (syncToDb) {
                SyncData syncData = new SyncData(key, value, SyncType.UPDATE, System.currentTimeMillis());
                pendingSyncData.put(key, syncData);
            }

            logger.debug("缓存更新成功: {}, 同步到数据库: {}", key, syncToDb);
        } catch (Exception e) {
            logger.error("更新缓存失败: {}", key, e);
            throw new GameException(ErrorCodes.DATA_SYNC_UPDATE_FAILED, e);
        }
    }

    /**
     * 删除缓存并标记为需要从数据库删除
     * 
     * @param key 缓存键
     * @param syncToDb 是否需要同步到数据库
     */
    public void deleteFromCache(String key, boolean syncToDb) {
        try {
            // 从Redis删除
            redisClient.del(key);

            // 如果需要同步到数据库，添加到待同步队列
            if (syncToDb) {
                SyncData syncData = new SyncData(key, null, SyncType.DELETE, System.currentTimeMillis());
                pendingSyncData.put(key, syncData);
            }

            logger.debug("缓存删除成功: {}, 同步到数据库: {}", key, syncToDb);
        } catch (Exception e) {
            logger.error("删除缓存失败: {}", key, e);
            throw new GameException(ErrorCodes.DATA_SYNC_DELETE_FAILED, e);
        }
    }

    // ========== 哈希缓存操作 ==========

    /**
     * 更新哈希缓存字段
     * 
     * @param key 缓存键
     * @param field 字段名
     * @param value 字段值
     * @param expireSeconds 过期时间（秒）
     * @param syncToDb 是否需要同步到数据库
     */
    public void updateHashField(String key, String field, String value, int expireSeconds, boolean syncToDb) {
        try {
            // 更新Redis哈希字段
            redisClient.hset(key, field, value);
            redisClient.expire(key, expireSeconds);

            // 如果需要同步到数据库，添加到待同步队列
            if (syncToDb) {
                String syncKey = key + ":" + field;
                SyncData syncData = new SyncData(syncKey, value, SyncType.HASH_UPDATE, System.currentTimeMillis());
                syncData.setHashKey(key);
                syncData.setHashField(field);
                pendingSyncData.put(syncKey, syncData);
            }

            logger.debug("哈希缓存字段更新成功: {}:{}, 同步到数据库: {}", key, field, syncToDb);
        } catch (Exception e) {
            logger.error("更新哈希缓存字段失败: {}:{}", key, field, e);
            throw new GameException(ErrorCodes.DATA_SYNC_UPDATE_FAILED, e);
        }
    }

    /**
     * 获取哈希缓存字段
     * 
     * @param key 缓存键
     * @param field 字段名
     * @return 字段值
     */
    public String getHashField(String key, String field) {
        try {
            return redisClient.hget(key, field);
        } catch (Exception e) {
            logger.error("获取哈希缓存字段失败: {}:{}", key, field, e);
            throw new GameException(ErrorCodes.DATA_SYNC_LOAD_FAILED, e);
        }
    }

    // ========== 定时同步 ==========

    /**
     * 定时将待同步数据刷新到数据库
     */
    @Scheduled(fixedDelayString = "${game.data.sync.flush-interval:30000}")
    @Async
    public void flushPendingData() {
        if (pendingSyncData.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;

        logger.info("开始同步待处理数据到数据库，数量: {}", pendingSyncData.size());

        // 批量处理待同步数据
        for (Map.Entry<String, SyncData> entry : pendingSyncData.entrySet()) {
            if (processedCount >= batchSize) {
                break;
            }

            String key = entry.getKey();
            SyncData syncData = entry.getValue();

            try {
                boolean success = syncToDatabase(syncData);
                if (success) {
                    pendingSyncData.remove(key);
                    successCount++;
                } else {
                    syncData.incrementRetryCount();
                    if (syncData.getRetryCount() >= maxRetry) {
                        logger.error("数据同步重试次数超限，丢弃数据: {}", key);
                        pendingSyncData.remove(key);
                        failureCount++;
                    }
                }
            } catch (Exception e) {
                logger.error("同步数据到数据库失败: {}", key, e);
                syncData.incrementRetryCount();
                if (syncData.getRetryCount() >= maxRetry) {
                    pendingSyncData.remove(key);
                    failureCount++;
                }
            }

            processedCount++;
        }

        long endTime = System.currentTimeMillis();
        syncSuccessCount.addAndGet(successCount);
        syncFailureCount.addAndGet(failureCount);
        lastSyncTime.set(endTime);

        logger.info("数据同步完成，处理: {}, 成功: {}, 失败: {}, 耗时: {}ms", 
                   processedCount, successCount, failureCount, endTime - startTime);
    }

    /**
     * 将单个数据同步到数据库
     * 
     * @param syncData 同步数据
     * @return 是否成功
     */
    private boolean syncToDatabase(SyncData syncData) {
        try {
            switch (syncData.getType()) {
                case UPDATE:
                    return syncUpdateToDatabase(syncData);
                case DELETE:
                    return syncDeleteToDatabase(syncData);
                case HASH_UPDATE:
                    return syncHashUpdateToDatabase(syncData);
                default:
                    logger.warn("未知的同步类型: {}", syncData.getType());
                    return false;
            }
        } catch (Exception e) {
            logger.error("同步数据到数据库异常: {}", syncData.getKey(), e);
            return false;
        }
    }

    /**
     * 同步更新操作到数据库
     */
    private boolean syncUpdateToDatabase(SyncData syncData) {
        // 这里需要根据具体的业务逻辑实现
        // 示例：根据key的前缀判断要更新的表和字段
        String key = syncData.getKey();
        String value = syncData.getValue();

        if (key.startsWith("player:")) {
            // 更新玩家数据
            String playerId = key.substring(7);
            String sql = "UPDATE players SET data = ?, updated_at = NOW() WHERE id = ?";
            int affected = mysqlClient.update(sql, value, playerId);
            return affected > 0;
        } else if (key.startsWith("room:")) {
            // 更新房间数据
            String roomId = key.substring(5);
            String sql = "UPDATE rooms SET data = ?, updated_at = NOW() WHERE id = ?";
            int affected = mysqlClient.update(sql, value, roomId);
            return affected > 0;
        }

        logger.warn("未处理的更新同步键: {}", key);
        return true; // 返回true避免重试
    }

    /**
     * 同步删除操作到数据库
     */
    private boolean syncDeleteToDatabase(SyncData syncData) {
        String key = syncData.getKey();

        if (key.startsWith("player:")) {
            // 删除玩家数据（通常是软删除）
            String playerId = key.substring(7);
            String sql = "UPDATE players SET deleted = 1, updated_at = NOW() WHERE id = ?";
            int affected = mysqlClient.update(sql, playerId);
            return affected > 0;
        } else if (key.startsWith("room:")) {
            // 删除房间数据
            String roomId = key.substring(5);
            String sql = "DELETE FROM rooms WHERE id = ?";
            int affected = mysqlClient.update(sql, roomId);
            return affected > 0;
        }

        logger.warn("未处理的删除同步键: {}", key);
        return true; // 返回true避免重试
    }

    /**
     * 同步哈希更新操作到数据库
     */
    private boolean syncHashUpdateToDatabase(SyncData syncData) {
        String hashKey = syncData.getHashKey();
        String field = syncData.getHashField();
        String value = syncData.getValue();

        if (hashKey.startsWith("player:")) {
            // 更新玩家特定字段
            String playerId = hashKey.substring(7);
            String sql = String.format("UPDATE players SET %s = ?, updated_at = NOW() WHERE id = ?", field);
            int affected = mysqlClient.update(sql, value, playerId);
            return affected > 0;
        }

        logger.warn("未处理的哈希更新同步键: {}:{}", hashKey, field);
        return true; // 返回true避免重试
    }

    // ========== 统计信息 ==========

    /**
     * 获取同步统计信息
     * 
     * @return 统计信息
     */
    public SyncStats getSyncStats() {
        return new SyncStats(
            pendingSyncData.size(),
            syncSuccessCount.get(),
            syncFailureCount.get(),
            lastSyncTime.get()
        );
    }

    // ========== 内部类 ==========

    /**
     * 同步数据
     */
    private static class SyncData {
        private final String key;
        private final String value;
        private final SyncType type;
        private final long timestamp;
        private String hashKey;
        private String hashField;
        private int retryCount = 0;

        public SyncData(String key, String value, SyncType type, long timestamp) {
            this.key = key;
            this.value = value;
            this.type = type;
            this.timestamp = timestamp;
        }

        // Getters and setters
        public String getKey() { return key; }
        public String getValue() { return value; }
        public SyncType getType() { return type; }
        public long getTimestamp() { return timestamp; }
        public String getHashKey() { return hashKey; }
        public void setHashKey(String hashKey) { this.hashKey = hashKey; }
        public String getHashField() { return hashField; }
        public void setHashField(String hashField) { this.hashField = hashField; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetryCount() { this.retryCount++; }
    }

    /**
     * 同步类型
     */
    private enum SyncType {
        UPDATE,
        DELETE,
        HASH_UPDATE
    }

    /**
     * 数据加载器接口
     */
    @FunctionalInterface
    public interface DataLoader {
        String load() throws Exception;
    }

    /**
     * 同步统计信息
     */
    public static class SyncStats {
        private final int pendingCount;
        private final long successCount;
        private final long failureCount;
        private final long lastSyncTime;

        public SyncStats(int pendingCount, long successCount, long failureCount, long lastSyncTime) {
            this.pendingCount = pendingCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.lastSyncTime = lastSyncTime;
        }

        // Getters
        public int getPendingCount() { return pendingCount; }
        public long getSuccessCount() { return successCount; }
        public long getFailureCount() { return failureCount; }
        public long getLastSyncTime() { return lastSyncTime; }
        public String getLastSyncTimeFormatted() { 
            return TimeUtils.formatTimestamp(lastSyncTime); 
        }
    }
}