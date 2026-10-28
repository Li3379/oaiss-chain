package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CarbonCoinTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 碳币交易流水 Repository
 *
 * @author OAISS Team
 */
@Repository
public interface CarbonCoinTransactionRepository extends JpaRepository<CarbonCoinTransaction, Long> {

    Page<CarbonCoinTransaction> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<CarbonCoinTransaction> findByUserIdAndTxTypeAndDeletedFalseOrderByCreatedAtDesc(Long userId, Integer txType, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CarbonCoinTransaction t WHERE t.userId = :userId AND t.txType = :txType AND t.deleted = false")
    java.math.BigDecimal sumAmountByUserIdAndTxType(@Param("userId") Long userId, @Param("txType") Integer txType);
}
