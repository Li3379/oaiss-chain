package com.oaiss.chain.repository;

import com.oaiss.chain.entity.Reviewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReviewerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReviewerRepository repository;

    private Reviewer reviewer1;
    private Reviewer reviewer2;
    private Reviewer deletedReviewer;

    @BeforeEach
    void setUp() {
        reviewer1 = createReviewer(1L, "QUAL-001", 1, 1);
        reviewer2 = createReviewer(2L, "QUAL-002", 2, 1);
        deletedReviewer = createReviewer(3L, "QUAL-003", 1, 0);
        deletedReviewer.setDeleted(true);

        entityManager.persist(reviewer1);
        entityManager.persist(reviewer2);
        entityManager.persist(deletedReviewer);
        entityManager.flush();
    }

    private Reviewer createReviewer(Long userId, String qualNo, Integer level, Integer status) {
        Reviewer r = Reviewer.builder()
                .userId(userId)
                .qualificationNo(qualNo)
                .level(level)
                .organization("Test Org")
                .completedReviews(0)
                .status(status)
                .build();
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }

    @Test
    @DisplayName("findByUserIdAndDeletedFalse should return reviewer by user ID")
    void findByUserId_shouldReturnReviewer() {
        Optional<Reviewer> result = repository.findByUserIdAndDeletedFalse(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getQualificationNo()).isEqualTo("QUAL-001");
    }

    @Test
    @DisplayName("findByUserIdAndDeletedFalse should return empty for deleted")
    void findByUserId_shouldReturnEmptyForDeleted() {
        Optional<Reviewer> result = repository.findByUserIdAndDeletedFalse(3L);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdInAndDeletedFalse should return matching reviewers")
    void findByUserIdIn_shouldReturnMatching() {
        List<Reviewer> result = repository.findByUserIdInAndDeletedFalse(List.of(1L, 2L));
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByQualificationNo should return matching reviewer")
    void findByQualificationNo_shouldReturnMatching() {
        Optional<Reviewer> result = repository.findByQualificationNo("QUAL-001");
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findByStatusAndDeletedFalse should filter by status")
    void findByStatus_shouldFilterByStatus() {
        Page<Reviewer> result = repository.findByStatusAndDeletedFalse(1, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findByLevelAndDeletedFalse should filter by level")
    void findByLevel_shouldFilterByLevel() {
        Page<Reviewer> result = repository.findByLevelAndDeletedFalse(1, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("findByDeletedFalse should return all undeleted reviewers")
    void findByDeletedFalse_shouldReturnAllUndeleted() {
        Page<Reviewer> result = repository.findByDeletedFalse(PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }
}
