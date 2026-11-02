package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.IndustryBenchmark;
import com.oaiss.chain.enums.AllocationStateEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.IndustryBenchmarkRepository;
import com.oaiss.chain.repository.QuotaTransactionRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 碳配额发放服务。
 *
 * <p>把原始需求中「排放强度低于行业基准的企业获得可交易盈余」这条机制落地：
 * <pre>
 * 报告审核通过
 *   → 校验（基准值存在？产量 &gt; 0？机组类型已选？）
 *   → 配额 = 发电量 × 基准值
 *   → 写入企业 carbon_quota / carbon_tradable + 流水
 *   → 实排 &lt; 配额 → 盈余可卖
 * </pre>
 *
 * <p><b>三段式时序（关键）</b>：所有可能失败的校验必须发生在**上链之前**。
 * 因为上链是不可逆的外部写入，若校验在上链之后失败，事务回滚会把 DB 的 txHash
 * 清掉但链上记录仍在，之后重试会被链码 {@code report already exists} 硬拒，
 * 报告永久卡死、配额永远发不出去。Mock 模式下上链恒成功，这个缺陷在测试里看不见。
 *
 * <p><b>幂等三态</b>：不能只靠 {@code quotaAllocatedAmount} 非空判断——那样
 * 「冲正后重发」会被短路。必须用 {@link AllocationStateEnum} 区分
 * 已发放 / 已冲正 / 待重发。
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaAllocationService {

    /** 配额精度：与 carbon_quota 列精度一致 */
    private static final int QUOTA_SCALE = 4;

    private final CarbonReportRepository reportRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final IndustryBenchmarkRepository benchmarkRepository;
    private final QuotaTransactionRepository quotaTransactionRepository;
    private final QuotaLedgerService quotaLedgerService;

    /**
     * 盈余判定。
     */
    public enum Judgement {
        /** 配额 > 实排，有盈余可卖 */
        SURPLUS,
        /** 配额 < 实排，需要购买 */
        SHORTFALL,
        /** 无法判定（范围一排放缺失） */
        UNDETERMINED
    }

    /**
     * 发放结果。
     */
    @Getter
    @Builder
    public static class AllocationResult {
        /** 关联报告ID */
        private final Long reportId;
        /** 本次发放的配额量 */
        private final BigDecimal quotaAmount;
        /** 报告的范围一直接排放（可能为 null） */
        private final BigDecimal scope1Emission;
        /** 盈余（正）或缺口（负）；无法判定时为 null */
        private final BigDecimal surplusOrGap;
        /** 判定结论 */
        private final Judgement judgement;
        /** 是否因幂等而跳过实际发放 */
        private final boolean idempotentSkip;
    }

    /**
     * 为报告发放配额。
     *
     * <p>前置校验全部在此之前完成（由调用方 {@code CarbonService} 保证），
     * 本方法内仍做防御性校验——它也会被重试端点直接调用。
     *
     * @param reportId  报告ID
     * @param auditorId 触发人（审核员或管理员）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AllocationResult allocateForReport(Long reportId, Long auditorId) {
        CarbonReport report = reportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "碳报告不存在: " + reportId));

        // ---- 幂等：已发放直接返回既有结果 ----
        AllocationStateEnum state = AllocationStateEnum.fromCode(report.getAllocationState());
        if (state != null && state.isAllocated()) {
            log.info("[QUOTA] 报告 {} 已发放过配额 {}，幂等跳过", reportId, report.getQuotaAllocatedAmount());
            return buildResult(report, report.getQuotaAllocatedAmount(), true);
        }

        // ---- 防御性校验 ----
        validatePrerequisites(report);

        // ---- 算配额 ----
        String industryCode = IndustryBenchmark.INDUSTRY_POWER;
        String unitType = report.getUnitType();
        Integer year = report.getPeriodYear();

        IndustryBenchmark benchmark = benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        industryCode, unitType, year, IndustryBenchmark.KIND_GENERATION)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        String.format("缺少基准值：行业=%s，机组类型=%s，年度=%d。"
                                        + "请管理员先在系统配置中录入该年度的行业基准值。",
                                industryCode, unitType, year)));

        BigDecimal quota = report.getOutputQuantity()
                .multiply(benchmark.getBenchmarkValue())
                .setScale(QUOTA_SCALE, RoundingMode.HALF_UP);

        if (quota.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "计算出的配额必须为正数，实际: " + quota.toPlainString());
        }

        // ---- 发放序号：冲正重发时递增，避免撞唯一索引 ----
        long priorAllocations = quotaTransactionRepository
                .countByRelatedReportIdAndAllocationSeqIsNotNull(reportId);
        int seq = (int) priorAllocations + 1;

        // ---- 记录发放前的 used 快照（冲正保护的比较基线）----
        Enterprise enterprise = enterpriseRepository.findById(report.getEnterpriseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "企业不存在: " + report.getEnterpriseId()));
        BigDecimal usedSnapshot = enterprise.getCarbonUsed() == null
                ? BigDecimal.ZERO : enterprise.getCarbonUsed();

        // ---- 写账本 ----
        quotaLedgerService.increaseForReport(
                report.getEnterpriseId(), quota,
                "碳报告审核通过，按行业基准值发放配额",
                auditorId, reportId, seq);

        // ---- 更新报告状态（幂等真相源）----
        report.setQuotaAllocatedAmount(quota);
        report.setQuotaUsedSnapshot(usedSnapshot);
        report.setAllocationState(AllocationStateEnum.ALLOCATED.getCode());
        report.setAllocationSeq(seq);
        reportRepository.save(report);

        log.info("[QUOTA] 报告 {} 发放配额 {} 吨（{} × {}，机组类型 {}，年度 {}，序号 {}）",
                reportId, quota.toPlainString(), report.getOutputQuantity().toPlainString(),
                benchmark.getBenchmarkValue().toPlainString(), unitType, year, seq);

        return buildResult(report, quota, false);
    }

    /**
     * 冲正已发放的配额。
     *
     * <p><b>冲正保护</b>：只允许冲正「未被动用」的配额。判据是
     * {@code currentUsed > quotaUsedSnapshot}（已消耗）或 {@code frozen > 0}
     * （已挂单待售）。
     *
     * <p>为什么不用「{@code tradable < 发放量}」：买入会增加 tradable，
     * 「卖 100 再买 100」就能把余额补回阈值之上，判定失效。而 used 与 frozen
     * 的增长是单向的。
     *
     * @param reportId  报告ID
     * @param auditorId 触发人
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeForReport(Long reportId, Long auditorId) {
        CarbonReport report = reportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "碳报告不存在: " + reportId));

        AllocationStateEnum state = AllocationStateEnum.fromCode(report.getAllocationState());
        if (state == null || !state.isAllocated()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该报告当前未发放配额（状态: " + report.getAllocationState() + "），无需冲正");
        }

        BigDecimal amount = report.getQuotaAllocatedAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "待冲正的配额数量无效");
        }

        Enterprise enterprise = enterpriseRepository.findById(report.getEnterpriseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "企业不存在: " + report.getEnterpriseId()));

        // ---- 冲正保护 ----
        BigDecimal currentUsed = enterprise.getCarbonUsed() == null
                ? BigDecimal.ZERO : enterprise.getCarbonUsed();
        BigDecimal snapshot = report.getQuotaUsedSnapshot();
        BigDecimal baseUsed = snapshot == null ? BigDecimal.ZERO : snapshot;

        if (currentUsed.compareTo(baseUsed) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    String.format("该企业已动用配额（发放时已用 %s，当前已用 %s），不可冲正",
                            baseUsed.toPlainString(), currentUsed.toPlainString()));
        }

        BigDecimal frozen = enterprise.getCarbonFrozen() == null
                ? BigDecimal.ZERO : enterprise.getCarbonFrozen();
        if (frozen.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    String.format("该企业存在冻结配额 %s 吨（挂单待售中），不可冲正",
                            frozen.toPlainString()));
        }

        // ---- 回退账本：quota 与 tradable 同减，used 不动 ----
        quotaLedgerService.revokeAllocation(
                report.getEnterpriseId(), amount,
                "配额冲正：报告重新审核，收回上次发放",
                auditorId, reportId);

        // ---- 状态机：REVOKED，并清空金额让重发能通过幂等判定 ----
        report.setAllocationState(AllocationStateEnum.REVOKED.getCode());
        report.setQuotaAllocatedAmount(null);
        report.setQuotaUsedSnapshot(null);
        reportRepository.save(report);

        log.info("[QUOTA] 报告 {} 冲正配额 {} 吨", reportId, amount.toPlainString());
    }

    /**
     * 前置校验——**必须在任何写入（含上链）之前调用**。
     *
     * <p>抛出的异常会让审核失败、报告退回待审，而非留一个「审过了但没发配额」的半成品。
     */
    public void validatePrerequisites(CarbonReport report) {
        if (report.getOutputQuantity() == null
                || report.getOutputQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "发电量（产量）缺失或非正数，无法计算配额。请在报告中补填发电量后重新提交。");
        }
        if (report.getUnitType() == null || report.getUnitType().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "机组类型未选择，无法确定适用基准值。请补选机组类型（I/II/III/IV）后重新提交。");
        }
        if (report.getPeriodYear() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "核算年度缺失，无法确定适用基准值。请填写规范的核算周期（如 2026-Q1）。");
        }
        if (report.getEnterpriseId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告未关联企业");
        }
    }

    // ==================== 内部方法 ====================

    private AllocationResult buildResult(CarbonReport report, BigDecimal quota, boolean skip) {
        BigDecimal scope1 = report.getScope1Emission();
        Judgement judgement;
        BigDecimal surplusOrGap;

        if (scope1 == null) {
            // 范围一缺失时不能默认"无盈余"——那会把无法判定说成有盈余
            judgement = Judgement.UNDETERMINED;
            surplusOrGap = null;
        } else {
            surplusOrGap = quota.subtract(scope1).setScale(QUOTA_SCALE, RoundingMode.HALF_UP);
            judgement = surplusOrGap.compareTo(BigDecimal.ZERO) >= 0
                    ? Judgement.SURPLUS : Judgement.SHORTFALL;
        }

        return AllocationResult.builder()
                .reportId(report.getId())
                .quotaAmount(quota)
                .scope1Emission(scope1)
                .surplusOrGap(surplusOrGap)
                .judgement(judgement)
                .idempotentSkip(skip)
                .build();
    }

    /**
     * 查询某报告的盈余判定（不触发发放，供列表/详情展示）。
     */
    @Transactional(readOnly = true)
    public Optional<Judgement> peekJudgement(Long reportId) {
        return reportRepository.findByIdAndDeletedFalse(reportId)
                .filter(r -> r.getQuotaAllocatedAmount() != null)
                .map(r -> buildResult(r, r.getQuotaAllocatedAmount(), true).getJudgement());
    }
}