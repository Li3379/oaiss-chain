package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CreditScore;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 信誉评分 Repository
 *
 * @author OAISS Team
 */
public interface CreditScoreRepository extends JpaRepository<CreditScore, Long> {

    /**
     * 根据企业ID查询信誉分
     */
    Optional<CreditScore> findByEnterpriseIdAndDeletedFalse(Long enterpriseId);

    /**
     * 根据企业ID集合批量查询信誉分（撮合排序批量解析，避免 N+1 查询）
     */
    List<CreditScore> findByEnterpriseIdInAndDeletedFalse(Collection<Long> enterpriseIds);

    /**
     * 查询被限制交易的企业
     */
    List<CreditScore> findByTradeRestrictedAndDeletedFalse(Boolean tradeRestricted);

    /**
     * 查询被冻结的企业
     */
    List<CreditScore> findByAccountFrozenAndDeletedFalse(Boolean accountFrozen);

    /**
     * 查询分数低于阈值的企业
     */
    List<CreditScore> findByScoreLessThanAndDeletedFalse(Integer threshold);

    /**
     * 分页查询所有未删除的信誉分
     */
    Page<CreditScore> findByDeletedFalse(Pageable pageable);

    /**
     * 交易受限名单（R-03 公示载体）：tradeRestricted 或 accountFrozen 或 delisted 任一为真。
     */
    @org.springframework.data.jpa.repository.Query("SELECT c FROM CreditScore c WHERE c.deleted = false " +
            "AND (c.tradeRestricted = true OR c.accountFrozen = true OR c.delisted = true)")
    List<CreditScore> findByAnyRestrictionAndDeletedFalse();}
