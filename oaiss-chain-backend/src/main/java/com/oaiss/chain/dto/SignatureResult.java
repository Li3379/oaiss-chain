package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 签名结果DTO
 * Signature Result DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureResult {

    /**
     * 签名数据（Base64编码）
     */
    private String signature;

    /**
     * 签名算法
     * 如：SHA256withRSA
     */
    private String algorithm;

    /**
     * 签名时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 签名是否有效
     * 用于验签结果返回
     */
    @Builder.Default
    private Boolean valid = false;

    /**
     * 签名者用户ID
     */
    private Long signerId;
}
