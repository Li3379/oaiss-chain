package com.oaiss.chain.service;

import com.oaiss.chain.entity.EmissionRating;
import com.oaiss.chain.repository.EmissionRatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 碳排放评级服务
 * 根据企业碳排放量进行A-E五级评定
 * <p>
 * 设计文档要求（doc01/doc03）：
 * - 碳排放评级系统
 * - 支持第三方审计和监管
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmissionRatingService {

    private final EmissionRatingRepository ratingRepository;

    /**
     * 评级阈值（吨CO2当量）
     * A: <1000, B: 1000-5000, C: 5000-20000, D: 20000-50000, E: >50000
     */
    private static final BigDecimal THRESHOLD_B = new BigDecimal("1000");
    private static final BigDecimal THRESHOLD_C = new BigDecimal("5000");
    private static final BigDecimal THRESHOLD_D = new BigDecimal("20000");
    private static final BigDecimal THRESHOLD_E = new BigDecimal("50000");

    /**
     * 获取企业评级历史
     */
    @Transactional(readOnly = true)
    public List<EmissionRating> getEnterpriseRatings(Long enterpriseId) {
        return ratingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId);
    }

    /**
     * 生成企业年度碳排放评级
     */
    @Transactional
    public EmissionRating rateEnterprise(Long enterpriseId, String year,
                                          BigDecimal totalEmission, BigDecimal revenue,
                                          Long ratedBy) {
        String level = calculateLevel(totalEmission);
        int score = calculateScore(totalEmission);
        BigDecimal intensity = revenue != null && revenue.compareTo(BigDecimal.ZERO) > 0
                ? totalEmission.divide(revenue, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("10000"))
                : null;

        // 已存在则更新，否则新建
        EmissionRating rating = ratingRepository
                .findByEnterpriseIdAndRatingYearAndDeletedFalse(enterpriseId, year)
                .map(existing -> {
                    existing.setTotalEmission(totalEmission);
                    existing.setEmissionIntensity(intensity);
                    existing.setRatingLevel(level);
                    existing.setRatingScore(score);
                    existing.setRatedBy(ratedBy);
                    return existing;
                })
                .orElseGet(() -> EmissionRating.builder()
                        .enterpriseId(enterpriseId)
                        .ratingYear(year)
                        .totalEmission(totalEmission)
                        .emissionIntensity(intensity)
                        .ratingLevel(level)
                        .ratingScore(score)
                        .ratedBy(ratedBy)
                        .build());

        return ratingRepository.save(rating);
    }

    /**
     * 获取行业排名
     */
    @Transactional(readOnly = true)
    public List<EmissionRating> getIndustryRanking(String year) {
        return ratingRepository.findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc(year);
    }

    // ==================== 私有方法 ====================

    private String calculateLevel(BigDecimal emission) {
        if (emission.compareTo(THRESHOLD_B) < 0) return "A";
        if (emission.compareTo(THRESHOLD_C) < 0) return "B";
        if (emission.compareTo(THRESHOLD_D) < 0) return "C";
        if (emission.compareTo(THRESHOLD_E) < 0) return "D";
        return "E";
    }

    private int calculateScore(BigDecimal emission) {
        // 线性映射到100分制（排放越低分数越高）
        if (emission.compareTo(THRESHOLD_B) < 0) return 90 + (int) ((1 - emission.doubleValue() / 1000) * 10);
        if (emission.compareTo(THRESHOLD_C) < 0) return 70 + (int) ((1 - emission.doubleValue() / 5000) * 20);
        if (emission.compareTo(THRESHOLD_D) < 0) return 50 + (int) ((1 - emission.doubleValue() / 20000) * 20);
        if (emission.compareTo(THRESHOLD_E) < 0) return 30 + (int) ((1 - emission.doubleValue() / 50000) * 20);
        return Math.max(10, (int) (30 - (emission.doubleValue() / 50000) * 20));
    }
}
