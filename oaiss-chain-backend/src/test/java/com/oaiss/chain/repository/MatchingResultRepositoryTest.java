package com.oaiss.chain.repository;

import com.oaiss.chain.entity.MatchingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MatchingResultRepository 数据访问层测试
 * 
 * @author OAISS Team
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("撮合结果仓储测试")
class MatchingResultRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MatchingResultRepository matchingResultRepository;

    private MatchingResult testResult;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        
        testResult = MatchingResult.builder()
                .matchNo("MR20240101001")
                .buyerId(1L)
                .sellerId(2L)
                .buyOrderId(100L)
                .sellOrderId(200L)
                .matchedQuantity(new BigDecimal("10.00"))
                .settlementPrice(new BigDecimal("100.00"))
                .totalAmount(new BigDecimal("1000.00"))
                .status(1) // 已结算
                .build();
        testResult.setDeleted(false);
        testResult.setCreatedAt(now);
        testResult.setUpdatedAt(now);
    }

    @Nested
    @DisplayName("findByUserIdRelated 测试")
    class FindByUserIdRelatedTests {

        @Test
        @DisplayName("查询用户相关撮合结果 - 作为买方")
        void findByUserIdRelated_AsBuyer_ShouldReturnResults() {
            // Given
            entityManager.persistAndFlush(testResult);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<MatchingResult> result = matchingResultRepository.findByUserIdRelated(1L, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getBuyerId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("查询用户相关撮合结果 - 作为卖方")
        void findByUserIdRelated_AsSeller_ShouldReturnResults() {
            // Given
            entityManager.persistAndFlush(testResult);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<MatchingResult> result = matchingResultRepository.findByUserIdRelated(2L, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSellerId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("查询用户相关撮合结果 - 无相关结果时返回空页")
        void findByUserIdRelated_WhenNoResults_ShouldReturnEmpty() {
            // Given
            entityManager.persistAndFlush(testResult);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<MatchingResult> result = matchingResultRepository.findByUserIdRelated(999L, pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("查询用户相关撮合结果 - 不包含已删除结果")
        void findByUserIdRelated_ShouldNotIncludeDeleted() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            MatchingResult deletedResult = MatchingResult.builder()
                    .matchNo("MR20240101002")
                    .buyerId(1L)
                    .sellerId(3L)
                    .buyOrderId(101L)
                    .sellOrderId(201L)
                    .matchedQuantity(new BigDecimal("5.00"))
                    .settlementPrice(new BigDecimal("50.00"))
                    .totalAmount(new BigDecimal("250.00"))
                    .status(1)
                    .build();
            deletedResult.setDeleted(true);
            deletedResult.setCreatedAt(now);
            deletedResult.setUpdatedAt(now);
            entityManager.persistAndFlush(testResult);
            entityManager.persistAndFlush(deletedResult);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<MatchingResult> result = matchingResultRepository.findByUserIdRelated(1L, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent()).allMatch(r -> !r.getDeleted());
        }
    }

    @Nested
    @DisplayName("findByDeletedFalse 测试")
    class FindByDeletedFalseTests {

        @Test
        @DisplayName("查询所有未删除撮合结果 - 应返回所有结果")
        void findByDeletedFalse_ShouldReturnAllNotDeleted() {
            // Given
            entityManager.persistAndFlush(testResult);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<MatchingResult> result = matchingResultRepository.findByDeletedFalse(pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent()).allMatch(r -> !r.getDeleted());
        }

        @Test
        @DisplayName("查询所有未删除撮合结果 - 支持分页")
        void findByDeletedFalse_ShouldSupportPagination() {
            // Given - 先持久化testResult
            entityManager.persistAndFlush(testResult);
            
            LocalDateTime now = LocalDateTime.now();
            
            // 添加更多结果
            for (int i = 2; i <= 5; i++) {
                MatchingResult result = MatchingResult.builder()
                        .matchNo("MR202401010" + i)
                        .buyerId((long) i)
                        .sellerId((long) i + 10)
                        .buyOrderId((long) i * 100)
                        .sellOrderId((long) i * 200)
                        .matchedQuantity(new BigDecimal("10.00"))
                        .settlementPrice(new BigDecimal("100.00"))
                        .totalAmount(new BigDecimal("1000.00"))
                        .status(1)
                        .build();
                result.setDeleted(false);
                result.setCreatedAt(now);
                result.setUpdatedAt(now);
                entityManager.persist(result);
            }
            entityManager.flush();

            Pageable firstPage = PageRequest.of(0, 2);

            // When
            Page<MatchingResult> page1 = matchingResultRepository.findByDeletedFalse(firstPage);

            // Then
            assertThat(page1.getContent()).hasSize(2);
            assertThat(page1.getTotalElements()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("JPA基础操作测试")
    class JpaBasicTests {

        @Test
        @DisplayName("保存撮合结果 - 应成功保存并生成ID")
        void save_ShouldPersistResultWithId() {
            // When
            MatchingResult saved = matchingResultRepository.save(testResult);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getBuyerId()).isEqualTo(1L);
            assertThat(saved.getSellerId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("更新撮合结果状态 - 应成功更新")
        void update_ShouldUpdateResultStatus() {
            // Given
            MatchingResult saved = entityManager.persistAndFlush(testResult);
            saved.setStatus(2); // 已取消

            // When
            MatchingResult updated = matchingResultRepository.save(saved);

            // Then
            assertThat(updated.getStatus()).isEqualTo(2);
        }

        @Test
        @DisplayName("删除撮合结果 - 应标记为已删除")
        void delete_ShouldMarkAsDeleted() {
            // Given
            MatchingResult saved = entityManager.persistAndFlush(testResult);

            // When
            saved.setDeleted(true);
            matchingResultRepository.save(saved);

            // Then
            var found = matchingResultRepository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getDeleted()).isTrue();
        }

        @Test
        @DisplayName("统计撮合结果数量 - 应返回正确数量")
        void count_ShouldReturnCorrectCount() {
            // Given
            entityManager.persistAndFlush(testResult);

            // When
            long count = matchingResultRepository.count();

            // Then
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }
}