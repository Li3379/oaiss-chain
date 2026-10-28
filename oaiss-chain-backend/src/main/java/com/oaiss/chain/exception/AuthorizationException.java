package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import lombok.Getter;

/**
 * 权限异常
 * Authorization Exception
 * 
 * @author OAISS Team
 */
@Getter
public class AuthorizationException extends BusinessException {

    public AuthorizationException(Integer code, String message) {
        super(code, message);
    }

    public AuthorizationException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 无权限访问
     */
    public static AuthorizationException permissionDenied() {
        return new AuthorizationException(ErrorCode.PERMISSION_DENIED, "无权限访问该资源");
    }

    /**
     * 无权限访问（自定义消息）
     */
    public static AuthorizationException permissionDenied(String message) {
        return new AuthorizationException(ErrorCode.PERMISSION_DENIED, message);
    }

    /**
     * 角色权限不足
     */
    public static AuthorizationException roleNotEnough(String requiredRole) {
        return new AuthorizationException(ErrorCode.PERMISSION_DENIED, 
                "需要角色: " + requiredRole + " 才能访问该资源");
    }
}
