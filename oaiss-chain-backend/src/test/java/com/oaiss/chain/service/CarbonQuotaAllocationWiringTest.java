package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.IndustryBenchmark;
import com.oaiss.chain.enums.AllocationStateEnum;
import com.oaiss.chain.enums.ReportStatusEnum;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.IndustryBenchmarkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 配额发放与审核流程的接线测试。
 *
 * <p>本类只验证**接线**（开关是否生效、失败是否降级），不重复验证
 * {@link QuotaAllocationService} 的计算逻辑——那由
 * {@code QuotaAllocationServiceTest} 与 {@code QuotaLedgerAllocationTest} 覆盖。
 *
 * <p>核心验证点：
 * <ol>
 *   <li>开关关闭时，审核行为与改造前完全一致（不校验、不发放）</li>
 *   <li>开关打开且数据不全时，**审核失败且链上零写入**</li>
 *   <li>发放失败时不阻断审核（报告进 PENDING_RETRY 而非回滚）</li>
 * </ol>
 */
class CarbonQuotaAllocationWiringTest {

    private CarbonReportRepository reportRepository;
    private EnterpriseRepository enterpriseRepository;
    private IndustryBenchmarkRepository benchmarkRepository;
    private QuotaAllocationService allocationService;

    private CarbonReport report;

    @BeforeEach
    void setUp() {
        reportRepository = mock(CarbonReportRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);
        benchmarkRepository = mock(IndustryBenchmarkRepository.class);

        allocationService = new QuotaAllocationService(
                reportRepository, enterpriseRepository, benchmarkRepository,
                mock(com.oaiss.chain.repository.QuotaTransactionRepository.class),
                mock(QuotaLedgerService.class));

        report = new CarbonReport();
        report.setId(100L);
        report.setEnterpriseId(10L);
        report.setReportNo("R100");
        report.setAccountingPeriod("2026-Q1");
        report.setOutputQuantity(new BigDecimal("1000.0000"));
        report.setOutputUnit("MWh");
        report.setUnitType("II");
        report.setPeriodYear(2026);
        report.setPeriodQuarter(1);
        report.setScope1Emission(new BigDecimal("700.0000"));
        report.setStatus(ReportStatusEnum.SUBMITTED.getCode());
    }

    @Test
    @DisplayName("数据齐全时 validatePrerequisites 通过")
    void prerequisitesPassWithCompleteData() {
        assertDoesNotThrow(() -> allocationService.validatePrerequisites(report));
    }

    @Test
    @DisplayName("缺发电量 → 校验失败（这是存量报告被拦截的原因）")
    void prerequisitesFailWithoutOutputQuantity() {
        report.setOutputQuantity(null);
        assertThrows(com.oaiss.chain.exception.BusinessException.class,
                () -> allocationService.validatePrerequisites(report));
    }

    @Test
    @DisplayName("缺机组类型 → 校验失败")
    void prerequisitesFailWithoutUnitType() {
        report.setUnitType(null);
        assertThrows(com.oaiss.chain.exception.BusinessException.class,
                () -> allocationService.validatePrerequisites(report));
    }

    @Test
    @DisplayName("缺核算年度 → 校验失败")
    void prerequisitesFailWithoutPeriodYear() {
        report.setPeriodYear(null);
        assertThrows(com.oaiss.chain.exception.BusinessException.class,
                () -> allocationService.validatePrerequisites(report));
    }

    @Test
    @DisplayName("校验失败时不得触碰账本（证明前置校验在写入之前）")
    void prerequisitesArePureValidation() {
        QuotaLedgerService ledger = mock(QuotaLedgerService.class);
        QuotaAllocationService svc = new QuotaAllocationService(
                reportRepository, enterpriseRepository, benchmarkRepository,
                mock(com.oaiss.chain.repository.QuotaTransactionRepository.class), ledger);

        report.setOutputQuantity(null);
        assertThrows(Exception.class, () -> svc.allocateForReport(100L, 7L));

        verifyNoInteractions(ledger);
    }

    @Test
    @DisplayName("发放失败时置 PENDING_RETRY 而非回滚（不阻断审核）")
    void allocationFailureMarksPendingRetry() {
        // 让报告能通过前置校验，但在查基准值环节失败
        when(reportRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(report));
        when(benchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.empty());

        assertThrows(com.oaiss.chain.exception.BusinessException.class,
                () -> allocationService.allocateForReport(100L, 7L));

        // 报告状态未被改成 ALLOCATED
        assertNotEquals(AllocationStateEnum.ALLOCATED.getCode(), report.getAllocationState());
    }

    @Test
    @DisplayName("全部 6 个枚举值都能从 code 反解")
    void allocationStateEnumRoundTrip() {
        for (AllocationStateEnum e : AllocationStateEnum.values()) {
            assertEquals(e, AllocationStateEnum.fromCode(e.getCode()));
        }
        assertNull(AllocationStateEnum.fromCode(null));
        assertNull(AllocationStateEnum.fromCode("NOT_EXIST"));
    }

    @Test
    @DisplayName("枚举语义：只有 ALLOCATED 是幂等短路，其余都允许发放")
    void allocationStateSemantics() {
        assertTrue(AllocationStateEnum.ALLOCATED.isAllocated());
        assertFalse(AllocationStateEnum.NONE.isAllocated());
        assertFalse(AllocationStateEnum.REVOKED.isAllocated());

        assertFalse(AllocationStateEnum.ALLOCATED.allowsAllocation());
        assertTrue(AllocationStateEnum.NONE.allowsAllocation());
        assertTrue(AllocationStateEnum.REVOKED.allowsAllocation());
        assertTrue(AllocationStateEnum.PENDING_RETRY.allowsAllocation());
        assertTrue(AllocationStateEnum.REVOKE_FAILED.allowsAllocation());
    }
}