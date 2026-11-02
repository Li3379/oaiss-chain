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

    /**
     * 链上存证标记：NULL-正常，HISTORICAL_MOCK-历史 Mock 假哈希（链上不存在，已确认）
     *
     * <p>必须透出给前端：否则企业会看到「已上链」却不知道这条存证在链上并不存在。</p>
     */
    private String chainMark;

    /** 标记原因 */
    private String chainMarkReason;

    /** 标记时间 */
    private LocalDateTime chainMarkedAt;
    private String attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== 配额发放结果（组件 1，只读）====================
    //
    // 全部只读：配额只能由审核流程写入，任何请求体里的同名字段都不会被采纳
    // （CarbonReportRequest 没有这些字段）。前端凭这三个值回答审核员和企业的
    // 两个问题：「这份报告发了多少吨」「我的排放是超了还是有盈余可卖」。

    /** 本报告发放的配额量（吨）；未发放为 null */
    private BigDecimal quotaAllocatedAmount;

    /** 发放状态机：NONE / ALLOCATED / REVOKED / PENDING_RETRY / REVOKE_FAILED；未发放过为 null */
    private String allocationState;

    /**
     * 盈余（正）或缺口（负），单位吨；{@link #quotaJudgement} 为 UNDETERMINED 时为 null。
     *
     * <p>为 null 而不是 0 是刻意的：0 表示「配额刚好等于排放」，
     * 与「无法判定」在业务上是两件事，用同一个值表达会让前端无法区分。
     */
    private BigDecimal quotaSurplusOrGap;

    /** 判定结论：SURPLUS / SHORTFALL / UNDETERMINED；未发放为 null */
    private String quotaJudgement;
}
