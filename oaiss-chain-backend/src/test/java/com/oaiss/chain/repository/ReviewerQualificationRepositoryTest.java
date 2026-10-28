package com.oaiss.chain.repository;

import com.oaiss.chain.entity.ReviewerQualification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReviewerQualificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReviewerQualificationRepository repository;

    private ReviewerQualification qual1;
    private ReviewerQualification qual2;
    private ReviewerQualification deletedQual;

    @BeforeEach
    void setUp() {
        qual1 = createQualification(1L, "碳审计师", "QC-001", 1);
        qual2 = createQualification(1L, "碳核查员", "QC-002", 1);
        deletedQual = createQualification(2L, "已删除资质", "QC-003", 2);
        deletedQual.setDeleted(true);

        entityManager.persist(qual1);
        entityManager.persist(qual2);
        entityManager.persist(deletedQual);
        entityManager.flush();
    }

    private ReviewerQualification createQualification(Long reviewerId, String type,
                                                       String certNo, Integer status) {
        ReviewerQualification q = ReviewerQualification.builder()
                .reviewerId(reviewerId)
                .qualificationType(type)
                .certificateNo(certNo)
                .issuingAuthority("Test Authority")
                .issuedDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusYears(3))
                .status(status)
                .build();
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        return q;
    }

    @Test
    @DisplayName("findByReviewerIdAndDeletedFalse should return reviewer qualifications")
    void findByReviewerId_shouldReturnQualifications() {
        List<ReviewerQualification> result = repository.findByReviewerIdAndDeletedFalse(1L);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByReviewerIdAndStatusAndDeletedFalse should filter by status")
    void findByReviewerIdAndStatus_shouldFilterByStatus() {
        List<ReviewerQualification> result = repository.findByReviewerIdAndStatusAndDeletedFalse(1L, 1);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("existsByCertificateNoAndDeletedFalse should return true for existing")
    void existsByCertificateNo_shouldReturnTrueForExisting() {
        assertThat(repository.existsByCertificateNoAndDeletedFalse("QC-001")).isTrue();
    }

    @Test
    @DisplayName("existsByCertificateNoAndDeletedFalse should return false for deleted")
    void existsByCertificateNo_shouldReturnFalseForDeleted() {
        assertThat(repository.existsByCertificateNoAndDeletedFalse("QC-003")).isFalse();
    }

    @Test
    @DisplayName("findByDeletedFalse should return all undeleted")
    void findByDeletedFalse_shouldReturnAllUndeleted() {
        Page<ReviewerQualification> result = repository.findByDeletedFalse(PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findByStatusAndDeletedFalse should filter by status")
    void findByStatusAndDeletedFalse_shouldFilterByStatus() {
        Page<ReviewerQualification> result = repository.findByStatusAndDeletedFalse(2, PageRequest.of(0, 10));
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc should return latest")
    void findFirstByReviewerIdAndStatus_shouldReturnLatest() {
        Optional<ReviewerQualification> result = repository
                .findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc(1L, 1);
        assertThat(result).isPresent();
    }
}
