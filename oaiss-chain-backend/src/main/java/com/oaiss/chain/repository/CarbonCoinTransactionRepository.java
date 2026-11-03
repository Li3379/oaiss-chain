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

    /**
     * 统计某报告已发放的次数——用于生成发放序号与幂等预查。
     *
     * <p>只数 ISSUE 类行（{@code related_seq} 非空）：回收行 seq 为 null，
     * 一起数会让「驳回后重发」的序号跳号。
     */
    long countByRelatedReportIdAndRelatedSeqIsNotNull(Long relatedReportId);

    /**
     * 按报告查全部流水（含发放与回收），供对账与「这份报告发了多少币」查询。
     */
    java.util.List<CarbonCoinTransaction> findByRelatedReportIdAndDeletedFalseOrderByIdAsc(Long relatedReportId);
}
