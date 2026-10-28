package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CarbonCoinAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CarbonCoinAccountRepository 数据访问层测试
 * 
 * @author OAISS Team
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("碳币账户仓储测试")
class CarbonCoinAccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CarbonCoinAccountRepository carbonCoinAccountRepository;

    private CarbonCoinAccount testAccount;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        
        testAccount = CarbonCoinAccount.builder()
                .userId(1L)
                .balance(new BigDecimal("1000.00"))
                .totalRecharged(new BigDecimal("2000.00"))
                .totalSpent(new BigDecimal("1000.00"))
                .status(1)
                .build();
        testAccount.setDeleted(false);
        testAccount.setCreatedAt(now);
        testAccount.setUpdatedAt(now);
    }

    @Nested
    @DisplayName("findByUserIdAndDeletedFalse 测试")
    class FindByUserIdTests {

        @Test
        @DisplayName("根据用户ID查找 - 当账户存在时 - 应返回账户")
        void findByUserId_WhenAccountExists_ShouldReturnAccount() {
            // Given
            entityManager.persistAndFlush(testAccount);

            // When
            var result = carbonCoinAccountRepository.findByUserIdAndDeletedFalse(1L);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getBalance()).isEqualByComparingTo("1000.00");
            assertThat(result.get().getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("根据用户ID查找 - 当账户不存在时 - 应返回空")
        void findByUserId_WhenAccountNotExists_ShouldReturnEmpty() {
            // When
            var result = carbonCoinAccountRepository.findByUserIdAndDeletedFalse(999L);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUserIdAndDeletedFalse 测试")
    class ExistsByUserIdTests {

        @Test
        @DisplayName("检查用户账户是否存在 - 当存在时 - 应返回true")
        void existsByUserId_WhenExists_ShouldReturnTrue() {
            // Given
            entityManager.persistAndFlush(testAccount);

            // When
            boolean exists = carbonCoinAccountRepository.existsByUserIdAndDeletedFalse(1L);

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("检查用户账户是否存在 - 当不存在时 - 应返回false")
        void existsByUserId_WhenNotExists_ShouldReturnFalse() {
            // When
            boolean exists = carbonCoinAccountRepository.existsByUserIdAndDeletedFalse(999L);

            // Then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("JPA基础操作测试")
    class JpaBasicTests {

        @Test
        @DisplayName("保存账户 - 应成功保存并生成ID")
        void save_ShouldPersistAccountWithId() {
            // When
            CarbonCoinAccount saved = carbonCoinAccountRepository.save(testAccount);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(1L);
            assertThat(saved.getBalance()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("更新账户余额 - 应成功更新")
        void update_ShouldUpdateBalance() {
            // Given
            CarbonCoinAccount saved = entityManager.persistAndFlush(testAccount);
            saved.setBalance(new BigDecimal("1500.00"));

            // When
            CarbonCoinAccount updated = carbonCoinAccountRepository.save(saved);

            // Then
            assertThat(updated.getBalance()).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("删除账户 - 应标记为已删除")
        void delete_ShouldMarkAsDeleted() {
            // Given
            CarbonCoinAccount saved = entityManager.persistAndFlush(testAccount);

            // When
            saved.setDeleted(true);
            carbonCoinAccountRepository.save(saved);

            // Then
            var found = carbonCoinAccountRepository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getDeleted()).isTrue();
        }

        @Test
        @DisplayName("统计账户数量 - 应返回正确数量")
        void count_ShouldReturnCorrectCount() {
            // Given
            entityManager.persistAndFlush(testAccount);

            // When
            long count = carbonCoinAccountRepository.count();

            // Then
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("余额操作测试")
    class BalanceOperationTests {

        @Test
        @DisplayName("充值碳币 - 应正确更新余额和累计充值")
        void recharge_ShouldUpdateBalanceAndTotalRecharged() {
            // Given
            CarbonCoinAccount saved = entityManager.persistAndFlush(testAccount);
            BigDecimal rechargeAmount = new BigDecimal("500.00");
            BigDecimal expectedBalance = saved.getBalance().add(rechargeAmount);
            BigDecimal expectedTotalRecharged = saved.getTotalRecharged().add(rechargeAmount);

            // When
            saved.setBalance(expectedBalance);
            saved.setTotalRecharged(expectedTotalRecharged);
            CarbonCoinAccount updated = carbonCoinAccountRepository.save(saved);

            // Then
            assertThat(updated.getBalance()).isEqualByComparingTo("1500.00");
            assertThat(updated.getTotalRecharged()).isEqualByComparingTo("2500.00");
        }

        @Test
        @DisplayName("消费碳币 - 应正确更新余额和累计消费")
        void spend_ShouldUpdateBalanceAndTotalSpent() {
            // Given
            CarbonCoinAccount saved = entityManager.persistAndFlush(testAccount);
            BigDecimal spendAmount = new BigDecimal("300.00");
            BigDecimal expectedBalance = saved.getBalance().subtract(spendAmount);
            BigDecimal expectedTotalSpent = saved.getTotalSpent().add(spendAmount);

            // When
            saved.setBalance(expectedBalance);
            saved.setTotalSpent(expectedTotalSpent);
            CarbonCoinAccount updated = carbonCoinAccountRepository.save(saved);

            // Then
            assertThat(updated.getBalance()).isEqualByComparingTo("700.00");
            assertThat(updated.getTotalSpent()).isEqualByComparingTo("1300.00");
        }
    }
}