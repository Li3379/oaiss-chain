package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电网碳排放计算响应
 * Power Grid Carbon Emission Calculation Response
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PowerGridCalculationResponse {

    /** 总排放量 (tCO2) */
    private BigDecimal totalEmission;

    /** 输配损耗排放量 (tCO2) */
    private BigDecimal transmissionLossEmission;

    /** 外购电力排放量 (tCO2) */
    private BigDecimal importedEmission;

    /** 六氟化硫设备泄漏排放量 (tCO2e) — 原生文档04支持 (GWP=23900) */
    private BigDecimal sf6Emission;

    /** 输配损耗电量 (MWh) — 中间值 V × L_rate */
    private BigDecimal transmissionLoss;

    /** 公式参考标准 */
    private String formulaReference;

    /** 报告年份 */
    private String reportingYear;

    /** 企业名称 */
    private String enterpriseName;

    /** 计算时间 */
    private LocalDateTime calculatedAt;
}
