package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 撮合结果响应DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
public class MatchingResultResponse {

    private Long id;
    private String matchNo;
    private Long buyOrderId;
    private Long sellOrderId;
    private Long buyerId;
    private Long sellerId;
    private String buyerName;
    private String sellerName;
    private BigDecimal matchedQuantity;
    private BigDecimal settlementPrice;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusText;
    private Long transactionId;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
}
