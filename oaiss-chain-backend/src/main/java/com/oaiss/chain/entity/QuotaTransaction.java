package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 碳配额变动流水表。
 *
 * <p>存在理由：配额变动此前只内联在交易结算路径里，直接在 {@code enterprise} 的
 * {@code carbon_quota / carbon_used / carbon_tradable / carbon_frozen} 四字段上就地加减，
 * 事后无法回答「这笔额度是被哪一笔交易、哪一次冻结动的」。成交审核的裁决依据、
 * 额度上限的周期累计、监管的逐笔归因，都需要这条能力。
 *
 * <p>口径约定（与 {@code carbon_coin_transaction} 保持一致）：
 * <ul>
 *   <li>本表只记**增量与前后值**，不存余额快照——{@code enterprise} 仍是余额的唯一真相源，
 *       避免产生第二真相源必然漂移。</li>
 *   <li>{@code before/after} 记的是**可交易余额**（carbonTradable）的前后值，
 *       因为它是所有变动最终收敛的字段；相邻两条流水的 after/before 必须相等。</li>
 * </ul>
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "quota_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotaTransaction extends BaseEntity {

    /**
     * 流水编号
     */
    @Column(name = "tx_no", nullable = false, unique = true, length = 50)
    private String txNo;

    /**
     * 企业ID
     */
    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    /**
     * 变动类型：FREEZE 冻结 / UNFREEZE 解冻 / TRANSFER 成交扣减 / INCREASE 买入增加 / ROLLBACK 驳回回滚
     */
    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;

    /**
     * 变动数量（正数，方向由 changeType 决定）
     */
    @Column(name = "amount", nullable = false, precision = 15, scale = 4)
    private BigDecimal amount;

    /**
     * 变动前可交易余额
     */
    @Column(name = "tradable_before", nullable = false, precision = 15, scale = 4)
    private BigDecimal tradableBefore;

    /**
     * 变动后可交易余额
     */
    @Column(name = "tradable_after", nullable = false, precision = 15, scale = 4)
    private BigDecimal tradableAfter;

    /**
     * 变动前冻结余额
     */
    @Column(name = "frozen_before", nullable = false, precision = 15, scale = 4)
    private BigDecimal frozenBefore;

    /**
     * 变动后冻结余额
     */
    @Column(name = "frozen_after", nullable = false, precision = 15, scale = 4)
    private BigDecimal frozenAfter;

    /**
     * 变动的业务来源：AUCTION / P2P / AUDIT / MANUAL
     */
    @Column(name = "source", length = 20)
    private String source;

    /**
     * 关联交易ID（成交扣减、回滚时填写）
     */
    @Column(name = "related_trade_id")
    private Long relatedTradeId;

    // ==================== 配额发放维度（V25） ====================

    /**
     * 关联碳报告ID（配额发放/冲正时填写）。
     *
     * <p>与 {@code relatedTradeId} 互斥：一笔流水要么源自交易，要么源自报告发放，
     * 不会同时有两者。
     */
    @Column(name = "related_report_id")
    private Long relatedReportId;

    /**
     * 变动前 {@code carbon_used}。
     *
     * <p>此前账本只记 tradable/frozen 的前后值，缺 used 维度，导致
     * 「这次变动是否动过 used」无从判断。补上后，冲正保护可以直接读流水验证。
     */
    @Column(name = "used_before", precision = 15, scale = 4)
    private BigDecimal usedBefore;

    /**
     * 变动后 {@code carbon_used}
     */
    @Column(name = "used_after", precision = 15, scale = 4)
    private BigDecimal usedAfter;

    /**
     * 发放幂等键，格式 {@code reportId#seq}。
     *
     * <p><b>为什么带序号而不是裸 reportId</b>：冲正后重发会为同一报告再发一次，
     * 裸 reportId 会撞唯一索引 {@code uk_quota_allocation}。
     * 带序号后每次发放的键唯一，而**冲正行此列留 NULL**——MySQL 唯一索引
     * 不约束 NULL，多行 NULL 可共存，正好只拦「同一序号被发两次」。
     */
    @Column(name = "allocation_key", length = 64)
    private String allocationKey;

    /**
     * 该报告的发放序号，从 1 开始，冲正重发时递增
     */
    @Column(name = "allocation_seq")
    private Integer allocationSeq;

    /**
     * 操作人用户ID（系统自动撮合时为撮合引擎）
     */
    @Column(name = "operator_id")
    private Long operatorId;

    /**
     * 备注
     */
    @Column(name = "remark", length = 255)
    private String remark;
}