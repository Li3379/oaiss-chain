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
    /**
     * 累计充值。
     *
     * <p><b>2026-11-03 起为历史遗留字段</b>：充值端点已下线，
     * 该值不会再增长。保留是为了让存量数据仍可解释，前端应展示
     * {@link #totalIssued} 而非本字段。
     */
    private BigDecimal totalRecharged;
    /**
     * 累计发放量（减排成果发放的累计，回收不减少）。
     *
     * <p>碳币模型改为「减排凭证」后的核心指标：企业一共获得过多少枚碳币。
     */
    private BigDecimal totalIssued;
    private BigDecimal totalSpent;
    private Integer status;
}
