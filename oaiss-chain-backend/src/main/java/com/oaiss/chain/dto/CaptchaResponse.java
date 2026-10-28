package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码响应DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponse {

    /**
     * 验证码Key（用于登录时校验）
     */
    private String captchaKey;

    /**
     * 验证码图片Base64
     */
    private String captchaImage;

    /**
     * 过期时间（秒）
     */
    @Builder.Default
    private Long expiresIn = 300L;
}
