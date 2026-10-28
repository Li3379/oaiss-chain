package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RSA密钥对响应DTO
 * RSA Key Pair Response DTO
 * 
 * 注意：不暴露私钥信息
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RsaKeyPairResponse {

    /**
     * 密钥对ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 公钥（Base64编码）
     */
    private String publicKey;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 密钥状态
     * 1-有效, 0-已失效, 2-已过期
     */
    private Integer keyStatus;

    /**
     * 密钥状态描述
     */
    private String keyStatusText;

    /**
     * 密钥版本号
     */
    private Integer keyVersion;

    /**
     * 密钥用途
     * 1-签名验签, 2-加密解密, 3-通用
     */
    private Integer keyUsage;

    /**
     * 密码学算法类型 (RSA / SM2)
     */
    private String algorithm;
}
