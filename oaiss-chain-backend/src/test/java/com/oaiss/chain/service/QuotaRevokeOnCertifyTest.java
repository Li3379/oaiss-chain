package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.ReviewRequest;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.enums.AllocationStateEnum;
import com.oaiss.chain.enums.ReportStatusEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 配额冲正与 certify 驳回的接线测试（AC-16）。
 *
 * <p><b>为什么必须单独立类</b>：2026-11-02 复核发现
 * {@code QuotaAllocationService.revokeForReport} 在全仓**零个生产调用方**，
 * 只在它自己的 4 个单测里被调用。也就是说：
 *
 * <pre>
 * 报告审核通过 → 发 1000 吨配额
 * 审核员在 certify 里驳回这份报告 → 状态变 REJECTED
 * 但那 1000 吨配额**仍然留在企业账上**，可自由交易
 * </pre>
 *
 * <p>这不是「少做一步」，而是资产错账：一份已被否决的报告对应的配额，
 * 企业可以拿去卖。计划里的 AC-16（冲正重发全链路）在代码中根本不存在。
 *
 * <p>本类守住的断言：
 * <ol>
 *   <li>certify 驳回时**必须**触发冲正</li>
 *   <li>冲正失败**不得阻断驳回**（否则企业有在途挂单时审核员连驳回都做不了）</li>
 *   <li>开关关闭时行为与改造前一致（不冲正，也不报错）</li>
 * </ol>
 */
class QuotaRevokeOnCertifyTest {

    private CarbonReportRepository reportRepository;
    private EnterpriseRepository enterpriseRepository;
    private QuotaAllocationService quotaAllocationService;
    private CarbonService carbonService;

    private CarbonReport report;

    private static final JwtUserDetails REVIEWER = new JwtUserDetails();

    static {
        REVIEWER.setUserId(7L);
        REVIEWER.setUsername("reviewer");
        REVIEWER.setUserType(3);
    }

    @BeforeEach
    void setUp() {
        reportRepository = mock(CarbonReportRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);
        quotaAllocationService = mock(QuotaAllocationService.class);

        carbonService = new CarbonService(
                reportRepository,
                enterpriseRepository,
                mock(UserRepository.class),
                new ObjectMapper(),
                mock(CreditScoreService.class),
                mock(EmissionRatingService.class),
                mock(BlockchainServicePort.class),
                mock(DigitalSignatureService.class),
                mock(CertificateGateService.class),
                quotaAllocationService,
                mock(com.oaiss.chain.repository.IndustryBenchmarkRepository.class));
        ReflectionTestUtils.setField(carbonService, "quotaAllocationEnabled", true);

        report = CarbonReport.builder()
                .reportNo("CR20260101001")
                .enterpriseId(10L)
                .accountingPeriod("2026-Q1")
                .title("已发放配额的报告")
                .reportType(1)
                .emissionData("{}")
                .status(ReportStatusEnum.ON_CHAIN.getCode())
                .quotaAllocatedAmount(new BigDecimal("1000.0000"))
                .allocationState(AllocationStateEnum.ALLOCATED.getCode())
                .scope1Emission(new BigDecimal("800.0000"))
                .build();
        report.setId(100L);

        when(reportRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(CarbonReport.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ReviewRequest rejectRequest() {
        return new ReviewRequest(100L, ReportStatusEnum.REJECTED.getCode(), "核查发现数据不实");
    }

    @Test
    @DisplayName("certify 驳回已发放配额的报告 → 必须冲正")
    void certifyReject_revokesAllocatedQuota() {
        carbonService.certifyReport(REVIEWER, rejectRequest());

        verify(quotaAllocationService, times(1)).revokeForReport(eq(100L), anyLong());
    }

    @Test
    @DisplayName("冲正成功 → 驳回保存时不得把状态写回 ALLOCATED")
    void certifyReject_keepsRevokedStateAfterSave() {
        // 冲正在独立事务里提交，外层实体还是旧值；必须重新读一次同步回来。
        // 不同步的话，随后的 save(report) 会把 ALLOCATED 和原金额覆盖回去，
        // 冲正等于白做，而账面看不出任何异常。
        CarbonReport afterRevoke = CarbonReport.builder()
                .reportNo("CR20260101001")
                .enterpriseId(10L)
                .status(ReportStatusEnum.REJECTED.getCode())
                .allocationState(AllocationStateEnum.REVOKED.getCode())
                .quotaAllocatedAmount(null)
                .build();
        afterRevoke.setId(100L);
        // 第二次 findById 返回冲正后的状态
        when(reportRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(report), Optional.of(afterRevoke));

        carbonService.certifyReport(REVIEWER, rejectRequest());

        assertEquals(AllocationStateEnum.REVOKED.getCode(), report.getAllocationState(),
                "冲正结果必须同步回外层实体，否则 save 会把它覆盖回 ALLOCATED");
        assertNull(report.getQuotaAllocatedAmount(), "冲正后金额必须清空，否则重发会被幂等短路");
    }

    @Test
    @DisplayName("冲正失败不阻断驳回——企业有在途挂单时审核员仍要能驳回")
    void certifyReject_revokeFailureDoesNotBlock() {
        doThrow(new BusinessException(com.oaiss.chain.constant.ErrorCode.PARAM_ERROR,
                "该企业存在冻结配额 500 吨（挂单待售中），不可冲正"))
                .when(quotaAllocationService).revokeForReport(anyLong(), anyLong());

        // 不抛异常
        assertDoesNotThrow(() -> carbonService.certifyReport(REVIEWER, rejectRequest()));

        // 驳回仍然生效
        assertEquals(ReportStatusEnum.REJECTED.getCode(), report.getStatus());
        // 冲正失败要留痕，供管理员重试
        assertEquals(AllocationStateEnum.REVOKE_FAILED.getCode(), report.getAllocationState(),
                "冲正失败必须置 REVOKE_FAILED，否则这笔该收回的配额彻底失联");
    }

    @Test
    @DisplayName("未发放配额的报告被驳回 → 不调冲正（避免无意义报错）")
    void certifyReject_unallocatedReportSkipsRevoke() {
        report.setQuotaAllocatedAmount(null);
        report.setAllocationState(AllocationStateEnum.NONE.getCode());

        carbonService.certifyReport(REVIEWER, rejectRequest());

        verify(quotaAllocationService, never()).revokeForReport(anyLong(), anyLong());
        assertEquals(ReportStatusEnum.REJECTED.getCode(), report.getStatus());
    }

    @Test
    @DisplayName("开关关闭时驳回不冲正——灰度期行为与改造前一致")
    void certifyReject_flagOffSkipsRevoke() {
        ReflectionTestUtils.setField(carbonService, "quotaAllocationEnabled", false);

        carbonService.certifyReport(REVIEWER, rejectRequest());

        verify(quotaAllocationService, never()).revokeForReport(anyLong(), anyLong());
    }

    @Test
    @DisplayName("review 驳回（初审阶段）也要冲正——两条驳回路径不能有一条漏网")
    void reviewReject_revokesAllocatedQuota() {
        report.setStatus(ReportStatusEnum.SUBMITTED.getCode());

        ReviewRequest req = new ReviewRequest(100L, ReportStatusEnum.REJECTED.getCode(), "退回修改");
        carbonService.reviewReport(REVIEWER, req);

        verify(quotaAllocationService, times(1)).revokeForReport(eq(100L), anyLong());
    }
}
