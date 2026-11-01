package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 成交审核记录表。
 *
 * <p>与 {@code transaction.audit_status} 的分工：主表只存**状态位**（高频查询、需要索引，
 * 审核员工单列表按状态分页），本表存**低频大字段**（审核意见、命中的风险理由、审核人、
 * 裁决时间）。前者随交易生命周期频繁变化，后者一旦写入不再改变。
 *
 * <p>为什么不合并进主表：审核意见是自由文本、风险理由是多条字符串，塞进
 * {@code transaction} 会让这张最高频的表随审核信息持续膨胀，且主表已被
 * 交易结算路径高频读写，不宜再变宽。
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "trade_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeAudit extends BaseEntity {

    // ==================== 审核态（与 transaction.audit_status 取值一致） ====================

    /** 无需审核：风险分流未命中，自动放行 */
    public static final int AUDIT_NOT_REQUIRED = 0;
    /** 待审核：已成交但未结算，资产处于冻结态 */
    public static final int AUDIT_PENDING = 1;
    /** 审核通过：允许结算并上链 */
    public static final int AUDIT_APPROVED = 2;
    /** 审核驳回：回滚额度与碳币，扣信誉分 */
    public static final int AUDIT_REJECTED = 3;

    // ==================== 驳回理由（与 TradeRiskAssessor 的 reason 前缀对齐） ====================

    public static final String REASON_LARGE_AMOUNT = "LARGE_AMOUNT";
    public static final String REASON_PRICE_DEVIATION = "PRICE_DEVIATION";
    public static final String REASON_LOW_CREDIT = "LOW_CREDIT";
    public static final String REASON_WASH_TRADING = "WASH_TRADING";
    public static final String REASON_OTHER = "OTHER";

    /**
     * 关联交易ID
     */
    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    /**
     * 审核态（0 无需审 / 1 待审 / 2 通过 / 3 驳回）
     */
    // 列类型显式声明为 TINYINT。不声明时 Hibernate 按 Integer 推断期望 INTEGER，
    // ddl-auto=validate 下会因实际列为 TINYINT 拒绝启动。
    @Column(name = "audit_status", nullable = false, columnDefinition = "TINYINT")
    @Builder.Default
    private Integer auditStatus = AUDIT_NOT_REQUIRED;

    /**
     * 命中的风险理由（JSON 数组字符串，如 ["LARGE_AMOUNT(1500>=1000)"]）。
     * 审核员据此判断，也是事后追责「为什么这笔被拦」的依据。
     */
    @Column(name = "risk_reasons", columnDefinition = "TEXT")
    private String riskReasons;

    /**
     * 裁决结果对应的主理由（驳回时必填，便于统计与线索聚合）
     */
    @Column(name = "decision_reason", length = 32)
    private String decisionReason;

    /**
     * 审核意见
     */
    @Column(name = "audit_remark", columnDefinition = "TEXT")
    private String auditRemark;

    /**
     * 审核人用户ID
     */
    @Column(name = "auditor_id")
    private Long auditorId;

    /**
     * 裁决时间
     */
    @Column(name = "audited_at")
    private LocalDateTime auditedAt;

    /**
     * 是否由管理员兜底裁决（区分主审与兜底，便于监管审计审核质量）
     */
    @Column(name = "fallback_decided", nullable = false)
    @Builder.Default
    private Boolean fallbackDecided = false;
}