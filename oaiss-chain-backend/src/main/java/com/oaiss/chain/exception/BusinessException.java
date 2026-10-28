package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.util.MessageUtils;
import lombok.Getter;

/**
 * 基础业务异常
 * Base Business Exception
 *
 * 支持国际化消息，通过消息键和参数获取本地化错误信息
 * Supports i18n messages via message keys and parameters
 *
 * @author OAISS Team
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息键
     */
    private final String messageKey;

    /**
     * 错误消息参数
     */
    private final Object[] args;

    /**
     * 原始消息（直接指定时使用）
     */
    private final String rawMessage;

    /**
     * 构造函数 - 使用原始消息（向后兼容）
     * 当 message 不以 "error." 开头时，视为原始消息
     * @param code 错误码
     * @param message 消息（原始消息或消息键）
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        // 判断是否为消息键（以 "error." 开头）
        if (message != null && message.startsWith("error.")) {
            this.messageKey = message;
            this.args = null;
            this.rawMessage = null;
        } else {
            this.messageKey = null;
            this.args = null;
            this.rawMessage = message;
        }
    }

    /**
     * 构造函数 - 使用原始消息和原因（向后兼容）
     * @param code 错误码
     * @param message 消息
     * @param cause 原因
     */
    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        // 判断是否为消息键（以 "error." 开头）
        if (message != null && message.startsWith("error.")) {
            this.messageKey = message;
            this.args = null;
            this.rawMessage = null;
        } else {
            this.messageKey = null;
            this.args = null;
            this.rawMessage = message;
        }
    }

    /**
     * 创建使用消息键和参数的异常
     * @param code 错误码
     * @param messageKey 消息键
     * @param args 参数
     * @return BusinessException
     */
    public static BusinessException of(Integer code, String messageKey, Object... args) {
        return new BusinessException(code, messageKey, args, true);
    }

    /**
     * 构造函数 - 使用消息键和参数（内部使用）
     */
    private BusinessException(Integer code, String messageKey, Object[] args, boolean useKey) {
        super(MessageUtils.getMessage(messageKey, args));
        this.code = code;
        this.messageKey = messageKey;
        this.args = args;
        this.rawMessage = null;
    }

    /**
     * 获取错误消息
     * @return 错误消息
     */
    @Override
    public String getMessage() {
        if (rawMessage != null) {
            return rawMessage;
        }
        if (messageKey != null) {
            return MessageUtils.getMessage(messageKey, args);
        }
        return super.getMessage();
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 系统错误
     */
    public static BusinessException systemError(String messageKey, Object... args) {
        return new BusinessException(ErrorCode.SYSTEM_ERROR, messageKey, args, true);
    }

    /**
     * 系统错误（带异常）
     */
    public static BusinessException systemError(String messageKey, Throwable cause, Object... args) {
        BusinessException ex = new BusinessException(ErrorCode.SYSTEM_ERROR, messageKey, args, true);
        ex.initCause(cause);
        return ex;
    }

    /**
     * 参数错误
     */
    public static BusinessException paramError(String messageKey, Object... args) {
        return new BusinessException(ErrorCode.PARAM_ERROR, messageKey, args, true);
    }

    /**
     * 资源不存在
     */
    public static BusinessException notFound(String messageKey, Object... args) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, messageKey, args, true);
    }

    /**
     * 认证失败
     */
    public static BusinessException authFailed(String messageKey, Object... args) {
        return new BusinessException(ErrorCode.LOGIN_FAILED, messageKey, args, true);
    }

    /**
     * 权限不足
     */
    public static BusinessException permissionDenied(String messageKey, Object... args) {
        return new BusinessException(ErrorCode.PERMISSION_DENIED, messageKey, args, true);
    }
}
