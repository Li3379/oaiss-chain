package com.oaiss.chain.repository;

import com.oaiss.chain.entity.QuotaTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 碳配额变动流水 Repository。
 *
 * @author OAISS Team
 */
@Repository
public interface QuotaTransactionRepository extends JpaRepository<QuotaTransaction, Long> {

    /**
     * 企业流水（按发生时间倒序）
     */
    Page<QuotaTransaction> findByEnterpriseIdAndDeletedFalseOrderByCreatedAtDesc(
            Long enterpriseId, Pageable pageable);

    /**
     * 某企业的最后一条流水——用于校验账本连续性（其 tradableAfter 应等于当前余额）
     */
    @Query("SELECT q FROM QuotaTransaction q WHERE q.enterpriseId = :enterpriseId "
            + "AND q.deleted = false ORDER BY q.id DESC")
    List<QuotaTransaction> findLatestByEnterpriseId(@Param("enterpriseId") Long enterpriseId,
                                                    Pageable pageable);

    /**
     * 按交易归因：这笔交易动了多少额度
     */
    List<QuotaTransaction> findByRelatedTradeIdAndDeletedFalse(Long relatedTradeId);

    /**
     * 周期累计口径：某企业在时间区间内的成交扣减总量（用于自然月额度上限）
     */
    @Query("SELECT COALESCE(SUM(q.amount), 0) FROM QuotaTransaction q "
            + "WHERE q.enterpriseId = :enterpriseId AND q.changeType = 'TRANSFER' "
            + "AND q.deleted = false AND q.createdAt BETWEEN :start AND :end")
    BigDecimal sumTransferredBetween(@Param("enterpriseId") Long enterpriseId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /**
     * 在途占用口径：待审核未结算的冻结总量（两段式审核期间不得重复挂单）
     */
    @Query("SELECT COALESCE(SUM(q.amount), 0) FROM QuotaTransaction q "
            + "WHERE q.enterpriseId = :enterpriseId AND q.changeType = 'FREEZE' "
            + "AND q.deleted = false")
    BigDecimal sumFrozen(@Param("enterpriseId") Long enterpriseId);
}