package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CarbonNeutralProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CarbonNeutralProjectRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CarbonNeutralProjectRepository repository;

    private CarbonNeutralProject project1;
    private CarbonNeutralProject project2;
    private CarbonNeutralProject deletedProject;

    @BeforeEach
    void setUp() {
        project1 = createProject("PN-001", "碳汇项目", 1, 100L, 0);
        project2 = createProject("PN-002", "CCUS项目", 2, 100L, 1);
        deletedProject = createProject("PN-003", "已删除项目", 1, 200L, 0);
        deletedProject.setDeleted(true);

        entityManager.persist(project1);
        entityManager.persist(project2);
        entityManager.persist(deletedProject);
        entityManager.flush();
    }

    private CarbonNeutralProject createProject(String projectNo, String projectName,
                                                Integer projectType, Long ownerId, Integer status) {
        CarbonNeutralProject p = CarbonNeutralProject.builder()
                .projectNo(projectNo)
                .projectName(projectName)
                .projectType(projectType)
                .ownerId(ownerId)
                .expectedReduction(new BigDecimal("1000.0000"))
                .investmentAmount(new BigDecimal("500000.00"))
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .status(status)
                .certStatus(0)
                .verificationStatus(0)
                .issuedCredits(BigDecimal.ZERO)
                .usedCredits(BigDecimal.ZERO)
                .build();
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    @Test
    @DisplayName("findByProjectNo should return matching project")
    void findByProjectNo_shouldReturnMatching() {
        Optional<CarbonNeutralProject> result = repository.findByProjectNo("PN-001");
        assertThat(result).isPresent();
        assertThat(result.get().getProjectName()).isEqualTo("碳汇项目");
    }

    @Test
    @DisplayName("findByProjectNo should return empty for non-existing")
    void findByProjectNo_shouldReturnEmptyForNonExisting() {
        Optional<CarbonNeutralProject> result = repository.findByProjectNo("NON-EXIST");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByOwnerIdAndDeletedFalse should return owner's projects")
    void findByOwnerIdAndDeletedFalse_shouldReturnOwnerProjects() {
        Page<CarbonNeutralProject> result = repository.findByOwnerIdAndDeletedFalse(
                100L, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findByProjectTypeAndDeletedFalse should filter by type")
    void findByProjectTypeAndDeletedFalse_shouldFilterByType() {
        Page<CarbonNeutralProject> result = repository.findByProjectTypeAndDeletedFalse(
                1, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getProjectType()).isEqualTo(1);
    }

    @Test
    @DisplayName("search with no filters should return all undeleted")
    void search_noFilters_shouldReturnAllUndeleted() {
        Page<CarbonNeutralProject> result = repository.search(
                null, null, null, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("search by projectType should filter correctly")
    void search_byProjectType_shouldFilterCorrectly() {
        Page<CarbonNeutralProject> result = repository.search(
                1, null, null, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("search by status should filter correctly")
    void search_byStatus_shouldFilterCorrectly() {
        Page<CarbonNeutralProject> result = repository.search(
                null, 1, null, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("search by keyword should match project name")
    void search_byKeyword_shouldMatchProjectName() {
        Page<CarbonNeutralProject> result = repository.search(
                null, null, "碳汇", PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getProjectName()).contains("碳汇");
    }

    @Test
    @DisplayName("search by keyword should match project no")
    void search_byKeyword_shouldMatchProjectNo() {
        Page<CarbonNeutralProject> result = repository.search(
                null, null, "PN-001", PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("findByOwnerIdAndStatusAndDeletedFalse should filter by owner and status")
    void findByOwnerIdAndStatusAndDeletedFalse_shouldFilter() {
        Page<CarbonNeutralProject> result = repository.findByOwnerIdAndStatusAndDeletedFalse(
                100L, 0, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
    }
}
