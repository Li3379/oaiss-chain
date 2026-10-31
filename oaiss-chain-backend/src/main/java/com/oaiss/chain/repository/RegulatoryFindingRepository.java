package com.oaiss.chain.repository;

import com.oaiss.chain.entity.RegulatoryFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 监管线索 Repository。
 *
 * @author OAISS Team
 */
@Repository
public interface RegulatoryFindingRepository extends JpaRepository<RegulatoryFinding, Long> {

    /**
     * 企业是否有该规则的未处置线索（幂等判据：避免重复产出同一问题）
     */
    boolean existsByEnterpriseIdAndRuleCodeAndResolvedFalse(Long enterpriseId, String ruleCode);

    /**
     * 某企业的全部未处置线索
     */
    List<RegulatoryFinding> findByEnterpriseIdAndResolvedFalseAndDeletedFalseOrderByCreatedAtDesc(
            Long enterpriseId);

    /**
     * 未处置线索分页（监管工作台主视图，按严重等级排序由调用方决定）
     */
    Page<RegulatoryFinding> findByResolvedFalseAndDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<RegulatoryFinding> findByResolvedFalseAndDeletedFalseOrderBySeverityAscCreatedAtDesc(
            Pageable pageable);

    /**
     * 某严重等级的未处置数量（工作台概览）
     */
    long countByResolvedFalseAndDeletedFalse();

    long countBySeverityAndResolvedFalseAndDeletedFalse(String severity);

    /**
     * 某企业的线索总数（含已处置，用于企业画像）
     */
    long countByEnterpriseIdAndDeletedFalse(Long enterpriseId);
}