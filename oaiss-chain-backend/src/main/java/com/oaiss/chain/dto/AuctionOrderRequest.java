package com.oaiss.chain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 拍卖挂单请求DTO
 *
 * @author OAISS Team
 */
@Data
public class AuctionOrderRequest {

    /**
     * 方向（1-买入, 2-卖出）
     */
    @NotNull(message = "挂单方向不能为空")
    @Min(value = 1, message = "挂单方向只能为1或2")
    @Max(value = 2, message = "挂单方向只能为1或2")
    private Integer direction;

    /**
     * 碳配额数量（吨CO2当量）
     */
    @NotNull(message = "挂单数量不能为空")
    @Positive(message = "挂单数量必须为正数")
    private BigDecimal quantity;

    /**
     * 期望单价（元/吨）
     */
    @NotNull(message = "挂单价格不能为空")
    @Positive(message = "挂单价格必须为正数")
    private BigDecimal price;
}
