package com.oaiss.chain.repository;

import com.oaiss.chain.entity.TradeAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 成交审核记录 Repository。
 *
 * @author OAISS Team
 */
@Repository
public interface TradeAuditRepository extends JpaRepository<TradeAudit, Long> {

    /**
     * 按审核态分页（审核员工单列表）
     */
    Page<TradeAudit> findByAuditStatusAndDeletedFalseOrderByCreatedAtAsc(
            Integer auditStatus, Pageable pageable);

    /**
     * 某交易的审核记录
     */
    List<TradeAudit> findByTradeIdAndDeletedFalseOrderByCreatedAtDesc(Long tradeId);

    /**
     * 某交易当前审核记录（一笔交易同时只应有一条有效记录）
     */
    Optional<TradeAudit> findFirstByTradeIdAndDeletedFalseOrderByCreatedAtDesc(Long tradeId);

    /**
     * 统计某审核态的数量（工单概览）
     */
    long countByAuditStatusAndDeletedFalse(Integer auditStatus);

    /**
     * 连续驳回计数：某企业作为卖方或买方在窗口期内被驳回的次数，
     * 供监管线索聚合使用（连续驳回 N 次进入监管视线）
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(a) FROM TradeAudit a WHERE a.deleted = false "
                    + "AND a.auditStatus = :auditStatus AND a.createdAt >= :since")
    long countRejectedSince(@org.springframework.data.repository.query.Param("auditStatus") Integer auditStatus,
                            @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}