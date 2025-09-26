-- 创建数据库
CREATE DATABASE IF NOT EXISTS lumina_game 
  DEFAULT CHARACTER SET utf8mb4 
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE lumina_game;

-- 玩家表
CREATE TABLE IF NOT EXISTS players (
                                       player_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '玩家ID',
    user_id VARCHAR(64) NOT NULL UNIQUE COMMENT '用户ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    nickname VARCHAR(128) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像URL',
    level INT NOT NULL DEFAULT 1 COMMENT '等级',
    experience BIGINT NOT NULL DEFAULT 0 COMMENT '经验值',
    status ENUM('OFFLINE', 'ONLINE', 'IN_LOBBY', 'IN_ROOM', 'IN_GAME', 'SPECTATING', 'AWAY', 'BUSY')
    NOT NULL DEFAULT 'OFFLINE' COMMENT '玩家状态',
    current_room_id VARCHAR(64) COMMENT '当前房间ID',
    current_session_id VARCHAR(64) COMMENT '当前会话ID',
    total_games INT NOT NULL DEFAULT 0 COMMENT '总游戏数',
    wins INT NOT NULL DEFAULT 0 COMMENT '胜利数',
    losses INT NOT NULL DEFAULT 0 COMMENT '失败数',
    draws INT NOT NULL DEFAULT 0 COMMENT '平局数',
    total_play_time BIGINT NOT NULL DEFAULT 0 COMMENT '总游戏时间(秒)',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_login_time TIMESTAMP NULL COMMENT '最后登录时间',
    last_active_time TIMESTAMP NULL COMMENT '最后活跃时间',
    online_time BIGINT NOT NULL DEFAULT 0 COMMENT '总在线时间(分钟)',
    INDEX idx_username (username),
    INDEX idx_user_id (user_id),
    INDEX idx_current_room (current_room_id),
    INDEX idx_status (status)
    ) ENGINE=InnoDB COMMENT='玩家信息表';

-- 房间表
CREATE TABLE IF NOT EXISTS rooms (
                                     room_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '房间ID',
    game_id VARCHAR(64) NOT NULL UNIQUE COMMENT '游戏ID',
    owner_id VARCHAR(64) NOT NULL COMMENT '房主ID',
    status ENUM('WAITING', 'PLAYING', 'PAUSED', 'FINISHED')
    NOT NULL DEFAULT 'WAITING' COMMENT '房间状态',
    max_players INT NOT NULL DEFAULT 8 COMMENT '最大玩家数',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_active_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    game_start_time TIMESTAMP NULL COMMENT '游戏开始时间',
    game_end_time TIMESTAMP NULL COMMENT '游戏结束时间',
    INDEX idx_owner (owner_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
    ) ENGINE=InnoDB COMMENT='房间信息表';

-- 房间配置表
CREATE TABLE IF NOT EXISTS room_configs (
                                            config_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
                                            room_id VARCHAR(64) NOT NULL COMMENT '房间ID',
    game_mode VARCHAR(32) NOT NULL COMMENT '游戏模式',
    map_name VARCHAR(64) COMMENT '地图名称',
    room_name VARCHAR(128) COMMENT '房间名称',
    is_private BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否私有',
    password VARCHAR(255) COMMENT '房间密码',
    max_players INT NOT NULL DEFAULT 8 COMMENT '最大玩家数',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (room_id) REFERENCES rooms(room_id) ON DELETE CASCADE,
    INDEX idx_room_id (room_id),
    INDEX idx_game_mode (game_mode)
    ) ENGINE=InnoDB COMMENT='房间配置表';

-- 房间玩家关系表
CREATE TABLE IF NOT EXISTS room_players (
                                            room_id VARCHAR(64) NOT NULL COMMENT '房间ID',
    player_id VARCHAR(64) NOT NULL COMMENT '玩家ID',
    join_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    player_status ENUM('JOINED', 'READY', 'PLAYING', 'LEFT')
    NOT NULL DEFAULT 'JOINED' COMMENT '玩家在房间中的状态',
    PRIMARY KEY (room_id, player_id),
    FOREIGN KEY (room_id) REFERENCES rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    INDEX idx_player_id (player_id),
    INDEX idx_join_time (join_time)
    ) ENGINE=InnoDB COMMENT='房间玩家关系表';

-- 游戏结果表
CREATE TABLE IF NOT EXISTS game_results (
                                            result_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '结果ID',
                                            game_id VARCHAR(64) NOT NULL UNIQUE COMMENT '游戏ID',
    room_id VARCHAR(64) NOT NULL COMMENT '房间ID',
    winner_id VARCHAR(64) COMMENT '获胜者ID',
    result_type ENUM('WIN', 'LOSE', 'DRAW', 'CANCELLED')
    NOT NULL DEFAULT 'CANCELLED' COMMENT '结果类型',
    duration INT NOT NULL COMMENT '游戏时长(秒)',
    end_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结束时间',
    details JSON COMMENT '详细结果信息',
    FOREIGN KEY (room_id) REFERENCES rooms(room_id),
    INDEX idx_room_id (room_id),
    INDEX idx_winner_id (winner_id),
    INDEX idx_end_time (end_time)
    ) ENGINE=InnoDB COMMENT='游戏结果表';

-- 会话表
CREATE TABLE IF NOT EXISTS sessions (
                                        session_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '会话ID',
    player_id VARCHAR(64) NOT NULL COMMENT '玩家ID',
    ip_address VARCHAR(45) COMMENT 'IP地址',
    user_agent TEXT COMMENT '用户代理',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_active_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    expire_time TIMESTAMP NOT NULL COMMENT '过期时间',
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    INDEX idx_player_id (player_id),
    INDEX idx_last_active (last_active_time),
    INDEX idx_expire_time (expire_time)
    ) ENGINE=InnoDB COMMENT='会话信息表';

-- 匹配队列表
CREATE TABLE IF NOT EXISTS match_queues (
                                            queue_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '队列ID',
                                            player_id VARCHAR(64) NOT NULL COMMENT '玩家ID',
    game_mode VARCHAR(32) NOT NULL COMMENT '游戏模式',
    status ENUM('WAITING', 'MATCHED', 'CANCELLED')
    NOT NULL DEFAULT 'WAITING' COMMENT '匹配状态',
    rating INT COMMENT '玩家评分',
    join_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    INDEX idx_player_id (player_id),
    INDEX idx_game_mode (game_mode),
    INDEX idx_status (status),
    INDEX idx_join_time (join_time)
    ) ENGINE=InnoDB COMMENT='匹配队列表';

-- 玩家统计数据表
CREATE TABLE IF NOT EXISTS player_stats (
                                            player_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '玩家ID',
    kills INT NOT NULL DEFAULT 0 COMMENT '击杀数',
    deaths INT NOT NULL DEFAULT 0 COMMENT '死亡数',
    assists INT NOT NULL DEFAULT 0 COMMENT '助攻数',
    score INT NOT NULL DEFAULT 0 COMMENT '得分',
    play_time BIGINT NOT NULL DEFAULT 0 COMMENT '游戏时间(秒)',
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE
    ) ENGINE=InnoDB COMMENT='玩家统计数据表';
