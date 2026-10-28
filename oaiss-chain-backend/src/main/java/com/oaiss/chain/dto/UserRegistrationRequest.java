package com.oaiss.chain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 新注册用户数预测请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationRequest {

    @NotEmpty(message = "日期列表不能为空")
    @Size(min = 2, message = "至少需要2个数据点")
    private List<String> dates;

    @NotEmpty(message = "注册数量列表不能为空")
    @Size(min = 2, message = "至少需要2个数据点")
    private List<Double> counts;

    @Min(value = 1, message = "预测天数至少为1")
    @Max(value = 365, message = "预测天数不能超过365")
    @Builder.Default
    private Integer horizonDays = 30;
}
