package com.whale.lumina.data;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import org.springframework.stereotype.Component;

/**
 * MySQL数据库客户端
 * 提供统一的数据库访问接口，封装常用的CRUD操作
 * 
 * @author Lumina Team
 */
@Component
public class MySQLClient {
    
    private static final Logger logger = Logger.getLogger(MySQLClient.class.getName());
    
    private final DataSource dataSource;
    
    /**
     * 构造函数
     * 
     * @param dataSource 数据源
     */
    public MySQLClient(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    // ========== 查询操作 ==========
    
    /**
     * 查询单个对象
     * 
     * @param sql SQL语句
     * @param rowMapper 行映射器
     * @param args 参数
     * @param <T> 返回类型
     * @return 查询结果
     */
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowMapper.mapRow(rs, 1);
                }
                return null;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询单个对象失败: " + sql, e);
            throw new GameException(ErrorCodes.DATA_MYSQL_QUERY_FAILED, e);
        }
    }
    
    /**
     * 查询对象列表
     * 
     * @param sql SQL语句
     * @param rowMapper 行映射器
     * @param args 参数
     * @param <T> 返回类型
     * @return 查询结果列表
     */
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        List<T> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                int rowNum = 1;
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs, rowNum++));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询对象列表失败: " + sql, e);
            throw new GameException(ErrorCodes.DATA_MYSQL_QUERY_FAILED, e);
        }
        return results;
    }
    
    /**
     * 查询Map列表
     * 
     * @param sql SQL语句
     * @param args 参数
     * @return 查询结果Map列表
     */
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询Map列表失败: " + sql, e);
            throw new GameException(ErrorCodes.DATA_MYSQL_QUERY_FAILED, e);
        }
        return results;
    }
    
    /**
     * 查询单个Map
     * 
     * @param sql SQL语句
     * @param args 参数
     * @return 查询结果Map
     */
    public Map<String, Object> queryForMap(String sql, Object... args) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    Map<String, Object> row = new HashMap<>();
                    
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    return row;
                }
                return null;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询单个Map失败: " + sql, e);
            throw new GameException(ErrorCodes.DATA_MYSQL_QUERY_FAILED, e);
        }
    }
    
    /**
     * 查询单个值
     * 
     * @param sql SQL语句
     * @param requiredType 返回类型
     * @param args 参数
     * @param <T> 返回类型
     * @return 查询结果
     */
    @SuppressWarnings("unchecked")
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object value = rs.getObject(1);
                    if (value == null) {
                        return null;
                    }
                    if (requiredType.isAssignableFrom(value.getClass())) {
                        return (T) value;
                    }
                    // 简单类型转换
                    if (requiredType == String.class) {
                        return (T) value.toString();
                    } else if (requiredType == Integer.class && value instanceof Number) {
                        return (T) Integer.valueOf(((Number) value).intValue());
                    } else if (requiredType == Long.class && value instanceof Number) {
                        return (T) Long.valueOf(((Number) value).longValue());
                    }
                    return (T) value;
                }
                return null;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询单个值失败: " + sql, e);
            throw new GameException(ErrorCodes.DATA_MYSQL_QUERY_FAILED, e);
        }
    }
    
    /**
     * 查询记录数
     * 
     * @param sql SQL语句
     * @param args 参数
     * @return 记录数
     */
    public int queryForCount(String sql, Object... args) {
        Integer count = queryForObject(sql, Integer.class, args);
        return count != null ? count : 0;
    }
    
    // ========== 更新操作 ==========
    
    /**
     * 执行更新操作
     * 
     * @param sql SQL语句
     * @param args 参数
     * @return 影响的行数
     */
    public int update(String sql, Object... args) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            setParameters(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "更新操作失败: " + sql, e);
            throw new GameException(ErrorCodes.DATA_MYSQL_UPDATE_FAILED, e);
        }
    }
    
    /**
     * 执行插入操作并返回生成的主键
     * 
     * @param sql SQL语句
     * @param args 参数
     * @return 生成的主键
     */
    public Long insertAndReturnKey(String sql, Object... args) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            setParameters(ps, args);
            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getLong(1);
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "插入操作失败: " + sql, e);
            throw new GameException(ErrorCodes.DATA_MYSQL_INSERT_FAILED, e);
        }
    }
    
    /**
     * 批量更新操作
     * 
     * @param sql SQL语句
     * @param batchArgs 批量参数
     * @return 每个操作影响的行数数组
     */
    public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            for (Object[] args : batchArgs) {
                setParameters(ps, args);
                ps.addBatch();
            }
            return ps.executeBatch();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "批量更新操作失败: " + sql, e);
            throw new GameException(ErrorCodes.DATA_MYSQL_BATCH_UPDATE_FAILED, e);
        }
    }
    
    // ========== 事务操作 ==========
    
    /**
     * 在事务中执行操作
     * 
     * @param operation 事务操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeInTransaction(TransactionOperation<T> operation) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            
            T result = operation.execute(this);
            conn.commit();
            return result;
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "事务回滚失败", rollbackEx);
                }
            }
            logger.log(Level.SEVERE, "事务执行失败", e);
            throw new GameException(ErrorCodes.DATA_MYSQL_TRANSACTION_FAILED, e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    logger.log(Level.SEVERE, "关闭连接失败", closeEx);
                }
            }
        }
    }
    
    /**
     * 在事务中执行操作（无返回值）
     * 
     * @param operation 事务操作
     */
    public void executeInTransaction(VoidTransactionOperation operation) {
        executeInTransaction(client -> {
            operation.execute(client);
            return null;
        });
    }
    
    // ========== 数据库连接检查 ==========
    
    /**
     * 检查数据库连接是否正常
     * 
     * @return 连接是否正常
     */
    public boolean isConnectionValid() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5); // 5秒超时
        } catch (SQLException e) {
            logger.log(Level.WARNING, "数据库连接检查失败", e);
            return false;
        }
    }
    
    /**
     * 获取数据库版本信息
     * 
     * @return 版本信息
     */
    public String getDatabaseVersion() {
        return queryForObject("SELECT VERSION()", String.class);
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 设置PreparedStatement参数
     */
    private void setParameters(PreparedStatement ps, Object... args) throws SQLException {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
        }
    }
    
    // ========== 内部接口 ==========
    
    /**
     * 事务操作接口
     */
    @FunctionalInterface
    public interface TransactionOperation<T> {
        T execute(MySQLClient client) throws Exception;
    }
    
    /**
     * 无返回值事务操作接口
     */
    @FunctionalInterface
    public interface VoidTransactionOperation {
        void execute(MySQLClient client) throws Exception;
    }
    
    /**
     * 行映射器接口
     */
    @FunctionalInterface
    public interface RowMapper<T> {
        T mapRow(ResultSet rs, int rowNum) throws SQLException;
    }
    
    // ========== 常用行映射器 ==========
    
    /**
     * 字符串行映射器
     */
    public static final RowMapper<String> STRING_ROW_MAPPER = (rs, rowNum) -> rs.getString(1);
    
    /**
     * 长整数行映射器
     */
    public static final RowMapper<Long> LONG_ROW_MAPPER = (rs, rowNum) -> rs.getLong(1);
    
    /**
     * 布尔值行映射器
     */
    public static final RowMapper<Boolean> BOOLEAN_ROW_MAPPER = (rs, rowNum) -> rs.getBoolean(1);
    
    /**
     * 整数行映射器
     */
    public static final RowMapper<Integer> INTEGER_ROW_MAPPER = (rs, rowNum) -> rs.getInt(1);
    
    /**
     * Map行映射器
     */
    public static final RowMapper<Map<String, Object>> MAP_ROW_MAPPER = (rs, rowNum) -> {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        Map<String, Object> row = new HashMap<>();
        
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            Object value = rs.getObject(i);
            row.put(columnName, value);
        }
        return row;
    };
}