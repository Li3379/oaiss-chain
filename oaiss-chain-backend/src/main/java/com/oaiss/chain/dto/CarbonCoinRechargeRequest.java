package com.oaiss.chain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 碳币充值请求
 *
 * @author OAISS Team
 */
@Data
public class CarbonCoinRechargeRequest {

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额最小0.01")
    private BigDecimal amount;

    /**
     * 支付方式（1-银行转账, 2-在线支付）
     */
    private Integer paymentMethod;

    private String remark;
}
