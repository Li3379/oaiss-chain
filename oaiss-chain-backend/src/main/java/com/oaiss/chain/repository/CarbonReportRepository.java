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
     * 查询「本地记录已标记上链」的报告（对账入口）。
     *
     * <p>对账只关心本地认为已上链的那些记录：本地没标上链的报告不参与比对，
     * 否则每一份草稿都会被报成「链上缺失」。</p>
     */
    Page<CarbonReport> findByBlockchainTxHashIsNotNullAndDeletedFalse(Pageable pageable);

    /**
     * 按哈希前缀查找（标记历史 Mock 脏数据用）
     */
    List<CarbonReport> findByBlockchainTxHashLikeAndDeletedFalse(String prefix);

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
     * 统计全市场核证减排配额总量。
     *
     * <p><b>只能统计审核完成且已存证的报告</b>（APPROVED / ON_CHAIN）。
     * 历史实现只过滤 `deleted=false`，把草稿与已驳回也算进「核证减排配额」——
     * 库内曾有 62% 的未审核数据被当成既成事实（D11）。</p>
     */
    @Query("SELECT COALESCE(SUM(r.totalEmission), 0) FROM CarbonReport r "
            + "WHERE r.deleted = false AND r.status IN :statuses")
    java.math.BigDecimal sumTotalEmissionByStatusIn(@Param("statuses") List<Integer> statuses);

    /**
     * 取某企业**权威状态**的报告（用于 AI 推理与评级）。
     *
     * <p>历史实现直接用 `findByEnterpriseIdAndDeletedFalse`，把草稿与已驳回一并喂给
     * AI 推理，导致「未审核数据被当既成事实」（D11）。</p>
     */
    List<CarbonReport> findByEnterpriseIdAndStatusInAndDeletedFalse(
            Long enterpriseId, List<Integer> statuses);

    /**
     * 取某企业某年度**权威状态**的报告，用于年度评级汇总（D10）。
     *
     * <p>历史实现用「最后一份通过报告的排放量」当年度评级，
     * 同一年份有多份报告时前面的会被覆盖——「年度评级」名不副实。</p>
     *
     * @param periodPrefix 年度前缀，如 "2026"（accountingPeriod 形如 2026-Q1）
     */
    @Query("SELECT r FROM CarbonReport r WHERE r.enterpriseId = :enterpriseId "
            + "AND r.deleted = false AND r.status IN :statuses "
            + "AND r.accountingPeriod LIKE CONCAT(:periodPrefix, '%')")
    List<CarbonReport> findYearlyAuthoritativeReports(
            @Param("enterpriseId") Long enterpriseId,
            @Param("periodPrefix") String periodPrefix,
            @Param("statuses") List<Integer> statuses);
}
