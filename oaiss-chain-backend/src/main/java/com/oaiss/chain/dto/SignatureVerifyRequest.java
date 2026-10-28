package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 签名验证请求DTO
 * Signature Verification Request DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureVerifyRequest {

    /**
     * 报告ID
     */
    @NotNull(message = "报告ID不能为空")
    private Long reportId;

    /**
     * 签名数据（Base64编码）
     */
    @NotBlank(message = "签名数据不能为空")
    private String signatureData;

    /**
     * 报告原始数据
     * 用于验签时重新计算哈希
     */
    @NotBlank(message = "报告数据不能为空")
    private String reportData;

    /**
     * 签名者用户ID（可选）
     * 如果提供，将使用该用户的公钥进行验签
     */
    private Long signerId;
}
