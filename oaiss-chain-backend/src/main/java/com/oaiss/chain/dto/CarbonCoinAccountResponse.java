package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 碳币账户响应
 *
 * @author OAISS Team
 */
@Data
@Builder
public class CarbonCoinAccountResponse {
    private Long id;
    private Long userId;
    private BigDecimal balance;
    private BigDecimal frozenBalance;
    private BigDecimal totalRecharged;
    private BigDecimal totalSpent;
    private Integer status;
}
