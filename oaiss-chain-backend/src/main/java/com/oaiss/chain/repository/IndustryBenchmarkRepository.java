package com.oaiss.chain.repository;

import com.oaiss.chain.entity.IndustryBenchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 行业基准值 Repository。
 *
 * @author OAISS Team
 */
@Repository
public interface IndustryBenchmarkRepository extends JpaRepository<IndustryBenchmark, Long> {

    /**
     * 精确查询某行业/机组类别/年度/类别下的基准值。
     *
     * <p><b>刻意不做跨年度降级</b>：查不到就返回空，由调用方决定是拒绝审核
     * 还是走别的兜底。跨年度回退（如 2027 年查不到就用 2026 年的值）会让
     * 配额计算悄悄用错年度的基准，属于"看起来能用但算错了"的缺陷。
     */
    Optional<IndustryBenchmark> findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
            String industryCode, String unitType, Integer benchmarkYear, String benchmarkKind);

    /**
     * 查询某行业某年度的全部基准值（就绪检查用）
     */
    List<IndustryBenchmark> findByIndustryCodeAndBenchmarkYearAndDeletedFalse(
            String industryCode, Integer benchmarkYear);

    /**
     * 查询全部未删除记录（管理端列表用）
     */
    List<IndustryBenchmark> findByDeletedFalseOrderByBenchmarkYearDescIndustryCodeAscUnitTypeAsc();
}