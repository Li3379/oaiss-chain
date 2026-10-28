package com.oaiss.chain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 碳排放预测请求
 *
 * @author OAISS Team
 */
@Data
public class CarbonPredictionRequest {

    @NotNull(message = "企业ID不能为空")
    private Long enterpriseId;

    /**
     * 预测月数（默认6，范围1-12）
     */
    @Min(value = 1, message = "预测月数不能小于1")
    @Max(value = 12, message = "预测月数不能大于12")
    private Integer predictMonths = 6;
}
