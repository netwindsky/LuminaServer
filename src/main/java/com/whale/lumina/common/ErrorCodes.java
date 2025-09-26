package com.whale.lumina.common;

/**
 * 游戏服务器统一错误码定义
 *
 * 错误码格式：模块前缀 + 具体错误编号
 * - AUTH_xxx: 认证相关错误 (1000-1999)
 * - GATEWAY_xxx: 网关相关错误 (2000-2999)
 * - MATCH_xxx: 匹配相关错误 (3000-3999)
 * - ROOM_xxx: 房间相关错误 (4000-4999)
 * - PLAYER_xxx: 玩家相关错误 (5000-5999)
 * - DATA_xxx: 数据层错误 (6000-6999)
 * - WEBRTC_xxx: WebRTC相关错误 (7000-7999)
 * - SYSTEM_xxx: 系统级错误 (9000-9999)
 *
 * @author Lumina Team
 */
public final class ErrorCodes {

    // 成功
    public static final String SUCCESS = "0000";

    // ========== 认证模块错误 (1000-1999) ==========
    public static final String AUTH_INVALID_TOKEN = "1001";
    public static final String AUTH_TOKEN_EXPIRED = "1002";
    public static final String AUTH_USER_NOT_FOUND = "1003";
    public static final String AUTH_INVALID_CREDENTIALS = "1004";
    public static final String AUTH_SESSION_NOT_FOUND = "1005";
    public static final String AUTH_DUPLICATE_LOGIN = "1006";
    public static final String AUTH_INVALID_PASSWORD = "1007";
    public static final String AUTH_USER_DISABLED = "1008";
    public static final String AUTH_LOGIN_FAILED = "1009";
    public static final String AUTH_SESSION_EXPIRED = "1010";
    public static final String AUTH_TOKEN_REFRESH_FAILED = "1011";
    public static final String AUTH_TOKEN_VALIDATION_FAILED = "1012";
    public static final String AUTH_LOGOUT_FAILED = "1013";
    public static final String AUTH_TOKEN_GENERATION_FAILED = "1014";
    public static final String AUTH_SESSION_CREATION_FAILED = "1015";
    public static final String AUTH_INVALID_USERNAME = "1016";
    public static final String AUTH_USERNAME_TOO_LONG = "1017";
    public static final String AUTH_PASSWORD_TOO_LONG = "1018";
    public static final String AUTH_IP_LOCKED = "1019";
    public static final String AUTH_SESSION_SAVE_FAILED = "1020";
    public static final String AUTH_SESSION_UPDATE_FAILED = "1021";
    public static final String AUTH_NOT_AUTHENTICATED = "1022";

    // ========== 网关模块错误 (2000-2999) ==========
    public static final String GATEWAY_CONNECTION_LIMIT = "2001";
    public static final String GATEWAY_INVALID_MESSAGE = "2002";
    public static final String GATEWAY_SESSION_CLOSED = "2003";
    public static final String GATEWAY_RATE_LIMIT = "2004";
    public static final String GATEWAY_CODEC_ERROR = "2005";
    public static final String GATEWAY_UNSUPPORTED_MESSAGE_TYPE = "2006";
    public static final String GATEWAY_MESSAGE_PROCESS_FAILED = "2007";
    public static final String GATEWAY_INVALID_MESSAGE_FORMAT = "2008";
    public static final String GATEWAY_MESSAGE_TOO_LARGE = "2009";
    public static final String GATEWAY_MESSAGE_ENCODE_FAILED = "2010";
    public static final String GATEWAY_INVALID_MESSAGE_LENGTH = "2011";
    public static final String GATEWAY_MESSAGE_DECODE_FAILED = "2012";
    public static final String GATEWAY_MESSAGE_CREATE_FAILED = "2013";
    public static final String GATEWAY_SERVER_START_FAILED = "2014";
    public static final String GATEWAY_SESSION_NOT_FOUND = "2015";

    // ========== 匹配模块错误 (3000-3999) ==========
    public static final String MATCH_QUEUE_FULL = "3001";
    public static final String MATCH_INVALID_MODE = "3002";
    public static final String MATCH_ALREADY_IN_QUEUE = "3003";
    public static final String MATCH_TICKET_NOT_FOUND = "3004";
    public static final String MATCH_TIMEOUT = "3005";
    public static final String MATCH_CANCELLED = "3006";
    public static final String MATCH_REQUEST_FAILED = "3007";
    public static final String PLAYER_ALREADY_IN_QUEUE = "3008";
    public static final String QUEUE_FULL = "3009";

    // ========== 房间模块错误 (4000-4999) ==========
    public static final String ROOM_NOT_FOUND = "4001";
    public static final String ROOM_FULL = "4002";
    public static final String ROOM_ALREADY_JOINED = "4003";
    public static final String ROOM_NOT_JOINED = "4004";
    public static final String ROOM_INVALID_STATE = "4005";
    public static final String ROOM_CREATE_FAILED = "4006";
    public static final String ROOM_MAX_ROOMS_REACHED = "4007";
    public static final String ROOM_PLAYER_ALREADY_IN_ROOM = "4008";
    public static final String ROOM_NOT_JOINABLE = "4009";
    public static final String ROOM_JOIN_FAILED = "4010";
    public static final String ROOM_INVALID_STATUS = "4011";
    public static final String ROOM_NOT_ENOUGH_PLAYERS = "4012";
    public static final String ROOM_START_GAME_FAILED = "4013";
    public static final String ROOM_INPUT_PROCESS_FAILED = "4014";

    // ========== 玩家模块错误 (5000-5999) ==========
    public static final String PLAYER_NOT_FOUND = "5001";
    public static final String PLAYER_INVALID_INPUT = "5002";
    public static final String PLAYER_INVALID_POSITION = "5003";
    public static final String PLAYER_SPEED_EXCEEDED = "5004";
    public static final String PLAYER_ACTION_COOLDOWN = "5005";

    // ========== 数据层错误 (6000-6999) ==========
    public static final String DATA_REDIS_CONNECTION_FAILED = "6001";
    public static final String DATA_MYSQL_CONNECTION_FAILED = "6002";
    public static final String DATA_SERIALIZATION_ERROR = "6003";
    public static final String DATA_DESERIALIZATION_ERROR = "6004";
    public static final String DATA_SAVE_FAILED = "6005";
    public static final String DATA_LOAD_FAILED = "6006";
    public static final String DATA_MYSQL_QUERY_FAILED = "6007";
    public static final String DATA_MYSQL_UPDATE_FAILED = "6008";
    public static final String DATA_MYSQL_BATCH_UPDATE_FAILED = "6009";
    public static final String DATA_MYSQL_TRANSACTION_FAILED = "6010";
    public static final String DATA_MYSQL_INSERT_FAILED = "6011";
    public static final String DATA_REDIS_OPERATION_FAILED = "6012";
    public static final String DATA_SYNC_LOAD_FAILED = "6013";
    public static final String DATA_SYNC_UPDATE_FAILED = "6014";
    public static final String DATA_SYNC_DELETE_FAILED = "6015";

    // ========== WebRTC模块错误 (7000-7999) ==========
    public static final String WEBRTC_NEGOTIATION_FAILED = "7001";
    public static final String WEBRTC_ICE_TIMEOUT = "7002";
    public static final String WEBRTC_INVALID_SDP = "7003";
    public static final String WEBRTC_PEER_NOT_FOUND = "7004";
    public static final String WEBRTC_SIGNALING_ERROR = "7005";
    public static final String WEBRTC_SIGNAL_FAILED = "7006";

    // ========== 系统级错误 (9000-9999) ==========
    public static final String SYSTEM_INTERNAL_ERROR = "9001";
    public static final String SYSTEM_SERVICE_UNAVAILABLE = "9002";
    public static final String SYSTEM_TIMEOUT = "9003";
    public static final String SYSTEM_RESOURCE_EXHAUSTED = "9004";
    public static final String SYSTEM_CONFIGURATION_ERROR = "9005";
    public static final String SYSTEM_ERROR = "9006";

    // 错误码描述映射
    public static String getErrorMessage(String errorCode) {
        switch (errorCode) {
            case SUCCESS:
                return "操作成功";

            // 认证模块
            case AUTH_INVALID_TOKEN:
                return "无效的认证令牌";
            case AUTH_TOKEN_EXPIRED:
                return "认证令牌已过期";
            case AUTH_USER_NOT_FOUND:
                return "用户不存在";
            case AUTH_INVALID_CREDENTIALS:
                return "用户名或密码错误";
            case AUTH_SESSION_NOT_FOUND:
                return "会话不存在";
            case AUTH_DUPLICATE_LOGIN:
                return "重复登录";
            case AUTH_INVALID_PASSWORD:
                return "密码错误";
            case AUTH_USER_DISABLED:
                return "用户已被禁用";
            case AUTH_LOGIN_FAILED:
                return "登录失败";
            case AUTH_SESSION_EXPIRED:
                return "会话已过期";
            case AUTH_TOKEN_REFRESH_FAILED:
                return "令牌刷新失败";
            case AUTH_TOKEN_VALIDATION_FAILED:
                return "令牌验证失败";
            case AUTH_LOGOUT_FAILED:
                return "登出失败";
            case AUTH_TOKEN_GENERATION_FAILED:
                return "令牌生成失败";
            case AUTH_SESSION_CREATION_FAILED:
                return "会话创建失败";
            case AUTH_INVALID_USERNAME:
                return "无效的用户名";
            case AUTH_USERNAME_TOO_LONG:
                return "用户名过长";
            case AUTH_PASSWORD_TOO_LONG:
                return "密码过长";
            case AUTH_IP_LOCKED:
                return "IP地址被锁定";
            case AUTH_SESSION_SAVE_FAILED:
                return "会话保存失败";
            case AUTH_SESSION_UPDATE_FAILED:
                return "会话更新失败";
            case AUTH_NOT_AUTHENTICATED:
                return "用户未认证";

            // 网关模块
            case GATEWAY_CONNECTION_LIMIT:
                return "连接数已达上限";
            case GATEWAY_INVALID_MESSAGE:
                return "无效的消息格式";
            case GATEWAY_SESSION_CLOSED:
                return "会话已关闭";
            case GATEWAY_RATE_LIMIT:
                return "请求频率过高";
            case GATEWAY_CODEC_ERROR:
                return "消息编解码错误";
            case GATEWAY_UNSUPPORTED_MESSAGE_TYPE:
                return "不支持的消息类型";
            case GATEWAY_MESSAGE_PROCESS_FAILED:
                return "消息处理失败";
            case GATEWAY_INVALID_MESSAGE_FORMAT:
                return "无效的消息格式";
            case GATEWAY_MESSAGE_TOO_LARGE:
                return "消息过大";
            case GATEWAY_MESSAGE_ENCODE_FAILED:
                return "消息编码失败";
            case GATEWAY_INVALID_MESSAGE_LENGTH:
                return "无效的消息长度";
            case GATEWAY_MESSAGE_DECODE_FAILED:
                return "消息解码失败";
            case GATEWAY_MESSAGE_CREATE_FAILED:
                return "消息创建失败";
            case GATEWAY_SERVER_START_FAILED:
                return "服务器启动失败";
            case GATEWAY_SESSION_NOT_FOUND:
                return "会话不存在";

            // 匹配模块
            case MATCH_QUEUE_FULL:
                return "匹配队列已满";
            case MATCH_INVALID_MODE:
                return "无效的匹配模式";
            case MATCH_ALREADY_IN_QUEUE:
                return "已在匹配队列中";
            case MATCH_TICKET_NOT_FOUND:
                return "匹配票据不存在";
            case MATCH_TIMEOUT:
                return "匹配超时";
            case MATCH_CANCELLED:
                return "匹配已取消";
            case MATCH_REQUEST_FAILED:
                return "匹配请求处理失败";
            case PLAYER_ALREADY_IN_QUEUE:
                return "玩家已在匹配队列中";
            case QUEUE_FULL:
                return "队列已满";

            // 房间模块
            case ROOM_NOT_FOUND:
                return "房间不存在";
            case ROOM_FULL:
                return "房间已满";
            case ROOM_ALREADY_JOINED:
                return "已加入房间";
            case ROOM_NOT_JOINED:
                return "未加入房间";
            case ROOM_INVALID_STATE:
                return "房间状态无效";
            case ROOM_CREATE_FAILED:
                return "房间创建失败";
            case ROOM_MAX_ROOMS_REACHED:
                return "达到最大房间数量限制";
            case ROOM_PLAYER_ALREADY_IN_ROOM:
                return "玩家已在房间中";
            case ROOM_NOT_JOINABLE:
                return "房间不可加入";
            case ROOM_JOIN_FAILED:
                return "加入房间失败";
            case ROOM_INVALID_STATUS:
                return "房间状态无效";
            case ROOM_NOT_ENOUGH_PLAYERS:
                return "房间玩家数量不足";
            case ROOM_START_GAME_FAILED:
                return "开始游戏失败";
            case ROOM_INPUT_PROCESS_FAILED:
                return "游戏输入处理失败";

            // 玩家模块
            case PLAYER_NOT_FOUND:
                return "玩家不存在";
            case PLAYER_INVALID_INPUT:
                return "无效的玩家输入";
            case PLAYER_INVALID_POSITION:
                return "无效的玩家位置";
            case PLAYER_SPEED_EXCEEDED:
                return "移动速度超限";
            case PLAYER_ACTION_COOLDOWN:
                return "技能冷却中";

            // 数据层
            case DATA_REDIS_CONNECTION_FAILED:
                return "Redis连接失败";
            case DATA_MYSQL_CONNECTION_FAILED:
                return "MySQL连接失败";
            case DATA_SERIALIZATION_ERROR:
                return "数据序列化错误";
            case DATA_DESERIALIZATION_ERROR:
                return "数据反序列化错误";
            case DATA_SAVE_FAILED:
                return "数据保存失败";
            case DATA_LOAD_FAILED:
                return "数据加载失败";
            case DATA_MYSQL_QUERY_FAILED:
                return "MySQL查询失败";
            case DATA_MYSQL_UPDATE_FAILED:
                return "MySQL更新失败";
            case DATA_MYSQL_BATCH_UPDATE_FAILED:
                return "MySQL批量更新失败";
            case DATA_MYSQL_TRANSACTION_FAILED:
                return "MySQL事务执行失败";
            case DATA_MYSQL_INSERT_FAILED:
                return "MySQL插入失败";
            case DATA_REDIS_OPERATION_FAILED:
                return "Redis操作失败";
            case DATA_SYNC_LOAD_FAILED:
                return "数据同步加载失败";
            case DATA_SYNC_UPDATE_FAILED:
                return "数据同步更新失败";
            case DATA_SYNC_DELETE_FAILED:
                return "数据同步删除失败";

            // WebRTC模块
            case WEBRTC_NEGOTIATION_FAILED:
                return "WebRTC协商失败";
            case WEBRTC_ICE_TIMEOUT:
                return "ICE连接超时";
            case WEBRTC_INVALID_SDP:
                return "无效的SDP";
            case WEBRTC_PEER_NOT_FOUND:
                return "对等端不存在";
            case WEBRTC_SIGNALING_ERROR:
                return "信令处理错误";
            case WEBRTC_SIGNAL_FAILED:
                return "WebRTC信令处理失败";

            // 系统级
            case SYSTEM_INTERNAL_ERROR:
                return "系统内部错误";
            case SYSTEM_SERVICE_UNAVAILABLE:
                return "服务不可用";
            case SYSTEM_TIMEOUT:
                return "系统超时";
            case SYSTEM_RESOURCE_EXHAUSTED:
                return "系统资源耗尽";
            case SYSTEM_CONFIGURATION_ERROR:
                return "系统配置错误";
            case SYSTEM_ERROR:
                return "系统错误";

            default:
                return "未知错误";
        }
    }

    // 私有构造函数，防止实例化
    private ErrorCodes() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
