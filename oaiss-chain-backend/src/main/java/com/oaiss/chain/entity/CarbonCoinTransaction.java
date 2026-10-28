package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 碳币交易流水表
 * 记录所有碳币相关的充值、消费、转账流水
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "carbon_coin_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonCoinTransaction extends BaseEntity {

    /**
     * 交易编号
     */
    @Column(name = "tx_no", nullable = false, unique = true, length = 50)
    private String txNo;

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 交易类型（1-充值, 2-购买配额, 3-出售配额, 4-转账）
     */
    @Column(name = "tx_type", nullable = false)
    private Integer txType;

    /**
     * 交易金额（碳币数量）
     */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * 交易前余额
     */
    @Column(name = "balance_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    /**
     * 交易后余额
     */
    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * 关联碳配额数量（出售/购买配额时）
     */
    @Column(name = "related_quota", precision = 15, scale = 4)
    private BigDecimal relatedQuota;

    /**
     * 关联交易ID（可选）
     */
    @Column(name = "related_trade_id")
    private Long relatedTradeId;

    /**
     * 对方用户ID（转账时）
     */
    @Column(name = "counterpart_id")
    private Long counterpartId;

    /**
     * 备注
     */
    @Column(name = "remark", length = 500)
    private String remark;
}
