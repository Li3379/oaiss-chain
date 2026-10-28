package com.oaiss.chain.service;

import com.oaiss.chain.entity.EmissionRating;
import com.oaiss.chain.exception.BusinessException;
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

    // ==================== getIndustryRanking ====================

    @Test
    @DisplayName("获取行业排名 - 成功")
    void getIndustryRanking_ShouldReturnList() {
        when(ratingRepository.findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc("2025"))
                .thenReturn(List.of(buildRating("2025")));
        List<EmissionRating> result = emissionRatingService.getIndustryRanking("2025");
        assertEquals(1, result.size());
    }

    private EmissionRating buildRating(String year) {
        return EmissionRating.builder()
                .enterpriseId(1L).ratingYear(year)
                .totalEmission(new BigDecimal("1000"))
                .ratingLevel("B").ratingScore(75)
                .ratedBy(1L).build();
    }
}
