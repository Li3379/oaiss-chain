package com.oaiss.chain.repository;

import com.oaiss.chain.entity.ReviewerQualification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 审核员资质 Repository
 *
 * @author OAISS Team
 */
@Repository
public interface ReviewerQualificationRepository extends JpaRepository<ReviewerQualification, Long> {

    List<ReviewerQualification> findByReviewerIdAndDeletedFalse(Long reviewerId);

    List<ReviewerQualification> findByReviewerIdAndStatusAndDeletedFalse(Long reviewerId, Integer status);

    boolean existsByCertificateNoAndDeletedFalse(String certificateNo);

    Page<ReviewerQualification> findByDeletedFalse(Pageable pageable);

    Page<ReviewerQualification> findByStatusAndDeletedFalse(Integer status, Pageable pageable);

    Optional<ReviewerQualification> findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc(
            Long reviewerId, Integer status);
}
