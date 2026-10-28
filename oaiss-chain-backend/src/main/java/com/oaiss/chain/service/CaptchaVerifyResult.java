package com.oaiss.chain.service;

/**
 * 验证码验证结果枚举
 */
public enum CaptchaVerifyResult {
    SUCCESS,
    NOT_FOUND,
    EXPIRED,
    WRONG_CODE
}
