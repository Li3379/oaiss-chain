package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 碳交易记录表
 * 存储所有碳交易记录（拍卖和P2P）
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    /**
     * 交易编号
     */
    @Column(name = "trade_no", nullable = false, unique = true, length = 50)
    private String tradeNo;

    /**
     * 交易类型（1-拍卖, 2-P2P, 3-配额分配）
     */
    @Column(name = "trade_type", nullable = false)
    private Integer tradeType;

    /**
     * 卖方用户ID
     */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /**
     * 买方用户ID
     */
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /**
     * 交易碳配额数量（吨CO2当量）
     */
    @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
    private java.math.BigDecimal quantity;

    /**
     * 单价（碳币/吨）
     */
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal unitPrice;

    /**
     * 总金额（元）
     */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal totalAmount;

    /**
     * 关联碳报告ID（可选）
     */
    @Column(name = "report_id")
    private Long reportId;

    /**
     * 交易状态（0-待处理, 1-处理中, 2-已完成, 3-已取消, 4-失败）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 0;

    /**
     * 成交审核态（0-无需审核, 1-待审核, 2-已通过, 3-已驳回）。
     *
     * <p>与 {@code status} 正交：{@code status} 描述交易的**生命周期**
     * （待处理/处理中/已完成/已取消/失败），{@code auditStatus} 描述这笔成交
     * **是否经过人工裁决**。不把审核态并入 {@code TradeStatusEnum} 的原因：
     * 那样会给前端状态渲染、i18n、以及全部依赖状态码的既有断言引入一个新状态码，
     * 回归面远大于收益，而两者语义本就分属不同维度。
     *
     * <p>取值与 {@link TradeAudit} 的常量保持一致，避免两处定义漂移。
     */
    // 列类型显式声明为 TINYINT（V17 建列时即 TINYINT）。不声明的话
    // Hibernate 按 Integer 推断期望 INTEGER，ddl-auto=validate 下会因
    // 实际列是 TINYINT 而拒绝启动——同一缺陷曾让 carbon_coin_transaction.tx_type
    // 在 CI 上直接阻塞后端启动。
    @Column(name = "audit_status", nullable = false, columnDefinition = "TINYINT")
    @Builder.Default
    private Integer auditStatus = 0;

    /**
     * 审核意见摘要（完整意见见 {@code trade_audit} 表）
     */
    @Column(name = "audit_remark", length = 500)
    private String auditRemark;

    /**
     * 交易备注
     */
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    /**
     * 区块链交易哈希
     */
    @Column(name = "blockchain_tx_hash", length = 255)
    private String blockchainTxHash;

    /**
     * 完成时间
     */
    @Column(name = "completed_at")
    private java.time.LocalDateTime completedAt;

    /**
     * 链上存证标记：NULL-正常，HISTORICAL_MOCK-历史 Mock 假哈希（链上不存在，已确认）
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
}
