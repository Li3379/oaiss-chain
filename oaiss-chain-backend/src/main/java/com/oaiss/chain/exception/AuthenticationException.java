package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import lombok.Getter;

/**
 * 认证异常
 * Authentication Exception
 * 
 * @author OAISS Team
 */
@Getter
public class AuthenticationException extends BusinessException {

    public AuthenticationException(Integer code, String message) {
        super(code, message);
    }

    public AuthenticationException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 用户未登录
     */
    public static AuthenticationException notLogin() {
        return new AuthenticationException(ErrorCode.USER_NOT_LOGIN, "用户未登录");
    }

    /**
     * Token无效
     */
    public static AuthenticationException tokenInvalid() {
        return new AuthenticationException(ErrorCode.TOKEN_INVALID, "Token无效");
    }

    /**
     * Token已过期
     */
    public static AuthenticationException tokenExpired() {
        return new AuthenticationException(ErrorCode.TOKEN_EXPIRED, "Token已过期，请重新登录");
    }

    /**
     * 账号已禁用
     */
    public static AuthenticationException accountDisabled() {
        return new AuthenticationException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用");
    }

    /**
     * 验证码错误
     */
    public static AuthenticationException captchaError() {
        return new AuthenticationException(ErrorCode.CAPTCHA_ERROR, "验证码错误");
    }

    /**
     * 验证码已过期
     */
    public static AuthenticationException captchaExpired() {
        return new AuthenticationException(ErrorCode.CAPTCHA_EXPIRED, "验证码已过期");
    }

    /**
     * 登录失败
     */
    public static AuthenticationException loginFailed(String message) {
        return new AuthenticationException(ErrorCode.LOGIN_FAILED, message);
    }

    /**
     * IP验证失败
     */
    public static AuthenticationException ipValidationFailed() {
        return new AuthenticationException(ErrorCode.IP_VALIDATION_FAILED, "IP地址验证失败");
    }
}
