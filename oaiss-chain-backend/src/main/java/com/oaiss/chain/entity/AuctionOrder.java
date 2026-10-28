package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拍卖挂单表
 * 存储双向拍卖中的买卖挂单
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "auction_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionOrder extends BaseEntity {

    /**
     * 挂单编号
     */
    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    private String orderNo;

    /**
     * 下单用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 方向（1-买入, 2-卖出）
     */
    @Column(name = "direction", nullable = false)
    private Integer direction;

    /**
     * 碳配额数量（吨CO2当量）
     */
    @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    /**
     * 期望单价（元/吨）
     */
    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    /**
     * 已匹配数量
     */
    @Column(name = "matched_quantity", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal matchedQuantity = BigDecimal.ZERO;

    /**
     * 挂单状态（0-待匹配, 1-部分匹配, 2-完全匹配, 3-已取消）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 0;

    /**
     * 冻结资产额（买单为碳币金额，卖单为配额吨数）
     */
    @Column(name = "frozen_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal frozenAmount = BigDecimal.ZERO;

    /**
     * 成交价格（撮合后）
     */
    @Column(name = "settlement_price", precision = 15, scale = 2)
    private BigDecimal settlementPrice;

    /**
     * 匹配时间
     */
    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    /**
     * 乐观锁版本号
     */
    @Version
    @Builder.Default
    private Long version = 0L;
}
