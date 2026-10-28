package com.oaiss.chain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发电企业碳排放计算请求（GB/T 32150-2015）
 * 25参数公式: 5种燃料 × 4参数 + 脱硫3参数 + 元数据2参数
 *
 * @author OAISS Team
 */
@Data
public class PowerGenerationCalculationRequest {

    // ==================== 原煤 (Raw Coal) ====================

    /** 原煤消耗量 (t) */
    @DecimalMin(value = "0", message = "原煤消耗量不能为负")
    private BigDecimal rawCoalFc;

    /** 原煤低位发热量 (GJ/t) */
    @DecimalMin(value = "0", message = "原煤低位发热量不能为负")
    private BigDecimal rawCoalNcv;

    /** 原煤含碳量 (tC/GJ) */
    @DecimalMin(value = "0", message = "原煤含碳量不能为负")
    private BigDecimal rawCoalCc;

    /** 原煤碳氧化率 */
    @DecimalMin(value = "0", message = "原煤碳氧化率不能为负")
    @DecimalMax(value = "1", message = "原煤碳氧化率不能大于1")
    private BigDecimal rawCoalOf;

    // ==================== 洗精煤 (Cleaned Coal) ====================

    /** 洗精煤消耗量 (t) */
    @DecimalMin(value = "0", message = "洗精煤消耗量不能为负")
    private BigDecimal cleanedCoalFc;

    /** 洗精煤低位发热量 (GJ/t) */
    @DecimalMin(value = "0", message = "洗精煤低位发热量不能为负")
    private BigDecimal cleanedCoalNcv;

    /** 洗精煤含碳量 (tC/GJ) */
    @DecimalMin(value = "0", message = "洗精煤含碳量不能为负")
    private BigDecimal cleanedCoalCc;

    /** 洗精煤碳氧化率 */
    @DecimalMin(value = "0", message = "洗精煤碳氧化率不能为负")
    @DecimalMax(value = "1", message = "洗精煤碳氧化率不能大于1")
    private BigDecimal cleanedCoalOf;

    // ==================== 其他洗煤 (Other Washed Coal) ====================

    /** 其他洗煤消耗量 (t) */
    @DecimalMin(value = "0", message = "其他洗煤消耗量不能为负")
    private BigDecimal otherWashedCoalFc;

    /** 其他洗煤低位发热量 (GJ/t) */
    @DecimalMin(value = "0", message = "其他洗煤低位发热量不能为负")
    private BigDecimal otherWashedCoalNcv;

    /** 其他洗煤含碳量 (tC/GJ) */
    @DecimalMin(value = "0", message = "其他洗煤含碳量不能为负")
    private BigDecimal otherWashedCoalCc;

    /** 其他洗煤碳氧化率 */
    @DecimalMin(value = "0", message = "其他洗煤碳氧化率不能为负")
    @DecimalMax(value = "1", message = "其他洗煤碳氧化率不能大于1")
    private BigDecimal otherWashedCoalOf;

    // ==================== 型煤 (Briquette) ====================

    /** 型煤消耗量 (t) */
    @DecimalMin(value = "0", message = "型煤消耗量不能为负")
    private BigDecimal briquetteFc;

    /** 型煤低位发热量 (GJ/t) */
    @DecimalMin(value = "0", message = "型煤低位发热量不能为负")
    private BigDecimal briquetteNcv;

    /** 型煤含碳量 (tC/GJ) */
    @DecimalMin(value = "0", message = "型煤含碳量不能为负")
    private BigDecimal briquetteCc;

    /** 型煤碳氧化率 */
    @DecimalMin(value = "0", message = "型煤碳氧化率不能为负")
    @DecimalMax(value = "1", message = "型煤碳氧化率不能大于1")
    private BigDecimal briquetteOf;

    // ==================== 其他煤 (Other Coal) ====================

    /** 其他煤消耗量 (t) */
    @DecimalMin(value = "0", message = "其他煤消耗量不能为负")
    private BigDecimal otherCoalFc;

    /** 其他煤低位发热量 (GJ/t) */
    @DecimalMin(value = "0", message = "其他煤低位发热量不能为负")
    private BigDecimal otherCoalNcv;

    /** 其他煤含碳量 (tC/GJ) */
    @DecimalMin(value = "0", message = "其他煤含碳量不能为负")
    private BigDecimal otherCoalCc;

    /** 其他煤碳氧化率 */
    @DecimalMin(value = "0", message = "其他煤碳氧化率不能为负")
    @DecimalMax(value = "1", message = "其他煤碳氧化率不能大于1")
    private BigDecimal otherCoalOf;

    // ==================== 脱硫参数 ====================

    /** 碳酸盐消耗量 (t) */
    @DecimalMin(value = "0", message = "碳酸盐消耗量不能为负")
    private BigDecimal carbonateConsumed;

    /** 脱硫排放因子 (tCO2/t) */
    @DecimalMin(value = "0", message = "脱硫排放因子不能为负")
    private BigDecimal desulfEmissionFactor;

    /** 脱硫转化率 */
    @DecimalMin(value = "0", message = "脱硫转化率不能为负")
    @DecimalMax(value = "1", message = "脱硫转化率不能大于1")
    private BigDecimal desulfConversionRate;

    // ==================== 元数据 ====================

    /** 核算年份 */
    @NotNull(message = "核算年份不能为空")
    private Integer reportingYear;

    /** 企业名称 */
    @NotBlank(message = "企业名称不能为空")
    private String enterpriseName;
}
