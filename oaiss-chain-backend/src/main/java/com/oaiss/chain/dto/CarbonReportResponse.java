package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 碳报告响应DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarbonReportResponse {

    private Long id;
    private String reportNo;
    private Long enterpriseId;
    private String enterpriseName;
    private String accountingPeriod;
    private String title;
    private Integer reportType;
    private String emissionData;
    private BigDecimal totalEmission;
    private BigDecimal scope1Emission;
    private BigDecimal scope2Emission;
    private BigDecimal scope3Emission;
    private String calculationMethod;
    private Integer status;
    private String statusText;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private String signatureData;
    /**
     * 签名验证结果：true=有效, false=无效(可能被篡改), null=无签名或未验证
     */
    private Boolean signatureValid;
    private String blockchainTxHash;
    private LocalDateTime onChainAt;
    private String attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
