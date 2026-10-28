package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CarbonReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 碳报告数据访问层
 *
 * @author OAISS Team
 */
@Repository
public interface CarbonReportRepository extends JpaRepository<CarbonReport, Long> {

    /**
     * 根据报告编号查询
     */
    Optional<CarbonReport> findByReportNo(String reportNo);

    /**
     * 按主键查询未删除报告。
     *
     * <p>BaseEntity 未声明 {@code @SQLRestriction}，继承的 {@code findById} 不会过滤软删除行，
     * 因此所有按 ID 取报告的业务入口都必须走本方法，避免已删除报告被读取、提交、审核或认证。</p>
     */
    Optional<CarbonReport> findByIdAndDeletedFalse(Long id);

    /**
     * 根据企业 ID 查询
     */
    List<CarbonReport> findByEnterpriseIdAndDeletedFalse(Long enterpriseId);

    Page<CarbonReport> findByEnterpriseIdAndDeletedFalse(Long enterpriseId, Pageable pageable);

    /**
     * 根据状态分页查询
     */
    Page<CarbonReport> findByStatusAndDeletedFalse(Integer status, Pageable pageable);

    /**
     * 根据审核员 ID 分页查询
     */
    Page<CarbonReport> findByReviewerIdAndDeletedFalse(Long reviewerId, Pageable pageable);

    /**
     * 根据审核员 ID 与多个状态分页查询
     */
    Page<CarbonReport> findByReviewerIdAndStatusInAndDeletedFalse(Long reviewerId, List<Integer> statuses, Pageable pageable);

    /**
     * 按审核员与状态集合统计其审结报告数（与审核历史同口径，供个人工作台统计使用）。
     */
    long countByReviewerIdAndStatusInAndDeletedFalse(Long reviewerId, List<Integer> statuses);

    /**
     * 根据企业 ID 和状态分页查询
     */
    Page<CarbonReport> findByEnterpriseIdAndStatusAndDeletedFalse(Long enterpriseId, Integer status, Pageable pageable);

    /**
     * 查询全部未删除报告
     */
    Page<CarbonReport> findByDeletedFalse(Pageable pageable);

    /**
     * 综合搜索
     */
    @Query("SELECT r FROM CarbonReport r WHERE r.deleted = false " +
            "AND (:enterpriseId IS NULL OR r.enterpriseId = :enterpriseId) " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:keyword IS NULL OR r.title LIKE %:keyword% OR r.reportNo LIKE %:keyword%)")
    Page<CarbonReport> search(
            @Param("enterpriseId") Long enterpriseId,
            @Param("status") Integer status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT r FROM CarbonReport r WHERE r.deleted = false " +
            "AND r.enterpriseId = :enterpriseId " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:title IS NULL OR r.title LIKE %:title%) " +
            "AND (:accountingPeriod IS NULL OR r.accountingPeriod LIKE %:accountingPeriod%)")
    Page<CarbonReport> searchMyReports(
            @Param("enterpriseId") Long enterpriseId,
            @Param("status") Integer status,
            @Param("title") String title,
            @Param("accountingPeriod") String accountingPeriod,
            Pageable pageable);

    /**
     * 统计指定状态的报告数量
     */
    long countByStatusAndDeletedFalse(Integer status);

    /**
     * 根据多个状态分页查询
     */
    Page<CarbonReport> findByStatusInAndDeletedFalse(List<Integer> statuses, Pageable pageable);

    /**
     * 统计指定状态集合的报告数量
     */
    long countByStatusInAndDeletedFalse(List<Integer> statuses);

    /**
     * 统计全市场核证报告累计减排配额总量
     */
    @Query("SELECT COALESCE(SUM(r.totalEmission), 0) FROM CarbonReport r WHERE r.deleted = false")
    java.math.BigDecimal sumTotalEmission();
}
