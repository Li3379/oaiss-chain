package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 碳币账户表
 * 存储企业的碳币余额和交易记录
 * <p>
 * 设计文档要求（doc03）：
 * - 碳额度与碳币之间的交易市场
 * - 企业可用碳币购买碳配额或出售碳配额获得碳币
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "carbon_coin_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonCoinAccount extends BaseEntity {

    /**
     * 关联用户ID
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * 碳币余额
     */
    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * 冻结碳币金额（买单挂单中）
     */
    @Column(name = "frozen_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    /**
     * 累计充值碳币
     */
    @Column(name = "total_recharged", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalRecharged = BigDecimal.ZERO;

    /**
     * 累计消费碳币
     */
    @Column(name = "total_spent", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    /**
     * 账户状态（0-禁用, 1-启用）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 1;

    /**
     * 乐观锁版本号
     */
    @Version
    @Builder.Default
    private Long version = 0L;
}
