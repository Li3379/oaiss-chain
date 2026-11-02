package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.IndustryBenchmark;
import com.oaiss.chain.enums.AllocationStateEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.IndustryBenchmarkRepository;
import com.oaiss.chain.repository.QuotaTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 配额发放服务测试。
 *
 * <p>这一层要守住的核心不变量：
 * <ol>
 *   <li><b>算得对</b>：配额 = 发电量 × 基准值，精度 4 位 HALF_UP</li>
 *   <li><b>只发一次</b>：同一报告重复触发不重复发放（幂等）</li>
 *   <li><b>冲正方向正确</b>：quota 与 tradable 同减、<b>used 绝不动</b></li>
 *   <li><b>冲正前先检查动用</b>：企业已用/已冻结的配额不能被冲走</li>
 *   <li><b>缺数据就失败</b>：基准值/产量/机组类型缺失，一律抛错而非静默跳过</li>
 * </ol>
 *
 * <p>会漏掉的：与上链时序的耦合、真实 DB 的唯一键约束——那需要集成测试。
 */
class QuotaAllocationServiceTest {

    private CarbonReportRepository reportRepository;
    private EnterpriseRepository enterpriseRepository;
    private IndustryBenchmarkRepository benchmarkRepository;
    private QuotaTransactionRepository quotaTransactionRepository;
    private QuotaLedgerService quotaLedgerService;
    private QuotaAllocationService allocationService;

    private CarbonReport report;
    private Enterprise enterprise;

    @BeforeEach
    void setUp() {
        reportRepository = mock(CarbonReportRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);
        benchmarkRepository = mock(IndustryBenchmarkRepository.class);
        quotaTransactionRepository = mock(QuotaTransactionRepository.class);
        quotaLedgerService = mock(QuotaLedgerService.class);

        allocationService = new QuotaAllocationService(reportRepository, enterpriseRepository,
                benchmarkRepository, quotaTransactionRepository, quotaLedgerService);

        report = new CarbonReport();
        report.setId(100L);
        report.setEnterpriseId(10L);
        report.setOutputQuantity(new BigDecimal("1000.0000"));
        report.setOutputUnit("MWh");
        report.setUnitType("II");
        report.setPeriodYear(2026);
        report.setPeriodQuarter(1);
        report.setScope1Emission(new BigDecimal("700.0000"));

        enterprise = new Enterprise();
        enterprise.setId(10L);
        enterprise.setCarbonQuota(new BigDecimal("5000.0000"));
        enterprise.setCarbonTradable(new BigDecimal("5000.0000"));
        enterprise.setCarbonUsed(BigDecimal.ZERO);
        enterprise.setCarbonFrozen(BigDecimal.ZERO);

        when(reportRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(report));
        when(enterpriseRepository.findById(10L)).thenReturn(Optional.of(enterprise));
        when(reportRepository.save(any(CarbonReport.class))).thenAnswer(i -> i.getArgument(0));
    }

    private IndustryBenchmark benchmark(String value) {
        return IndustryBenchmark.builder()
                .industryCode("POWER")
                .unitType("II")
                .benchmarkYear(2026)
                .benchmarkKind("GENERATION_BENCHMARK")
                .benchmarkValue(new BigDecimal(value))
                .benchmarkUnit("tCO2/MWh")
                .build();
    }

    // ==================== 计算正确性 ====================

    @Test
    @DisplayName("配额 = 发电量 × 基准值，精度 4 位 HALF_UP")
    void allocate_computesQuotaWithCorrectScale() {
        when(benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        "POWER", "II", 2026, "GENERATION_BENCHMARK"))
                .thenReturn(Optional.of(benchmark("0.7985")));

        QuotaAllocationService.AllocationResult result =
                allocationService.allocateForReport(100L, 7L);

        // 1000 × 0.7985 = 798.5000
        assertEquals(new BigDecimal("798.5000"), result.getQuotaAmount());
        assertEquals(4, result.getQuotaAmount().scale());
    }

    @Test
    @DisplayName("精度：产品超过 4 位小数时 HALF_UP 舍入")
    void allocate_roundsHalfUp() {
        report.setOutputQuantity(new BigDecimal("1234.5678"));
        when(benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.of(benchmark("0.7985")));

        QuotaAllocationService.AllocationResult result =
                allocationService.allocateForReport(100L, 7L);

        // 1234.5678 × 0.7985 = 985.80238830 → 4 位 HALF_UP = 985.8024
        assertEquals(4, result.getQuotaAmount().scale());
        assertEquals(new BigDecimal("985.8024"), result.getQuotaAmount());
    }

    // ==================== 幂等 ====================

    @Test
    @DisplayName("已发放（ALLOCATED）→ 幂等返回，不重复写库")
    void allocate_isIdempotentWhenAlreadyAllocated() {
        report.setAllocationState(AllocationStateEnum.ALLOCATED.getCode());
        report.setQuotaAllocatedAmount(new BigDecimal("798.5000"));

        QuotaAllocationService.AllocationResult result =
                allocationService.allocateForReport(100L, 7L);

        assertEquals(new BigDecimal("798.5000"), result.getQuotaAmount());
        assertTrue(result.isIdempotentSkip());
        // 关键：不得再调账本
        verify(quotaLedgerService, never()).increaseForReport(anyLong(), any(), anyString(),
                anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("已冲正（REVOKED）→ 允许重发，序号递增")
    void allocate_reallocatesAfterRevokeWithIncrementedSeq() {
        report.setAllocationState(AllocationStateEnum.REVOKED.getCode());
        when(quotaTransactionRepository.countByRelatedReportIdAndAllocationSeqIsNotNull(100L))
                .thenReturn(1L);
        when(benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.of(benchmark("0.7985")));

        allocationService.allocateForReport(100L, 7L);

        verify(quotaLedgerService).increaseForReport(anyLong(), any(), anyString(),
                anyLong(), eq(100L), eq(2));
        // 序号应为 2（已发过 1 次）
        assertEquals(2, report.getAllocationSeq());
    }

    // ==================== 冲正 ====================

    @Test
    @DisplayName("冲正：quota 与 tradable 同减、used 不动")
    void revoke_decreasesQuotaAndTradable_butNotUsed() {
        report.setAllocationState(AllocationStateEnum.ALLOCATED.getCode());
        report.setQuotaAllocatedAmount(new BigDecimal("798.5000"));
        report.setQuotaUsedSnapshot(BigDecimal.ZERO);
        // 当前状态：配额未被动用
        enterprise.setCarbonQuota(new BigDecimal("5798.5000"));
        enterprise.setCarbonTradable(new BigDecimal("5798.5000"));

        allocationService.revokeForReport(100L, 7L);

        verify(quotaLedgerService).revokeAllocation(eq(10L), eq(new BigDecimal("798.5000")),
                anyString(), eq(7L), eq(100L));
        assertEquals(AllocationStateEnum.REVOKED.getCode(), report.getAllocationState());
        assertNull(report.getQuotaAllocatedAmount());
        assertNull(report.getQuotaUsedSnapshot());
    }

    @Test
    @DisplayName("冲正保护：used 较发放时增长 → 拒绝")
    void revoke_rejectedWhenUsedIncreased() {
        report.setAllocationState(AllocationStateEnum.ALLOCATED.getCode());
        report.setQuotaAllocatedAmount(new BigDecimal("798.5000"));
        report.setQuotaUsedSnapshot(BigDecimal.ZERO);
        // 企业已用了 100 吨
        enterprise.setCarbonUsed(new BigDecimal("100.0000"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.revokeForReport(100L, 7L));

        assertTrue(ex.getMessage().contains("已动用") || ex.getMessage().contains("不可冲正"),
                "错误信息应说明配额已动用，实际: " + ex.getMessage());
        verify(quotaLedgerService, never()).revokeAllocation(anyLong(), any(), anyString(),
                anyLong(), anyLong());
    }

    @Test
    @DisplayName("冲正保护：存在冻结（挂单待售）→ 拒绝")
    void revoke_rejectedWhenFrozenPositive() {
        report.setAllocationState(AllocationStateEnum.ALLOCATED.getCode());
        report.setQuotaAllocatedAmount(new BigDecimal("798.5000"));
        report.setQuotaUsedSnapshot(BigDecimal.ZERO);
        // 企业挂了 50 吨卖单
        enterprise.setCarbonFrozen(new BigDecimal("50.0000"));

        assertThrows(BusinessException.class,
                () -> allocationService.revokeForReport(100L, 7L));

        verify(quotaLedgerService, never()).revokeAllocation(anyLong(), any(), anyString(),
                anyLong(), anyLong());
    }

    @Test
    @DisplayName("冲正保护：未发放过的报告不可冲正")
    void revoke_rejectedWhenNeverAllocated() {
        report.setAllocationState(AllocationStateEnum.NONE.getCode());

        assertThrows(BusinessException.class,
                () -> allocationService.revokeForReport(100L, 7L));
    }

    // ==================== 缺数据 ====================

    @Test
    @DisplayName("基准值缺失 → 抛错，指明行业/年度/机组类型")
    void allocate_failsWhenBenchmarkMissing() {
        when(benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> allocationService.allocateForReport(100L, 7L));

        String msg = ex.getMessage();
        assertTrue(msg.contains("2026") || msg.contains("基准值"),
                "错误信息应指明缺失的年度或基准值，实际: " + msg);
    }

    @Test
    @DisplayName("产量为空 → 抛错")
    void allocate_failsWhenOutputMissing() {
        report.setOutputQuantity(null);

        assertThrows(BusinessException.class,
                () -> allocationService.allocateForReport(100L, 7L));
    }

    @Test
    @DisplayName("产量 ≤ 0 → 抛错")
    void allocate_failsWhenOutputNonPositive() {
        report.setOutputQuantity(BigDecimal.ZERO);

        assertThrows(BusinessException.class,
                () -> allocationService.allocateForReport(100L, 7L));
    }

    @Test
    @DisplayName("机组类型为空 → 抛错")
    void allocate_failsWhenUnitTypeMissing() {
        report.setUnitType(null);

        assertThrows(BusinessException.class,
                () -> allocationService.allocateForReport(100L, 7L));
    }

    @Test
    @DisplayName("核算年度为空 → 抛错")
    void allocate_failsWhenPeriodYearMissing() {
        report.setPeriodYear(null);

        assertThrows(BusinessException.class,
                () -> allocationService.allocateForReport(100L, 7L));
    }

    @Test
    @DisplayName("报告不存在 → 抛错")
    void allocate_failsWhenReportNotFound() {
        when(reportRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> allocationService.allocateForReport(999L, 7L));
    }

    // ==================== 盈余判定 ====================

    @Test
    @DisplayName("实排 < 配额 → SURPLUS")
    void judgement_surplus() {
        when(benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.of(benchmark("0.7985")));

        QuotaAllocationService.AllocationResult r = allocationService.allocateForReport(100L, 7L);

        // 配额 798.5，范围一排放 700 → 盈余 98.5
        assertEquals(QuotaAllocationService.Judgement.SURPLUS, r.getJudgement());
        assertEquals(new BigDecimal("98.5000"), r.getSurplusOrGap());
    }

    @Test
    @DisplayName("实排 > 配额 → SHORTFALL")
    void judgement_shortfall() {
        report.setScope1Emission(new BigDecimal("900.0000"));
        when(benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.of(benchmark("0.7985")));

        QuotaAllocationService.AllocationResult r = allocationService.allocateForReport(100L, 7L);

        assertEquals(QuotaAllocationService.Judgement.SHORTFALL, r.getJudgement());
    }

    @Test
    @DisplayName("范围一排放为空 → UNDETERMINED（不是默认无盈余）")
    void judgement_undeterminedWhenScope1Missing() {
        report.setScope1Emission(null);
        when(benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.of(benchmark("0.7985")));

        QuotaAllocationService.AllocationResult r = allocationService.allocateForReport(100L, 7L);

        assertEquals(QuotaAllocationService.Judgement.UNDETERMINED, r.getJudgement());
        assertNull(r.getSurplusOrGap());
    }
}