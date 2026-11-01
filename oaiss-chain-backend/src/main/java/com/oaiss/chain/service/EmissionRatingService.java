package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonReport;
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

    /**
     * 权威报告状态：已通过与已上链。
     *
     * <p>定义点在 {@link com.oaiss.chain.enums.ReportStatusEnum#authoritativeCodes()}。</p>
     */
    private static final java.util.List<Integer> AUTHORITATIVE_STATUSES =
            com.oaiss.chain.enums.ReportStatusEnum.authoritativeCodes();


    private final EmissionRatingRepository ratingRepository;

    /** 用于按年度汇总权威报告（D10） */
    private final com.oaiss.chain.repository.CarbonReportRepository carbonReportRepository;

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
     * 按**年度汇总**重算企业评级（D10 修正）。
     *
     * <p>历史实现是「每份通过的报告直接 rateEnterprise(该报告排放量)」，
     * 由于 upsert 语义，同一年份有多份报告时只有最后一份生效——
     * 「年度评级」实际是「最后一份报告的评级」。</p>
     *
     * <p>现在改为：取该年度所有已通过/已上链报告，**求和后**再评级。
     * 只有一份报告时结果与旧行为一致；多份时才是真正的年度汇总。</p>
     *
     * @return 重算后的评级；该年度无权威报告时返回 null，不覆盖既有记录
     */
    @Transactional
    public EmissionRating recalculateYearlyRating(Long enterpriseId, String year, Long ratedBy) {
        if (enterpriseId == null || year == null || year.isBlank()) {
            return null;
        }
        String periodPrefix = year.length() > 4 ? year.substring(0, 4) : year;

        List<CarbonReport> yearly = carbonReportRepository
                .findYearlyAuthoritativeReports(enterpriseId, periodPrefix, AUTHORITATIVE_STATUSES);
        if (yearly.isEmpty()) {
            log.debug("No authoritative reports for enterprise {} year {}, skip rating recalc",
                    enterpriseId, periodPrefix);
            return null;
        }

        BigDecimal yearlyTotal = yearly.stream()
                .map(CarbonReport::getTotalEmission)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Recalculating yearly rating: enterprise={}, year={}, reports={}, total={}",
                enterpriseId, periodPrefix, yearly.size(), yearlyTotal);
        return rateEnterprise(enterpriseId, periodPrefix, yearlyTotal, null, ratedBy, yearly.size());
    }

    /**
     * 生成企业年度碳排放评级（无来源份数，保留给「单份报告直接评级」的旧调用方）。
     */
    @Transactional
    public EmissionRating rateEnterprise(Long enterpriseId, String year,
                                          BigDecimal totalEmission, BigDecimal revenue,
                                          Long ratedBy) {
        return rateEnterprise(enterpriseId, year, totalEmission, revenue, ratedBy, null);
    }

    /**
     * 生成企业年度碳排放评级。
     *
     * @param sourceReportCount 本次汇总所依据的报告份数；null 表示调用方未提供来源
     */
    @Transactional
    public EmissionRating rateEnterprise(Long enterpriseId, String year,
                                          BigDecimal totalEmission, BigDecimal revenue,
                                          Long ratedBy, Integer sourceReportCount) {
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
                    if (sourceReportCount != null) {
                        existing.setSourceReportCount(sourceReportCount);
                    }
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
                        .sourceReportCount(sourceReportCount)
                        .build());

        EmissionRating saved = ratingRepository.save(rating);
        recalculatePercentiles(year, saved);
        return saved;
    }

    /**
     * 回写同年度全体企业的排放百分位（0-100，越小越好；并列共享同一值）。
     * 在每次评级保存后重算整组 cohort，保证任一企业重评时其余企业百分位同步刷新。
     */
    private void recalculatePercentiles(String year, EmissionRating current) {
        List<EmissionRating> cohort = ratingRepository
                .findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc(year);
        if (cohort.isEmpty()) {
            return;
        }
        for (EmissionRating r : cohort) {
            long less = cohort.stream()
                    .filter(o -> o.getTotalEmission().compareTo(r.getTotalEmission()) < 0)
                    .count();
            long greater = cohort.stream()
                    .filter(o -> o.getTotalEmission().compareTo(r.getTotalEmission()) > 0)
                    .count();
            int percentile = (less + greater == 0) ? 0
                    : (int) Math.round(100.0 * less / (less + greater));
            if (!Integer.valueOf(percentile).equals(r.getPercentileRank())) {
                r.setPercentileRank(percentile);
                ratingRepository.save(r);
            }
        }
        cohort.stream()
                .filter(r -> r.getEnterpriseId().equals(current.getEnterpriseId()))
                .findFirst()
                .ifPresent(match -> current.setPercentileRank(match.getPercentileRank()));
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
