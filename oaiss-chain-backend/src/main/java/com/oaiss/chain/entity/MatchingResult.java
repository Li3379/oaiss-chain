package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 撮合结果表
 * 存储双向拍卖的撮合匹配结果
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "matching_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchingResult extends BaseEntity {

    /**
     * 撮合编号
     */
    @Column(name = "match_no", nullable = false, unique = true, length = 50)
    private String matchNo;

    /**
     * 买单ID
     */
    @Column(name = "buy_order_id", nullable = false)
    private Long buyOrderId;

    /**
     * 卖单ID
     */
    @Column(name = "sell_order_id", nullable = false)
    private Long sellOrderId;

    /**
     * 买方用户ID
     */
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /**
     * 卖方用户ID
     */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /**
     * 匹配数量（吨CO2当量）
     */
    @Column(name = "matched_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal matchedQuantity;

    /**
     * 成交单价（元/吨）
     */
    @Column(name = "settlement_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal settlementPrice;

    /**
     * 成交总金额（元）
     */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    /**
     * 撮合状态（0-待结算, 1-已结算, 2-失败）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 0;

    /**
     * 关联交易记录ID（结算后关联）
     */
    @Column(name = "transaction_id")
    private Long transactionId;

    /**
     * 结算时间
     */
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
