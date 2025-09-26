package com.whale.lumina.logic;

import com.whale.lumina.common.GameException;
import com.whale.lumina.common.ErrorCodes;
import com.whale.lumina.common.TimeUtils;
import com.whale.lumina.room.Room;
import com.whale.lumina.room.GameResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 战斗服务类
 * 
 * 负责处理游戏中的战斗逻辑，包括伤害计算、技能释放、状态效果等
 * 
 * @author Lumina Team
 */
@Service
public class CombatService {

    private static final Logger logger = LoggerFactory.getLogger(CombatService.class);

    // 战斗会话管理
    private final Map<String, CombatSession> activeCombats;
    private final AtomicLong nextCombatId;

    // 战斗统计
    private final AtomicLong totalCombats;
    private final AtomicLong totalDamageDealt;
    private final AtomicLong totalSkillsUsed;

    /**
     * 构造函数
     */
    public CombatService() {
        this.activeCombats = new ConcurrentHashMap<>();
        this.nextCombatId = new AtomicLong(1);
        this.totalCombats = new AtomicLong(0);
        this.totalDamageDealt = new AtomicLong(0);
        this.totalSkillsUsed = new AtomicLong(0);
    }

    // ========== 战斗会话管理 ==========

    /**
     * 创建战斗会话
     * 
     * @param room 房间对象
     * @param participants 参与者列表
     * @return 战斗会话ID
     * @throws GameException 创建失败时抛出
     */
    public String createCombatSession(Room room, List<String> participants) throws GameException {
        if (room == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "房间不能为空");
        }
        
        if (participants == null || participants.isEmpty()) {
            throw new GameException(ErrorCodes.PLAYER_NOT_FOUND, "参与者列表不能为空");
        }

        String combatId = "combat_" + nextCombatId.getAndIncrement();
        
        try {
            CombatSession session = new CombatSession(combatId, room.getRoomId(), participants);
            activeCombats.put(combatId, session);
            totalCombats.incrementAndGet();
            
            logger.info("战斗会话已创建: combatId={}, roomId={}, participants={}", 
                       combatId, room.getRoomId(), participants.size());
            
            return combatId;
            
        } catch (Exception e) {
            logger.error("创建战斗会话失败: roomId={}", room.getRoomId(), e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "创建战斗会话失败: " + e.getMessage());
        }
    }

    /**
     * 结束战斗会话
     * 
     * @param combatId 战斗会话ID
     * @return 战斗结果
     * @throws GameException 结束失败时抛出
     */
    public CombatResult endCombatSession(String combatId) throws GameException {
        CombatSession session = activeCombats.get(combatId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "战斗会话不存在: " + combatId);
        }

        try {
            CombatResult result = session.end();
            activeCombats.remove(combatId);
            
            logger.info("战斗会话已结束: combatId={}, duration={}ms, winner={}", 
                       combatId, result.getDuration(), result.getWinner());
            
            return result;
            
        } catch (Exception e) {
            logger.error("结束战斗会话失败: combatId={}", combatId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "结束战斗会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取战斗会话
     * 
     * @param combatId 战斗会话ID
     * @return 战斗会话
     */
    public CombatSession getCombatSession(String combatId) {
        return activeCombats.get(combatId);
    }

    /**
     * 检查战斗会话是否存在
     * 
     * @param combatId 战斗会话ID
     * @return 是否存在
     */
    public boolean hasCombatSession(String combatId) {
        return activeCombats.containsKey(combatId);
    }

    // ========== 战斗行为处理 ==========

    /**
     * 处理攻击行为
     * 
     * @param combatId 战斗会话ID
     * @param attackerId 攻击者ID
     * @param targetId 目标ID
     * @param attackData 攻击数据
     * @return 攻击结果
     * @throws GameException 处理失败时抛出
     */
    public AttackResult processAttack(String combatId, String attackerId, String targetId, 
                                    AttackData attackData) throws GameException {
        CombatSession session = activeCombats.get(combatId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "战斗会话不存在: " + combatId);
        }

        try {
            AttackResult result = session.processAttack(attackerId, targetId, attackData);
            totalDamageDealt.addAndGet(result.getDamage());
            
            logger.debug("处理攻击: combatId={}, attacker={}, target={}, damage={}", 
                        combatId, attackerId, targetId, result.getDamage());
            
            return result;
            
        } catch (Exception e) {
            logger.error("处理攻击失败: combatId={}, attacker={}, target={}", 
                        combatId, attackerId, targetId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "处理攻击失败: " + e.getMessage());
        }
    }

    /**
     * 处理技能释放
     * 
     * @param combatId 战斗会话ID
     * @param casterId 释放者ID
     * @param skillId 技能ID
     * @param targets 目标列表
     * @param skillData 技能数据
     * @return 技能结果
     * @throws GameException 处理失败时抛出
     */
    public SkillResult processSkill(String combatId, String casterId, String skillId, 
                                  List<String> targets, SkillData skillData) throws GameException {
        CombatSession session = activeCombats.get(combatId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "战斗会话不存在: " + combatId);
        }

        try {
            SkillResult result = session.processSkill(casterId, skillId, targets, skillData);
            totalSkillsUsed.incrementAndGet();
            
            logger.debug("处理技能: combatId={}, caster={}, skill={}, targets={}", 
                        combatId, casterId, skillId, targets.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("处理技能失败: combatId={}, caster={}, skill={}", 
                        combatId, casterId, skillId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "处理技能失败: " + e.getMessage());
        }
    }

    /**
     * 处理状态效果
     * 
     * @param combatId 战斗会话ID
     * @param playerId 玩家ID
     * @param effectId 效果ID
     * @param effectData 效果数据
     * @return 效果结果
     * @throws GameException 处理失败时抛出
     */
    public EffectResult processEffect(String combatId, String playerId, String effectId, 
                                    EffectData effectData) throws GameException {
        CombatSession session = activeCombats.get(combatId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "战斗会话不存在: " + combatId);
        }

        try {
            EffectResult result = session.processEffect(playerId, effectId, effectData);
            
            logger.debug("处理状态效果: combatId={}, player={}, effect={}", 
                        combatId, playerId, effectId);
            
            return result;
            
        } catch (Exception e) {
            logger.error("处理状态效果失败: combatId={}, player={}, effect={}", 
                        combatId, playerId, effectId, e);
            throw new GameException(ErrorCodes.SYSTEM_ERROR, "处理状态效果失败: " + e.getMessage());
        }
    }

    // ========== 战斗状态查询 ==========

    /**
     * 获取玩家战斗状态
     * 
     * @param combatId 战斗会话ID
     * @param playerId 玩家ID
     * @return 战斗状态
     * @throws GameException 查询失败时抛出
     */
    public CombatState getPlayerCombatState(String combatId, String playerId) throws GameException {
        CombatSession session = activeCombats.get(combatId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "战斗会话不存在: " + combatId);
        }

        return session.getPlayerState(playerId);
    }

    /**
     * 获取战斗会话状态
     * 
     * @param combatId 战斗会话ID
     * @return 会话状态
     * @throws GameException 查询失败时抛出
     */
    public CombatSessionState getCombatSessionState(String combatId) throws GameException {
        CombatSession session = activeCombats.get(combatId);
        if (session == null) {
            throw new GameException(ErrorCodes.ROOM_NOT_FOUND, "战斗会话不存在: " + combatId);
        }

        return session.getSessionState();
    }

    // ========== 统计信息 ==========

    /**
     * 获取活跃战斗数量
     * 
     * @return 活跃战斗数量
     */
    public int getActiveCombatCount() {
        return activeCombats.size();
    }

    /**
     * 获取总战斗数量
     * 
     * @return 总战斗数量
     */
    public long getTotalCombats() {
        return totalCombats.get();
    }

    /**
     * 获取总伤害值
     * 
     * @return 总伤害值
     */
    public long getTotalDamageDealt() {
        return totalDamageDealt.get();
    }

    /**
     * 获取总技能使用次数
     * 
     * @return 总技能使用次数
     */
    public long getTotalSkillsUsed() {
        return totalSkillsUsed.get();
    }

    /**
     * 获取战斗统计信息
     * 
     * @return 统计信息
     */
    public CombatStatistics getCombatStatistics() {
        return new CombatStatistics(
            getActiveCombatCount(),
            getTotalCombats(),
            getTotalDamageDealt(),
            getTotalSkillsUsed()
        );
    }

    // ========== 内部类 ==========

    /**
     * 战斗会话类
     */
    public static class CombatSession {
        private final String combatId;
        private final String roomId;
        private final List<String> participants;
        private final Map<String, CombatState> playerStates;
        private final List<CombatEvent> combatEvents;
        private final long startTime;
        private long endTime;
        private boolean ended;

        public CombatSession(String combatId, String roomId, List<String> participants) {
            this.combatId = combatId;
            this.roomId = roomId;
            this.participants = new ArrayList<>(participants);
            this.playerStates = new ConcurrentHashMap<>();
            this.combatEvents = new ArrayList<>();
            this.startTime = System.currentTimeMillis();
            this.ended = false;

            // 初始化玩家状态
            for (String playerId : participants) {
                playerStates.put(playerId, new CombatState(playerId));
            }
        }

        public AttackResult processAttack(String attackerId, String targetId, AttackData attackData) {
            CombatState attacker = playerStates.get(attackerId);
            CombatState target = playerStates.get(targetId);

            if (attacker == null || target == null) {
                throw new IllegalArgumentException("攻击者或目标不存在");
            }

            // 计算伤害
            int damage = calculateDamage(attacker, target, attackData);
            
            // 应用伤害
            target.takeDamage(damage);
            
            // 记录事件
            CombatEvent event = new CombatEvent("ATTACK", attackerId, targetId, damage);
            combatEvents.add(event);

            return new AttackResult(attackerId, targetId, damage, target.getCurrentHP() <= 0);
        }

        public SkillResult processSkill(String casterId, String skillId, List<String> targets, SkillData skillData) {
            CombatState caster = playerStates.get(casterId);
            if (caster == null) {
                throw new IllegalArgumentException("释放者不存在");
            }

            List<SkillEffect> effects = new ArrayList<>();
            
            for (String targetId : targets) {
                CombatState target = playerStates.get(targetId);
                if (target != null) {
                    // 应用技能效果
                    SkillEffect effect = applySkillEffect(caster, target, skillData);
                    effects.add(effect);
                }
            }

            // 记录事件
            CombatEvent event = new CombatEvent("SKILL", casterId, skillId, effects.size());
            combatEvents.add(event);

            return new SkillResult(casterId, skillId, targets, effects);
        }

        public EffectResult processEffect(String playerId, String effectId, EffectData effectData) {
            CombatState player = playerStates.get(playerId);
            if (player == null) {
                throw new IllegalArgumentException("玩家不存在");
            }

            // 应用状态效果
            boolean applied = player.applyEffect(effectId, effectData);

            // 记录事件
            CombatEvent event = new CombatEvent("EFFECT", playerId, effectId, applied ? 1 : 0);
            combatEvents.add(event);

            return new EffectResult(playerId, effectId, applied);
        }

        private int calculateDamage(CombatState attacker, CombatState target, AttackData attackData) {
            // 简单的伤害计算公式
            int baseDamage = attackData.getBaseDamage();
            int attackPower = attacker.getAttackPower();
            int defense = target.getDefense();
            
            int damage = Math.max(1, baseDamage + attackPower - defense);
            
            // 应用随机因子
            double randomFactor = 0.8 + Math.random() * 0.4; // 0.8-1.2
            damage = (int) (damage * randomFactor);
            
            return damage;
        }

        private SkillEffect applySkillEffect(CombatState caster, CombatState target, SkillData skillData) {
            // 根据技能类型应用不同效果
            switch (skillData.getType()) {
                case "DAMAGE":
                    int damage = skillData.getValue();
                    target.takeDamage(damage);
                    return new SkillEffect(target.getPlayerId(), "DAMAGE", damage);
                    
                case "HEAL":
                    int heal = skillData.getValue();
                    target.heal(heal);
                    return new SkillEffect(target.getPlayerId(), "HEAL", heal);
                    
                case "BUFF":
                    target.applyBuff(skillData.getEffectId(), skillData.getDuration());
                    return new SkillEffect(target.getPlayerId(), "BUFF", skillData.getValue());
                    
                case "DEBUFF":
                    target.applyDebuff(skillData.getEffectId(), skillData.getDuration());
                    return new SkillEffect(target.getPlayerId(), "DEBUFF", skillData.getValue());
                    
                default:
                    return new SkillEffect(target.getPlayerId(), "UNKNOWN", 0);
            }
        }

        public CombatResult end() {
            if (ended) {
                throw new IllegalStateException("战斗会话已结束");
            }

            ended = true;
            endTime = System.currentTimeMillis();

            // 确定获胜者
            String winner = determineWinner();
            
            return new CombatResult(combatId, roomId, participants, winner, 
                                  startTime, endTime, new ArrayList<>(combatEvents));
        }

        private String determineWinner() {
            // 简单的获胜判定：血量最高的玩家获胜
            String winner = null;
            int maxHP = -1;
            
            for (CombatState state : playerStates.values()) {
                if (state.getCurrentHP() > maxHP) {
                    maxHP = state.getCurrentHP();
                    winner = state.getPlayerId();
                }
            }
            
            return winner;
        }

        public CombatState getPlayerState(String playerId) {
            return playerStates.get(playerId);
        }

        public CombatSessionState getSessionState() {
            return new CombatSessionState(combatId, roomId, participants, 
                                        new HashMap<>(playerStates), ended, 
                                        System.currentTimeMillis() - startTime);
        }

        // Getters
        public String getCombatId() { return combatId; }
        public String getRoomId() { return roomId; }
        public List<String> getParticipants() { return new ArrayList<>(participants); }
        public boolean isEnded() { return ended; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
    }

    /**
     * 战斗状态类
     */
    public static class CombatState {
        private final String playerId;
        private int currentHP;
        private int maxHP;
        private int attackPower;
        private int defense;
        private final Map<String, Integer> buffs;
        private final Map<String, Integer> debuffs;

        public CombatState(String playerId) {
            this.playerId = playerId;
            this.maxHP = 100;
            this.currentHP = maxHP;
            this.attackPower = 10;
            this.defense = 5;
            this.buffs = new HashMap<>();
            this.debuffs = new HashMap<>();
        }

        public void takeDamage(int damage) {
            currentHP = Math.max(0, currentHP - damage);
        }

        public void heal(int amount) {
            currentHP = Math.min(maxHP, currentHP + amount);
        }

        public void applyBuff(String buffId, int duration) {
            buffs.put(buffId, duration);
        }

        public void applyDebuff(String debuffId, int duration) {
            debuffs.put(debuffId, duration);
        }

        public boolean applyEffect(String effectId, EffectData effectData) {
            // 根据效果类型应用不同逻辑
            switch (effectData.getType()) {
                case "POISON":
                    takeDamage(effectData.getValue());
                    return true;
                case "REGENERATION":
                    heal(effectData.getValue());
                    return true;
                default:
                    return false;
            }
        }

        // Getters
        public String getPlayerId() { return playerId; }
        public int getCurrentHP() { return currentHP; }
        public int getMaxHP() { return maxHP; }
        public int getAttackPower() { return attackPower; }
        public int getDefense() { return defense; }
        public Map<String, Integer> getBuffs() { return new HashMap<>(buffs); }
        public Map<String, Integer> getDebuffs() { return new HashMap<>(debuffs); }
    }

    // ========== 数据类 ==========

    public static class AttackData {
        private final int baseDamage;
        private final String attackType;

        public AttackData(int baseDamage, String attackType) {
            this.baseDamage = baseDamage;
            this.attackType = attackType;
        }

        public int getBaseDamage() { return baseDamage; }
        public String getAttackType() { return attackType; }
    }

    public static class SkillData {
        private final String type;
        private final int value;
        private final String effectId;
        private final int duration;

        public SkillData(String type, int value, String effectId, int duration) {
            this.type = type;
            this.value = value;
            this.effectId = effectId;
            this.duration = duration;
        }

        public String getType() { return type; }
        public int getValue() { return value; }
        public String getEffectId() { return effectId; }
        public int getDuration() { return duration; }
    }

    public static class EffectData {
        private final String type;
        private final int value;
        private final int duration;

        public EffectData(String type, int value, int duration) {
            this.type = type;
            this.value = value;
            this.duration = duration;
        }

        public String getType() { return type; }
        public int getValue() { return value; }
        public int getDuration() { return duration; }
    }

    // ========== 结果类 ==========

    public static class AttackResult {
        private final String attackerId;
        private final String targetId;
        private final int damage;
        private final boolean targetKilled;

        public AttackResult(String attackerId, String targetId, int damage, boolean targetKilled) {
            this.attackerId = attackerId;
            this.targetId = targetId;
            this.damage = damage;
            this.targetKilled = targetKilled;
        }

        public String getAttackerId() { return attackerId; }
        public String getTargetId() { return targetId; }
        public int getDamage() { return damage; }
        public boolean isTargetKilled() { return targetKilled; }
    }

    public static class SkillResult {
        private final String casterId;
        private final String skillId;
        private final List<String> targets;
        private final List<SkillEffect> effects;

        public SkillResult(String casterId, String skillId, List<String> targets, List<SkillEffect> effects) {
            this.casterId = casterId;
            this.skillId = skillId;
            this.targets = targets;
            this.effects = effects;
        }

        public String getCasterId() { return casterId; }
        public String getSkillId() { return skillId; }
        public List<String> getTargets() { return targets; }
        public List<SkillEffect> getEffects() { return effects; }
    }

    public static class EffectResult {
        private final String playerId;
        private final String effectId;
        private final boolean applied;

        public EffectResult(String playerId, String effectId, boolean applied) {
            this.playerId = playerId;
            this.effectId = effectId;
            this.applied = applied;
        }

        public String getPlayerId() { return playerId; }
        public String getEffectId() { return effectId; }
        public boolean isApplied() { return applied; }
    }

    public static class SkillEffect {
        private final String targetId;
        private final String effectType;
        private final int value;

        public SkillEffect(String targetId, String effectType, int value) {
            this.targetId = targetId;
            this.effectType = effectType;
            this.value = value;
        }

        public String getTargetId() { return targetId; }
        public String getEffectType() { return effectType; }
        public int getValue() { return value; }
    }

    public static class CombatResult {
        private final String combatId;
        private final String roomId;
        private final List<String> participants;
        private final String winner;
        private final long startTime;
        private final long endTime;
        private final List<CombatEvent> events;

        public CombatResult(String combatId, String roomId, List<String> participants, String winner,
                          long startTime, long endTime, List<CombatEvent> events) {
            this.combatId = combatId;
            this.roomId = roomId;
            this.participants = participants;
            this.winner = winner;
            this.startTime = startTime;
            this.endTime = endTime;
            this.events = events;
        }

        public long getDuration() { return endTime - startTime; }

        public String getCombatId() { return combatId; }
        public String getRoomId() { return roomId; }
        public List<String> getParticipants() { return participants; }
        public String getWinner() { return winner; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public List<CombatEvent> getEvents() { return events; }
    }

    public static class CombatEvent {
        private final String type;
        private final String sourceId;
        private final String targetId;
        private final int value;
        private final long timestamp;

        public CombatEvent(String type, String sourceId, String targetId, int value) {
            this.type = type;
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        public String getType() { return type; }
        public String getSourceId() { return sourceId; }
        public String getTargetId() { return targetId; }
        public int getValue() { return value; }
        public long getTimestamp() { return timestamp; }
    }

    public static class CombatSessionState {
        private final String combatId;
        private final String roomId;
        private final List<String> participants;
        private final Map<String, CombatState> playerStates;
        private final boolean ended;
        private final long duration;

        public CombatSessionState(String combatId, String roomId, List<String> participants,
                                Map<String, CombatState> playerStates, boolean ended, long duration) {
            this.combatId = combatId;
            this.roomId = roomId;
            this.participants = participants;
            this.playerStates = playerStates;
            this.ended = ended;
            this.duration = duration;
        }

        public String getCombatId() { return combatId; }
        public String getRoomId() { return roomId; }
        public List<String> getParticipants() { return participants; }
        public Map<String, CombatState> getPlayerStates() { return playerStates; }
        public boolean isEnded() { return ended; }
        public long getDuration() { return duration; }
    }

    public static class CombatStatistics {
        private final int activeCombats;
        private final long totalCombats;
        private final long totalDamage;
        private final long totalSkills;

        public CombatStatistics(int activeCombats, long totalCombats, long totalDamage, long totalSkills) {
            this.activeCombats = activeCombats;
            this.totalCombats = totalCombats;
            this.totalDamage = totalDamage;
            this.totalSkills = totalSkills;
        }

        public int getActiveCombats() { return activeCombats; }
        public long getTotalCombats() { return totalCombats; }
        public long getTotalDamage() { return totalDamage; }
        public long getTotalSkills() { return totalSkills; }
    }
}