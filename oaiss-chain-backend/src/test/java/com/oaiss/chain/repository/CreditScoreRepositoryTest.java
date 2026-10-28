package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CreditScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 信誉评分 Repository 集成测试
 * 
 * @author OAISS Team
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreditScoreRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CreditScoreRepository creditScoreRepository;

    private CreditScore creditScore1;
    private CreditScore creditScore2;
    private CreditScore creditScore3;
    private CreditScore deletedCreditScore;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        
        // 创建测试数据
        creditScore1 = CreditScore.builder()
                .enterpriseId(1L)
                .score(95)
                .level("EXCELLENT")
                .tradeRestricted(false)
                .accountFrozen(false)
                .lastEvaluatedAt(now)
                .build();
        creditScore1.setDeleted(false);
        creditScore1.setCreatedAt(now);
        creditScore1.setUpdatedAt(now);

        creditScore2 = CreditScore.builder()
                .enterpriseId(2L)
                .score(35)
                .level("DANGER")
                .tradeRestricted(true)
                .accountFrozen(false)
                .lastEvaluatedAt(now)
                .build();
        creditScore2.setDeleted(false);
        creditScore2.setCreatedAt(now);
        creditScore2.setUpdatedAt(now);

        creditScore3 = CreditScore.builder()
                .enterpriseId(3L)
                .score(15)
                .level("FROZEN")
                .tradeRestricted(true)
                .accountFrozen(true)
                .lastEvaluatedAt(now)
                .build();
        creditScore3.setDeleted(false);
        creditScore3.setCreatedAt(now);
        creditScore3.setUpdatedAt(now);

        deletedCreditScore = CreditScore.builder()
                .enterpriseId(4L)
                .score(100)
                .level("EXCELLENT")
                .tradeRestricted(false)
                .accountFrozen(false)
                .lastEvaluatedAt(now)
                .build();
        deletedCreditScore.setDeleted(true);
        deletedCreditScore.setCreatedAt(now);
        deletedCreditScore.setUpdatedAt(now);

        // 持久化测试数据
        entityManager.persistAndFlush(creditScore1);
        entityManager.persistAndFlush(creditScore2);
        entityManager.persistAndFlush(creditScore3);
        entityManager.persistAndFlush(deletedCreditScore);
    }

    @Test
    @DisplayName("根据企业ID查询信誉分 - 查询存在的未删除记录")
    void findByEnterpriseIdAndDeletedFalse_ExistingRecord_ReturnsOptional() {
        // When
        Optional<CreditScore> result = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEnterpriseId()).isEqualTo(1L);
        assertThat(result.get().getScore()).isEqualTo(95);
        assertThat(result.get().getLevel()).isEqualTo("EXCELLENT");
        assertThat(result.get().getDeleted()).isFalse();
    }

    @Test
    @DisplayName("根据企业ID查询信誉分 - 查询已删除记录返回空")
    void findByEnterpriseIdAndDeletedFalse_DeletedRecord_ReturnsEmpty() {
        // When
        Optional<CreditScore> result = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(4L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("根据企业ID查询信誉分 - 查询不存在的记录返回空")
    void findByEnterpriseIdAndDeletedFalse_NonExistentRecord_ReturnsEmpty() {
        // When
        Optional<CreditScore> result = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(999L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("查询被限制交易的企业 - 返回所有被限制交易且未删除的记录")
    void findByTradeRestrictedAndDeletedFalse_RestrictedTrue_ReturnsRestrictedEnterprises() {
        // When
        List<CreditScore> result = creditScoreRepository.findByTradeRestrictedAndDeletedFalse(true);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting("enterpriseId")
                .containsExactlyInAnyOrder(2L, 3L);
        assertThat(result).allMatch(cs -> cs.getTradeRestricted());
        assertThat(result).allMatch(cs -> !cs.getDeleted());
    }

    @Test
    @DisplayName("查询未被限制交易的企业 - 返回所有未被限制交易且未删除的记录")
    void findByTradeRestrictedAndDeletedFalse_RestrictedFalse_ReturnsUnrestrictedEnterprises() {
        // When
        List<CreditScore> result = creditScoreRepository.findByTradeRestrictedAndDeletedFalse(false);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEnterpriseId()).isEqualTo(1L);
        assertThat(result.get(0).getTradeRestricted()).isFalse();
    }

    @Test
    @DisplayName("查询被限制交易的企业 - 不包含已删除的记录")
    void findByTradeRestrictedAndDeletedFalse_ExcludesDeletedRecords() {
        // Given - 已删除的记录也是 tradeRestricted = false
        // When
        List<CreditScore> result = creditScoreRepository.findByTradeRestrictedAndDeletedFalse(false);

        // Then - 不应包含 enterpriseId=4 的已删除记录
        assertThat(result).extracting("enterpriseId")
                .doesNotContain(4L);
    }

    @Test
    @DisplayName("查询被冻结的企业 - 返回所有被冻结且未删除的记录")
    void findByAccountFrozenAndDeletedFalse_FrozenTrue_ReturnsFrozenEnterprises() {
        // When
        List<CreditScore> result = creditScoreRepository.findByAccountFrozenAndDeletedFalse(true);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEnterpriseId()).isEqualTo(3L);
        assertThat(result.get(0).getAccountFrozen()).isTrue();
        assertThat(result.get(0).getDeleted()).isFalse();
    }

    @Test
    @DisplayName("查询未被冻结的企业 - 返回所有未被冻结且未删除的记录")
    void findByAccountFrozenAndDeletedFalse_FrozenFalse_ReturnsUnfrozenEnterprises() {
        // When
        List<CreditScore> result = creditScoreRepository.findByAccountFrozenAndDeletedFalse(false);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting("enterpriseId")
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(result).allMatch(cs -> !cs.getAccountFrozen());
    }

    @Test
    @DisplayName("查询被冻结的企业 - 不包含已删除的记录")
    void findByAccountFrozenAndDeletedFalse_ExcludesDeletedRecords() {
        // When
        List<CreditScore> result = creditScoreRepository.findByAccountFrozenAndDeletedFalse(false);

        // Then - 不应包含 enterpriseId=4 的已删除记录
        assertThat(result).extracting("enterpriseId")
                .doesNotContain(4L);
    }

    @Test
    @DisplayName("查询分数低于阈值的企业 - 返回所有低于阈值且未删除的记录")
    void findByScoreLessThanAndDeletedFalse_ReturnsLowScoreEnterprises() {
        // Given
        Integer threshold = 50;

        // When
        List<CreditScore> result = creditScoreRepository.findByScoreLessThanAndDeletedFalse(threshold);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting("enterpriseId")
                .containsExactlyInAnyOrder(2L, 3L);
        assertThat(result).allMatch(cs -> cs.getScore() < threshold);
        assertThat(result).allMatch(cs -> !cs.getDeleted());
    }

    @Test
    @DisplayName("查询分数低于阈值的企业 - 使用高阈值返回多条记录")
    void findByScoreLessThanAndDeletedFalse_HighThreshold_ReturnsMultipleRecords() {
        // Given
        Integer threshold = 50;

        // When
        List<CreditScore> result = creditScoreRepository.findByScoreLessThanAndDeletedFalse(threshold);

        // Then - score < 50 的记录有 creditScore2(35) 和 creditScore3(15)
        assertThat(result).hasSize(2);
        assertThat(result).extracting("enterpriseId")
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("查询分数低于阈值的企业 - 使用极低阈值返回空列表")
    void findByScoreLessThanAndDeletedFalse_VeryLowThreshold_ReturnsEmptyList() {
        // Given
        Integer threshold = 10;

        // When
        List<CreditScore> result = creditScoreRepository.findByScoreLessThanAndDeletedFalse(threshold);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("查询分数低于阈值的企业 - 不包含已删除的记录")
    void findByScoreLessThanAndDeletedFalse_ExcludesDeletedRecords() {
        // Given
        Integer threshold = 101;

        // When
        List<CreditScore> result = creditScoreRepository.findByScoreLessThanAndDeletedFalse(threshold);

        // Then - 不应包含 enterpriseId=4 的已删除记录（score=100）
        assertThat(result).extracting("enterpriseId")
                .doesNotContain(4L);
    }

    @Test
    @DisplayName("JPA基础操作 - 保存新信誉评分记录")
    void save_NewCreditScore_PersistsSuccessfully() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        CreditScore newCreditScore = CreditScore.builder()
                .enterpriseId(100L)
                .score(80)
                .level("GOOD")
                .tradeRestricted(false)
                .accountFrozen(false)
                .lastEvaluatedAt(now)
                .build();
        newCreditScore.setDeleted(false);
        newCreditScore.setCreatedAt(now);
        newCreditScore.setUpdatedAt(now);

        // When
        CreditScore saved = creditScoreRepository.save(newCreditScore);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEnterpriseId()).isEqualTo(100L);
        assertThat(saved.getScore()).isEqualTo(80);
    }

    @Test
    @DisplayName("JPA基础操作 - 更新信誉评分记录")
    void save_UpdateCreditScore_UpdatesSuccessfully() {
        // Given
        CreditScore existing = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L).orElseThrow();
        existing.setScore(70);
        existing.setLevel("GOOD");

        // When
        CreditScore updated = creditScoreRepository.save(existing);

        // Then
        assertThat(updated.getScore()).isEqualTo(70);
        assertThat(updated.getLevel()).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("JPA基础操作 - 查询所有未删除的记录")
    void findAll_ReturnsAllRecords() {
        // When
        List<CreditScore> allRecords = creditScoreRepository.findAll();

        // Then - 包含4条记录（包括已删除的）
        assertThat(allRecords).hasSize(4);
    }

    @Test
    @DisplayName("JPA基础操作 - 根据ID查询记录")
    void findById_ReturnsRecord() {
        // Given
        Long id = creditScore1.getId();

        // When
        Optional<CreditScore> result = creditScoreRepository.findById(id);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEnterpriseId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("JPA基础操作 - 删除记录")
    void delete_RemovesRecord() {
        // Given
        Long id = creditScore1.getId();

        // When
        creditScoreRepository.deleteById(id);
        Optional<CreditScore> result = creditScoreRepository.findById(id);

        // Then
        assertThat(result).isEmpty();
    }
}
