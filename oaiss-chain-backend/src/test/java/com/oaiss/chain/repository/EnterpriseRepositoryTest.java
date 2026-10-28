package com.oaiss.chain.repository;

import com.oaiss.chain.entity.Enterprise;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 企业数据访问层集成测试
 *
 * @author OAISS Team
 */
@DataJpaTest
@ActiveProfiles("test")
class EnterpriseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EnterpriseRepository enterpriseRepository;

    private Enterprise enterprise1;
    private Enterprise enterprise2;
    private Enterprise deletedEnterprise;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        
        entityManager.getEntityManager().createQuery("DELETE FROM Enterprise").executeUpdate();
        entityManager.flush();

        // 创建测试企业1 - 未认证状态
        enterprise1 = Enterprise.builder()
                .userId(1001L)
                .enterpriseName("绿色科技有限公司")
                .creditCode("91110108MA01ABCD12")
                .address("北京市海淀区中关村大街1号")
                .contactPerson("张三")
                .contactPhone("13800138001")
                .industry("制造业")
                .scale("大型")
                .carbonQuota(new BigDecimal("10000.0000"))
                .carbonUsed(new BigDecimal("2000.0000"))
                .carbonTradable(new BigDecimal("3000.0000"))
                .licenseUrl("https://example.com/license1.pdf")
                .certStatus(0)
                .build();
        enterprise1.setDeleted(false);
        enterprise1.setCreatedAt(now);
        enterprise1.setUpdatedAt(now);

        // 创建测试企业2 - 已认证状态
        enterprise2 = Enterprise.builder()
                .userId(1002L)
                .enterpriseName("环保科技股份有限公司")
                .creditCode("91110108MA01EFGH34")
                .address("上海市浦东新区张江高科技园区")
                .contactPerson("李四")
                .contactPhone("13800138002")
                .industry("环保")
                .scale("中型")
                .carbonQuota(new BigDecimal("5000.0000"))
                .carbonUsed(new BigDecimal("1000.0000"))
                .carbonTradable(new BigDecimal("1500.0000"))
                .licenseUrl("https://example.com/license2.pdf")
                .certStatus(2)
                .build();
        enterprise2.setDeleted(false);
        enterprise2.setCreatedAt(now);
        enterprise2.setUpdatedAt(now);

        // 创建已删除的企业 - 认证中状态
        deletedEnterprise = Enterprise.builder()
                .userId(1003L)
                .enterpriseName("已删除企业")
                .creditCode("91110108MA01IJKL56")
                .address("广州市天河区天河路123号")
                .contactPerson("王五")
                .contactPhone("13800138003")
                .industry("服务业")
                .scale("小型")
                .carbonQuota(new BigDecimal("2000.0000"))
                .carbonUsed(new BigDecimal("500.0000"))
                .carbonTradable(new BigDecimal("800.0000"))
                .certStatus(1)
                .build();
        deletedEnterprise.setDeleted(true);
        deletedEnterprise.setCreatedAt(now);
        deletedEnterprise.setUpdatedAt(now);

        // 持久化测试数据
        entityManager.persistAndFlush(enterprise1);
        entityManager.persistAndFlush(enterprise2);
        entityManager.persistAndFlush(deletedEnterprise);
    }

    // ==================== findByUserId 测试 ====================

    @Test
    @DisplayName("根据用户ID查找企业 - 存在时返回企业")
    void findByUserId_WhenExists_ShouldReturnEnterprise() {
        // When
        Optional<Enterprise> result = enterpriseRepository.findByUserId(1001L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(1001L);
        assertThat(result.get().getEnterpriseName()).isEqualTo("绿色科技有限公司");
        assertThat(result.get().getCreditCode()).isEqualTo("91110108MA01ABCD12");
    }

    @Test
    @DisplayName("根据用户ID查找企业 - 不存在时返回空")
    void findByUserId_WhenNotExists_ShouldReturnEmpty() {
        // When
        Optional<Enterprise> result = enterpriseRepository.findByUserId(9999L);

        // Then
        assertThat(result).isEmpty();
    }

    // ==================== findByCreditCode 测试 ====================

    @Test
    @DisplayName("根据信用代码查找企业 - 存在时返回企业")
    void findByCreditCode_WhenExists_ShouldReturnEnterprise() {
        // When
        Optional<Enterprise> result = enterpriseRepository.findByCreditCode("91110108MA01EFGH34");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getCreditCode()).isEqualTo("91110108MA01EFGH34");
        assertThat(result.get().getEnterpriseName()).isEqualTo("环保科技股份有限公司");
        assertThat(result.get().getCertStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("根据信用代码查找企业 - 不存在时返回空")
    void findByCreditCode_WhenNotExists_ShouldReturnEmpty() {
        // When
        Optional<Enterprise> result = enterpriseRepository.findByCreditCode("91110108MA01NOTFOUND");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("根据信用代码查找企业 - 精确匹配验证")
    void findByCreditCode_ShouldMatchExactly() {
        // When - 使用部分信用代码查询
        Optional<Enterprise> result = enterpriseRepository.findByCreditCode("91110108MA01ABCD");

        // Then - 不应该匹配（需要完整18位）
        assertThat(result).isEmpty();
    }

    // ==================== existsByCreditCode 测试 ====================

    @Test
    @DisplayName("检查信用代码是否存在 - 存在时返回true")
    void existsByCreditCode_WhenExists_ShouldReturnTrue() {
        // When
        boolean exists = enterpriseRepository.existsByCreditCode("91110108MA01ABCD12");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("检查信用代码是否存在 - 不存在时返回false")
    void existsByCreditCode_WhenNotExists_ShouldReturnFalse() {
        // When
        boolean exists = enterpriseRepository.existsByCreditCode("91110108MA01NEWCODE");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("检查信用代码是否存在 - 已删除企业的信用代码仍然存在")
    void existsByCreditCode_DeletedEnterprise_ShouldStillExist() {
        // When
        boolean exists = enterpriseRepository.existsByCreditCode("91110108MA01IJKL56");

        // Then - 即使企业已删除，信用代码仍然存在数据库中
        assertThat(exists).isTrue();
    }

    // ==================== findByCertStatusAndDeletedFalse 测试 ====================

    @Test
    @DisplayName("根据认证状态查询未删除企业 - 查询未认证企业")
    void findByCertStatusAndDeletedFalse_Unauthenticated_ShouldReturnUndeleted() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Enterprise> result = enterpriseRepository.findByCertStatusAndDeletedFalse(0, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCertStatus()).isEqualTo(0);
        assertThat(result.getContent().get(0).getEnterpriseName()).isEqualTo("绿色科技有限公司");
        assertThat(result.getContent().get(0).getDeleted()).isFalse();
    }

    @Test
    @DisplayName("根据认证状态查询未删除企业 - 查询已认证企业")
    void findByCertStatusAndDeletedFalse_Certified_ShouldReturnUndeleted() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Enterprise> result = enterpriseRepository.findByCertStatusAndDeletedFalse(2, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCertStatus()).isEqualTo(2);
        assertThat(result.getContent().get(0).getEnterpriseName()).isEqualTo("环保科技股份有限公司");
    }

    @Test
    @DisplayName("根据认证状态查询未删除企业 - 排除已删除企业")
    void findByCertStatusAndDeletedFalse_ShouldExcludeDeleted() {
        // Given - deletedEnterprise的certStatus为1（认证中）
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Enterprise> result = enterpriseRepository.findByCertStatusAndDeletedFalse(1, pageable);

        // Then - 应该返回空，因为认证中状态的企业已被删除
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("根据认证状态查询未删除企业 - 不存在的认证状态")
    void findByCertStatusAndDeletedFalse_NonExistentStatus_ShouldReturnEmpty() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 认证状态3（认证失败）
        Page<Enterprise> result = enterpriseRepository.findByCertStatusAndDeletedFalse(3, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    // ==================== findByDeletedFalse 测试 ====================

    @Test
    @DisplayName("查询所有未删除企业 - 返回所有未删除的记录")
    void findByDeletedFalse_ShouldReturnAllUndeleted() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Enterprise> result = enterpriseRepository.findByDeletedFalse(pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Enterprise::getDeleted)
                .allMatch(deleted -> !deleted);
    }

    @Test
    @DisplayName("查询所有未删除企业 - 排除已删除企业")
    void findByDeletedFalse_ShouldExcludeDeleted() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Enterprise> result = enterpriseRepository.findByDeletedFalse(pageable);

        // Then
        List<String> names = result.getContent().stream()
                .map(Enterprise::getEnterpriseName)
                .toList();
        assertThat(names).containsExactlyInAnyOrder("绿色科技有限公司", "环保科技股份有限公司");
        assertThat(names).doesNotContain("已删除企业");
    }

    @Test
    @DisplayName("查询所有未删除企业 - 分页测试")
    void findByDeletedFalse_Pagination_ShouldWorkCorrectly() {
        // Given - 创建更多未删除的企业
        LocalDateTime now = LocalDateTime.now();
        Enterprise enterprise3 = Enterprise.builder()
                .userId(1004L)
                .enterpriseName("第三家企业")
                .creditCode("91110108MA01MNOP78")
                .certStatus(0)
                .build();
        enterprise3.setDeleted(false);
        enterprise3.setCreatedAt(now);
        enterprise3.setUpdatedAt(now);
        entityManager.persistAndFlush(enterprise3);

        // When - 第一页，每页2条
        Pageable firstPage = PageRequest.of(0, 2);
        Page<Enterprise> firstResult = enterpriseRepository.findByDeletedFalse(firstPage);

        // Then
        assertThat(firstResult.getContent()).hasSize(2);
        assertThat(firstResult.getTotalElements()).isEqualTo(3);
        assertThat(firstResult.getTotalPages()).isEqualTo(2);
        assertThat(firstResult.hasNext()).isTrue();

        // When - 第二页
        Pageable secondPage = PageRequest.of(1, 2);
        Page<Enterprise> secondResult = enterpriseRepository.findByDeletedFalse(secondPage);

        // Then
        assertThat(secondResult.getContent()).hasSize(1);
        assertThat(secondResult.hasNext()).isFalse();
    }

    @Test
    @DisplayName("查询所有未删除企业 - 空结果场景")
    void findByDeletedFalse_WhenAllDeleted_ShouldReturnEmpty() {
        // Given - 删除所有企业
        entityManager.clear();
        enterpriseRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        Enterprise newDeleted = Enterprise.builder()
                .userId(2001L)
                .enterpriseName("新的已删除企业")
                .creditCode("91110108MA01QRST90")
                .certStatus(0)
                .build();
        newDeleted.setDeleted(true);
        newDeleted.setCreatedAt(now);
        newDeleted.setUpdatedAt(now);
        entityManager.persistAndFlush(newDeleted);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Enterprise> result = enterpriseRepository.findByDeletedFalse(pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ==================== 数据完整性测试 ====================

    @Test
    @DisplayName("数据完整性 - 碳配额字段精度验证")
    void dataIntegrity_CarbonQuotaPrecision_ShouldBePreserved() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Enterprise enterprise = Enterprise.builder()
                .userId(3001L)
                .enterpriseName("配额精度测试企业")
                .creditCode("91110108MA01UVWX12")
                .carbonQuota(new BigDecimal("12345.6789"))
                .carbonUsed(new BigDecimal("2345.6789"))
                .carbonTradable(new BigDecimal("3456.7890"))
                .certStatus(0)
                .build();
        enterprise.setDeleted(false);
        enterprise.setCreatedAt(now);
        enterprise.setUpdatedAt(now);

        // When
        Enterprise saved = entityManager.persistAndFlush(enterprise);
        Enterprise found = enterpriseRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertThat(found.getCarbonQuota()).isEqualByComparingTo(new BigDecimal("12345.6789"));
        assertThat(found.getCarbonUsed()).isEqualByComparingTo(new BigDecimal("2345.6789"));
        assertThat(found.getCarbonTradable()).isEqualByComparingTo(new BigDecimal("3456.7890"));
    }

    @Test
    @DisplayName("数据完整性 - 默认值验证")
    void dataIntegrity_DefaultValues_ShouldBeSet() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Enterprise enterprise = Enterprise.builder()
                .userId(4001L)
                .enterpriseName("默认值测试企业")
                .creditCode("91110108MA01YZAB34")
                .build();
        enterprise.setDeleted(false);
        enterprise.setCreatedAt(now);
        enterprise.setUpdatedAt(now);

        // When
        Enterprise saved = entityManager.persistAndFlush(enterprise);

        // Then
        assertThat(saved.getCertStatus()).isEqualTo(0);
        assertThat(saved.getCarbonQuota()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCarbonUsed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCarbonTradable()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("唯一性约束 - userId必须唯一")
    void uniqueness_UserId_ShouldBeUnique() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Enterprise first = Enterprise.builder()
                .userId(5001L)
                .enterpriseName("第一个企业")
                .creditCode("91110108MA01CDEF56")
                .build();
        first.setDeleted(false);
        first.setCreatedAt(now);
        first.setUpdatedAt(now);
        entityManager.persistAndFlush(first);

        Enterprise duplicate = Enterprise.builder()
                .userId(5001L) // 相同的userId
                .enterpriseName("重复用户ID企业")
                .creditCode("91110108MA01GHIJ78")
                .build();
        duplicate.setDeleted(false);
        duplicate.setCreatedAt(now);
        duplicate.setUpdatedAt(now);

        // When & Then - Hibernate throws ConstraintViolationException for unique constraint violations
        org.junit.jupiter.api.Assertions.assertThrows(
                org.hibernate.exception.ConstraintViolationException.class,
                () -> {
                    entityManager.persistAndFlush(duplicate);
                }
        );
    }

    @Test
    @DisplayName("唯一性约束 - creditCode必须唯一")
    void uniqueness_CreditCode_ShouldBeUnique() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Enterprise first = Enterprise.builder()
                .userId(6001L)
                .enterpriseName("第一个企业")
                .creditCode("91110108MA01KLMN90")
                .build();
        first.setDeleted(false);
        first.setCreatedAt(now);
        first.setUpdatedAt(now);
        entityManager.persistAndFlush(first);

        Enterprise duplicate = Enterprise.builder()
                .userId(6002L)
                .enterpriseName("重复信用代码企业")
                .creditCode("91110108MA01KLMN90") // 相同的creditCode
                .build();
        duplicate.setDeleted(false);
        duplicate.setCreatedAt(now);
        duplicate.setUpdatedAt(now);

        // When & Then - Hibernate throws ConstraintViolationException for unique constraint violations
        org.junit.jupiter.api.Assertions.assertThrows(
                org.hibernate.exception.ConstraintViolationException.class,
                () -> {
                    entityManager.persistAndFlush(duplicate);
                }
        );
    }
}
