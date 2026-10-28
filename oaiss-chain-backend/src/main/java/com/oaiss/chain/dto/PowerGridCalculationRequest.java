package com.oaiss.chain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 电网碳排放计算请求
 * Power Grid Carbon Emission Calculation Request
 *
 * <p>基于 GB/T 32150-2015 标准，9参数电网碳排放计算</p>
 *
 * @author OAISS Team
 */
@Data
public class PowerGridCalculationRequest {

    /** 输配电量 (MWh) */
    @NotNull(message = "输配电量不能为空")
    @DecimalMin(value = "0", message = "输配电量不能为负数")
    private BigDecimal transmissionVolume;

    /** 线损率 (0-1) */
    @NotNull(message = "线损率不能为空")
    @DecimalMin(value = "0", message = "线损率不能为负数")
    @DecimalMax(value = "1", message = "线损率不能超过1")
    private BigDecimal lineLossRate;

    /** 电网排放因子 (tCO2/MWh) */
    @NotNull(message = "电网排放因子不能为空")
    @DecimalMin(value = "0", message = "电网排放因子不能为负数")
    private BigDecimal gridEmissionFactor;

    /** 发电量 (MWh) — 可选参考 */
    @DecimalMin(value = "0", message = "发电量不能为负数")
    private BigDecimal generationVolume;

    /** 外购电量 (MWh) — 可选 */
    @DecimalMin(value = "0", message = "外购电量不能为负数")
    private BigDecimal importedElectricity;

    /** 外售电量 (MWh) — 可选 */
    @DecimalMin(value = "0", message = "外售电量不能为负数")
    private BigDecimal exportedElectricity;

    /** 外购电力排放因子 (tCO2/MWh) — 可选 */
    @DecimalMin(value = "0", message = "外购电力排放因子不能为负数")
    private BigDecimal importEmissionFactor;

    /** 退役设备SF6容量 (kg) — 原生文档04表3支持 */
    @DecimalMin(value = "0", message = "退役设备SF6容量不能为负数")
    private BigDecimal recCapacity;

    /** 退役设备SF6实际回收量 (kg) — 原生文档04表3支持 */
    @DecimalMin(value = "0", message = "退役设备SF6回收量不能为负数")
    private BigDecimal recRecovery;

    /** 检修设备SF6容量 (kg) — 原生文档04表3支持 */
    @DecimalMin(value = "0", message = "检修设备SF6容量不能为负数")
    private BigDecimal repCapacity;

    /** 检修设备SF6实际回收量 (kg) — 原生文档04表3支持 */
    @DecimalMin(value = "0", message = "检修设备SF6回收量不能为负数")
    private BigDecimal repRecovery;

    /** 报告年份 */
    @NotNull(message = "报告年份不能为空")
    private Integer reportingYear;

    /** 企业名称 */
    @NotBlank(message = "企业名称不能为空")
    private String enterpriseName;
}
