package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拍卖挂单响应DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
public class AuctionOrderResponse {

    private Long id;
    private String orderNo;
    private Long userId;
    private Integer direction;
    private String directionText;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal matchedQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal frozenAmount;
    private Integer status;
    private String statusText;
    private BigDecimal settlementPrice;
    private LocalDateTime matchedAt;
    private LocalDateTime createdAt;
}
