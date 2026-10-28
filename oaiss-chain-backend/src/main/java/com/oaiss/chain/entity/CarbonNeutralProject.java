package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 碳中和项目表
 * 记录碳减排/碳汇项目信息
 * <p>
 * 设计文档要求（doc01/doc03）：
 * - 碳中和项目管理
 * - 支持碳汇、CCUS、可再生能源等类型项目
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "carbon_neutral_project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonNeutralProject extends BaseEntity {

    /**
     * 项目编号
     */
    @Column(name = "project_no", nullable = false, unique = true, length = 50)
    private String projectNo;

    /**
     * 项目名称
     */
    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    /**
     * 项目类型（1-碳汇, 2-CCUS, 3-可再生能源, 4-节能改造, 5-其他）
     */
    @Column(name = "project_type", nullable = false)
    private Integer projectType;

    /**
     * 项目业主ID（企业ID）
     */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /**
     * 项目描述
     */
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * 项目地点
     */
    @Column(name = "location", length = 200)
    private String location;

    /**
     * 预计减排量（吨CO2当量/年）
     */
    @Column(name = "expected_reduction", precision = 15, scale = 4)
    private BigDecimal expectedReduction;

    /**
     * 实际减排量（吨CO2当量）
     */
    @Column(name = "actual_reduction", precision = 15, scale = 4)
    private BigDecimal actualReduction;

    /**
     * 项目投资金额（元）
     */
    @Column(name = "investment_amount", precision = 15, scale = 2)
    private BigDecimal investmentAmount;

    /**
     * 开始日期
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * 结束日期
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * 项目状态（0-筹备, 1-进行中, 2-已完成, 3-已终止）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 0;

    /**
     * 认证状态（0-未认证, 1-认证中, 2-已认证）
     */
    @Column(name = "cert_status", nullable = false)
    @Builder.Default
    private Integer certStatus = 0;

    /**
     * 认证机构
     */
    @Column(name = "cert_org", length = 200)
    private String certOrg;

    /**
     * 认证日期
     */
    @Column(name = "cert_date")
    private LocalDate certDate;

    /**
     * 认证编号
     */
    @Column(name = "cert_no", length = 100)
    private String certNo;

    /**
     * 方法学（项目采用的方法学）
     */
    @Column(name = "methodology", length = 200)
    private String methodology;

    /**
     * 核算周期（年）
     */
    @Column(name = "accounting_period")
    private Integer accountingPeriod;

    /**
     * 已签发碳信用量（吨CO2当量）
     */
    @Column(name = "issued_credits", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal issuedCredits = BigDecimal.ZERO;

    /**
     * 已使用碳信用量（吨CO2当量）
     */
    @Column(name = "used_credits", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal usedCredits = BigDecimal.ZERO;

    /**
     * 项目申请资料（JSON）
     */
    @Column(name = "application_data", columnDefinition = "TEXT")
    private String applicationData;

    /**
     * 核算报告（JSON）
     */
    @Column(name = "verification_report", columnDefinition = "TEXT")
    private String verificationReport;

    /**
     * 附件文件列表（JSON）
     */
    @Column(name = "attachments", columnDefinition = "TEXT")
    private String attachments;

    /**
     * 审核意见
     */
    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    /**
     * 审核人ID
     */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /**
     * 审核时间
     */
    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    /**
     * 监测数据（JSON，存储项目监测指标）
     */
    @Column(name = "monitoring_data", columnDefinition = "TEXT")
    private String monitoringData;

    /**
     * 上次监测日期
     */
    @Column(name = "last_monitoring_date")
    private LocalDate lastMonitoringDate;

    /**
     * 第三方核证机构ID
     */
    @Column(name = "verifier_id")
    private Long verifierId;

    /**
     * 核证状态（0-未核证, 1-核证中, 2-已核证, 3-核证失败）
     */
    @Column(name = "verification_status")
    @Builder.Default
    private Integer verificationStatus = 0;
}
