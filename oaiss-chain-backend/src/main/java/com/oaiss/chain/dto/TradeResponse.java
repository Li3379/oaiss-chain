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

    /**
     * 成交审核态：null/0=无需审核，1=待审核，2=已通过，3=已驳回。
     *
     * <p>P2P 确认命中风控后不再「确认即完成」，而是停在待审核。
     * 前端必须据此显示「已提交审核」而非「交易完成」——
     * 否则用户会以为资产已经到账，而实际上配额与碳币仍在冻结态。</p>
     */
    private Integer auditStatus;
}
