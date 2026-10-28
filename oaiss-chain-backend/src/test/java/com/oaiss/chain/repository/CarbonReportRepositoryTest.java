package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CarbonReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CarbonReportRepository 数据访问层测试
 * 
 * @author OAISS Team
 */
@DataJpaTest
@ActiveProfiles("test")
class CarbonReportRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CarbonReportRepository carbonReportRepository;

    private CarbonReport report1;
    private CarbonReport report2;
    private CarbonReport report3;
    private CarbonReport deletedReport;

    @BeforeEach
    void setUp() {
        // 创建测试数据 - 未删除的报告
        report1 = createCarbonReport("RPT-2024-001", 1L, 100L, "2024-Q1", "企业1碳报告", 1, 1);
        report2 = createCarbonReport("RPT-2024-002", 1L, 100L, "2024-Q2", "企业1碳报告Q2", 1, 2);
        report3 = createCarbonReport("RPT-2024-003", 2L, 200L, "2024-Q1", "企业2碳报告", 1, 1);
        
        // 创建已删除的报告
        deletedReport = createCarbonReport("RPT-2024-004", 1L, 100L, "2024-Q3", "已删除报告", 1, 0);
        deletedReport.setDeleted(true);

        entityManager.persist(report1);
        entityManager.persist(report2);
        entityManager.persist(report3);
        entityManager.persist(deletedReport);
        entityManager.flush();
    }

    private CarbonReport createCarbonReport(String reportNo, Long enterpriseId, Long submitterId,
                                            String accountingPeriod, String title, Integer reportType, Integer status) {
        LocalDateTime now = LocalDateTime.now();
        CarbonReport report = CarbonReport.builder()
                .reportNo(reportNo)
                .enterpriseId(enterpriseId)
                .submitterId(submitterId)
                .accountingPeriod(accountingPeriod)
                .title(title)
                .reportType(reportType)
                .emissionData("{\"scope1\":100,\"scope2\":200}")
                .totalEmission(new BigDecimal("300.0000"))
                .scope1Emission(new BigDecimal("100.0000"))
                .scope2Emission(new BigDecimal("200.0000"))
                .scope3Emission(BigDecimal.ZERO)
                .status(status)
                .build();
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        return report;
    }

    @Test
    @DisplayName("根据报告编号查找 - 存在时返回报告")
    void findByReportNo_whenExists_shouldReturnReport() {
        // When
        Optional<CarbonReport> result = carbonReportRepository.findByReportNo("RPT-2024-001");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getReportNo()).isEqualTo("RPT-2024-001");
        assertThat(result.get().getTitle()).isEqualTo("企业1碳报告");
    }

    @Test
    @DisplayName("根据报告编号查找 - 不存在时返回空")
    void findByReportNo_whenNotExists_shouldReturnEmpty() {
        // When
        Optional<CarbonReport> result = carbonReportRepository.findByReportNo("NOT-EXIST");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("根据企业ID分页查询 - 返回该企业未删除的报告")
    void findByEnterpriseIdAndDeletedFalse_shouldReturnUndeletedReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByEnterpriseIdAndDeletedFalse(1L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(CarbonReport::getEnterpriseId)
                .allMatch(id -> id.equals(1L));
        assertThat(result.getContent())
                .extracting(CarbonReport::getReportNo)
                .containsExactlyInAnyOrder("RPT-2024-001", "RPT-2024-002");
    }

    @Test
    @DisplayName("根据企业ID分页查询 - 不存在时返回空页")
    void findByEnterpriseIdAndDeletedFalse_whenNoMatch_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByEnterpriseIdAndDeletedFalse(999L, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("根据状态分页查询 - 返回指定状态的未删除报告")
    void findByStatusAndDeletedFalse_shouldReturnUndeletedReportsByStatus() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByStatusAndDeletedFalse(1, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(CarbonReport::getStatus)
                .allMatch(status -> status.equals(1));
    }

    @Test
    @DisplayName("根据状态分页查询 - 状态不存在时返回空页")
    void findByStatusAndDeletedFalse_whenNoMatch_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByStatusAndDeletedFalse(99, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("根据审核员ID分页查询 - 返回该审核员审核的报告")
    void findByReviewerIdAndDeletedFalse_shouldReturnReviewedReports() {
        // Given
        report1.setReviewerId(500L);
        report2.setReviewerId(500L);
        report3.setReviewerId(600L);
        entityManager.persist(report1);
        entityManager.persist(report2);
        entityManager.persist(report3);
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByReviewerIdAndDeletedFalse(500L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(CarbonReport::getReviewerId)
                .allMatch(id -> id.equals(500L));
    }

    @Test
    @DisplayName("根据审核员ID分页查询 - 审核员无报告时返回空页")
    void findByReviewerIdAndDeletedFalse_whenNoMatch_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByReviewerIdAndDeletedFalse(999L, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("根据企业ID和状态查询 - 返回符合条件的报告")
    void findByEnterpriseIdAndStatusAndDeletedFalse_shouldReturnMatchingReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByEnterpriseIdAndStatusAndDeletedFalse(1L, 1, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEnterpriseId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("根据企业ID和状态查询 - 无匹配时返回空页")
    void findByEnterpriseIdAndStatusAndDeletedFalse_whenNoMatch_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByEnterpriseIdAndStatusAndDeletedFalse(1L, 99, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("查询所有未删除的报告 - 返回所有未删除报告")
    void findByDeletedFalse_shouldReturnAllUndeletedReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.findByDeletedFalse(pageable);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(CarbonReport::getDeleted)
                .allMatch(deleted -> !deleted);
    }

    @Test
    @DisplayName("综合搜索 - 按企业ID搜索")
    void search_byEnterpriseId_shouldReturnMatchingReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.search(1L, null, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(CarbonReport::getEnterpriseId)
                .allMatch(id -> id.equals(1L));
    }

    @Test
    @DisplayName("综合搜索 - 按状态搜索")
    void search_byStatus_shouldReturnMatchingReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.search(null, 1, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(CarbonReport::getStatus)
                .allMatch(status -> status.equals(1));
    }

    @Test
    @DisplayName("综合搜索 - 按关键字搜索标题")
    void search_byKeywordTitle_shouldReturnMatchingReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.search(null, null, "企业1", pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(CarbonReport::getTitle)
                .allMatch(title -> title.contains("企业1"));
    }

    @Test
    @DisplayName("综合搜索 - 按关键字搜索报告编号")
    void search_byKeywordReportNo_shouldReturnMatchingReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.search(null, null, "RPT-2024-001", pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReportNo()).isEqualTo("RPT-2024-001");
    }

    @Test
    @DisplayName("综合搜索 - 组合条件搜索")
    void search_combinedConditions_shouldReturnMatchingReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 搜索企业1且状态为1的报告
        Page<CarbonReport> result = carbonReportRepository.search(1L, 1, null, pageable);

        // Then - report1和report2都是企业1，但只有report1状态为1
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEnterpriseId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("综合搜索 - 无条件搜索返回所有未删除报告")
    void search_noConditions_shouldReturnAllUndeletedReports() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.search(null, null, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(CarbonReport::getDeleted)
                .allMatch(deleted -> !deleted);
    }

    @Test
    @DisplayName("综合搜索 - 关键字不匹配时返回空页")
    void search_noMatchKeyword_shouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<CarbonReport> result = carbonReportRepository.search(null, null, "不存在的关键字", pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("分页功能验证 - 正确分页返回数据")
    void pagination_shouldReturnCorrectPage() {
        // Given
        Pageable firstPage = PageRequest.of(0, 2);
        Pageable secondPage = PageRequest.of(1, 2);

        // When
        Page<CarbonReport> firstResult = carbonReportRepository.findByDeletedFalse(firstPage);
        Page<CarbonReport> secondResult = carbonReportRepository.findByDeletedFalse(secondPage);

        // Then
        assertThat(firstResult.getContent()).hasSize(2);
        assertThat(firstResult.getTotalElements()).isEqualTo(3);
        assertThat(firstResult.getTotalPages()).isEqualTo(2);
        assertThat(firstResult.isFirst()).isTrue();
        assertThat(firstResult.hasNext()).isTrue();

        assertThat(secondResult.getContent()).hasSize(1);
        assertThat(secondResult.isLast()).isTrue();
    }

    @Test
    @DisplayName("保存报告 - 成功保存并返回ID")
    void save_shouldPersistReport() {
        // Given
        CarbonReport newReport = createCarbonReport("RPT-2024-NEW", 3L, 300L, "2024", "新报告", 2, 0);

        // When
        CarbonReport saved = carbonReportRepository.save(newReport);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getReportNo()).isEqualTo("RPT-2024-NEW");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("更新报告 - 成功更新状态")
    void update_shouldModifyReport() {
        // Given
        report1.setStatus(3);
        report1.setReviewerId(500L);
        report1.setReviewComment("审核通过");
        report1.setReviewedAt(LocalDateTime.now());

        // When
        CarbonReport updated = carbonReportRepository.save(report1);

        // Then
        assertThat(updated.getStatus()).isEqualTo(3);
        assertThat(updated.getReviewerId()).isEqualTo(500L);
        assertThat(updated.getReviewComment()).isEqualTo("审核通过");
        assertThat(updated.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("删除报告 - 逻辑删除后不应被查询到")
    void delete_logicalDelete_shouldNotBeFound() {
        // Given
        Long reportId = report1.getId();

        // When
        report1.setDeleted(true);
        carbonReportRepository.save(report1);

        // Then
        Pageable pageable = PageRequest.of(0, 10);
        Page<CarbonReport> result = carbonReportRepository.findByDeletedFalse(pageable);
        assertThat(result.getContent())
                .extracting(CarbonReport::getId)
                .doesNotContain(reportId);
    }
}
