package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.EmissionRating;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EmissionRatingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 碳排放评级服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmissionRatingServiceTest {

    @Mock
    private EmissionRatingRepository ratingRepository;

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @InjectMocks
    private EmissionRatingService emissionRatingService;

    // ==================== getEnterpriseRatings ====================

    @Test
    @DisplayName("获取企业评级历史 - 成功")
    void getEnterpriseRatings_ShouldReturnList() {
        when(ratingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(1L)).thenReturn(List.of(buildRating("2025")));
        List<EmissionRating> result = emissionRatingService.getEnterpriseRatings(1L);
        assertEquals(1, result.size());
    }

    // ==================== rateEnterprise ====================

    @Test
    @DisplayName("评级 - A级(<1000吨)")
    void rateEnterprise_LevelA() {
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.rateEnterprise(
                1L, "2025", new BigDecimal("500"), new BigDecimal("100000"), 1L);

        assertEquals("A", rating.getRatingLevel());
        assertTrue(rating.getRatingScore() >= 90);
        assertNotNull(rating.getEmissionIntensity());
    }

    @Test
    @DisplayName("评级 - B级(1000-5000吨)")
    void rateEnterprise_LevelB() {
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.rateEnterprise(
                1L, "2025", new BigDecimal("3000"), new BigDecimal("500000"), 1L);

        assertEquals("B", rating.getRatingLevel());
        assertTrue(rating.getRatingScore() >= 70 && rating.getRatingScore() < 90);
    }

    @Test
    @DisplayName("评级 - C级(5000-20000吨)")
    void rateEnterprise_LevelC() {
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.rateEnterprise(
                1L, "2025", new BigDecimal("10000"), new BigDecimal("1000000"), 1L);

        assertEquals("C", rating.getRatingLevel());
        assertTrue(rating.getRatingScore() >= 50 && rating.getRatingScore() < 70);
    }

    @Test
    @DisplayName("评级 - D级(20000-50000吨)")
    void rateEnterprise_LevelD() {
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.rateEnterprise(
                1L, "2025", new BigDecimal("30000"), new BigDecimal("2000000"), 1L);

        assertEquals("D", rating.getRatingLevel());
        assertTrue(rating.getRatingScore() >= 30 && rating.getRatingScore() < 50);
    }

    @Test
    @DisplayName("评级 - E级(>50000吨)")
    void rateEnterprise_LevelE() {
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.rateEnterprise(
                1L, "2025", new BigDecimal("60000"), new BigDecimal("5000000"), 1L);

        assertEquals("E", rating.getRatingLevel());
        assertTrue(rating.getRatingScore() < 30);
    }

    @Test
    @DisplayName("评级 - 已存在时更新")
    void rateEnterprise_AlreadyExists_ShouldUpdate() {
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025"))
                .thenReturn(Optional.of(buildRating("2025")));
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.rateEnterprise(
                1L, "2025", new BigDecimal("500"), null, 1L);

        assertNotNull(rating);
        verify(ratingRepository).save(any(EmissionRating.class));
    }

    @Test
    @DisplayName("评级 - revenue为null时intensity为null")
    void rateEnterprise_NullRevenue_IntensityIsNull() {
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.rateEnterprise(1L, "2025", new BigDecimal("500"), null, 1L);
        assertNull(rating.getEmissionIntensity());
    }

    @Test
    @DisplayName("评级 - revenue为0时intensity为null")
    void rateEnterprise_ZeroRevenue_IntensityIsNull() {
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.rateEnterprise(1L, "2025", new BigDecimal("500"), BigDecimal.ZERO, 1L);
        assertNull(rating.getEmissionIntensity());
    }

    // ==================== recalculateYearlyRating (D10 / AC-B5 / AC-B6) ====================

    @Test
    @DisplayName("年度评级 - 同年多份权威报告应求和，而不是取最后一份")
    void recalculateYearlyRating_SumsAllAuthoritativeReports() {
        when(carbonReportRepository.findYearlyAuthoritativeReports(eq(1L), eq("2025"), anyList()))
                .thenReturn(List.of(buildReport(new BigDecimal("1000")), buildReport(new BigDecimal("2500"))));
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.recalculateYearlyRating(1L, "2025", 9L);

        assertNotNull(rating);
        // 1000 + 2500 = 3500 → B 级区间
        assertEquals(0, new BigDecimal("3500").compareTo(rating.getTotalEmission()));
        assertEquals("B", rating.getRatingLevel());
        // AC-B6：必须记录「这个数是几份报告加出来的」
        assertEquals(2, rating.getSourceReportCount());
    }

    @Test
    @DisplayName("年度评级 - 该年度无权威报告时返回 null 且不写入评级")
    void recalculateYearlyRating_NoReports_ReturnsNull() {
        when(carbonReportRepository.findYearlyAuthoritativeReports(eq(1L), eq("2025"), anyList()))
                .thenReturn(List.of());

        EmissionRating rating = emissionRatingService.recalculateYearlyRating(1L, "2025", 9L);

        assertNull(rating);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    @DisplayName("年度评级 - 入参年份带季度时按前四位归年")
    void recalculateYearlyRating_QuarterPeriodNormalizedToYear() {
        when(carbonReportRepository.findYearlyAuthoritativeReports(eq(1L), eq("2025"), anyList()))
                .thenReturn(List.of(buildReport(new BigDecimal("1000"))));
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmissionRating rating = emissionRatingService.recalculateYearlyRating(1L, "2025-Q3", 9L);

        assertNotNull(rating);
        assertEquals("2025", rating.getRatingYear());
        assertEquals(1, rating.getSourceReportCount());
    }

    // ==================== getIndustryRanking ====================

    @Test
    @DisplayName("获取行业排名 - 成功")
    void getIndustryRanking_ShouldReturnList() {
        when(ratingRepository.findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc("2025"))
                .thenReturn(List.of(buildRating("2025")));
        List<EmissionRating> result = emissionRatingService.getIndustryRanking("2025");
        assertEquals(1, result.size());
    }

    private CarbonReport buildReport(BigDecimal total) {
        CarbonReport report = CarbonReport.builder()
                .enterpriseId(1L)
                .accountingPeriod("2025")
                .totalEmission(total)
                .status(3)
                .build();
        return report;
    }

    private EmissionRating buildRating(String year) {
        return EmissionRating.builder()
                .enterpriseId(1L).ratingYear(year)
                .totalEmission(new BigDecimal("1000"))
                .ratingLevel("B").ratingScore(75)
                .ratedBy(1L).build();
    }

    // ==================== percentileRank 回写 (TDD RED) ====================

    @Test
    @DisplayName("评级保存后回写同年度全体百分位：排放越低百分位越小(越小越好)")
    void rateEnterprise_writesBackPercentileForCohort() {
        EmissionRating low = EmissionRating.builder().enterpriseId(1L).ratingYear("2025")
                .totalEmission(new BigDecimal("500")).build();
        low.setId(1L);
        EmissionRating high = EmissionRating.builder().enterpriseId(2L).ratingYear("2025")
                .totalEmission(new BigDecimal("1500")).build();
        high.setId(2L);
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any(EmissionRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ratingRepository.findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc("2025"))
                .thenReturn(List.of(low, high));

        EmissionRating result = emissionRatingService.rateEnterprise(1L, "2025",
                new BigDecimal("500"), null, 99L);

        assertEquals(0, result.getPercentileRank());
        assertEquals(100, high.getPercentileRank());
        verify(ratingRepository, times(3)).save(any(EmissionRating.class));
    }

    @Test
    @DisplayName("并列排放量共享同一百分位")
    void rateEnterprise_tiesSharePercentile() {
        EmissionRating a = EmissionRating.builder().enterpriseId(1L).ratingYear("2025")
                .totalEmission(new BigDecimal("800")).build();
        a.setId(1L);
        EmissionRating b = EmissionRating.builder().enterpriseId(2L).ratingYear("2025")
                .totalEmission(new BigDecimal("800")).build();
        b.setId(2L);
        EmissionRating c = EmissionRating.builder().enterpriseId(3L).ratingYear("2025")
                .totalEmission(new BigDecimal("2000")).build();
        c.setId(3L);
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any(EmissionRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ratingRepository.findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc("2025"))
                .thenReturn(List.of(a, b, c));

        emissionRatingService.rateEnterprise(1L, "2025", new BigDecimal("800"), null, 99L);

        assertEquals(0, a.getPercentileRank());
        assertEquals(0, b.getPercentileRank());
        assertEquals(100, c.getPercentileRank());
    }

    @Test
    @DisplayName("单人 cohort 百分位为 0")
    void rateEnterprise_singleEnterprisePercentileZero() {
        EmissionRating solo = EmissionRating.builder().enterpriseId(1L).ratingYear("2025")
                .totalEmission(new BigDecimal("500")).build();
        solo.setId(1L);
        when(ratingRepository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025")).thenReturn(Optional.empty());
        when(ratingRepository.save(any(EmissionRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ratingRepository.findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc("2025"))
                .thenReturn(List.of(solo));

        EmissionRating result = emissionRatingService.rateEnterprise(1L, "2025",
                new BigDecimal("500"), null, 99L);

        assertEquals(0, result.getPercentileRank());
    }
}
