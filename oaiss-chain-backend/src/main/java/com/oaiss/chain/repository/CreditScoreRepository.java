package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CreditScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
