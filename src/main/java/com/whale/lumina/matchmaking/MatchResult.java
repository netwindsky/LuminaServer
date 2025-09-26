package com.whale.lumina.matchmaking;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 匹配结果实体类
 * 
 * 表示一次匹配的结果，包含匹配的玩家、游戏模式、质量评分等信息
 * 
 * @author Lumina Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MatchResult {

    /**
     * 匹配ID
     */
    private String matchId;

    /**
     * 游戏模式
     */
    private String gameMode;

    /**
     * 匹配类型
     */
    private MatchQueue.MatchType matchType;

    /**
     * 匹配的玩家列表
     */
    private List<MatchRequest> players;

    /**
     * 匹配时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime matchTime;

    /**
     * 匹配质量评分（0-100）
     */
    private int qualityScore;

    /**
     * 预计等待时间（秒）
     */
    private int estimatedWaitTime;

    /**
     * 匹配状态
     */
    private MatchStatus status;

    /**
     * 房间ID（匹配成功后分配）
     */
    private String roomId;

    /**
     * 服务器ID
     */
    private String serverId;

    /**
     * 匹配元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    // ========== 构造函数 ==========

    public MatchResult() {
        this.players = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.createTime = LocalDateTime.now();
        this.status = MatchStatus.PENDING;
    }

    public MatchResult(String matchId, String gameMode, MatchQueue.MatchType matchType) {
        this();
        this.matchId = matchId;
        this.gameMode = gameMode;
        this.matchType = matchType;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建快速匹配结果
     * 
     * @param matchId 匹配ID
     * @param gameMode 游戏模式
     * @param players 玩家列表
     * @return 匹配结果
     */
    public static MatchResult createQuickMatch(String matchId, String gameMode, List<MatchRequest> players) {
        MatchResult result = new MatchResult(matchId, gameMode, MatchQueue.MatchType.QUICK_MATCH);
        result.setPlayers(players);
        result.setMatchTime(LocalDateTime.now());
        return result;
    }

    /**
     * 创建排位匹配结果
     * 
     * @param matchId 匹配ID
     * @param gameMode 游戏模式
     * @param players 玩家列表
     * @param qualityScore 质量评分
     * @return 匹配结果
     */
    public static MatchResult createRankedMatch(String matchId, String gameMode, 
                                              List<MatchRequest> players, int qualityScore) {
        MatchResult result = new MatchResult(matchId, gameMode, MatchQueue.MatchType.RANKED_MATCH);
        result.setPlayers(players);
        result.setQualityScore(qualityScore);
        result.setMatchTime(LocalDateTime.now());
        return result;
    }

    /**
     * 创建自定义匹配结果
     * 
     * @param matchId 匹配ID
     * @param gameMode 游戏模式
     * @param players 玩家列表
     * @param metadata 元数据
     * @return 匹配结果
     */
    public static MatchResult createCustomMatch(String matchId, String gameMode, 
                                              List<MatchRequest> players, Map<String, Object> metadata) {
        MatchResult result = new MatchResult(matchId, gameMode, MatchQueue.MatchType.CUSTOM_MATCH);
        result.setPlayers(players);
        result.setMetadata(metadata);
        result.setMatchTime(LocalDateTime.now());
        return result;
    }

    // ========== 业务方法 ==========

    /**
     * 添加玩家到匹配结果
     * 
     * @param player 玩家请求
     */
    public void addPlayer(MatchRequest player) {
        if (players == null) {
            players = new ArrayList<>();
        }
        players.add(player);
    }

    /**
     * 移除玩家
     * 
     * @param playerId 玩家ID
     * @return 是否成功移除
     */
    public boolean removePlayer(String playerId) {
        if (players == null) {
            return false;
        }
        return players.removeIf(player -> Objects.equals(player.getPlayerId(), playerId));
    }

    /**
     * 获取玩家数量
     * 
     * @return 玩家数量
     */
    public int getPlayerCount() {
        return players != null ? players.size() : 0;
    }

    /**
     * 获取玩家ID列表
     * 
     * @return 玩家ID列表
     */
    public List<String> getPlayerIds() {
        List<String> playerIds = new ArrayList<>();
        if (players != null) {
            for (MatchRequest player : players) {
                playerIds.add(player.getPlayerId());
            }
        }
        return playerIds;
    }

    /**
     * 检查是否包含指定玩家
     * 
     * @param playerId 玩家ID
     * @return 是否包含
     */
    public boolean containsPlayer(String playerId) {
        if (players == null) {
            return false;
        }
        return players.stream().anyMatch(player -> Objects.equals(player.getPlayerId(), playerId));
    }

    /**
     * 获取指定玩家的匹配请求
     * 
     * @param playerId 玩家ID
     * @return 匹配请求，如果不存在返回null
     */
    public MatchRequest getPlayerRequest(String playerId) {
        if (players == null) {
            return null;
        }
        return players.stream()
                .filter(player -> Objects.equals(player.getPlayerId(), playerId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 更新匹配状态
     * 
     * @param status 新状态
     */
    public void updateStatus(MatchStatus status) {
        this.status = status;
        
        // 根据状态更新相关字段
        switch (status) {
            case ACCEPTED:
                // 匹配被接受时的处理
                break;
            case REJECTED:
                // 匹配被拒绝时的处理
                break;
            case EXPIRED:
                // 匹配过期时的处理
                if (expireTime == null) {
                    expireTime = LocalDateTime.now();
                }
                break;
            case COMPLETED:
                // 匹配完成时的处理
                break;
        }
    }

    /**
     * 检查匹配是否过期
     * 
     * @return 是否过期
     */
    public boolean isExpired() {
        if (expireTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 检查匹配是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return matchId != null && 
               gameMode != null && 
               matchType != null && 
               players != null && 
               !players.isEmpty() &&
               !isExpired();
    }

    /**
     * 计算匹配持续时间（秒）
     * 
     * @return 持续时间
     */
    public long getDurationSeconds() {
        if (createTime == null) {
            return 0;
        }
        
        LocalDateTime endTime = (status == MatchStatus.COMPLETED || status == MatchStatus.EXPIRED) 
                               ? (expireTime != null ? expireTime : LocalDateTime.now())
                               : LocalDateTime.now();
        
        return java.time.Duration.between(createTime, endTime).getSeconds();
    }

    // ========== 元数据操作 ==========

    /**
     * 设置元数据
     * 
     * @param key 键
     * @param value 值
     */
    public void setMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * 获取元数据
     * 
     * @param key 键
     * @return 值
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * 获取元数据（指定类型）
     * 
     * @param key 键
     * @param type 类型
     * @param <T> 泛型类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = getMetadata(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 移除元数据
     * 
     * @param key 键
     * @return 被移除的值
     */
    public Object removeMetadata(String key) {
        return metadata != null ? metadata.remove(key) : null;
    }

    // ========== Getters and Setters ==========

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public MatchQueue.MatchType getMatchType() { return matchType; }
    public void setMatchType(MatchQueue.MatchType matchType) { this.matchType = matchType; }

    public List<MatchRequest> getPlayers() { return players; }
    public void setPlayers(List<MatchRequest> players) { this.players = players; }

    public LocalDateTime getMatchTime() { return matchTime; }
    public void setMatchTime(LocalDateTime matchTime) { this.matchTime = matchTime; }

    public int getQualityScore() { return qualityScore; }
    public void setQualityScore(int qualityScore) { this.qualityScore = qualityScore; }

    public int getEstimatedWaitTime() { return estimatedWaitTime; }
    public void setEstimatedWaitTime(int estimatedWaitTime) { this.estimatedWaitTime = estimatedWaitTime; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    // ========== Object 方法重写 ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchResult that = (MatchResult) o;
        return Objects.equals(matchId, that.matchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId);
    }

    @Override
    public String toString() {
        return String.format("MatchResult{matchId='%s', gameMode='%s', matchType=%s, " +
                           "playerCount=%d, qualityScore=%d, status=%s}",
                           matchId, gameMode, matchType, getPlayerCount(), qualityScore, status);
    }

    // ========== 枚举类 ==========

    /**
     * 匹配状态枚举
     */
    public enum MatchStatus {
        PENDING,    // 等待中
        ACCEPTED,   // 已接受
        REJECTED,   // 已拒绝
        EXPIRED,    // 已过期
        COMPLETED   // 已完成
    }
}