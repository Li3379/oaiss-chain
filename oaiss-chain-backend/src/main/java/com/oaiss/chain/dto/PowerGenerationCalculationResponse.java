package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发电企业碳排放计算响应（GB/T 32150-2015）
 *
 * @author OAISS Team
 */
@Data
@Builder
public class PowerGenerationCalculationResponse {

    /** 总排放量 (tCO2) */
    private BigDecimal totalEmission;

    /** 燃烧排放量 (tCO2) */
    private BigDecimal combustionEmission;

    /** 脱硫排放量 (tCO2) */
    private BigDecimal desulfurizationEmission;

    /** 各燃料排放明细 */
    private List<FuelEmissionDetail> fuelDetails;

    /** 核算年份 */
    private String reportingYear;

    /** 企业名称 */
    private String enterpriseName;

    /** 公式依据 */
    private String formulaReference;

    /** 计算时间 */
    private LocalDateTime calculatedAt;

    /**
     * 单种燃料排放明细
     */
    @Data
    @Builder
    public static class FuelEmissionDetail {

        /** 燃料类型 */
        private String fuelType;

        /** 燃料消耗量 (t) */
        private BigDecimal fuelConsumption;

        /** 低位发热量 (GJ/t) */
        private BigDecimal netCalorificValue;

        /** 含碳量 (tC/GJ) */
        private BigDecimal carbonContent;

        /** 碳氧化率 */
        private BigDecimal oxidationRate;

        /** 排放量 (tCO2) */
        private BigDecimal emission;
    }
}
