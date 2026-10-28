package com.oaiss.chain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 燃煤碳氧化率实测反推计算请求DTO (GB/T 32150-2015)
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoalOxidationRateRequest {

    /** 炉渣产量（吨） */
    @NotNull(message = "炉渣产量不能为空")
    @DecimalMin(value = "0.0", message = "炉渣产量不能为负")
    private BigDecimal slagWeight;

    /** 炉渣含碳量（比例，如 0.02 表示 2%） */
    @NotNull(message = "炉渣含碳量不能为空")
    @DecimalMin(value = "0.0", message = "炉渣含碳量不能为负")
    @DecimalMax(value = "1.0", message = "炉渣含碳量不能超过1.0")
    private BigDecimal slagCarbonContent;

    /** 飞灰产量（吨） */
    @NotNull(message = "飞灰产量不能为空")
    @DecimalMin(value = "0.0", message = "飞灰产量不能为负")
    private BigDecimal flyAshWeight;

    /** 飞灰含碳量（比例，如 0.03 表示 3%） */
    @NotNull(message = "飞灰含碳量不能为空")
    @DecimalMin(value = "0.0", message = "飞灰含碳量不能为负")
    @DecimalMax(value = "1.0", message = "飞灰含碳量不能超过1.0")
    private BigDecimal flyAshCarbonContent;

    /** 除尘系统平均除尘效率（比例，默认 1.0 表示 100%） */
    @DecimalMin(value = "0.01", message = "除尘效率必须大于0")
    @DecimalMax(value = "1.0", message = "除尘效率不能超过1.0")
    private BigDecimal dustRemovalEfficiency;

    /** 燃煤消耗量（吨） */
    @NotNull(message = "燃煤消耗量不能为空")
    @DecimalMin(value = "0.0001", message = "燃煤消耗量必须大于0")
    private BigDecimal coalConsumption;

    /** 燃煤低位发热值（千焦/千克 kJ/kg） */
    @NotNull(message = "低位发热值不能为空")
    @DecimalMin(value = "0.0001", message = "低位发热值必须大于0")
    private BigDecimal netCalorificValue;

    /** 燃煤单位热值含碳量（吨碳/太焦 tC/TJ） */
    @NotNull(message = "单位热值含碳量不能为空")
    @DecimalMin(value = "0.0001", message = "单位热值含碳量必须大于0")
    private BigDecimal carbonContent;
}
