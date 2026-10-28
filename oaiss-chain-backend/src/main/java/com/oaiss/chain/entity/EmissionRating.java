package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 碳排放评级表
 * 根据企业碳排放量进行等级评定
 * <p>
 * 设计文档要求（doc01/doc03）：
 * - 碳排放评级系统，用于第三方审计和监管
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "emission_rating")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmissionRating extends BaseEntity {

    /**
     * 企业ID
     */
    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    /**
     * 评级年度
     */
    @Column(name = "rating_year", nullable = false, length = 4)
    private String ratingYear;

    /**
     * 碳排放总量（吨CO2当量）
     */
    @Column(name = "total_emission", nullable = false, precision = 15, scale = 4)
    private BigDecimal totalEmission;

    /**
     * 碳排放强度（吨CO2/万元产值）
     */
    @Column(name = "emission_intensity", precision = 15, scale = 4)
    private BigDecimal emissionIntensity;

    /**
     * 评级等级（A/B/C/D/E，A最优）
     */
    @Column(name = "rating_level", nullable = false, length = 1)
    private String ratingLevel;

    /**
     * 评级分数（0-100）
     */
    @Column(name = "rating_score", nullable = false)
    private Integer ratingScore;

    /**
     * 同行业排名百分位（0-100，越小越好）
     */
    @Column(name = "percentile_rank")
    private Integer percentileRank;

    /**
     * 减排比例（较上一年，百分比）
     */
    @Column(name = "reduction_ratio", precision = 5, scale = 2)
    private BigDecimal reductionRatio;

    /**
     * 评级机构/审核员ID
     */
    @Column(name = "rated_by")
    private Long ratedBy;

    /**
     * 评级说明
     */
    @Column(name = "remark", length = 1000)
    private String remark;
}
