package com.oaiss.chain.repository;

import com.oaiss.chain.entity.EnterpriseAdmission;
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
class EnterpriseAdmissionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EnterpriseAdmissionRepository repository;

    private EnterpriseAdmission admission1;
    private EnterpriseAdmission admission2;
    private EnterpriseAdmission deletedAdmission;

    @BeforeEach
    void setUp() {
        admission1 = createAdmission(1L, "CERT-001", 1);
        admission2 = createAdmission(1L, "CERT-002", 2);
        deletedAdmission = createAdmission(2L, "CERT-003", 1);
        deletedAdmission.setDeleted(true);

        entityManager.persist(admission1);
        entityManager.persist(admission2);
        entityManager.persist(deletedAdmission);
        entityManager.flush();
    }

    private EnterpriseAdmission createAdmission(Long enterpriseId, String certificateNo, Integer status) {
        EnterpriseAdmission admission = EnterpriseAdmission.builder()
                .enterpriseId(enterpriseId)
                .certificateNo(certificateNo)
                .issuedDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusYears(1))
                .status(status)
                .build();
        admission.setCreatedAt(LocalDateTime.now());
        admission.setUpdatedAt(LocalDateTime.now());
        return admission;
    }

    @Test
    @DisplayName("findByEnterpriseIdAndDeletedFalse should return enterprise admissions")
    void findByEnterpriseIdAndDeletedFalse_shouldReturnAdmissions() {
        List<EnterpriseAdmission> result = repository.findByEnterpriseIdAndDeletedFalse(1L);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findFirstByEnterpriseIdAndStatusAndDeletedFalse should return first match")
    void findFirstByEnterpriseIdAndStatus_shouldReturnFirstMatch() {
        Optional<EnterpriseAdmission> result = repository.findFirstByEnterpriseIdAndStatusAndDeletedFalse(1L, 1);
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByDeletedFalse should return all undeleted")
    void findByDeletedFalse_shouldReturnAllUndeleted() {
        Page<EnterpriseAdmission> result = repository.findByDeletedFalse(PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findByStatusAndDeletedFalse should filter by status")
    void findByStatusAndDeletedFalse_shouldFilterByStatus() {
        Page<EnterpriseAdmission> result = repository.findByStatusAndDeletedFalse(2, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("existsByCertificateNoAndDeletedFalse should return true for existing")
    void existsByCertificateNo_shouldReturnTrueForExisting() {
        assertThat(repository.existsByCertificateNoAndDeletedFalse("CERT-001")).isTrue();
    }

    @Test
    @DisplayName("existsByCertificateNoAndDeletedFalse should return false for deleted")
    void existsByCertificateNo_shouldReturnFalseForDeleted() {
        assertThat(repository.existsByCertificateNoAndDeletedFalse("CERT-003")).isFalse();
    }
}
