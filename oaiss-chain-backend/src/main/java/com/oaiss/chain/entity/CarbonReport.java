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
     * 链上存证标记：NULL-正常，HISTORICAL_MOCK-历史 Mock 假哈希（链上不存在，已确认）
     *
     * <p>标记只加注解，不改动 {@code blockchainTxHash} / {@code status} / {@code onChainAt}：
     * 哈希是历史真相，抹掉就没有审计线索了。</p>
     */
    @Column(name = "chain_mark", length = 32)
    private String chainMark;

    /**
     * 标记原因（供审计与复核）
     */
    @Column(name = "chain_mark_reason", length = 200)
    private String chainMarkReason;

    /**
     * 标记时间
     */
    @Column(name = "chain_marked_at")
    private java.time.LocalDateTime chainMarkedAt;

    /**
     * 标记操作人用户ID
     */
    @Column(name = "chain_marked_by")
    private Long chainMarkedBy;

    /**
     * 附件URL（JSON数组）
     */
    @Column(name = "attachments", columnDefinition = "TEXT")
    private String attachments;

    // ==================== 配额发放（V25） ====================

    /**
     * 产量（发电量，MWh）。
     *
     * <p>配额公式 {@code 配额 = 产量 × 基准值} 的乘数因子。与 {@code totalEmission}
     * 是**不同量纲**，不能互相推导：排放是「排了多少 CO2」，产量是「发了多少电」。
     */
    @Column(name = "output_quantity", precision = 15, scale = 4)
    private java.math.BigDecimal outputQuantity;

    /**
     * 产量单位。本轮固定 {@code MWh}
     */
    @Column(name = "output_unit", length = 20)
    private String outputUnit;

    /**
     * 机组类别（I/II/III/IV），决定查哪一行基准值。
     *
     * <p>由企业填报时自选，本轮不做装机容量校验——真实市场按 300MW 容量分
     * I/II 类，但项目无容量字段，故交由企业声明。
     */
    @Column(name = "unit_type", length = 32)
    private String unitType;

    /**
     * 核算年份。从 {@code accountingPeriod} 解析而来（该字段是无格式约束的自由文本，
     * 无法承载唯一性）。
     *
     * <p><b>软删除时置 NULL</b>：唯一键 {@code (enterprise_id, period_year, period_quarter, is_deleted)}
     * 中 {@code is_deleted} 从 0 变 1 已能释放一个位，但若同一周期被反复删除重建，
     * 仍会因历史行占用而冲突，故删除时同时清空周期字段。
     */
    @Column(name = "period_year")
    private Integer periodYear;

    /**
     * 核算季度 1-4；年度报告为 NULL
     */
    @Column(name = "period_quarter")
    private Integer periodQuarter;

    /**
     * 已发放配额量（本次发放值，非累计）
     */
    @Column(name = "quota_allocated_amount", precision = 15, scale = 4)
    private java.math.BigDecimal quotaAllocatedAmount;

    /**
     * 发放时的 {@code carbon_used} 快照，作为冲正保护的比较基线。
     *
     * <p>为什么需要它：冲正要判断「企业是否已动用这批配额」。而
     * {@code quota_transaction} 的历史流水没有 used 维度（V25 才补），
     * 账本本身也不完整（23 处内联写点未迁移），无法靠流水回放重建 used。
     * 故在发放那一刻把 used 快照下来，冲正时直接比。
     */
    @Column(name = "quota_used_snapshot", precision = 15, scale = 4)
    private java.math.BigDecimal quotaUsedSnapshot;

    /**
     * 配额发放状态机。
     *
     * <p>三态设计的原因：{@code quotaAllocatedAmount} 单靠「非空即已发」无法区分
     * 「已发放」与「已冲正待重发」——若冲正后置空，幂等失效；若不置空，
     * 第二次审核永远发不出去（会命中幂等短路）。故显式引入状态机。
     *
     * @see com.oaiss.chain.enums.AllocationStateEnum
     */
    @Column(name = "allocation_state", length = 16)
    private String allocationState;

    /**
     * 该报告的配额发放序号，从 1 开始；冲正重发时递增。
     *
     * <p>用于构造发放幂等键 {@code reportId#seq}——若用裸 reportId，
     * 冲正后重发会撞唯一索引。
     */
    @Column(name = "allocation_seq")
    private Integer allocationSeq;

    /**
     * 已用碳信用抵扣的超排缺口（吨）。
     *
     * <p>缺口 = {@code scope1Emission − quotaAllocatedAmount}；抵扣后此列增加，
     * 剩余缺口 = {@code scope1Emission − quotaAllocatedAmount − shortfallOffset}。
     *
     * <p><b>为什么记在报告上</b>：缺口本身就是报告属性（配额 − 实排），
     * 抵扣也是针对同一份报告，放同一行比拉关联表更直白，
     * 也让「这份报告还差多少」一次查询即可得到。
     */
    @Column(name = "shortfall_offset", precision = 15, scale = 4)
    @Builder.Default
    private java.math.BigDecimal shortfallOffset = java.math.BigDecimal.ZERO;

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
