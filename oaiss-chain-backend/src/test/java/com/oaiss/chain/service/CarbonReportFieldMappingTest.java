package com.oaiss.chain.service;

import com.oaiss.chain.dto.CarbonReportRequest;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.enums.ReportStatusEnum;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.security.JwtUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 报告填报字段落库测试（组件 1 的接线断点回归）。
 *
 * <p><b>为什么单独立类</b>：2026-11-02 复核发现前端从 {@code 35f4d28} 起就在发
 * {@code periodYear / periodQuarter / outputQuantity / outputUnit / unitType}，
 * 而后端 {@link CarbonReportRequest} 从未声明这五个字段。Spring Boot 默认
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false}，于是这些值被**静默丢弃、HTTP 200**，
 * 库里的 `output_quantity`、`unit_type`、`period_year` 永远是 NULL。
 *
 * <p>后果不是「少一个展示字段」，而是：一旦 {@code app.quota.allocation.enabled=true}，
 * {@code QuotaAllocationService.validatePrerequisites} 每一份报告都会抛
 * 「发电量缺失」——**没有任何报告能通过审核**。而既有单测全绿，因为
 * {@code CarbonServiceTest} 只断言「保存被调用了一次」，不看保存了什么。
 *
 * <p>本类守住的断言：请求里带了什么，实体里就必须有什么。
 */
class CarbonReportFieldMappingTest {

    private CarbonReportRepository reportRepository;
    private EnterpriseRepository enterpriseRepository;
    private CarbonService carbonService;

    @BeforeEach
    void setUp() {
        reportRepository = mock(CarbonReportRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);

        Enterprise enterprise = Enterprise.builder().userId(1L).enterpriseName("测试企业").build();
        enterprise.setId(10L);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(enterprise));
        when(reportRepository.save(any(CarbonReport.class))).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * 用 Mockito 深度 stub 构造 CarbonService：本类只关心映射，其余依赖全部给 mock。
     * 依赖清单跟随 CarbonService 构造器，新增依赖时这里会编译失败——这是有意的信号。
     */
    private void buildService() {
        // createReport 会先解析排放明细再建实体；objectMapper 必须能真的读 JSON，
        // 否则会在映射发生之前就失败，测不到本类关心的东西。
        carbonService = new CarbonService(
                reportRepository,
                enterpriseRepository,
                mock(com.oaiss.chain.repository.UserRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(CreditScoreService.class),
                mock(EmissionRatingService.class),
                mock(BlockchainServicePort.class),
                mock(DigitalSignatureService.class),
                mock(CertificateGateService.class),
                mock(QuotaAllocationService.class),
                mock(com.oaiss.chain.repository.IndustryBenchmarkRepository.class),
                org.mockito.Mockito.mock(CarbonCoinService.class));
    }

    /** 与前端 CarbonUpload 提交的最小合法载荷一致（scope1 明细为空数组） */
    private CarbonReportRequest fullRequest() {
        CarbonReportRequest request = new CarbonReportRequest();
        request.setAccountingPeriod("2026-Q1");
        request.setTitle("2026年一季度发电碳排放报告");
        request.setReportType(1);
        request.setEmissionData("{\"scope1\":[]}");
        request.setPeriodYear(2026);
        request.setPeriodQuarter(1);
        request.setOutputQuantity(new BigDecimal("1234.5678"));
        request.setOutputUnit("MWh");
        request.setUnitType("II");
        return request;
    }

    private static final JwtUserDetails USER = new JwtUserDetails();

    static {
        USER.setUserId(1L);
        USER.setUsername("testuser");
    }

    @Test
    @DisplayName("创建报告：五个配额依据字段全部落库")
    void createReport_persistsQuotaBasisFields() {
        buildService();

        carbonService.createReport(USER, fullRequest());

        ArgumentCaptor<CarbonReport> captor = ArgumentCaptor.forClass(CarbonReport.class);
        verify(reportRepository).save(captor.capture());
        CarbonReport saved = captor.getValue();

        assertEquals(Integer.valueOf(2026), saved.getPeriodYear(), "核算年度必须落库，否则查不到基准值");
        assertEquals(Integer.valueOf(1), saved.getPeriodQuarter(), "核算季度必须落库");
        assertEquals(0, new BigDecimal("1234.5678").compareTo(saved.getOutputQuantity()),
                "发电量必须落库，否则配额算不出来");
        assertEquals("MWh", saved.getOutputUnit(), "发电量单位必须落库");
        assertEquals("II", saved.getUnitType(), "机组类型必须落库，否则不知道查哪一行基准值");
    }

    // ==================== 同周期重复填报 ====================

    @Test
    @DisplayName("同企业同周期已有报告 → 创建时给出可读提示，而不是让唯一键抛出 500")
    void createReport_duplicatePeriodGivesReadableError() {
        buildService();
        CarbonReport existing = CarbonReport.builder()
                .reportNo("CR20260101009").enterpriseId(10L)
                .accountingPeriod("2026-Q1").title("已存在").reportType(1)
                .emissionData("{}").status(ReportStatusEnum.DRAFT.getCode())
                .periodYear(2026).periodQuarter(1).build();
        existing.setId(900L);
        when(reportRepository.findByEnterpriseIdAndPeriodYearAndPeriodQuarterAndDeletedFalse(
                10L, 2026, 1)).thenReturn(Optional.of(existing));

        var ex = assertThrows(com.oaiss.chain.exception.CarbonException.class,
                () -> carbonService.createReport(USER, fullRequest()));

        assertTrue(ex.getMessage().contains("2026-Q1"),
                "提示里要说清是哪个周期冲突了，否则企业不知道该改哪一项；实际: " + ex.getMessage());
        verify(reportRepository, never()).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("不同周期可正常创建")
    void createReport_differentPeriodAllowed() {
        buildService();
        when(reportRepository.findByEnterpriseIdAndPeriodYearAndPeriodQuarterAndDeletedFalse(
                10L, 2026, 1)).thenReturn(Optional.empty());

        carbonService.createReport(USER, fullRequest());

        verify(reportRepository).save(any(CarbonReport.class));
    }

    /**
     * 用前端 CarbonUpload.vue 真实提交的 JSON 反序列化——这是「字段被静默丢弃」的精确回归点。
     *
     * <p>直接构造 DTO 只能证明「DTO 有 setter」。真实断链发生在 Jackson 反序列化：
     * 请求体里有字段、DTO 里没有，Spring Boot 默认不报错，值直接消失。
     * 所以必须从原始 JSON 走一遍，才能锁住这条路径。
     */
    @Test
    @DisplayName("前端真实 JSON 载荷 → DTO 五个字段全部接住（防静默丢弃回归）")
    void jsonPayload_fromFrontendBindsAllFields() throws Exception {
        String frontendJson = """
                {
                  "accountingPeriod": "2026-Q1",
                  "title": "2026年一季度发电碳排放报告",
                  "reportType": 1,
                  "emissionData": "{\\"scope1\\":[]}",
                  "periodYear": 2026,
                  "periodQuarter": 1,
                  "outputQuantity": 1234.5678,
                  "outputUnit": "MWh",
                  "unitType": "II"
                }
                """;

        CarbonReportRequest bound = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(frontendJson, CarbonReportRequest.class);

        assertEquals(Integer.valueOf(2026), bound.getPeriodYear(), "periodYear 被 Jackson 丢弃了");
        assertEquals(Integer.valueOf(1), bound.getPeriodQuarter(), "periodQuarter 被 Jackson 丢弃了");
        assertEquals(0, new BigDecimal("1234.5678").compareTo(bound.getOutputQuantity()),
                "outputQuantity 被 Jackson 丢弃了");
        assertEquals("MWh", bound.getOutputUnit(), "outputUnit 被 Jackson 丢弃了");
        assertEquals("II", bound.getUnitType(), "unitType 被 Jackson 丢弃了");
    }

    @Test
    @DisplayName("创建报告：没传的字段保持 null，不编造默认值")
    void createReport_leavesAbsentFieldsNull() {
        buildService();
        CarbonReportRequest request = fullRequest();
        request.setOutputQuantity(null);
        request.setUnitType(null);

        carbonService.createReport(USER, request);

        ArgumentCaptor<CarbonReport> captor = ArgumentCaptor.forClass(CarbonReport.class);
        verify(reportRepository).save(captor.capture());
        CarbonReport saved = captor.getValue();

        assertNull(saved.getOutputQuantity(), "缺省不能填 0——0 会被误判为「填了但不合规」而非「没填」");
        assertNull(saved.getUnitType());
        // 周期字段仍在
        assertEquals(Integer.valueOf(2026), saved.getPeriodYear());
    }

    // ==================== 配额结果透出（AC-15）====================

    @Test
    @DisplayName("响应透出发放量与判定：审核员审完要知道发了多少吨、盈余还是缺口")
    void response_exposesAllocationAmountAndJudgement() {
        buildService();
        CarbonReport allocated = CarbonReport.builder()
                .reportNo("CR20260101001")
                .enterpriseId(10L)
                .accountingPeriod("2026-Q1")
                .title("已发放")
                .reportType(1)
                .emissionData("{}")
                .status(ReportStatusEnum.ON_CHAIN.getCode())
                .quotaAllocatedAmount(new BigDecimal("985.8024"))
                .allocationState("ALLOCATED")
                .scope1Emission(new BigDecimal("700.0000"))
                .outputQuantity(new BigDecimal("1234.5678"))
                .unitType("II")
                .periodYear(2026)
                .build();
        allocated.setId(100L);
        when(reportRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(allocated));

        var response = carbonService.getReport(USER, 100L);

        assertEquals(0, new BigDecimal("985.8024").compareTo(response.getQuotaAllocatedAmount()));
        assertEquals("ALLOCATED", response.getAllocationState());
        // 配额 985.8024 − 实排 700 = 盈余 285.8024
        assertEquals(0, new BigDecimal("285.8024").compareTo(response.getQuotaSurplusOrGap()));
        assertEquals("SURPLUS", response.getQuotaJudgement());
    }

    @Test
    @DisplayName("未发放的报告：配额三字段全 null，不能显示成「0 吨盈余」")
    void response_unallocatedReportHasNullQuotaFields() {
        buildService();
        CarbonReport draft = CarbonReport.builder()
                .reportNo("CR20260101002")
                .enterpriseId(10L)
                .accountingPeriod("2026-Q1")
                .title("草稿")
                .reportType(1)
                .emissionData("{}")
                .status(ReportStatusEnum.DRAFT.getCode())
                .build();
        draft.setId(101L);
        when(reportRepository.findByIdAndDeletedFalse(101L)).thenReturn(Optional.of(draft));

        var response = carbonService.getReport(USER, 101L);

        assertNull(response.getQuotaAllocatedAmount(), "未发放时报 0 会被读成「发了 0 吨」");
        assertNull(response.getAllocationState());
        assertNull(response.getQuotaSurplusOrGap());
        assertNull(response.getQuotaJudgement());
    }

    @Test
    @DisplayName("范围一缺失 → 判定 UNDETERMINED，且不给数字")
    void response_missingScope1IsUndetermined() {
        buildService();
        CarbonReport allocated = CarbonReport.builder()
                .reportNo("CR20260101003")
                .enterpriseId(10L)
                .accountingPeriod("2026-Q1")
                .title("无范围一")
                .reportType(1)
                .emissionData("{}")
                .status(ReportStatusEnum.ON_CHAIN.getCode())
                .quotaAllocatedAmount(new BigDecimal("985.8024"))
                .allocationState("ALLOCATED")
                .scope1Emission(null)
                .build();
        allocated.setId(102L);
        when(reportRepository.findByIdAndDeletedFalse(102L)).thenReturn(Optional.of(allocated));

        var response = carbonService.getReport(USER, 102L);

        assertEquals("UNDETERMINED", response.getQuotaJudgement());
        assertNull(response.getQuotaSurplusOrGap(), "判不出来不能给 0——0 会被当成「刚好平衡」");
    }
}