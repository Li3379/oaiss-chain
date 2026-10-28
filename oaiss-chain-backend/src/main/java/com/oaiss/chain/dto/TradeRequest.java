package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 碳交易请求DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeRequest {

    /**
     * 交易类型（1-拍卖, 2-P2P）
     */
    @NotNull(message = "交易类型不能为空")
    private Integer tradeType;

    /**
     * 买方ID（P2P交易时必填）
     */
    private Long buyerId;

    /**
     * 卖方ID（P2P交易时必填）
     */
    private Long sellerId;

    /**
     * 碳配额数量（吨CO2当量）
     */
    @NotNull(message = "交易数量不能为空")
    @Positive(message = "交易数量必须为正数")
    private BigDecimal quantity;

    /**
     * 单价（元/吨）
     */
    @NotNull(message = "单价不能为空")
    @Positive(message = "单价必须为正数")
    private BigDecimal unitPrice;

    /**
     * 关联碳报告ID（可选）
     */
    private Long reportId;

    /**
     * 交易备注
     */
    private String remark;
}
