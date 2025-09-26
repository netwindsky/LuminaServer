package com.whale.lumina.auth;

import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.GameException;
import com.whale.lumina.common.TimeUtils;
import com.whale.lumina.data.MySQLClient;
import com.whale.lumina.data.RedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 认证服务
 * 
 * 负责用户认证、会话管理、令牌生成和验证等功能
 * 
 * @author Lumina Team
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private MySQLClient mysqlClient;

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private SessionStore sessionStore;

    // 配置参数
    @Value("${lumina.auth.token-expire-seconds:3600}")
    private int tokenExpireSeconds;

    @Value("${lumina.auth.session-expire-seconds:7200}")
    private int sessionExpireSeconds;

    @Value("${lumina.auth.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${lumina.auth.lockout-duration-seconds:300}")
    private int lockoutDurationSeconds;

    @Value("${lumina.auth.jwt-secret:lumina-game-server-secret-key}")
    private String jwtSecret;

    // 统计信息
    private final AtomicLong totalLoginAttempts = new AtomicLong(0);
    private final AtomicLong successfulLogins = new AtomicLong(0);
    private final AtomicLong failedLogins = new AtomicLong(0);

    // 登录尝试记录（IP -> 尝试次数和时间）
    private final ConcurrentHashMap<String, LoginAttemptRecord> loginAttempts = new ConcurrentHashMap<>();

    // 安全随机数生成器
    private final SecureRandom secureRandom = new SecureRandom();

    // ========== 用户认证 ==========

    /**
     * 用户登录
     * 
     * @param username 用户名
     * @param password 密码
     * @param clientIP 客户端IP
     * @return 认证结果
     */
    public AuthResult login(String username, String password, String clientIP) {
        totalLoginAttempts.incrementAndGet();
        
        try {
            // 验证输入参数
            validateLoginInput(username, password);
            
            // 检查IP是否被锁定
            checkIPLockout(clientIP);
            
            // 查询用户信息
            UserInfo userInfo = getUserInfo(username);
            if (userInfo == null) {
                recordFailedLogin(clientIP);
                failedLogins.incrementAndGet();
                throw new GameException(ErrorCodes.AUTH_USER_NOT_FOUND);
            }
            
            // 验证密码
            if (!verifyPassword(password, userInfo.getPasswordHash(), userInfo.getSalt())) {
                recordFailedLogin(clientIP);
                failedLogins.incrementAndGet();
                throw new GameException(ErrorCodes.AUTH_INVALID_PASSWORD);
            }
            
            // 检查用户状态
            if (!userInfo.isActive()) {
                throw new GameException(ErrorCodes.AUTH_USER_DISABLED);
            }
            
            // 生成访问令牌
            String accessToken = generateAccessToken(userInfo.getUserId());
            String refreshToken = generateRefreshToken(userInfo.getUserId());
            
            // 创建会话
            AuthSession session = createSession(userInfo.getUserId(), accessToken, refreshToken, clientIP);
            
            // 清除失败登录记录
            clearFailedLoginRecord(clientIP);
            successfulLogins.incrementAndGet();
            
            logger.info("用户登录成功: userId={}, username={}, clientIP={}", 
                       userInfo.getUserId(), username, clientIP);
            
            return new AuthResult(true, userInfo, session, null);
            
        } catch (GameException e) {
            logger.warn("用户登录失败: username={}, clientIP={}, error={}", username, clientIP, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("用户登录异常: username={}, clientIP={}", username, clientIP, e);
            throw new GameException(ErrorCodes.AUTH_LOGIN_FAILED, e);
        }
    }

    /**
     * 刷新访问令牌
     * 
     * @param refreshToken 刷新令牌
     * @return 新的访问令牌
     */
    public String refreshAccessToken(String refreshToken) {
        try {
            // 验证刷新令牌
            String userId = validateRefreshToken(refreshToken);
            
            // 检查会话是否存在
            AuthSession session = sessionStore.getSessionByRefreshToken(refreshToken);
            if (session == null || session.isExpired()) {
                throw new GameException(ErrorCodes.AUTH_SESSION_EXPIRED);
            }
            
            // 生成新的访问令牌
            String newAccessToken = generateAccessToken(userId);
            
            // 更新会话
            session.setAccessToken(newAccessToken);
            session.setLastActiveTime(System.currentTimeMillis());
            sessionStore.updateSession(session);
            
            logger.debug("刷新访问令牌成功: userId={}", userId);
            
            return newAccessToken;
            
        } catch (GameException e) {
            logger.warn("刷新访问令牌失败: error={}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("刷新访问令牌异常", e);
            throw new GameException(ErrorCodes.AUTH_TOKEN_REFRESH_FAILED, e);
        }
    }

    /**
     * 验证访问令牌
     * 
     * @param accessToken 访问令牌
     * @return 用户ID
     */
    public String validateAccessToken(String accessToken) {
        try {
            // 解析JWT令牌
            Map<String, Object> claims = parseJWT(accessToken);
            
            String userId = (String) claims.get("userId");
            Long expireTime = (Long) claims.get("exp");
            
            if (userId == null || expireTime == null) {
                throw new GameException(ErrorCodes.AUTH_INVALID_TOKEN);
            }
            
            // 检查令牌是否过期
            if (System.currentTimeMillis() > expireTime) {
                throw new GameException(ErrorCodes.AUTH_TOKEN_EXPIRED);
            }
            
            // 检查会话是否存在
            AuthSession session = sessionStore.getSessionByAccessToken(accessToken);
            if (session == null || session.isExpired()) {
                throw new GameException(ErrorCodes.AUTH_SESSION_EXPIRED);
            }
            
            // 更新会话活跃时间
            session.setLastActiveTime(System.currentTimeMillis());
            sessionStore.updateSession(session);
            
            return userId;
            
        } catch (GameException e) {
            throw e;
        } catch (Exception e) {
            logger.error("验证访问令牌异常", e);
            throw new GameException(ErrorCodes.AUTH_TOKEN_VALIDATION_FAILED, e);
        }
    }

    /**
     * 用户登出
     * 
     * @param accessToken 访问令牌
     */
    public void logout(String accessToken) {
        try {
            // 获取会话
            AuthSession session = sessionStore.getSessionByAccessToken(accessToken);
            if (session != null) {
                // 删除会话
                sessionStore.removeSession(session.getSessionId());
                
                logger.info("用户登出成功: userId={}, sessionId={}", 
                           session.getUserId(), session.getSessionId());
            }
            
        } catch (Exception e) {
            logger.error("用户登出异常", e);
            throw new GameException(ErrorCodes.AUTH_LOGOUT_FAILED, e);
        }
    }

    // ========== 用户管理 ==========

    /**
     * 获取用户信息
     * 
     * @param username 用户名
     * @return 用户信息
     */
    private UserInfo getUserInfo(String username) {
        try {
            String sql = "SELECT user_id, username, password_hash, salt, email, status, " +
                        "created_time, last_login_time FROM users WHERE username = ?";
            
            return mysqlClient.queryForObject(sql, new Object[]{username}, (rs, rowNum) -> {
                UserInfo userInfo = new UserInfo();
                userInfo.setUserId(rs.getString("user_id"));
                userInfo.setUsername(rs.getString("username"));
                userInfo.setPasswordHash(rs.getString("password_hash"));
                userInfo.setSalt(rs.getString("salt"));
                userInfo.setEmail(rs.getString("email"));
                userInfo.setStatus(rs.getInt("status"));
                userInfo.setCreatedTime(rs.getLong("created_time"));
                userInfo.setLastLoginTime(rs.getLong("last_login_time"));
                return userInfo;
            });
            
        } catch (Exception e) {
            logger.error("查询用户信息失败: username={}", username, e);
            return null;
        }
    }

    /**
     * 更新用户最后登录时间
     * 
     * @param userId 用户ID
     */
    private void updateLastLoginTime(String userId) {
        try {
            String sql = "UPDATE users SET last_login_time = ? WHERE user_id = ?";
            mysqlClient.update(sql, System.currentTimeMillis(), userId);
        } catch (Exception e) {
            logger.error("更新用户最后登录时间失败: userId={}", userId, e);
        }
    }

    // ========== 密码验证 ==========

    /**
     * 验证密码
     * 
     * @param password 明文密码
     * @param passwordHash 密码哈希
     * @param salt 盐值
     * @return 是否匹配
     */
    private boolean verifyPassword(String password, String passwordHash, String salt) {
        try {
            String computedHash = hashPassword(password, salt);
            return computedHash.equals(passwordHash);
        } catch (Exception e) {
            logger.error("密码验证异常", e);
            return false;
        }
    }

    /**
     * 哈希密码
     * 
     * @param password 明文密码
     * @param salt 盐值
     * @return 密码哈希
     */
    private String hashPassword(String password, String salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt.getBytes(StandardCharsets.UTF_8));
        byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hashedPassword);
    }

    // ========== 令牌管理 ==========

    /**
     * 生成访问令牌
     * 
     * @param userId 用户ID
     * @return 访问令牌
     */
    private String generateAccessToken(String userId) {
        try {
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime + (tokenExpireSeconds * 1000L);
            
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", userId);
            claims.put("iat", currentTime);
            claims.put("exp", expireTime);
            claims.put("type", "access");
            
            return createJWT(claims);
            
        } catch (Exception e) {
            logger.error("生成访问令牌失败: userId={}", userId, e);
            throw new GameException(ErrorCodes.AUTH_TOKEN_GENERATION_FAILED, e);
        }
    }

    /**
     * 生成刷新令牌
     * 
     * @param userId 用户ID
     * @return 刷新令牌
     */
    private String generateRefreshToken(String userId) {
        try {
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime + (sessionExpireSeconds * 1000L);
            
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", userId);
            claims.put("iat", currentTime);
            claims.put("exp", expireTime);
            claims.put("type", "refresh");
            
            return createJWT(claims);
            
        } catch (Exception e) {
            logger.error("生成刷新令牌失败: userId={}", userId, e);
            throw new GameException(ErrorCodes.AUTH_TOKEN_GENERATION_FAILED, e);
        }
    }

    /**
     * 验证刷新令牌
     * 
     * @param refreshToken 刷新令牌
     * @return 用户ID
     */
    private String validateRefreshToken(String refreshToken) {
        try {
            Map<String, Object> claims = parseJWT(refreshToken);
            
            String userId = (String) claims.get("userId");
            String type = (String) claims.get("type");
            Long expireTime = (Long) claims.get("exp");
            
            if (userId == null || !"refresh".equals(type) || expireTime == null) {
                throw new GameException(ErrorCodes.AUTH_INVALID_TOKEN);
            }
            
            if (System.currentTimeMillis() > expireTime) {
                throw new GameException(ErrorCodes.AUTH_TOKEN_EXPIRED);
            }
            
            return userId;
            
        } catch (GameException e) {
            throw e;
        } catch (Exception e) {
            logger.error("验证刷新令牌异常", e);
            throw new GameException(ErrorCodes.AUTH_TOKEN_VALIDATION_FAILED, e);
        }
    }

    // ========== JWT工具方法 ==========

    /**
     * 创建JWT令牌
     * 
     * @param claims 声明
     * @return JWT令牌
     */
    private String createJWT(Map<String, Object> claims) throws Exception {
        // 简化的JWT实现，实际项目中应使用专业的JWT库
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        
        StringBuilder payloadBuilder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (!first) payloadBuilder.append(",");
            payloadBuilder.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                payloadBuilder.append("\"").append(entry.getValue()).append("\"");
            } else {
                payloadBuilder.append(entry.getValue());
            }
            first = false;
        }
        payloadBuilder.append("}");
        
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadBuilder.toString().getBytes(StandardCharsets.UTF_8));
        
        String signature = createSignature(header + "." + payload);
        
        return header + "." + payload + "." + signature;
    }

    /**
     * 解析JWT令牌
     * 
     * @param jwt JWT令牌
     * @return 声明
     */
    private Map<String, Object> parseJWT(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        
        // 验证签名
        String expectedSignature = createSignature(parts[0] + "." + parts[1]);
        if (!expectedSignature.equals(parts[2])) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }
        
        // 解析payload
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        
        // 简化的JSON解析，实际项目中应使用JSON库
        Map<String, Object> claims = new HashMap<>();
        // 这里应该使用专业的JSON解析库，暂时使用简化实现
        // 实际实现中需要正确解析JSON
        
        return claims;
    }

    /**
     * 创建签名
     * 
     * @param data 数据
     * @return 签名
     */
    private String createSignature(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    // ========== 会话管理 ==========

    /**
     * 创建会话
     * 
     * @param userId 用户ID
     * @param accessToken 访问令牌
     * @param refreshToken 刷新令牌
     * @param clientIP 客户端IP
     * @return 会话
     */
    private AuthSession createSession(String userId, String accessToken, String refreshToken, String clientIP) {
        try {
            String sessionId = generateSessionId();
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime + (sessionExpireSeconds * 1000L);
            
            AuthSession session = new AuthSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setAccessToken(accessToken);
            session.setRefreshToken(refreshToken);
            session.setClientIP(clientIP);
            session.setCreatedTime(currentTime);
            session.setLastActiveTime(currentTime);
            session.setExpireTime(expireTime);
            
            // 保存会话
            sessionStore.saveSession(session);
            
            // 更新用户最后登录时间
            updateLastLoginTime(userId);
            
            return session;
            
        } catch (Exception e) {
            logger.error("创建会话失败: userId={}", userId, e);
            throw new GameException(ErrorCodes.AUTH_SESSION_CREATION_FAILED, e);
        }
    }

    /**
     * 生成会话ID
     * 
     * @return 会话ID
     */
    private String generateSessionId() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    // ========== 安全控制 ==========

    /**
     * 验证登录输入
     * 
     * @param username 用户名
     * @param password 密码
     */
    private void validateLoginInput(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new GameException(ErrorCodes.AUTH_INVALID_USERNAME);
        }
        
        if (password == null || password.isEmpty()) {
            throw new GameException(ErrorCodes.AUTH_INVALID_PASSWORD);
        }
        
        if (username.length() > 50) {
            throw new GameException(ErrorCodes.AUTH_USERNAME_TOO_LONG);
        }
        
        if (password.length() > 100) {
            throw new GameException(ErrorCodes.AUTH_PASSWORD_TOO_LONG);
        }
    }

    /**
     * 检查IP锁定
     * 
     * @param clientIP 客户端IP
     */
    private void checkIPLockout(String clientIP) {
        LoginAttemptRecord record = loginAttempts.get(clientIP);
        if (record != null && record.isLocked()) {
            throw new GameException(ErrorCodes.AUTH_IP_LOCKED);
        }
    }

    /**
     * 记录失败登录
     * 
     * @param clientIP 客户端IP
     */
    private void recordFailedLogin(String clientIP) {
        loginAttempts.compute(clientIP, (ip, record) -> {
            if (record == null) {
                record = new LoginAttemptRecord();
            }
            record.incrementAttempts();
            return record;
        });
    }

    /**
     * 清除失败登录记录
     * 
     * @param clientIP 客户端IP
     */
    private void clearFailedLoginRecord(String clientIP) {
        loginAttempts.remove(clientIP);
    }

    // ========== 事件监听 ==========

    /**
     * 监听登录请求事件
     */
    @EventListener
    public void handleLoginRequest(com.whale.lumina.gateway.GatewayController.LoginRequestEvent event) {
        // 这里应该解析Protocol Buffers消息并处理登录请求
        // 暂时使用模拟实现
        logger.info("处理登录请求事件: sessionId={}", event.getSessionId());
    }

    // ========== 统计信息 ==========

    /**
     * 获取认证统计信息
     * 
     * @return 统计信息
     */
    public AuthStats getAuthStats() {
        return new AuthStats(
            totalLoginAttempts.get(),
            successfulLogins.get(),
            failedLogins.get(),
            sessionStore.getActiveSessionCount(),
            loginAttempts.size()
        );
    }

    // ========== 内部类 ==========

    /**
     * 登录尝试记录
     */
    private class LoginAttemptRecord {
        private int attempts = 0;
        private long lastAttemptTime = System.currentTimeMillis();

        public void incrementAttempts() {
            this.attempts++;
            this.lastAttemptTime = System.currentTimeMillis();
        }

        public boolean isLocked() {
            if (attempts >= maxLoginAttempts) {
                long lockoutEndTime = lastAttemptTime + (lockoutDurationSeconds * 1000L);
                if (System.currentTimeMillis() < lockoutEndTime) {
                    return true;
                } else {
                    // 锁定时间已过，重置尝试次数
                    attempts = 0;
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * 用户信息
     */
    public static class UserInfo {
        private String userId;
        private String username;
        private String passwordHash;
        private String salt;
        private String email;
        private int status; // 0: 禁用, 1: 启用
        private long createdTime;
        private long lastLoginTime;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
        public String getSalt() { return salt; }
        public void setSalt(String salt) { this.salt = salt; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public long getCreatedTime() { return createdTime; }
        public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
        public long getLastLoginTime() { return lastLoginTime; }
        public void setLastLoginTime(long lastLoginTime) { this.lastLoginTime = lastLoginTime; }
        
        public boolean isActive() { return status == 1; }
    }

    /**
     * 认证结果
     */
    public static class AuthResult {
        private final boolean success;
        private final UserInfo userInfo;
        private final AuthSession session;
        private final String errorMessage;

        public AuthResult(boolean success, UserInfo userInfo, AuthSession session, String errorMessage) {
            this.success = success;
            this.userInfo = userInfo;
            this.session = session;
            this.errorMessage = errorMessage;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public UserInfo getUserInfo() { return userInfo; }
        public AuthSession getSession() { return session; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 认证统计信息
     */
    public static class AuthStats {
        private final long totalLoginAttempts;
        private final long successfulLogins;
        private final long failedLogins;
        private final long activeSessionCount;
        private final long lockedIPCount;

        public AuthStats(long totalLoginAttempts, long successfulLogins, long failedLogins, 
                        long activeSessionCount, long lockedIPCount) {
            this.totalLoginAttempts = totalLoginAttempts;
            this.successfulLogins = successfulLogins;
            this.failedLogins = failedLogins;
            this.activeSessionCount = activeSessionCount;
            this.lockedIPCount = lockedIPCount;
        }

        // Getters
        public long getTotalLoginAttempts() { return totalLoginAttempts; }
        public long getSuccessfulLogins() { return successfulLogins; }
        public long getFailedLogins() { return failedLogins; }
        public long getActiveSessionCount() { return activeSessionCount; }
        public long getLockedIPCount() { return lockedIPCount; }
        public double getSuccessRate() { 
            return totalLoginAttempts > 0 ? (double) successfulLogins / totalLoginAttempts : 0.0; 
        }
    }
}