package com.whale.lumina.data;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis客户端封装类
 *
 * 提供Redis操作的统一接口，包括连接池管理、异常处理和性能监控
 *
 * @author Lumina Team
 */
@Component
public class RedisClient {

    private static final Logger logger = LoggerFactory.getLogger(RedisClient.class);

    @Value("${lumina.redis.host:localhost}")
    private String host;

    @Value("${lumina.redis.port:6379}")
    private int port;

    @Value("${lumina.redis.password:}")
    private String password;

    @Value("${lumina.redis.database:0}")
    private int database;

    @Value("${lumina.redis.timeout:2000}")
    private int timeout;

    @Value("${lumina.redis.pool.max-total:50}")
    private int maxTotal;

    @Value("${lumina.redis.pool.max-idle:20}")
    private int maxIdle;

    @Value("${lumina.redis.pool.min-idle:5}")
    private int minIdle;

    @Value("${lumina.redis.pool.max-wait:3000}")
    private long maxWait;

    private JedisPool jedisPool;

    /**
     * 初始化Redis连接池
     */
    @PostConstruct
    public void init() {
        try {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(maxTotal);
            config.setMaxIdle(maxIdle);
            config.setMinIdle(minIdle);
            config.setMaxWait(Duration.ofMillis(maxWait));
            config.setTestOnBorrow(true);
            config.setTestOnReturn(true);
            config.setTestWhileIdle(true);

            if (password != null && !password.trim().isEmpty()) {
                jedisPool = new JedisPool(config, host, port, timeout, password, database);
            } else {
                jedisPool = new JedisPool(config, host, port, timeout, null, database);
            }

            // 测试连接
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                logger.info("Redis连接池初始化成功: {}:{}/{}", host, port, database);
            }
        } catch (Exception e) {
            logger.error("Redis连接池初始化失败", e);
            throw new GameException(ErrorCodes.DATA_REDIS_CONNECTION_FAILED, e);
        }
    }

    /**
     * 销毁连接池
     */
    @PreDestroy
    public void destroy() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("Redis连接池已关闭");
        }
    }

    /**
     * 执行Redis操作
     *
     * @param operation Redis操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T execute(RedisOperation<T> operation) {
        try (Jedis jedis = jedisPool.getResource()) {
            return operation.execute(jedis);
        } catch (JedisException e) {
            logger.error("Redis操作失败", e);
            throw new GameException(ErrorCodes.DATA_REDIS_OPERATION_FAILED, e);
        }
    }

    // ========== 字符串操作 ==========

    /**
     * 设置键值对
     */
    public void set(String key, String value) {
        execute(jedis -> jedis.set(key, value));
    }

    /**
     * 设置键值对（带过期时间）
     */
    public void setex(String key, int seconds, String value) {
        execute(jedis -> jedis.setex(key, seconds, value));
    }

    /**
     * 设置键值对（带过期时间）
     */
    public void setWithExpire(String key, String value, int expireSeconds) {
        execute(jedis -> jedis.setex(key, expireSeconds, value));
    }

    /**
     * 获取值
     */
    public String get(String key) {
        return execute(jedis -> jedis.get(key));
    }

    /**
     * 删除键
     */
    public Long del(String... keys) {
        return execute(jedis -> jedis.del(keys));
    }

    /**
     * 检查键是否存在
     */
    public Boolean exists(String key) {
        return execute(jedis -> jedis.exists(key));
    }

    /**
     * 设置过期时间
     */
    public Long expire(String key, int seconds) {
        return execute(jedis -> jedis.expire(key, seconds));
    }

    /**
     * 获取剩余过期时间
     */
    public Long ttl(String key) {
        return execute(jedis -> jedis.ttl(key));
    }

    /**
     * 查找匹配的键
     */
    public Set<String> keys(String pattern) {
        return execute(jedis -> jedis.keys(pattern));
    }

    // ========== 哈希操作 ==========

    /**
     * 设置哈希字段
     */
    public Long hset(String key, String field, String value) {
        return execute(jedis -> jedis.hset(key, field, value));
    }

    /**
     * 批量设置哈希字段
     */
    public String hmset(String key, Map<String, String> hash) {
        return execute(jedis -> jedis.hmset(key, hash));
    }

    /**
     * 获取哈希字段值
     */
    public String hget(String key, String field) {
        return execute(jedis -> jedis.hget(key, field));
    }

    /**
     * 获取所有哈希字段
     */
    public Map<String, String> hgetAll(String key) {
        return execute(jedis -> jedis.hgetAll(key));
    }

    /**
     * 删除哈希字段
     */
    public Long hdel(String key, String... fields) {
        return execute(jedis -> jedis.hdel(key, fields));
    }

    /**
     * 检查哈希字段是否存在
     */
    public Boolean hexists(String key, String field) {
        return execute(jedis -> jedis.hexists(key, field));
    }

    // ========== 列表操作 ==========

    /**
     * 左侧推入元素
     */
    public Long lpush(String key, String... values) {
        return execute(jedis -> jedis.lpush(key, values));
    }

    /**
     * 右侧推入元素
     */
    public Long rpush(String key, String... values) {
        return execute(jedis -> jedis.rpush(key, values));
    }

    /**
     * 左侧弹出元素
     */
    public String lpop(String key) {
        return execute(jedis -> jedis.lpop(key));
    }

    /**
     * 右侧弹出元素
     */
    public String rpop(String key) {
        return execute(jedis -> jedis.rpop(key));
    }

    /**
     * 获取列表长度
     */
    public Long llen(String key) {
        return execute(jedis -> jedis.llen(key));
    }

    /**
     * 获取列表范围内的元素
     */
    public List<String> lrange(String key, long start, long stop) {
        return execute(jedis -> jedis.lrange(key, start, stop));
    }

    /**
     * 修剪列表
     */
    public String ltrim(String key, long start, long stop) {
        return execute(jedis -> jedis.ltrim(key, start, stop));
    }

    // ========== 集合操作 ==========

    /**
     * 添加集合成员
     */
    public Long sadd(String key, String... members) {
        return execute(jedis -> jedis.sadd(key, members));
    }

    /**
     * 移除集合成员
     */
    public Long srem(String key, String... members) {
        return execute(jedis -> jedis.srem(key, members));
    }

    /**
     * 获取所有集合成员
     */
    public Set<String> smembers(String key) {
        return execute(jedis -> jedis.smembers(key));
    }

    /**
     * 检查是否为集合成员
     */
    public Boolean sismember(String key, String member) {
        return execute(jedis -> jedis.sismember(key, member));
    }

    /**
     * 获取集合大小
     */
    public Long scard(String key) {
        return execute(jedis -> jedis.scard(key));
    }

    /**
     * 获取集合交集
     */
    public Set<String> sinter(String... keys) {
        return execute(jedis -> jedis.sinter(keys));
    }

    // ========== 有序集合操作 ==========

    /**
     * 添加有序集合成员
     */
    public Long zadd(String key, double score, String member) {
        return execute(jedis -> jedis.zadd(key, score, member));
    }

    /**
     * 移除有序集合成员
     */
    public Long zrem(String key, String... members) {
        return execute(jedis -> jedis.zrem(key, members));
    }

    /**
     * 将Set<String>转换为List<String>
     */
    private List<String> setToList(Set<String> set) {
        return new ArrayList<>(set);
    }

    /**
     * 获取有序集合范围内的成员
     */
    public Set<String> zrange(String key, long start, long stop) {
        return execute(jedis -> {
            Set<String> result = (Set<String>) jedis.zrange(key, start, stop);
            return result;
        });
    }

    /**
     * 获取有序集合范围内的成员（返回List）
     */
    public List<String> zrangeAsList(String key, long start, long stop) {
        return setToList(zrange(key, start, stop));
    }

    /**
     * 按分数降序获取有序集合范围内的成员（返回List）
     */
    public List<String> zrevrangeAsList(String key, long start, long stop) {
        return setToList(zrevrange(key, start, stop));
    }

    /**
     * 获取有序集合大小
     */
    public Long zcard(String key) {
        return execute(jedis -> jedis.zcard(key));
    }

    /**
     * 获取成员分数
     */
    public Double zscore(String key, String member) {
        return execute(jedis -> jedis.zscore(key, member));
    }

    /**
     * 按分数降序获取有序集合范围内的成员
     */
    public Set<String> zrevrange(String key, long start, long stop) {
        return execute(jedis -> {
            Set<String> result = (Set<String>) jedis.zrevrange(key, start, stop);
            return result;
        });
    }

    // ========== 发布订阅 ==========

    /**
     * 发布消息
     */
    public Long publish(String channel, String message) {
        return execute(jedis -> jedis.publish(channel, message));
    }

    /**
     * Redis操作接口
     */
    @FunctionalInterface
    public interface RedisOperation<T> {
        T execute(Jedis jedis);
    }
}
