package com.whale.lumina.common;

/**
 * 游戏服务器自定义异常基类
 * 
 * 所有业务异常都应继承此类，提供统一的错误码和错误信息处理
 * 
 * @author Lumina Team
 */
public class GameException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;

    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     */
    public GameException(String errorCode) {
        this(errorCode, (Object[]) null);
    }

    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param message 错误信息
     */
    public GameException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param cause 原因异常
     */
    public GameException(String errorCode, Throwable cause) {
        super(ErrorCodes.getErrorMessage(errorCode), cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * 构造函数
     * 
     * @param errorCode 错误码
     * @param message 错误信息
     * @param cause 原因异常
     */
    public GameException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * 构造函数（支持参数化错误信息）
     * 
     * @param errorCode 错误码
     * @param args 错误信息参数
     */
    public GameException(String errorCode, Object... args) {
        super(ErrorCodes.getErrorMessage(errorCode));
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * 获取错误码
     * 
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误信息参数
     * 
     * @return 错误信息参数
     */
    public Object[] getArgs() {
        return args;
    }

    /**
     * 获取完整的错误信息
     * 
     * @return 错误信息
     */
    public String getFullMessage() {
        String baseMessage = getMessage();
        if (args != null && args.length > 0) {
            return String.format(baseMessage, args);
        }
        return baseMessage;
    }

    @Override
    public String toString() {
        return String.format("GameException{errorCode='%s', message='%s'}", 
                           errorCode, getFullMessage());
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建认证异常
     */
    public static GameException authError(String errorCode) {
        return new GameException(errorCode);
    }

    /**
     * 创建网关异常
     */
    public static GameException gatewayError(String errorCode) {
        return new GameException(errorCode);
    }

    /**
     * 创建匹配异常
     */
    public static GameException matchError(String errorCode) {
        return new GameException(errorCode);
    }

    /**
     * 创建房间异常
     */
    public static GameException roomError(String errorCode) {
        return new GameException(errorCode);
    }

    /**
     * 创建玩家异常
     */
    public static GameException playerError(String errorCode) {
        return new GameException(errorCode);
    }

    /**
     * 创建数据层异常
     */
    public static GameException dataError(String errorCode) {
        return new GameException(errorCode);
    }

    /**
     * 创建WebRTC异常
     */
    public static GameException webrtcError(String errorCode) {
        return new GameException(errorCode);
    }

    /**
     * 创建系统异常
     */
    public static GameException systemError(String errorCode) {
        return new GameException(errorCode);
    }

    /**
     * 创建系统异常（带原因）
     */
    public static GameException systemError(String errorCode, Throwable cause) {
        return new GameException(errorCode, cause);
    }
}