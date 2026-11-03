package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.enums.ReportStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 签名载荷覆盖范围测试（AC-21）。
 *
 * <p><b>问题</b>：签名载荷自始至终只有 {@code emissionData}。配额发放依赖的
 * {@code outputQuantity}（发电量）与 {@code unitType}（机组类型）**不在签名覆盖范围内**。
 * 也就是说，一份带有效签名的报告，其发电量可以被任意篡改而验签依然通过——
 * 而发电量直接决定发多少吨配额。
 *
 * <p>「编辑后强制重签」不解决这个问题：它保证的是「签名与 emissionData 一致」，
 * 篡改产量的人只要不碰 emissionData，就不会触发重签。
 *
 * <p><b>为什么载荷要版本化</b>：改成新载荷会让存量报告的签名全部失效。
 * 存量报告的 {@code output_quantity} 为 NULL（该列是本轮才加的），
 * 因此规则是「有产量/机组类型 → 用新载荷；两者都没有 → 用旧载荷」。
 * 两条路径对称：签名和验签调用同一个 {@code signaturePayload}。
 */
class CarbonSignaturePayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CarbonReport report(String emissionData, String outputQuantity, String unitType) {
        CarbonReport r = CarbonReport.builder()
                .reportNo("CR20260101001")
                .enterpriseId(10L)
                .accountingPeriod("2026-Q1")
                .title("测试")
                .reportType(1)
                .emissionData(emissionData)
                .status(ReportStatusEnum.SUBMITTED.getCode())
                .outputQuantity(outputQuantity == null ? null : new BigDecimal(outputQuantity))
                .unitType(unitType)
                .build();
        r.setId(100L);
        return r;
    }

    private CarbonService service() {
        return new CarbonService(
                org.mockito.Mockito.mock(com.oaiss.chain.repository.CarbonReportRepository.class),
                org.mockito.Mockito.mock(com.oaiss.chain.repository.EnterpriseRepository.class),
                org.mockito.Mockito.mock(com.oaiss.chain.repository.UserRepository.class),
                objectMapper,
                org.mockito.Mockito.mock(CreditScoreService.class),
                org.mockito.Mockito.mock(EmissionRatingService.class),
                org.mockito.Mockito.mock(BlockchainServicePort.class),
                org.mockito.Mockito.mock(DigitalSignatureService.class),
                org.mockito.Mockito.mock(CertificateGateService.class),
                org.mockito.Mockito.mock(QuotaAllocationService.class),
                org.mockito.Mockito.mock(com.oaiss.chain.repository.IndustryBenchmarkRepository.class),
                org.mockito.Mockito.mock(CarbonCoinService.class));
    }

    @Test
    @DisplayName("有产量与机组类型 → 载荷包含这两个字段")
    void payload_includesQuotaBasisFields() {
        String payload = service().signaturePayload(report("{\"scope1\":[]}", "1234.5678", "II"));

        assertTrue(payload.contains("1234.5678"), "载荷必须含发电量，否则篡改产量验签仍通过");
        assertTrue(payload.contains("II"), "载荷必须含机组类型");
    }

    @Test
    @DisplayName("篡改发电量 → 载荷变化（签名因此失效）")
    void payload_changesWhenOutputQuantityTampered() {
        CarbonService svc = service();
        String original = svc.signaturePayload(report("{\"scope1\":[]}", "1234.5678", "II"));
        String tampered = svc.signaturePayload(report("{\"scope1\":[]}", "9999.0000", "II"));

        assertNotEquals(original, tampered, "产量变了载荷必须变，否则篡改不可检测");
    }

    @Test
    @DisplayName("篡改机组类型 → 载荷变化")
    void payload_changesWhenUnitTypeTampered() {
        CarbonService svc = service();
        String original = svc.signaturePayload(report("{\"scope1\":[]}", "1234.5678", "II"));
        String tampered = svc.signaturePayload(report("{\"scope1\":[]}", "1234.5678", "IV"));

        assertNotEquals(original, tampered, "机组类型决定查哪一行基准值，篡改它会改配额");
    }

    @Test
    @DisplayName("存量报告（无产量无机组类型）→ 载荷退回旧格式，既有签名仍可验")
    void payload_fallsBackToLegacyForExistingReports() {
        CarbonReport legacy = report("{\"scope1\":[]}", null, null);

        assertEquals("{\"scope1\":[]}", service().signaturePayload(legacy),
                "存量报告不能因为本轮改造而集体验签失败——那会让所有历史报告卡在审核");
    }

    @Test
    @DisplayName("载荷是确定性的：同一份报告两次生成完全相同")
    void payload_isDeterministic() {
        CarbonService svc = service();
        CarbonReport r = report("{\"scope1\":[]}", "1234.5678", "II");

        assertEquals(svc.signaturePayload(r), svc.signaturePayload(r));
    }
}
