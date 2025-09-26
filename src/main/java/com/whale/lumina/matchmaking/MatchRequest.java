package com.whale.lumina.matchmaking;

import com.whale.lumina.player.Player;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 匹配请求实体
 * 
 * 表示玩家的匹配请求，包含匹配条件、优先级和状态信息
 * 
 * @author Lumina Team
 */
public class MatchRequest {

    private String requestId;
    private String playerId;
    private String gameMode;
    private MatchQueue.MatchType matchType;
    private LocalDateTime requestTime;
    private int priority;
    private MatchStatus status;
    
    // 匹配条件
    private MatchCriteria criteria;
    
    // 玩家信息（缓存）
    private transient Player player;
    
    // 扩展属性
    private Map<String, Object> metadata;

    /**
     * 默认构造函数
     */
    public MatchRequest() {
        this.requestId = generateRequestId();
        this.requestTime = LocalDateTime.now();
        this.priority = 0;
        this.status = MatchStatus.WAITING;
        this.criteria = new MatchCriteria();
        this.metadata = new HashMap<>();
    }

    /**
     * 构造函数
     * 
     * @param playerId 玩家ID
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     */
    public MatchRequest(String playerId, String gameMode, MatchQueue.MatchType matchType) {
        this();
        this.playerId = playerId;
        this.gameMode = gameMode;
        this.matchType = matchType;
    }

    /**
     * 构造函数
     * 
     * @param playerId 玩家ID
     * @param gameMode 游戏模式
     * @param matchType 匹配类型
     * @param criteria 匹配条件
     */
    public MatchRequest(String playerId, String gameMode, MatchQueue.MatchType matchType, MatchCriteria criteria) {
        this(playerId, gameMode, matchType);
        this.criteria = criteria != null ? criteria : new MatchCriteria();
    }

    // ========== 优先级管理 ==========

    /**
     * 计算优先级
     * 
     * 基于等待时间、玩家等级、匹配类型等因素计算优先级
     */
    public void calculatePriority() {
        int basePriority = 0;
        
        // 基于匹配类型的基础优先级
        switch (matchType) {
            case QUICK_MATCH:
                basePriority = 100;
                break;
            case RANKED_MATCH:
                basePriority = 200;
                break;
            case CUSTOM_MATCH:
                basePriority = 50;
                break;
            case TOURNAMENT:
                basePriority = 300;
                break;
        }
        
        // 基于等待时间的优先级提升
        long waitTimeMinutes = getWaitTimeMinutes();
        int waitTimePriority = (int) (waitTimeMinutes * 10);
        
        // 基于玩家等级的优先级调整
        int levelPriority = 0;
        if (player != null) {
            levelPriority = Math.min(player.getLevel(), 50); // 最多50点等级优先级
        }
        
        // 基于匹配条件的优先级调整
        int criteriaPriority = criteria.getPriorityBonus();
        
        this.priority = basePriority + waitTimePriority + levelPriority + criteriaPriority;
    }

    /**
     * 提升优先级
     * 
     * 用于长时间等待的玩家
     */
    public void boostPriority() {
        this.priority += 50;
        calculatePriority(); // 重新计算以确保合理性
    }

    /**
     * 重置优先级
     */
    public void resetPriority() {
        this.priority = 0;
        calculatePriority();
    }

    // ========== 匹配兼容性 ==========

    /**
     * 检查与另一个请求的兼容性
     * 
     * @param other 另一个匹配请求
     * @return 兼容性分数（0-100，100表示完全兼容）
     */
    public int getCompatibilityScore(MatchRequest other) {
        if (other == null || !this.gameMode.equals(other.gameMode) || this.matchType != other.matchType) {
            return 0;
        }
        
        return criteria.calculateCompatibility(other.criteria);
    }

    /**
     * 检查是否可以与另一个请求匹配
     * 
     * @param other 另一个匹配请求
     * @return 是否可以匹配
     */
    public boolean isCompatibleWith(MatchRequest other) {
        return getCompatibilityScore(other) >= criteria.getMinCompatibilityScore();
    }

    /**
     * 检查是否可以与玩家列表匹配
     * 
     * @param others 其他匹配请求列表
     * @return 是否可以匹配
     */
    public boolean isCompatibleWithGroup(java.util.List<MatchRequest> others) {
        if (others == null || others.isEmpty()) {
            return true;
        }
        
        for (MatchRequest other : others) {
            if (!isCompatibleWith(other)) {
                return false;
            }
        }
        
        return true;
    }

    // ========== 状态管理 ==========

    /**
     * 更新状态
     * 
     * @param newStatus 新状态
     */
    public void updateStatus(MatchStatus newStatus) {
        this.status = newStatus;
        addMetadata("lastStatusUpdate", LocalDateTime.now());
    }

    /**
     * 检查请求是否仍然活跃
     * 
     * @return 是否活跃
     */
    public boolean isActive() {
        return status == MatchStatus.WAITING || status == MatchStatus.MATCHING;
    }

    /**
     * 检查请求是否已完成
     * 
     * @return 是否已完成
     */
    public boolean isCompleted() {
        return status == MatchStatus.MATCHED || status == MatchStatus.CANCELLED || status == MatchStatus.EXPIRED;
    }

    // ========== 时间相关方法 ==========

    /**
     * 获取等待时间（分钟）
     * 
     * @return 等待时间
     */
    public long getWaitTimeMinutes() {
        return java.time.Duration.between(requestTime, LocalDateTime.now()).toMinutes();
    }

    /**
     * 获取等待时间（毫秒）
     * 
     * @return 等待时间
     */
    @JsonIgnore
    public long getRequestTimeMillis() {
        return java.time.ZoneId.systemDefault().getRules()
            .getOffset(requestTime).getTotalSeconds() * 1000 +
            requestTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 检查是否超时
     * 
     * @param timeoutMinutes 超时时间（分钟）
     * @return 是否超时
     */
    public boolean isTimeout(int timeoutMinutes) {
        return getWaitTimeMinutes() >= timeoutMinutes;
    }

    // ========== 元数据管理 ==========

    /**
     * 添加元数据
     * 
     * @param key 键
     * @param value 值
     */
    public void addMetadata(String key, Object value) {
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
     * 移除元数据
     * 
     * @param key 键
     */
    public void removeMetadata(String key) {
        if (metadata != null) {
            metadata.remove(key);
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 生成请求ID
     * 
     * @return 请求ID
     */
    private String generateRequestId() {
        return "req_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    /**
     * 创建快速匹配请求
     * 
     * @param playerId 玩家ID
     * @param gameMode 游戏模式
     * @return 匹配请求
     */
    public static MatchRequest createQuickMatch(String playerId, String gameMode) {
        MatchCriteria criteria = new MatchCriteria();
        criteria.setSkillRangePercent(30); // 技能范围30%
        criteria.setMaxWaitTime(300); // 最大等待5分钟
        criteria.setMinCompatibilityScore(60); // 最小兼容性60%
        
        return new MatchRequest(playerId, gameMode, MatchQueue.MatchType.QUICK_MATCH, criteria);
    }

    /**
     * 创建排位匹配请求
     * 
     * @param playerId 玩家ID
     * @param gameMode 游戏模式
     * @return 匹配请求
     */
    public static MatchRequest createRankedMatch(String playerId, String gameMode) {
        MatchCriteria criteria = new MatchCriteria();
        criteria.setSkillRangePercent(15); // 技能范围15%
        criteria.setMaxWaitTime(600); // 最大等待10分钟
        criteria.setMinCompatibilityScore(80); // 最小兼容性80%
        
        return new MatchRequest(playerId, gameMode, MatchQueue.MatchType.RANKED_MATCH, criteria);
    }

    // ========== Getters and Setters ==========

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public MatchQueue.MatchType getMatchType() { return matchType; }
    public void setMatchType(MatchQueue.MatchType matchType) { this.matchType = matchType; }

    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public MatchCriteria getCriteria() { return criteria; }
    public void setCriteria(MatchCriteria criteria) { this.criteria = criteria; }

    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    // ========== Object Methods ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchRequest that = (MatchRequest) o;
        return Objects.equals(requestId, that.requestId) && Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, playerId);
    }

    @Override
    public String toString() {
        return String.format("MatchRequest{requestId='%s', playerId='%s', gameMode='%s', " +
                           "matchType=%s, priority=%d, status=%s, waitTime=%d min}",
                           requestId, playerId, gameMode, matchType, priority, status, getWaitTimeMinutes());
    }

    // ========== 内部类 ==========

    /**
     * 匹配状态枚举
     */
    public enum MatchStatus {
        WAITING,    // 等待中
        MATCHING,   // 匹配中
        MATCHED,    // 已匹配
        CANCELLED,  // 已取消
        EXPIRED     // 已过期
    }

    /**
     * 匹配条件类
     */
    public static class MatchCriteria {
        private int skillRangePercent = 20; // 技能范围百分比
        private int maxWaitTime = 600; // 最大等待时间（秒）
        private int minCompatibilityScore = 70; // 最小兼容性分数
        private boolean allowCrossPlatform = true; // 是否允许跨平台
        private String preferredRegion; // 首选区域
        private Map<String, Object> customCriteria; // 自定义条件

        public MatchCriteria() {
            this.customCriteria = new HashMap<>();
        }

        /**
         * 计算与另一个条件的兼容性
         * 
         * @param other 另一个匹配条件
         * @return 兼容性分数（0-100）
         */
        public int calculateCompatibility(MatchCriteria other) {
            if (other == null) {
                return 0;
            }
            
            int score = 100;
            
            // 跨平台兼容性
            if (this.allowCrossPlatform != other.allowCrossPlatform) {
                score -= 20;
            }
            
            // 区域兼容性
            if (this.preferredRegion != null && other.preferredRegion != null) {
                if (!this.preferredRegion.equals(other.preferredRegion)) {
                    score -= 30;
                }
            }
            
            // 技能范围兼容性
            int skillDiff = Math.abs(this.skillRangePercent - other.skillRangePercent);
            score -= Math.min(skillDiff, 20);
            
            return Math.max(0, score);
        }

        /**
         * 获取优先级加成
         * 
         * @return 优先级加成
         */
        public int getPriorityBonus() {
            int bonus = 0;
            
            // 严格匹配条件给予更高优先级
            if (skillRangePercent <= 10) {
                bonus += 20;
            }
            if (minCompatibilityScore >= 90) {
                bonus += 15;
            }
            
            return bonus;
        }

        // Getters and Setters
        public int getSkillRangePercent() { return skillRangePercent; }
        public void setSkillRangePercent(int skillRangePercent) { this.skillRangePercent = skillRangePercent; }

        public int getMaxWaitTime() { return maxWaitTime; }
        public void setMaxWaitTime(int maxWaitTime) { this.maxWaitTime = maxWaitTime; }

        public int getMinCompatibilityScore() { return minCompatibilityScore; }
        public void setMinCompatibilityScore(int minCompatibilityScore) { this.minCompatibilityScore = minCompatibilityScore; }

        public boolean isAllowCrossPlatform() { return allowCrossPlatform; }
        public void setAllowCrossPlatform(boolean allowCrossPlatform) { this.allowCrossPlatform = allowCrossPlatform; }

        public String getPreferredRegion() { return preferredRegion; }
        public void setPreferredRegion(String preferredRegion) { this.preferredRegion = preferredRegion; }

        public Map<String, Object> getCustomCriteria() { return customCriteria; }
        public void setCustomCriteria(Map<String, Object> customCriteria) { this.customCriteria = customCriteria; }

        @Override
        public String toString() {
            return String.format("MatchCriteria{skillRange=%d%%, maxWaitTime=%ds, " +
                               "minCompatibility=%d%%, crossPlatform=%s, region='%s'}",
                               skillRangePercent, maxWaitTime, minCompatibilityScore, 
                               allowCrossPlatform, preferredRegion);
        }
    }
}