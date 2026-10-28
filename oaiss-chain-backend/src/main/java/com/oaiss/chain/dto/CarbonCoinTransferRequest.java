package com.oaiss.chain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 碳币转账请求
 *
 * @author OAISS Team
 */
@Data
public class CarbonCoinTransferRequest {

    @NotNull(message = "对方用户ID不能为空")
    private Long counterpartId;

    @NotNull(message = "转账金额不能为空")
    @DecimalMin(value = "0.01", message = "转账金额最小0.01")
    private BigDecimal amount;

    private String remark;
}
