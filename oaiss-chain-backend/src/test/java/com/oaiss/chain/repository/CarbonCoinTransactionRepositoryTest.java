package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CarbonCoinTransaction;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CarbonCoinTransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CarbonCoinTransactionRepository repository;

    private CarbonCoinTransaction tx1;
    private CarbonCoinTransaction tx2;
    private CarbonCoinTransaction tx3;

    @BeforeEach
    void setUp() {
        tx1 = createTx("TX-001", 1L, 1, new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("100.00"));
        tx2 = createTx("TX-002", 1L, 2, new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("50.00"));
        tx3 = createTx("TX-003", 2L, 1, new BigDecimal("200.00"), new BigDecimal("0.00"), new BigDecimal("200.00"));

        entityManager.persist(tx1);
        entityManager.persist(tx2);
        entityManager.persist(tx3);
        entityManager.flush();
    }

    private CarbonCoinTransaction createTx(String txNo, Long userId, Integer txType,
                                            BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter) {
        CarbonCoinTransaction tx = CarbonCoinTransaction.builder()
                .txNo(txNo)
                .userId(userId)
                .txType(txType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .build();
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());
        return tx;
    }

    @Test
    @DisplayName("findByUserIdAndDeletedFalseOrderByCreatedAtDesc should return user transactions")
    void findByUserId_shouldReturnUserTransactions() {
        Page<CarbonCoinTransaction> result = repository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(
                1L, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(CarbonCoinTransaction::getUserId)
                .allMatch(id -> id.equals(1L));
    }

    @Test
    @DisplayName("findByUserIdAndTxTypeAndDeletedFalseOrderByCreatedAtDesc should filter by type")
    void findByUserIdAndTxType_shouldFilterByType() {
        Page<CarbonCoinTransaction> result = repository.findByUserIdAndTxTypeAndDeletedFalseOrderByCreatedAtDesc(
                1L, 1, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTxType()).isEqualTo(1);
    }

    @Test
    @DisplayName("sumAmountByUserIdAndTxType should return correct sum")
    void sumAmountByUserIdAndTxType_shouldReturnCorrectSum() {
        BigDecimal sum = repository.sumAmountByUserIdAndTxType(1L, 1);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("sumAmountByUserIdAndTxType should return zero for no matching records")
    void sumAmountByUserIdAndTxType_shouldReturnZeroForNoMatch() {
        BigDecimal sum = repository.sumAmountByUserIdAndTxType(999L, 1);
        assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
