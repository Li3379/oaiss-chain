package com.oaiss.chain.repository;

import com.oaiss.chain.entity.EmissionRating;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class EmissionRatingRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmissionRatingRepository repository;

    private EmissionRating rating1;
    private EmissionRating rating2;
    private EmissionRating deletedRating;

    @BeforeEach
    void setUp() {
        rating1 = createRating(1L, "2024", new BigDecimal("1000.0000"), "A", 95);
        rating2 = createRating(1L, "2023", new BigDecimal("1200.0000"), "B", 75);
        deletedRating = createRating(2L, "2024", new BigDecimal("500.0000"), "A", 90);
        deletedRating.setDeleted(true);

        entityManager.persist(rating1);
        entityManager.persist(rating2);
        entityManager.persist(deletedRating);
        entityManager.flush();
    }

    private EmissionRating createRating(Long enterpriseId, String ratingYear,
                                         BigDecimal totalEmission, String ratingLevel, Integer ratingScore) {
        EmissionRating rating = EmissionRating.builder()
                .enterpriseId(enterpriseId)
                .ratingYear(ratingYear)
                .totalEmission(totalEmission)
                .emissionIntensity(new BigDecimal("0.5000"))
                .ratingLevel(ratingLevel)
                .ratingScore(ratingScore)
                .percentileRank(10)
                .reductionRatio(new BigDecimal("5.00"))
                .build();
        rating.setCreatedAt(LocalDateTime.now());
        rating.setUpdatedAt(LocalDateTime.now());
        return rating;
    }

    @Test
    @DisplayName("findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc should return ordered ratings")
    void findByEnterpriseId_shouldReturnOrderedRatings() {
        List<EmissionRating> result = repository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(1L);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRatingYear()).isEqualTo("2024");
        assertThat(result.get(1).getRatingYear()).isEqualTo("2023");
    }

    @Test
    @DisplayName("findByEnterpriseIdAndRatingYearAndDeletedFalse should return matching rating")
    void findByEnterpriseIdAndRatingYear_shouldReturnMatching() {
        Optional<EmissionRating> result = repository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2024");
        assertThat(result).isPresent();
        assertThat(result.get().getRatingLevel()).isEqualTo("A");
    }

    @Test
    @DisplayName("findByEnterpriseIdAndRatingYearAndDeletedFalse should return empty for non-existing")
    void findByEnterpriseIdAndRatingYear_shouldReturnEmptyForNonExisting() {
        Optional<EmissionRating> result = repository.findByEnterpriseIdAndRatingYearAndDeletedFalse(1L, "2025");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc should return ordered by emission")
    void findByRatingYear_shouldReturnOrderedByEmission() {
        List<EmissionRating> result = repository.findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc("2024");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalEmission()).isEqualByComparingTo(new BigDecimal("1000.0000"));
    }
}
