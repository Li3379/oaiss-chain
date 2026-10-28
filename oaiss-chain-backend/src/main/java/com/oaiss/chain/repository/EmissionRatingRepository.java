package com.oaiss.chain.repository;

import com.oaiss.chain.entity.EmissionRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 碳排放评级 Repository
 *
 * @author OAISS Team
 */
@Repository
public interface EmissionRatingRepository extends JpaRepository<EmissionRating, Long> {

    List<EmissionRating> findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(Long enterpriseId);

    Optional<EmissionRating> findByEnterpriseIdAndRatingYearAndDeletedFalse(Long enterpriseId, String ratingYear);

    List<EmissionRating> findByRatingYearAndDeletedFalseOrderByTotalEmissionAsc(String ratingYear);
}
