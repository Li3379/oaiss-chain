package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 碳交易响应DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeResponse {

    private Long id;
    private String tradeNo;
    private Integer tradeType;
    private String tradeTypeText;
    private Long sellerId;
    private String sellerName;
    private Long buyerId;
    private String buyerName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Long reportId;
    private Integer status;
    private String statusText;
    private String remark;
    private String blockchainTxHash;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
