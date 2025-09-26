package com.whale.lumina.room;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 房间配置类
 * 
 * 定义房间的各种配置参数，包括游戏模式、玩家数量、游戏规则等
 * 
 * @author Lumina Team
 */
public class RoomConfig {

    // 基本配置
    private String gameMode;
    private int minPlayers;
    private int maxPlayers;
    private boolean isPrivate;
    private String password;
    private String roomName;
    private String description;

    // 游戏配置
    private int gameTimeLimit; // 游戏时间限制（秒）
    private int roundTimeLimit; // 回合时间限制（秒）
    private int maxRounds; // 最大回合数
    private boolean autoStart; // 是否自动开始
    private int autoStartDelay; // 自动开始延迟（秒）
    private boolean allowSpectators; // 是否允许观战
    private int maxSpectators; // 最大观战人数

    // 房间规则
    private boolean allowReconnect; // 是否允许重连
    private int reconnectTimeLimit; // 重连时间限制（秒）
    private boolean kickInactivePlayers; // 是否踢出不活跃玩家
    private int inactiveTimeLimit; // 不活跃时间限制（秒）
    private boolean pauseOnPlayerLeave; // 玩家离开时是否暂停

    // 新增字段
    private boolean ranked; // 是否排位赛
    private boolean tournament; // 是否锦标赛
    private String mapName; // 地图名称
    private Integer gameTime; // 游戏时间（分钟）

    // 扩展配置
    private final Map<String, Object> customSettings;

    /**
     * 默认构造函数
     */
    public RoomConfig() {
        // 设置默认值
        this.gameMode = "default";
        this.minPlayers = 2;
        this.maxPlayers = 10;
        this.isPrivate = false;
        this.password = null;
        this.roomName = "";
        this.description = "";
        
        this.gameTimeLimit = 1800; // 30分钟
        this.roundTimeLimit = 300; // 5分钟
        this.maxRounds = 10;
        this.autoStart = false;
        this.autoStartDelay = 10;
        this.allowSpectators = true;
        this.maxSpectators = 5;
        
        this.allowReconnect = true;
        this.reconnectTimeLimit = 300; // 5分钟
        this.kickInactivePlayers = true;
        this.inactiveTimeLimit = 180; // 3分钟
        this.pauseOnPlayerLeave = false;
        
        this.customSettings = new HashMap<>();
    }

    /**
     * 构造函数
     * 
     * @param gameMode 游戏模式
     * @param minPlayers 最小玩家数
     * @param maxPlayers 最大玩家数
     */
    public RoomConfig(String gameMode, int minPlayers, int maxPlayers) {
        this();
        this.gameMode = gameMode;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    /**
     * 构造函数
     * 
     * @param gameMode 游戏模式
     * @param minPlayers 最小玩家数
     * @param maxPlayers 最大玩家数
     * @param isPrivate 是否私有房间
     * @param roomName 房间名称
     */
    public RoomConfig(String gameMode, int minPlayers, int maxPlayers, boolean isPrivate, String roomName) {
        this(gameMode, minPlayers, maxPlayers);
        this.isPrivate = isPrivate;
        this.roomName = roomName;
    }

    // ========== 验证方法 ==========

    /**
     * 验证配置是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        // 检查基本参数
        if (gameMode == null || gameMode.trim().isEmpty()) {
            return false;
        }
        
        if (minPlayers < 1 || maxPlayers < minPlayers) {
            return false;
        }
        
        if (maxPlayers > 100) { // 设置合理的上限
            return false;
        }
        
        // 检查时间限制
        if (gameTimeLimit < 0 || roundTimeLimit < 0) {
            return false;
        }
        
        if (maxRounds < 1) {
            return false;
        }
        
        if (autoStartDelay < 0) {
            return false;
        }
        
        // 检查观战配置
        if (maxSpectators < 0) {
            return false;
        }
        
        // 检查重连配置
        if (reconnectTimeLimit < 0 || inactiveTimeLimit < 0) {
            return false;
        }
        
        // 检查私有房间配置
        if (isPrivate && (password == null || password.trim().isEmpty())) {
            return false;
        }
        
        return true;
    }

    /**
     * 获取验证错误信息
     * 
     * @return 错误信息列表
     */
    public String getValidationErrors() {
        StringBuilder errors = new StringBuilder();
        
        if (gameMode == null || gameMode.trim().isEmpty()) {
            errors.append("游戏模式不能为空; ");
        }
        
        if (minPlayers < 1) {
            errors.append("最小玩家数必须大于0; ");
        }
        
        if (maxPlayers < minPlayers) {
            errors.append("最大玩家数不能小于最小玩家数; ");
        }
        
        if (maxPlayers > 100) {
            errors.append("最大玩家数不能超过100; ");
        }
        
        if (gameTimeLimit < 0) {
            errors.append("游戏时间限制不能为负数; ");
        }
        
        if (roundTimeLimit < 0) {
            errors.append("回合时间限制不能为负数; ");
        }
        
        if (maxRounds < 1) {
            errors.append("最大回合数必须大于0; ");
        }
        
        if (autoStartDelay < 0) {
            errors.append("自动开始延迟不能为负数; ");
        }
        
        if (maxSpectators < 0) {
            errors.append("最大观战人数不能为负数; ");
        }
        
        if (reconnectTimeLimit < 0) {
            errors.append("重连时间限制不能为负数; ");
        }
        
        if (inactiveTimeLimit < 0) {
            errors.append("不活跃时间限制不能为负数; ");
        }
        
        if (isPrivate && (password == null || password.trim().isEmpty())) {
            errors.append("私有房间必须设置密码; ");
        }
        
        return errors.toString();
    }

    // ========== 自定义设置管理 ==========

    /**
     * 设置自定义配置
     * 
     * @param key 键
     * @param value 值
     */
    public void setCustomSetting(String key, Object value) {
        customSettings.put(key, value);
    }

    /**
     * 获取自定义配置
     * 
     * @param key 键
     * @return 值
     */
    public Object getCustomSetting(String key) {
        return customSettings.get(key);
    }

    /**
     * 获取自定义配置（带类型转换）
     * 
     * @param key 键
     * @param clazz 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomSetting(String key, Class<T> clazz) {
        Object value = customSettings.get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 获取自定义配置（带默认值）
     * 
     * @param key 键
     * @param defaultValue 默认值
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomSetting(String key, T defaultValue) {
        Object value = customSettings.get(key);
        if (value != null && defaultValue.getClass().isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }

    /**
     * 移除自定义配置
     * 
     * @param key 键
     * @return 被移除的值
     */
    public Object removeCustomSetting(String key) {
        return customSettings.remove(key);
    }

    /**
     * 检查是否包含自定义配置
     * 
     * @param key 键
     * @return 是否包含
     */
    public boolean hasCustomSetting(String key) {
        return customSettings.containsKey(key);
    }

    /**
     * 获取所有自定义配置
     * 
     * @return 自定义配置映射
     */
    public Map<String, Object> getCustomSettings() {
        return new HashMap<>(customSettings);
    }

    // ========== 便利方法 ==========

    /**
     * 创建默认配置
     * 
     * @param gameMode 游戏模式
     * @return 配置对象
     */
    public static RoomConfig createDefault(String gameMode) {
        return new RoomConfig(gameMode, 2, 10);
    }

    /**
     * 创建私有房间配置
     * 
     * @param gameMode 游戏模式
     * @param password 密码
     * @param roomName 房间名称
     * @return 配置对象
     */
    public static RoomConfig createPrivate(String gameMode, String password, String roomName) {
        RoomConfig config = new RoomConfig(gameMode, 2, 10, true, roomName);
        config.setPassword(password);
        return config;
    }

    /**
     * 创建快速匹配配置
     * 
     * @param gameMode 游戏模式
     * @param playerCount 玩家数量
     * @return 配置对象
     */
    public static RoomConfig createQuickMatch(String gameMode, int playerCount) {
        RoomConfig config = new RoomConfig(gameMode, playerCount, playerCount);
        config.setAutoStart(true);
        config.setAutoStartDelay(5);
        config.setAllowSpectators(false);
        return config;
    }

    /**
     * 复制配置
     * 
     * @return 新的配置对象
     */
    public RoomConfig copy() {
        RoomConfig copy = new RoomConfig();
        
        // 复制基本配置
        copy.gameMode = this.gameMode;
        copy.minPlayers = this.minPlayers;
        copy.maxPlayers = this.maxPlayers;
        copy.isPrivate = this.isPrivate;
        copy.password = this.password;
        copy.roomName = this.roomName;
        copy.description = this.description;
        
        // 复制游戏配置
        copy.gameTimeLimit = this.gameTimeLimit;
        copy.roundTimeLimit = this.roundTimeLimit;
        copy.maxRounds = this.maxRounds;
        copy.autoStart = this.autoStart;
        copy.autoStartDelay = this.autoStartDelay;
        copy.allowSpectators = this.allowSpectators;
        copy.maxSpectators = this.maxSpectators;
        
        // 复制房间规则
        copy.allowReconnect = this.allowReconnect;
        copy.reconnectTimeLimit = this.reconnectTimeLimit;
        copy.kickInactivePlayers = this.kickInactivePlayers;
        copy.inactiveTimeLimit = this.inactiveTimeLimit;
        copy.pauseOnPlayerLeave = this.pauseOnPlayerLeave;
        
        // 复制自定义设置
        copy.customSettings.putAll(this.customSettings);
        
        return copy;
    }

    // ========== Getters and Setters ==========

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getGameTimeLimit() {
        return gameTimeLimit;
    }

    public void setGameTimeLimit(int gameTimeLimit) {
        this.gameTimeLimit = gameTimeLimit;
    }

    public int getRoundTimeLimit() {
        return roundTimeLimit;
    }

    public void setRoundTimeLimit(int roundTimeLimit) {
        this.roundTimeLimit = roundTimeLimit;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public int getAutoStartDelay() {
        return autoStartDelay;
    }

    public void setAutoStartDelay(int autoStartDelay) {
        this.autoStartDelay = autoStartDelay;
    }

    public boolean isAllowSpectators() {
        return allowSpectators;
    }

    public void setAllowSpectators(boolean allowSpectators) {
        this.allowSpectators = allowSpectators;
    }

    public int getMaxSpectators() {
        return maxSpectators;
    }

    public void setMaxSpectators(int maxSpectators) {
        this.maxSpectators = maxSpectators;
    }

    public boolean isAllowReconnect() {
        return allowReconnect;
    }

    public void setAllowReconnect(boolean allowReconnect) {
        this.allowReconnect = allowReconnect;
    }

    public int getReconnectTimeLimit() {
        return reconnectTimeLimit;
    }

    public void setReconnectTimeLimit(int reconnectTimeLimit) {
        this.reconnectTimeLimit = reconnectTimeLimit;
    }

    public boolean isKickInactivePlayers() {
        return kickInactivePlayers;
    }

    public void setKickInactivePlayers(boolean kickInactivePlayers) {
        this.kickInactivePlayers = kickInactivePlayers;
    }

    public int getInactiveTimeLimit() {
        return inactiveTimeLimit;
    }

    public void setInactiveTimeLimit(int inactiveTimeLimit) {
        this.inactiveTimeLimit = inactiveTimeLimit;
    }

    public boolean isPauseOnPlayerLeave() {
        return pauseOnPlayerLeave;
    }

    public void setPauseOnPlayerLeave(boolean pauseOnPlayerLeave) {
        this.pauseOnPlayerLeave = pauseOnPlayerLeave;
    }

    // 新增方法
    public boolean isRanked() {
        return ranked;
    }

    public void setRanked(boolean ranked) {
        this.ranked = ranked;
    }

    public boolean isTournament() {
        return tournament;
    }

    public void setTournament(boolean tournament) {
        this.tournament = tournament;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public Integer getGameTime() {
        return gameTime;
    }

    public void setGameTime(Integer gameTime) {
        this.gameTime = gameTime;
    }

    // ========== Object方法重写 ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RoomConfig that = (RoomConfig) obj;
        return minPlayers == that.minPlayers &&
               maxPlayers == that.maxPlayers &&
               isPrivate == that.isPrivate &&
               gameTimeLimit == that.gameTimeLimit &&
               roundTimeLimit == that.roundTimeLimit &&
               maxRounds == that.maxRounds &&
               autoStart == that.autoStart &&
               autoStartDelay == that.autoStartDelay &&
               allowSpectators == that.allowSpectators &&
               maxSpectators == that.maxSpectators &&
               allowReconnect == that.allowReconnect &&
               reconnectTimeLimit == that.reconnectTimeLimit &&
               kickInactivePlayers == that.kickInactivePlayers &&
               inactiveTimeLimit == that.inactiveTimeLimit &&
               pauseOnPlayerLeave == that.pauseOnPlayerLeave &&
               Objects.equals(gameMode, that.gameMode) &&
               Objects.equals(password, that.password) &&
               Objects.equals(roomName, that.roomName) &&
               Objects.equals(description, that.description) &&
               Objects.equals(customSettings, that.customSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameMode, minPlayers, maxPlayers, isPrivate, password, roomName, 
                          description, gameTimeLimit, roundTimeLimit, maxRounds, autoStart, 
                          autoStartDelay, allowSpectators, maxSpectators, allowReconnect, 
                          reconnectTimeLimit, kickInactivePlayers, inactiveTimeLimit, 
                          pauseOnPlayerLeave, customSettings);
    }

    @Override
    public String toString() {
        return String.format("RoomConfig{gameMode='%s', players=%d-%d, private=%s, autoStart=%s}", 
                           gameMode, minPlayers, maxPlayers, isPrivate, autoStart);
    }

    /**
     * 获取详细信息
     * 
     * @return 详细信息字符串
     */
    public String getDetailedInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Room Configuration:\n");
        sb.append("  Game Mode: ").append(gameMode).append("\n");
        sb.append("  Players: ").append(minPlayers).append("-").append(maxPlayers).append("\n");
        sb.append("  Private: ").append(isPrivate).append("\n");
        
        if (roomName != null && !roomName.isEmpty()) {
            sb.append("  Name: ").append(roomName).append("\n");
        }
        
        if (description != null && !description.isEmpty()) {
            sb.append("  Description: ").append(description).append("\n");
        }
        
        sb.append("  Game Time Limit: ").append(gameTimeLimit).append("s\n");
        sb.append("  Round Time Limit: ").append(roundTimeLimit).append("s\n");
        sb.append("  Max Rounds: ").append(maxRounds).append("\n");
        sb.append("  Auto Start: ").append(autoStart);
        
        if (autoStart) {
            sb.append(" (").append(autoStartDelay).append("s delay)");
        }
        sb.append("\n");
        
        sb.append("  Allow Spectators: ").append(allowSpectators);
        if (allowSpectators) {
            sb.append(" (max ").append(maxSpectators).append(")");
        }
        sb.append("\n");
        
        sb.append("  Allow Reconnect: ").append(allowReconnect);
        if (allowReconnect) {
            sb.append(" (").append(reconnectTimeLimit).append("s limit)");
        }
        sb.append("\n");
        
        sb.append("  Kick Inactive: ").append(kickInactivePlayers);
        if (kickInactivePlayers) {
            sb.append(" (").append(inactiveTimeLimit).append("s limit)");
        }
        sb.append("\n");
        
        sb.append("  Pause on Leave: ").append(pauseOnPlayerLeave).append("\n");
        
        if (!customSettings.isEmpty()) {
            sb.append("  Custom Settings: ").append(customSettings.size()).append(" items\n");
        }
        
        return sb.toString();
    }
}