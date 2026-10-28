package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * 碳报告表
 * 存储企业提交的碳核算报告
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "carbon_report")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "enterpriseId", type = Long.class))
@Filter(name = "tenantFilter", condition = "enterprise_id = :enterpriseId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonReport extends BaseEntity {

    /**
     * 报告编号
     */
    @Column(name = "report_no", nullable = false, unique = true, length = 50)
    private String reportNo;

    /**
     * 提交企业ID
     */
    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    /**
     * 提交用户ID
     */
    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    /**
     * 核算周期（如 2024-Q1, 2024）
     */
    @Column(name = "accounting_period", nullable = false, length = 20)
    private String accountingPeriod;

    /**
     * 报告标题
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 报告类型（1-季度, 2-年度）
     */
    @Column(name = "report_type", nullable = false)
    private Integer reportType;

    /**
     * 碳排放数据（JSON格式）
     * 包含各排放源数据
     */
    @Column(name = "emission_data", nullable = false, columnDefinition = "TEXT")
    private String emissionData;

    /**
     * 核算结果 - 总排放量（吨CO2当量）
     */
    @Column(name = "total_emission", precision = 15, scale = 4)
    private java.math.BigDecimal totalEmission;

    /**
     * 核算结果 - 直接排放（范围1）
     */
    @Column(name = "scope1_emission", precision = 15, scale = 4)
    private java.math.BigDecimal scope1Emission;

    /**
     * 核算结果 - 间接排放（范围2）
     */
    @Column(name = "scope2_emission", precision = 15, scale = 4)
    private java.math.BigDecimal scope2Emission;

    /**
     * 核算结果 - 其他间接排放（范围3）
     */
    @Column(name = "scope3_emission", precision = 15, scale = 4)
    private java.math.BigDecimal scope3Emission;

    /**
     * 核算方法
     */
    @Column(name = "calculation_method", length = 100)
    private String calculationMethod;

    /**
     * 报告状态（0-草稿, 1-已提交, 2-审核中, 3-审核通过, 4-审核拒绝, 5-已上链）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 0;

    /**
     * 审核员ID
     */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /**
     * 审核意见
     */
    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    /**
     * 审核时间
     */
    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    /**
     * RSA签名数据
     */
    @Column(name = "signature_data", columnDefinition = "TEXT")
    private String signatureData;

    /**
     * 区块链交易哈希（上链后）
     */
    @Column(name = "blockchain_tx_hash", length = 255)
    private String blockchainTxHash;

    /**
     * 上链时间
     */
    @Column(name = "on_chain_at")
    private java.time.LocalDateTime onChainAt;

    /**
     * 附件URL（JSON数组）
     */
    @Column(name = "attachments", columnDefinition = "TEXT")
    private String attachments;

    /**
     * 企业名称（非持久化，用于列表展示）
     */
    @Transient
    private String enterpriseName;

    /**
     * 状态文本（非持久化，用于列表展示）
     */
    @Transient
    private String statusText;

    /**
     * 签名验证结果（非持久化，仅审核列表返回）：
     * true=有效, false=无效(可能被篡改), null=无签名或未验证
     */
    @Transient
    private Boolean signatureValid;
}
