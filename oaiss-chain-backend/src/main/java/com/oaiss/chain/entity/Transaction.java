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
     * 单价（元/吨）
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
}
