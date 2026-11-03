package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.IndustryBenchmark;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.IndustryBenchmarkRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 行业基准值维护服务。
 *
 * <p>基准值是配额发放的依据。缺了它，审核会失败（配额算不出来）——所以必须有一个
 * 让管理员补录的入口，否则审核员会被卡住且无法自己解决。
 *
 * <p><b>不做跨年度降级</b>：查不到就返回空。用上一年度的基准值算当年的配额，
 * 会让配额悄悄算错而没人发现，比明确报错更危险。
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryBenchmarkService {

    /** 本轮仅支持发电行业 */
    private static final Set<String> SUPPORTED_INDUSTRIES = Set.of(IndustryBenchmark.INDUSTRY_POWER);

    /** 本轮仅支持发电基准值 */
    private static final Set<String> SUPPORTED_KINDS = Set.of(IndustryBenchmark.KIND_GENERATION);

    /** 发电行业四类机组 */
    private static final Set<String> VALID_UNIT_TYPES = Set.of("I", "II", "III", "IV");

    private final IndustryBenchmarkRepository benchmarkRepository;

    /**
     * 查询单条基准值；无记录返回空（不降级、不抛错，由调用方决定如何处理）。
     */
    @Transactional(readOnly = true)
    public java.util.Optional<IndustryBenchmark> resolve(String industryCode, String unitType,
                                                         Integer year, String kind) {
        return benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        industryCode, unitType, year, kind);
    }

    /**
     * 列表查询（可按年度过滤）
     */
    @Transactional(readOnly = true)
    public List<IndustryBenchmark> list(Integer year) {
        if (year == null) {
            return benchmarkRepository
                    .findByDeletedFalseOrderByBenchmarkYearDescIndustryCodeAscUnitTypeAsc();
        }
        return benchmarkRepository.findByIndustryCodeAndBenchmarkYearAndDeletedFalse(
                IndustryBenchmark.INDUSTRY_POWER, year);
    }

    /**
     * 就绪检查：某年度四类机组的基准值是否齐备。
     *
     * <p>用途：审核员在点「通过」之前先看一眼，避免点了才报错——他自己无法修复
     * （只有管理员能录基准值），提前告知能省一次无效操作。
     */
    @Transactional(readOnly = true)
    public Readiness readiness(Integer year) {
        List<IndustryBenchmark> found = benchmarkRepository
                .findByIndustryCodeAndBenchmarkYearAndDeletedFalse(
                        IndustryBenchmark.INDUSTRY_POWER, year);
        Set<String> presentUnitTypes = found.stream()
                .filter(b -> IndustryBenchmark.KIND_GENERATION.equals(b.getBenchmarkKind()))
                .map(IndustryBenchmark::getUnitType)
                .collect(Collectors.toSet());

        List<String> missing = VALID_UNIT_TYPES.stream()
                .filter(ut -> !presentUnitTypes.contains(ut))
                .sorted()
                .toList();

        return Readiness.builder()
                .industryCode(IndustryBenchmark.INDUSTRY_POWER)
                .year(year)
                .ready(missing.isEmpty())
                .presentUnitTypes(presentUnitTypes.stream().sorted().toList())
                .missingUnitTypes(missing)
                .build();
    }

    /**
     * 新增基准值。
     */
    @Transactional
    public IndustryBenchmark create(IndustryBenchmark input) {
        validate(input);
        // 唯一性：同行业+机组类型+年度+类别只能有一条
        boolean exists = benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        input.getIndustryCode(), input.getUnitType(),
                        input.getBenchmarkYear(), input.getBenchmarkKind())
                .isPresent();
        if (exists) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    String.format("该基准值已存在：%s / %s 类机组 / %d 年度。请使用更新接口。",
                            input.getIndustryCode(), input.getUnitType(), input.getBenchmarkYear()));
        }
        return benchmarkRepository.save(input);
    }

    /**
     * 更新基准值。
     */
    @Transactional
    public IndustryBenchmark update(Long id, BigDecimal newValue) {
        IndustryBenchmark existing = benchmarkRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "基准值记录不存在: " + id));
        if (newValue == null || newValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "基准值必须为正数");
        }
        BigDecimal old = existing.getBenchmarkValue();
        existing.setBenchmarkValue(newValue);
        IndustryBenchmark saved = benchmarkRepository.save(existing);
        if (log.isInfoEnabled()) {
            log.info("[BENCHMARK] 更新基准值 id={} {} {} {}: {} -> {}",
                    id, existing.getIndustryCode(), existing.getUnitType(),
                    existing.getBenchmarkYear(), old, newValue);
        }
        return saved;
    }

    /**
     * 软删除基准值。
     *
     * <p>用软删而非物理删：已经按该基准值发过配额的报告需要能回溯依据，
     * 物理删会让历史发放失去可解释性。
     */
    @Transactional
    public void delete(Long id) {
        IndustryBenchmark existing = benchmarkRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "基准值记录不存在: " + id));
        existing.setDeleted(true);
        benchmarkRepository.save(existing);
        if (log.isWarnEnabled()) {
            log.warn("[BENCHMARK] 软删除基准值 id={} {} {} {}", id,
                    existing.getIndustryCode(), existing.getUnitType(), existing.getBenchmarkYear());
        }
    }

    // ==================== 内部方法 ====================

    private void validate(IndustryBenchmark input) {
        if (input.getIndustryCode() == null || !SUPPORTED_INDUSTRIES.contains(input.getIndustryCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "本轮仅支持 POWER（发电）行业，实际: " + input.getIndustryCode());
        }
        if (input.getUnitType() == null || !VALID_UNIT_TYPES.contains(input.getUnitType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "机组类型必须为 I/II/III/IV 之一，实际: " + input.getUnitType());
        }
        if (input.getBenchmarkKind() == null || !SUPPORTED_KINDS.contains(input.getBenchmarkKind())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "本轮仅支持 GENERATION_BENCHMARK，实际: " + input.getBenchmarkKind());
        }
        if (input.getBenchmarkYear() == null || input.getBenchmarkYear() < 2000
                || input.getBenchmarkYear() > 2100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "基准年度无效: " + input.getBenchmarkYear());
        }
        if (input.getBenchmarkValue() == null
                || input.getBenchmarkValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "基准值必须为正数——0 或负数会让所有审核在发放环节失败");
        }
        if (input.getBenchmarkUnit() == null || input.getBenchmarkUnit().isBlank()) {
            input.setBenchmarkUnit("tCO2/MWh");
        }
        if (!"tCO2/MWh".equals(input.getBenchmarkUnit())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "发电基准值单位必须为 tCO2/MWh，实际: " + input.getBenchmarkUnit());
        }
    }

    /**
     * 就绪检查结果。
     */
    @Getter
    @Builder
    public static class Readiness {
        private final String industryCode;
        private final Integer year;
        /** 四类机组是否齐备 */
        private final boolean ready;
        private final List<String> presentUnitTypes;
        private final List<String> missingUnitTypes;
    }
}