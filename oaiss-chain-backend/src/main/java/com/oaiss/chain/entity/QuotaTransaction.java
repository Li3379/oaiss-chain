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