package com.whale.lumina.room;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 房间搜索条件类
 * 
 * 用于定义房间搜索和过滤的条件
 * 
 * @author Lumina Team
 */
public class RoomSearchCriteria {

    // 基本搜索条件
    private String gameMode;
    private Room.RoomStatus status;
    private Boolean isPrivate;
    private String roomName;
    private String ownerId;

    // 玩家数量条件
    private Integer minPlayers;
    private Integer maxPlayers;
    private Integer exactPlayers;
    private Boolean hasAvailableSlots;

    // 时间条件
    private Long createdAfter;
    private Long createdBefore;
    private Long lastActiveAfter;
    private Long lastActiveBefore;

    // 游戏配置条件
    private Integer maxGameTimeLimit;
    private Integer minGameTimeLimit;
    private Boolean autoStart;
    private Boolean allowSpectators;
    private Boolean allowReconnect;

    // 排序和分页
    private SortBy sortBy;
    private SortOrder sortOrder;
    private Integer limit;
    private Integer offset;

    // 自定义条件
    private final Map<String, Object> customCriteria;

    /**
     * 排序字段枚举
     */
    public enum SortBy {
        CREATE_TIME,
        LAST_ACTIVE_TIME,
        PLAYER_COUNT,
        ROOM_NAME,
        GAME_MODE
    }

    /**
     * 排序顺序枚举
     */
    public enum SortOrder {
        ASC,
        DESC
    }

    /**
     * 默认构造函数
     */
    public RoomSearchCriteria() {
        this.customCriteria = new HashMap<>();
        this.sortBy = SortBy.LAST_ACTIVE_TIME;
        this.sortOrder = SortOrder.DESC;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建空的搜索条件
     * 
     * @return 搜索条件对象
     */
    public static RoomSearchCriteria empty() {
        return new RoomSearchCriteria();
    }

    /**
     * 按游戏模式搜索
     * 
     * @param gameMode 游戏模式
     * @return 搜索条件对象
     */
    public static RoomSearchCriteria byGameMode(String gameMode) {
        RoomSearchCriteria criteria = new RoomSearchCriteria();
        criteria.setGameMode(gameMode);
        return criteria;
    }

    /**
     * 按房间状态搜索
     * 
     * @param status 房间状态
     * @return 搜索条件对象
     */
    public static RoomSearchCriteria byStatus(Room.RoomStatus status) {
        RoomSearchCriteria criteria = new RoomSearchCriteria();
        criteria.setStatus(status);
        return criteria;
    }

    /**
     * 搜索可加入的房间
     * 
     * @return 搜索条件对象
     */
    public static RoomSearchCriteria joinable() {
        RoomSearchCriteria criteria = new RoomSearchCriteria();
        criteria.setStatus(Room.RoomStatus.WAITING);
        criteria.setHasAvailableSlots(true);
        criteria.setIsPrivate(false);
        return criteria;
    }

    /**
     * 按房主搜索
     * 
     * @param ownerId 房主ID
     * @return 搜索条件对象
     */
    public static RoomSearchCriteria byOwner(String ownerId) {
        RoomSearchCriteria criteria = new RoomSearchCriteria();
        criteria.setOwnerId(ownerId);
        return criteria;
    }

    /**
     * 搜索公开房间
     * 
     * @return 搜索条件对象
     */
    public static RoomSearchCriteria publicRooms() {
        RoomSearchCriteria criteria = new RoomSearchCriteria();
        criteria.setIsPrivate(false);
        return criteria;
    }

    // ========== 链式设置方法 ==========

    /**
     * 设置游戏模式
     * 
     * @param gameMode 游戏模式
     * @return 当前对象
     */
    public RoomSearchCriteria withGameMode(String gameMode) {
        this.gameMode = gameMode;
        return this;
    }

    /**
     * 设置房间状态
     * 
     * @param status 房间状态
     * @return 当前对象
     */
    public RoomSearchCriteria withStatus(Room.RoomStatus status) {
        this.status = status;
        return this;
    }

    /**
     * 设置是否私有
     * 
     * @param isPrivate 是否私有
     * @return 当前对象
     */
    public RoomSearchCriteria withPrivate(Boolean isPrivate) {
        this.isPrivate = isPrivate;
        return this;
    }

    /**
     * 设置房间名称
     * 
     * @param roomName 房间名称
     * @return 当前对象
     */
    public RoomSearchCriteria withRoomName(String roomName) {
        this.roomName = roomName;
        return this;
    }

    /**
     * 设置房主ID
     * 
     * @param ownerId 房主ID
     * @return 当前对象
     */
    public RoomSearchCriteria withOwner(String ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    /**
     * 设置最小玩家数
     * 
     * @param minPlayers 最小玩家数
     * @return 当前对象
     */
    public RoomSearchCriteria withMinPlayers(Integer minPlayers) {
        this.minPlayers = minPlayers;
        return this;
    }

    /**
     * 设置最大玩家数
     * 
     * @param maxPlayers 最大玩家数
     * @return 当前对象
     */
    public RoomSearchCriteria withMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
        return this;
    }

    /**
     * 设置精确玩家数
     * 
     * @param exactPlayers 精确玩家数
     * @return 当前对象
     */
    public RoomSearchCriteria withExactPlayers(Integer exactPlayers) {
        this.exactPlayers = exactPlayers;
        return this;
    }

    /**
     * 设置玩家数范围
     * 
     * @param min 最小值
     * @param max 最大值
     * @return 当前对象
     */
    public RoomSearchCriteria withPlayerRange(Integer min, Integer max) {
        this.minPlayers = min;
        this.maxPlayers = max;
        return this;
    }

    /**
     * 设置是否有可用位置
     * 
     * @param hasAvailableSlots 是否有可用位置
     * @return 当前对象
     */
    public RoomSearchCriteria withAvailableSlots(Boolean hasAvailableSlots) {
        this.hasAvailableSlots = hasAvailableSlots;
        return this;
    }

    /**
     * 设置创建时间范围
     * 
     * @param after 之后
     * @param before 之前
     * @return 当前对象
     */
    public RoomSearchCriteria withCreatedTime(Long after, Long before) {
        this.createdAfter = after;
        this.createdBefore = before;
        return this;
    }

    /**
     * 设置最后活跃时间范围
     * 
     * @param after 之后
     * @param before 之前
     * @return 当前对象
     */
    public RoomSearchCriteria withLastActiveTime(Long after, Long before) {
        this.lastActiveAfter = after;
        this.lastActiveBefore = before;
        return this;
    }

    /**
     * 设置游戏时间限制范围
     * 
     * @param min 最小值
     * @param max 最大值
     * @return 当前对象
     */
    public RoomSearchCriteria withGameTimeLimit(Integer min, Integer max) {
        this.minGameTimeLimit = min;
        this.maxGameTimeLimit = max;
        return this;
    }

    /**
     * 设置是否自动开始
     * 
     * @param autoStart 是否自动开始
     * @return 当前对象
     */
    public RoomSearchCriteria withAutoStart(Boolean autoStart) {
        this.autoStart = autoStart;
        return this;
    }

    /**
     * 设置是否允许观战
     * 
     * @param allowSpectators 是否允许观战
     * @return 当前对象
     */
    public RoomSearchCriteria withAllowSpectators(Boolean allowSpectators) {
        this.allowSpectators = allowSpectators;
        return this;
    }

    /**
     * 设置是否允许重连
     * 
     * @param allowReconnect 是否允许重连
     * @return 当前对象
     */
    public RoomSearchCriteria withAllowReconnect(Boolean allowReconnect) {
        this.allowReconnect = allowReconnect;
        return this;
    }

    /**
     * 设置排序方式
     * 
     * @param sortBy 排序字段
     * @param sortOrder 排序顺序
     * @return 当前对象
     */
    public RoomSearchCriteria withSort(SortBy sortBy, SortOrder sortOrder) {
        this.sortBy = sortBy;
        this.sortOrder = sortOrder;
        return this;
    }

    /**
     * 设置分页
     * 
     * @param limit 限制数量
     * @param offset 偏移量
     * @return 当前对象
     */
    public RoomSearchCriteria withPaging(Integer limit, Integer offset) {
        this.limit = limit;
        this.offset = offset;
        return this;
    }

    /**
     * 设置自定义条件
     * 
     * @param key 键
     * @param value 值
     * @return 当前对象
     */
    public RoomSearchCriteria withCustomCriteria(String key, Object value) {
        this.customCriteria.put(key, value);
        return this;
    }

    // ========== 验证方法 ==========

    /**
     * 验证搜索条件是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        // 检查玩家数量条件
        if (minPlayers != null && minPlayers < 0) {
            return false;
        }
        
        if (maxPlayers != null && maxPlayers < 0) {
            return false;
        }
        
        if (minPlayers != null && maxPlayers != null && minPlayers > maxPlayers) {
            return false;
        }
        
        if (exactPlayers != null && exactPlayers < 0) {
            return false;
        }
        
        // 检查时间条件
        if (createdAfter != null && createdBefore != null && createdAfter > createdBefore) {
            return false;
        }
        
        if (lastActiveAfter != null && lastActiveBefore != null && lastActiveAfter > lastActiveBefore) {
            return false;
        }
        
        // 检查游戏时间限制
        if (minGameTimeLimit != null && minGameTimeLimit < 0) {
            return false;
        }
        
        if (maxGameTimeLimit != null && maxGameTimeLimit < 0) {
            return false;
        }
        
        if (minGameTimeLimit != null && maxGameTimeLimit != null && minGameTimeLimit > maxGameTimeLimit) {
            return false;
        }
        
        // 检查分页参数
        if (limit != null && limit <= 0) {
            return false;
        }
        
        if (offset != null && offset < 0) {
            return false;
        }
        
        return true;
    }

    /**
     * 检查是否为空条件
     * 
     * @return 是否为空
     */
    public boolean isEmpty() {
        return gameMode == null &&
               status == null &&
               isPrivate == null &&
               roomName == null &&
               ownerId == null &&
               minPlayers == null &&
               maxPlayers == null &&
               exactPlayers == null &&
               hasAvailableSlots == null &&
               createdAfter == null &&
               createdBefore == null &&
               lastActiveAfter == null &&
               lastActiveBefore == null &&
               maxGameTimeLimit == null &&
               minGameTimeLimit == null &&
               autoStart == null &&
               allowSpectators == null &&
               allowReconnect == null &&
               customCriteria.isEmpty();
    }

    /**
     * 清空所有条件
     */
    public void clear() {
        gameMode = null;
        status = null;
        isPrivate = null;
        roomName = null;
        ownerId = null;
        minPlayers = null;
        maxPlayers = null;
        exactPlayers = null;
        hasAvailableSlots = null;
        createdAfter = null;
        createdBefore = null;
        lastActiveAfter = null;
        lastActiveBefore = null;
        maxGameTimeLimit = null;
        minGameTimeLimit = null;
        autoStart = null;
        allowSpectators = null;
        allowReconnect = null;
        customCriteria.clear();
    }

    // ========== Getters and Setters ==========

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public Room.RoomStatus getStatus() {
        return status;
    }

    public void setStatus(Room.RoomStatus status) {
        this.status = status;
    }

    public Boolean getIsPrivate() {
        return isPrivate;
    }

    public void setIsPrivate(Boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Integer getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(Integer minPlayers) {
        this.minPlayers = minPlayers;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public Integer getExactPlayers() {
        return exactPlayers;
    }

    public void setExactPlayers(Integer exactPlayers) {
        this.exactPlayers = exactPlayers;
    }

    public Boolean getHasAvailableSlots() {
        return hasAvailableSlots;
    }

    public void setHasAvailableSlots(Boolean hasAvailableSlots) {
        this.hasAvailableSlots = hasAvailableSlots;
    }

    public Long getCreatedAfter() {
        return createdAfter;
    }

    public void setCreatedAfter(Long createdAfter) {
        this.createdAfter = createdAfter;
    }

    public Long getCreatedBefore() {
        return createdBefore;
    }

    public void setCreatedBefore(Long createdBefore) {
        this.createdBefore = createdBefore;
    }

    public Long getLastActiveAfter() {
        return lastActiveAfter;
    }

    public void setLastActiveAfter(Long lastActiveAfter) {
        this.lastActiveAfter = lastActiveAfter;
    }

    public Long getLastActiveBefore() {
        return lastActiveBefore;
    }

    public void setLastActiveBefore(Long lastActiveBefore) {
        this.lastActiveBefore = lastActiveBefore;
    }

    public Integer getMaxGameTimeLimit() {
        return maxGameTimeLimit;
    }

    public void setMaxGameTimeLimit(Integer maxGameTimeLimit) {
        this.maxGameTimeLimit = maxGameTimeLimit;
    }

    public Integer getMinGameTimeLimit() {
        return minGameTimeLimit;
    }

    public void setMinGameTimeLimit(Integer minGameTimeLimit) {
        this.minGameTimeLimit = minGameTimeLimit;
    }

    public Boolean getAutoStart() {
        return autoStart;
    }

    public void setAutoStart(Boolean autoStart) {
        this.autoStart = autoStart;
    }

    public Boolean getAllowSpectators() {
        return allowSpectators;
    }

    public void setAllowSpectators(Boolean allowSpectators) {
        this.allowSpectators = allowSpectators;
    }

    public Boolean getAllowReconnect() {
        return allowReconnect;
    }

    public void setAllowReconnect(Boolean allowReconnect) {
        this.allowReconnect = allowReconnect;
    }

    public SortBy getSortBy() {
        return sortBy;
    }

    public void setSortBy(SortBy sortBy) {
        this.sortBy = sortBy;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Map<String, Object> getCustomCriteria() {
        return new HashMap<>(customCriteria);
    }

    public Object getCustomCriteria(String key) {
        return customCriteria.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getCustomCriteria(String key, Class<T> clazz) {
        Object value = customCriteria.get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // ========== Object方法重写 ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RoomSearchCriteria that = (RoomSearchCriteria) obj;
        return Objects.equals(gameMode, that.gameMode) &&
               status == that.status &&
               Objects.equals(isPrivate, that.isPrivate) &&
               Objects.equals(roomName, that.roomName) &&
               Objects.equals(ownerId, that.ownerId) &&
               Objects.equals(minPlayers, that.minPlayers) &&
               Objects.equals(maxPlayers, that.maxPlayers) &&
               Objects.equals(exactPlayers, that.exactPlayers) &&
               Objects.equals(hasAvailableSlots, that.hasAvailableSlots) &&
               Objects.equals(createdAfter, that.createdAfter) &&
               Objects.equals(createdBefore, that.createdBefore) &&
               Objects.equals(lastActiveAfter, that.lastActiveAfter) &&
               Objects.equals(lastActiveBefore, that.lastActiveBefore) &&
               Objects.equals(maxGameTimeLimit, that.maxGameTimeLimit) &&
               Objects.equals(minGameTimeLimit, that.minGameTimeLimit) &&
               Objects.equals(autoStart, that.autoStart) &&
               Objects.equals(allowSpectators, that.allowSpectators) &&
               Objects.equals(allowReconnect, that.allowReconnect) &&
               sortBy == that.sortBy &&
               sortOrder == that.sortOrder &&
               Objects.equals(limit, that.limit) &&
               Objects.equals(offset, that.offset) &&
               Objects.equals(customCriteria, that.customCriteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameMode, status, isPrivate, roomName, ownerId, minPlayers, maxPlayers,
                          exactPlayers, hasAvailableSlots, createdAfter, createdBefore, lastActiveAfter,
                          lastActiveBefore, maxGameTimeLimit, minGameTimeLimit, autoStart, allowSpectators,
                          allowReconnect, sortBy, sortOrder, limit, offset, customCriteria);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RoomSearchCriteria{");
        
        if (gameMode != null) sb.append("gameMode='").append(gameMode).append("', ");
        if (status != null) sb.append("status=").append(status).append(", ");
        if (isPrivate != null) sb.append("private=").append(isPrivate).append(", ");
        if (roomName != null) sb.append("roomName='").append(roomName).append("', ");
        if (ownerId != null) sb.append("ownerId='").append(ownerId).append("', ");
        if (minPlayers != null || maxPlayers != null) {
            sb.append("players=").append(minPlayers).append("-").append(maxPlayers).append(", ");
        }
        if (exactPlayers != null) sb.append("exactPlayers=").append(exactPlayers).append(", ");
        if (hasAvailableSlots != null) sb.append("hasSlots=").append(hasAvailableSlots).append(", ");
        
        if (sb.length() > "RoomSearchCriteria{".length()) {
            sb.setLength(sb.length() - 2); // 移除最后的 ", "
        }
        
        sb.append("}");
        return sb.toString();
    }
}