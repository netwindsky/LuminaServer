package com.whale.lumina.player;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家实体类
 * 
 * 表示游戏中的玩家信息，包括基本信息、游戏状态、统计数据等
 * 
 * @author Lumina Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Player {

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("avatar")
    private String avatar;

    @JsonProperty("level")
    private int level;

    @JsonProperty("experience")
    private long experience;

    @JsonProperty("status")
    private PlayerStatus status;

    @JsonProperty("currentRoomId")
    private String currentRoomId;

    @JsonProperty("currentSessionId")
    private String currentSessionId;

    @JsonProperty("gameStats")
    private GameStats gameStats;

    @JsonProperty("preferences")
    private PlayerPreferences preferences;

    @JsonProperty("createdTime")
    private LocalDateTime createdTime;

    @JsonProperty("lastLoginTime")
    private LocalDateTime lastLoginTime;

    @JsonProperty("lastActiveTime")
    private LocalDateTime lastActiveTime;

    @JsonProperty("onlineTime")
    private long onlineTime; // 总在线时间（分钟）

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * 玩家状态枚举
     */
    public enum PlayerStatus {
        OFFLINE,        // 离线
        ONLINE,         // 在线
        IN_LOBBY,       // 在大厅
        IN_ROOM,        // 在房间中
        IN_GAME,        // 游戏中
        SPECTATING,     // 观战中
        AWAY,           // 离开
        BUSY            // 忙碌
    }

    /**
     * 游戏统计数据
     */
    public static class GameStats {
        @JsonProperty("totalGames")
        private int totalGames;

        @JsonProperty("wins")
        private int wins;

        @JsonProperty("losses")
        private int losses;

        @JsonProperty("draws")
        private int draws;

        @JsonProperty("totalPlayTime")
        private long totalPlayTime; // 总游戏时间（分钟）

        @JsonProperty("averageGameTime")
        private double averageGameTime; // 平均游戏时间（分钟）

        @JsonProperty("winRate")
        private double winRate; // 胜率

        @JsonProperty("ranking")
        private int ranking; // 排名

        @JsonProperty("score")
        private long score; // 总分数

        @JsonProperty("achievements")
        private Set<String> achievements; // 成就列表

        @JsonProperty("gameTypeStats")
        private Map<String, GameTypeStats> gameTypeStats; // 按游戏类型的统计

        /**
         * 游戏类型统计
         */
        public static class GameTypeStats {
            @JsonProperty("gameType")
            private String gameType;

            @JsonProperty("games")
            private int games;

            @JsonProperty("wins")
            private int wins;

            @JsonProperty("losses")
            private int losses;

            @JsonProperty("draws")
            private int draws;

            @JsonProperty("bestScore")
            private long bestScore;

            @JsonProperty("averageScore")
            private double averageScore;

            public GameTypeStats() {}

            public GameTypeStats(String gameType) {
                this.gameType = gameType;
            }

            // Getters and Setters
            public String getGameType() { return gameType; }
            public void setGameType(String gameType) { this.gameType = gameType; }

            public int getGames() { return games; }
            public void setGames(int games) { this.games = games; }

            public int getWins() { return wins; }
            public void setWins(int wins) { this.wins = wins; }

            public int getLosses() { return losses; }
            public void setLosses(int losses) { this.losses = losses; }

            public int getDraws() { return draws; }
            public void setDraws(int draws) { this.draws = draws; }

            public long getBestScore() { return bestScore; }
            public void setBestScore(long bestScore) { this.bestScore = bestScore; }

            public double getAverageScore() { return averageScore; }
            public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

            public double getWinRate() {
                return games > 0 ? (double) wins / games * 100 : 0.0;
            }
        }

        /**
         * 默认构造函数
         */
        public GameStats() {
            this.achievements = new HashSet<>();
            this.gameTypeStats = new HashMap<>();
        }

        /**
         * 更新游戏结果
         */
        public void updateGameResult(String gameType, boolean won, boolean draw, long score, long gameTimeMinutes) {
            totalGames++;
            totalPlayTime += gameTimeMinutes;
            
            if (won) {
                wins++;
            } else if (draw) {
                draws++;
            } else {
                losses++;
            }
            
            this.score += score;
            updateWinRate();
            updateAverageGameTime();
            
            // 更新游戏类型统计
            updateGameTypeStats(gameType, won, draw, score);
        }

        /**
         * 更新胜率
         */
        private void updateWinRate() {
            winRate = totalGames > 0 ? (double) wins / totalGames * 100 : 0.0;
        }

        /**
         * 更新平均游戏时间
         */
        private void updateAverageGameTime() {
            averageGameTime = totalGames > 0 ? (double) totalPlayTime / totalGames : 0.0;
        }

        /**
         * 更新游戏类型统计
         */
        private void updateGameTypeStats(String gameType, boolean won, boolean draw, long score) {
            GameTypeStats typeStats = gameTypeStats.computeIfAbsent(gameType, GameTypeStats::new);
            
            typeStats.games++;
            if (won) {
                typeStats.wins++;
            } else if (draw) {
                typeStats.draws++;
            } else {
                typeStats.losses++;
            }
            
            if (score > typeStats.bestScore) {
                typeStats.bestScore = score;
            }
            
            // 更新平均分数
            typeStats.averageScore = (typeStats.averageScore * (typeStats.games - 1) + score) / typeStats.games;
        }

        /**
         * 添加成就
         */
        public void addAchievement(String achievement) {
            achievements.add(achievement);
        }

        /**
         * 检查是否有成就
         */
        public boolean hasAchievement(String achievement) {
            return achievements.contains(achievement);
        }

        // Getters and Setters
        public int getTotalGames() { return totalGames; }
        public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }

        public int getLosses() { return losses; }
        public void setLosses(int losses) { this.losses = losses; }

        public int getDraws() { return draws; }
        public void setDraws(int draws) { this.draws = draws; }

        public long getTotalPlayTime() { return totalPlayTime; }
        public void setTotalPlayTime(long totalPlayTime) { this.totalPlayTime = totalPlayTime; }

        public double getAverageGameTime() { return averageGameTime; }
        public void setAverageGameTime(double averageGameTime) { this.averageGameTime = averageGameTime; }

        public double getWinRate() { return winRate; }
        public void setWinRate(double winRate) { this.winRate = winRate; }

        public int getRanking() { return ranking; }
        public void setRanking(int ranking) { this.ranking = ranking; }

        public long getScore() { return score; }
        public void setScore(long score) { this.score = score; }

        public Set<String> getAchievements() { return achievements; }
        public void setAchievements(Set<String> achievements) { this.achievements = achievements; }

        public Map<String, GameTypeStats> getGameTypeStats() { return gameTypeStats; }
        public void setGameTypeStats(Map<String, GameTypeStats> gameTypeStats) { this.gameTypeStats = gameTypeStats; }
    }

    /**
     * 玩家偏好设置
     */
    public static class PlayerPreferences {
        @JsonProperty("language")
        private String language;

        @JsonProperty("soundEnabled")
        private boolean soundEnabled;

        @JsonProperty("musicEnabled")
        private boolean musicEnabled;

        @JsonProperty("notificationsEnabled")
        private boolean notificationsEnabled;

        @JsonProperty("autoMatchmaking")
        private boolean autoMatchmaking;

        @JsonProperty("preferredGameModes")
        private Set<String> preferredGameModes;

        @JsonProperty("blockedPlayers")
        private Set<String> blockedPlayers;

        @JsonProperty("friendsList")
        private Set<String> friendsList;

        @JsonProperty("customSettings")
        private Map<String, Object> customSettings;

        /**
         * 默认构造函数
         */
        public PlayerPreferences() {
            this.language = "zh-CN";
            this.soundEnabled = true;
            this.musicEnabled = true;
            this.notificationsEnabled = true;
            this.autoMatchmaking = false;
            this.preferredGameModes = new HashSet<>();
            this.blockedPlayers = new HashSet<>();
            this.friendsList = new HashSet<>();
            this.customSettings = new HashMap<>();
        }

        // Getters and Setters
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public boolean isSoundEnabled() { return soundEnabled; }
        public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }

        public boolean isMusicEnabled() { return musicEnabled; }
        public void setMusicEnabled(boolean musicEnabled) { this.musicEnabled = musicEnabled; }

        public boolean isNotificationsEnabled() { return notificationsEnabled; }
        public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

        public boolean isAutoMatchmaking() { return autoMatchmaking; }
        public void setAutoMatchmaking(boolean autoMatchmaking) { this.autoMatchmaking = autoMatchmaking; }

        public Set<String> getPreferredGameModes() { return preferredGameModes; }
        public void setPreferredGameModes(Set<String> preferredGameModes) { this.preferredGameModes = preferredGameModes; }

        public Set<String> getBlockedPlayers() { return blockedPlayers; }
        public void setBlockedPlayers(Set<String> blockedPlayers) { this.blockedPlayers = blockedPlayers; }

        public Set<String> getFriendsList() { return friendsList; }
        public void setFriendsList(Set<String> friendsList) { this.friendsList = friendsList; }

        public Map<String, Object> getCustomSettings() { return customSettings; }
        public void setCustomSettings(Map<String, Object> customSettings) { this.customSettings = customSettings; }
    }

    /**
     * 默认构造函数
     */
    public Player() {
        this.level = 1;
        this.experience = 0;
        this.status = PlayerStatus.OFFLINE;
        this.gameStats = new GameStats();
        this.preferences = new PlayerPreferences();
        this.createdTime = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
        this.onlineTime = 0;
        this.metadata = new ConcurrentHashMap<>();
    }

    /**
     * 构造函数
     */
    public Player(String playerId, String userId, String username) {
        this();
        this.playerId = playerId;
        this.userId = userId;
        this.username = username;
        this.nickname = username; // 默认昵称为用户名
    }

    // ========== 状态管理方法 ==========

    /**
     * 玩家上线
     */
    public void goOnline() {
        if (status == PlayerStatus.OFFLINE) {
            status = PlayerStatus.ONLINE;
            lastLoginTime = LocalDateTime.now();
            updateActiveTime();
        }
    }

    /**
     * 玩家下线
     */
    public void goOffline() {
        if (status != PlayerStatus.OFFLINE) {
            // 计算本次在线时间
            if (lastLoginTime != null) {
                long sessionTime = java.time.Duration.between(lastLoginTime, LocalDateTime.now()).toMinutes();
                onlineTime += sessionTime;
            }
            
            status = PlayerStatus.OFFLINE;
            currentRoomId = null;
            currentSessionId = null;
            updateActiveTime();
        }
    }

    /**
     * 进入房间
     */
    public void enterRoom(String roomId) {
        this.currentRoomId = roomId;
        this.status = PlayerStatus.IN_ROOM;
        updateActiveTime();
    }

    /**
     * 离开房间
     */
    public void leaveRoom() {
        this.currentRoomId = null;
        this.status = PlayerStatus.IN_LOBBY;
        updateActiveTime();
    }

    /**
     * 开始游戏
     */
    public void startGame() {
        if (status == PlayerStatus.IN_ROOM) {
            status = PlayerStatus.IN_GAME;
            updateActiveTime();
        }
    }

    /**
     * 结束游戏
     */
    public void endGame() {
        if (status == PlayerStatus.IN_GAME) {
            status = currentRoomId != null ? PlayerStatus.IN_ROOM : PlayerStatus.IN_LOBBY;
            updateActiveTime();
        }
    }

    /**
     * 开始观战
     */
    public void startSpectating() {
        status = PlayerStatus.SPECTATING;
        updateActiveTime();
    }

    /**
     * 设置状态为离开
     */
    public void setAway() {
        if (status != PlayerStatus.OFFLINE && status != PlayerStatus.IN_GAME) {
            status = PlayerStatus.AWAY;
            updateActiveTime();
        }
    }

    /**
     * 设置状态为忙碌
     */
    public void setBusy() {
        if (status != PlayerStatus.OFFLINE && status != PlayerStatus.IN_GAME) {
            status = PlayerStatus.BUSY;
            updateActiveTime();
        }
    }

    /**
     * 更新活跃时间
     */
    public void updateActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }

    // ========== 经验和等级管理 ==========

    /**
     * 添加经验值
     */
    public void addExperience(long exp) {
        this.experience += exp;
        updateLevel();
    }

    /**
     * 更新等级
     */
    private void updateLevel() {
        int newLevel = calculateLevel(experience);
        if (newLevel > level) {
            level = newLevel;
            // 可以在这里触发升级事件
        }
    }

    /**
     * 计算等级
     */
    private int calculateLevel(long exp) {
        // 简单的等级计算公式：每1000经验升一级
        return (int) (exp / 1000) + 1;
    }

    /**
     * 获取下一级所需经验
     */
    public long getExperienceToNextLevel() {
        long nextLevelExp = (long) level * 1000;
        return Math.max(0, nextLevelExp - experience);
    }

    /**
     * 获取当前等级进度百分比
     */
    public double getLevelProgress() {
        long currentLevelExp = (long) (level - 1) * 1000;
        long nextLevelExp = (long) level * 1000;
        long progressExp = experience - currentLevelExp;
        return (double) progressExp / (nextLevelExp - currentLevelExp) * 100;
    }

    // ========== 游戏统计更新 ==========

    /**
     * 更新游戏结果
     */
    public void updateGameResult(String gameType, boolean won, boolean draw, long score, long gameTimeMinutes) {
        gameStats.updateGameResult(gameType, won, draw, score, gameTimeMinutes);
        
        // 根据游戏结果添加经验
        long expGain = calculateExperienceGain(won, draw, score);
        addExperience(expGain);
    }

    /**
     * 计算经验获得
     */
    private long calculateExperienceGain(boolean won, boolean draw, long score) {
        long baseExp = 10;
        if (won) {
            baseExp += 20;
        } else if (draw) {
            baseExp += 10;
        }
        
        // 根据分数获得额外经验
        baseExp += score / 100;
        
        return baseExp;
    }

    // ========== 社交功能 ==========

    /**
     * 添加好友
     */
    public void addFriend(String friendId) {
        preferences.getFriendsList().add(friendId);
    }

    /**
     * 移除好友
     */
    public void removeFriend(String friendId) {
        preferences.getFriendsList().remove(friendId);
    }

    /**
     * 检查是否是好友
     */
    public boolean isFriend(String playerId) {
        return preferences.getFriendsList().contains(playerId);
    }

    /**
     * 屏蔽玩家
     */
    public void blockPlayer(String playerId) {
        preferences.getBlockedPlayers().add(playerId);
    }

    /**
     * 取消屏蔽玩家
     */
    public void unblockPlayer(String playerId) {
        preferences.getBlockedPlayers().remove(playerId);
    }

    /**
     * 检查是否屏蔽了玩家
     */
    public boolean isBlocked(String playerId) {
        return preferences.getBlockedPlayers().contains(playerId);
    }

    // ========== 状态检查方法 ==========

    /**
     * 检查是否在线
     */
    public boolean isOnline() {
        return status != PlayerStatus.OFFLINE;
    }

    /**
     * 检查是否在游戏中
     */
    public boolean isInGame() {
        return status == PlayerStatus.IN_GAME;
    }

    /**
     * 检查是否在房间中
     */
    public boolean isInRoom() {
        return currentRoomId != null && !currentRoomId.isEmpty();
    }

    /**
     * 检查是否可用（可以接收邀请等）
     */
    public boolean isAvailable() {
        return status == PlayerStatus.ONLINE || status == PlayerStatus.IN_LOBBY;
    }

    /**
     * 检查是否空闲超时
     */
    public boolean isIdleTimeout(long timeoutMinutes) {
        if (lastActiveTime == null) {
            return false;
        }
        long idleMinutes = java.time.Duration.between(lastActiveTime, LocalDateTime.now()).toMinutes();
        return idleMinutes > timeoutMinutes;
    }

    // ========== 元数据操作 ==========

    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 移除元数据
     */
    public void removeMetadata(String key) {
        metadata.remove(key);
    }

    // ========== Getters and Setters ==========

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public long getExperience() { return experience; }
    public void setExperience(long experience) { this.experience = experience; }

    public PlayerStatus getStatus() { return status; }
    public void setStatus(PlayerStatus status) { this.status = status; }

    public String getCurrentRoomId() { return currentRoomId; }
    public void setCurrentRoomId(String currentRoomId) { this.currentRoomId = currentRoomId; }

    public String getCurrentSessionId() { return currentSessionId; }
    public void setCurrentSessionId(String currentSessionId) { this.currentSessionId = currentSessionId; }

    public GameStats getGameStats() { return gameStats; }
    public void setGameStats(GameStats gameStats) { this.gameStats = gameStats; }

    public PlayerPreferences getPreferences() { return preferences; }
    public void setPreferences(PlayerPreferences preferences) { this.preferences = preferences; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime lastLoginTime) { this.lastLoginTime = lastLoginTime; }

    public LocalDateTime getLastActiveTime() { return lastActiveTime; }
    public void setLastActiveTime(LocalDateTime lastActiveTime) { this.lastActiveTime = lastActiveTime; }

    public long getOnlineTime() { return onlineTime; }
    public void setOnlineTime(long onlineTime) { this.onlineTime = onlineTime; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(playerId, player.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }

    @Override
    public String toString() {
        return String.format("Player{playerId='%s', userId='%s', username='%s', nickname='%s', " +
                           "level=%d, status=%s, currentRoomId='%s'}",
                           playerId, userId, username, nickname, level, status, currentRoomId);
    }
}