package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.OutboxMessage;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 链上对账服务单元测试。
 *
 * <p>对账回答的是「本地库说上链了，链上真的有吗」——此前系统只有单向写入，
 * 没有任何从链上读回并比对的通道，因此「还原」无从谈起。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChainReconciliationServiceTest {

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @Mock
    private BlockchainServicePort blockchainService;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private com.oaiss.chain.repository.TransactionRepository transactionRepository;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private ChainReconciliationService chainReconciliationService;

    @BeforeEach
    void wireService() {
        chainReconciliationService = new ChainReconciliationService(
                carbonReportRepository, blockchainService, outboxMessageRepository,
                transactionRepository, objectMapper, null);
    }

    private com.oaiss.chain.entity.Transaction pendingTrade;
    private CarbonReport onChainReport;
    private CarbonReport dbOnlyReport;

    @BeforeEach
    void seedReports() {
        onChainReport = CarbonReport.builder()
                .reportNo("CR-001")
                .enterpriseId(1L)
                .totalEmission(new java.math.BigDecimal("100.00"))
                .blockchainTxHash("tx-001")
                .build();
        onChainReport.setId(1L);

        dbOnlyReport = CarbonReport.builder()
                .reportNo("CR-002")
                .enterpriseId(1L)
                .totalEmission(new java.math.BigDecimal("200.00"))
                .blockchainTxHash(null)
                .build();
        dbOnlyReport.setId(2L);

        pendingTrade = com.oaiss.chain.entity.Transaction.builder()
                .tradeNo("TX-MOCK-1")
                .sellerId(1L)
                .buyerId(2L)
                .blockchainTxHash("tx_mock_999")
                .build();
        pendingTrade.setId(9L);
    }


    // ==================== 标记历史脏数据 ====================

    @Test
    @DisplayName("标记历史脏数据-写入标记与原因，且不改动民业务状态与 txHash")
    void markHistoricalMockData_recordsMarkWithoutTouchingBusinessState() {
        onChainReport.setBlockchainTxHash("tx_mock_123");
        when(carbonReportRepository.findByBlockchainTxHashLikeAndDeletedFalse(any()))
                .thenReturn(List.of(onChainReport));
        when(carbonReportRepository.saveAll(any())).thenReturn(List.of(onChainReport));

        int marked = chainReconciliationService.markHistoricalMockData();

        assertEquals(1, marked);
        assertEquals("HISTORICAL_MOCK", onChainReport.getChainMark());
        assertNotNull(onChainReport.getChainMarkReason());
        // 关键：业务状态与哈希原样保留——标记是「加注解」，不是「改事实」
        assertEquals("tx_mock_123", onChainReport.getBlockchainTxHash());
    }

    @Test
    @DisplayName("标记历史脏数据-重复执行幂等，已标记的不再重复写")
    void markHistoricalMockData_isIdempotent() {
        onChainReport.setChainMark("HISTORICAL_MOCK");
        when(carbonReportRepository.findByBlockchainTxHashLikeAndDeletedFalse(any()))
                .thenReturn(List.of(onChainReport));

        int marked = chainReconciliationService.markHistoricalMockData();

        assertEquals(0, marked, "已标记的记录不应重复计数");
        verify(carbonReportRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("标记历史脏数据-同时标记交易表的 Mock 哈希")
    void markHistoricalMockData_alsoMarksTrades() {
        when(carbonReportRepository.findByBlockchainTxHashLikeAndDeletedFalse(any()))
                .thenReturn(List.of());
        when(transactionRepository.findByBlockchainTxHashLikeAndDeletedFalse(any()))
                .thenReturn(List.of(pendingTrade));

        int marked = chainReconciliationService.markHistoricalMockData();

        assertEquals(1, marked);
        assertEquals("HISTORICAL_MOCK", pendingTrade.getChainMark());
    }

    // ==================== 判定优先级表（9 行）====================

    /** 全量遍历桩：返回给定列表。 */
    private void stubAll(List<CarbonReport> reports) {
        when(carbonReportRepository.findByBlockchainTxHashIsNotNullAndDeletedFalse(any(Sort.class)))
                .thenReturn(reports);
    }

    /** 构造一份 11 字段齐全的 v2 链上快照。 */
    private String v2Snapshot(String txHash) {
        return "{\"reportId\":\"1\",\"reportNo\":\"CR-001\",\"enterpriseId\":\"1\",\"accountingPeriod\":\"2026-Q1\",\"totalEmission\":\"100.00\",\"scope1Emission\":\"80.00\",\"scope2Emission\":\"20.00\",\"scope3Emission\":\"0.00\",\"desulfEmission\":\"0\",\"transmissionLossEmission\":\"0\",\"sf6Emission\":\"0\",\"signature\":\"sig-001\",\"schemaVersion\":\"v2\",\"txHash\":\"" + txHash + "\"}";
    }
    @Test
    @DisplayName("第 1 行：已标记 HISTORICAL_MOCK → 不查链、txHashVerified=null")
    void reconcile_classifiesMarkedMockRecordsSeparately() {
        onChainReport.setChainMark("HISTORICAL_MOCK");
        onChainReport.setChainMarkReason("Mock 模式生成，链上不存在");
        stubAll(List.of(onChainReport));

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getHistoricalMock());
        assertEquals(0, result.getMissingOnChain(), "已标记的脏数据不应继续报缺失");
        assertEquals("HISTORICAL_MOCK", result.getDetails().get(0).get("status"));
        assertEquals(null, result.getDetails().get(0).get("txHashVerified"), "欠账永不验证");
        // 已标记的记录无须再去查链——那正是标记的意义：省掉必然失败的往返
        verify(blockchainService, never()).queryReportSnapshot(anyLong());
    }

    @Test
    @DisplayName("第 2 行：链不可达 → UNVERIFIABLE，不得误判为缺失")
    void reconcile_marksUnverifiableWhenChainUnreachable() {
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L))
                .thenThrow(new RuntimeException("Fabric network timeout"));

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getUnverifiable(), "网络故障不得渲染成「链上缺失」");
        assertEquals(0, result.getMissingOnChain());
        assertEquals(null, result.getDetails().get(0).get("txHashVerified"));
    }
    @Test
    @DisplayName("第 2 行：快照缺 txHash 字段 → UNVERIFIABLE（不得填 false）")
    void reconcile_marksUnverifiableWhenTxHashFieldMissing() {
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L))
                .thenReturn("{\"reportId\":\"1\",\"totalEmission\":\"100.00\"}");

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getUnverifiable());
        assertEquals(null, result.getDetails().get(0).get("txHashVerified"),
                "「不知道」不能说成「哈希为假」");
    }
    @Test
    @DisplayName("第 3 行：链上查无记录 → MISSING_ON_CHAIN，txHashVerified=false")
    void reconcile_marksMissingWhenChainHasNoRecord() {
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn(null);

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getMissingOnChain());
        assertEquals("MISSING_ON_CHAIN", result.getDetails().get(0).get("status"));
        assertEquals(false, result.getDetails().get(0).get("txHashVerified"));
    }

    @Test
    @DisplayName("第 4 行：v1 结构 → HISTORICAL_V1，仍做哈希验证、不做内容比对")
    void reconcile_classifiesV1AsHistoricalButStillVerifiesHash() {
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn("{\"reportId\":\"1\",\"totalEmission\":\"\",\"schemaVersion\":\"v1\",\"txHash\":\"tx-001\"}");

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getHistoricalV1(), "v1 归历史类，不落 INSUFFICIENT_DATA");
        assertEquals(0, result.getInsufficientData(), "v1 优先于金额不足判定");
        assertEquals(0, result.getMatched());
        assertEquals(true, result.getDetails().get(0).get("txHashVerified"),
                "v1 也必须验哈希——不得提前 return");
    }
    @Test
    @DisplayName("第 4 行：v1 哈希不符仍归 HISTORICAL_V1，仅由 txHashVerified=false 暴露")
    void reconcile_v1HashMismatchStaysHistorical() {
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn("{\"reportId\":\"1\",\"totalEmission\":\"\",\"schemaVersion\":\"v1\",\"txHash\":\"tx-OTHER\"}");

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getHistoricalV1());
        assertEquals(false, result.getDetails().get(0).get("txHashVerified"));
    }
    @Test
    @DisplayName("第 5 行：v2 但 totalEmission 为空 → INSUFFICIENT_DATA（不得判 MATCHED）")
    void reconcile_v2EmptyTotalIsInsufficientNotMatched() {
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn("{\"reportId\":\"1\",\"totalEmission\":\"\",\"schemaVersion\":\"v2\",\"txHash\":\"tx-001\"}");

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getInsufficientData(), "总量比不了就不许判一致");
        assertEquals(0, result.getMatched(), "这是本轮要消灭的假一致");
        assertEquals(true, result.getDetails().get(0).get("txHashVerified"));
    }
    @Test
    @DisplayName("第 6 行：可比对字段数 < 4 → INSUFFICIENT_DATA")
    void reconcile_tooFewComparableFieldsIsInsufficient() {
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn("{\"totalEmission\":\"100.00\",\"schemaVersion\":\"v2\",\"txHash\":\"tx-001\"}");

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getInsufficientData());
        assertEquals(0, result.getMatched());
    }
    @Test
    @DisplayName("第 7 行：内容字段不符 → CONTENT_MISMATCH，mismatchFields 列出具体字段")
    void reconcile_marksContentMismatchWithFieldList() {
        stubAll(List.of(onChainReport));
        String snap = v2Snapshot("tx-001").replace("\"totalEmission\":\"100.00\"", "\"totalEmission\":\"150.00\"");
        when(blockchainService.queryReportSnapshot(1L)).thenReturn(snap);

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getContentMismatch());
        assertEquals("100.00", result.getDetails().get(0).get("dbTotalEmission"));
        assertEquals("150.00", result.getDetails().get(0).get("chainTotalEmission"));
        @SuppressWarnings("unchecked")
        var fields = (List<String>) result.getDetails().get(0).get("mismatchFields");
        assertTrue(fields.contains("totalEmission"), "应列出具体不符字段");
    }
    @Test
    @DisplayName("第 7 行：过程排放（脱硫）不符也能被发现——快照专为可核验而加的字段")
    void reconcile_detectsProcessEmissionMismatch() {
        onChainReport.setEmissionData("{\"desulf\":25}");
        stubAll(List.of(onChainReport));
        String snap = v2Snapshot("tx-001").replace("\"desulfEmission\":\"0\"", "\"desulfEmission\":\"99\"");
        when(blockchainService.queryReportSnapshot(1L)).thenReturn(snap);

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getContentMismatch());
        @SuppressWarnings("unchecked")
        var fields = (List<String>) result.getDetails().get(0).get("mismatchFields");
        assertTrue(fields.contains("desulfEmission"), "脱硫量被改必须被发现");
    }
    @Test
    @DisplayName("第 8 行：内容全等但 txHash 不符 → CONTENT_MISMATCH（不得宣称哈希已验证）")
    void reconcile_hashMismatchWithEqualContentIsMismatch() {
        alignAllFields();
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn(v2Snapshot("tx-OTHER"));

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getContentMismatch());
        assertEquals(false, result.getDetails().get(0).get("txHashVerified"));
        @SuppressWarnings("unchecked")
        var fields = (List<String>) result.getDetails().get(0).get("mismatchFields");
        assertTrue(fields.contains("txHash"));
        assertEquals(0, result.getMatched(), "内容对但哈希错，不是一致");
    }

    @Test
    @DisplayName("第 9 行：哈希已验证 + 全字段一致 → MATCHED")
    void reconcile_marksMatchedWhenEverythingAgrees() {
        alignAllFields();
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn(v2Snapshot("tx-001"));

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getMatched());
        assertEquals(true, result.getDetails().get(0).get("txHashVerified"));
        assertEquals(0, result.getMissingOnChain());
        assertEquals(0, result.getContentMismatch());
    }

    /** 让库侧字段与 v2Snapshot 完全对齐（供第 8/9 行用例使用）。 */
    private void alignAllFields() {
        onChainReport.setSignatureData("sig-001");
        onChainReport.setAccountingPeriod("2026-Q1");
        onChainReport.setScope1Emission(new java.math.BigDecimal("80.00"));
        onChainReport.setScope2Emission(new java.math.BigDecimal("20.00"));
        onChainReport.setScope3Emission(new java.math.BigDecimal("0.00"));
    }
    // ==================== 计数口径与不变量 ====================

    @Test
    @DisplayName("不变量：7 类计数之和 == checkedGlobal（含 HISTORICAL_MOCK）")
    void reconcile_countsSumEqualsCheckedGlobal() {
        CarbonReport mock = CarbonReport.builder().reportNo("CR-M").enterpriseId(1L)
                .blockchainTxHash("tx_mock_1").chainMark("HISTORICAL_MOCK").build();
        mock.setId(3L);
        CarbonReport missing = CarbonReport.builder().reportNo("CR-X").enterpriseId(1L)
                .blockchainTxHash("tx-x").build();
        missing.setId(4L);

        stubAll(List.of(onChainReport, mock, missing));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn(null);
        when(blockchainService.queryReportSnapshot(4L)).thenReturn(null);

        var r = chainReconciliationService.reconcile(1, 10);

        int sum = r.getMatched() + r.getInsufficientData() + r.getHistoricalV1()
                + r.getHistoricalMock() + r.getMissingOnChain() + r.getContentMismatch()
                + r.getUnverifiable();
        assertEquals(r.getCheckedGlobal(), sum, "7 类之和必须等于全局总数");
        assertEquals(3, r.getCheckedGlobal());
        assertEquals(1, r.getHistoricalMock());
        assertEquals(2, r.getMissingOnChain());
    }

    @Test
    @DisplayName("分页：明细按 offset 切片，但计数始终是全局")
    void reconcile_paginatesDetailsButKeepsGlobalCounts() {
        stubAll(List.of(onChainReport, dbOnlyReport));
        when(blockchainService.queryReportSnapshot(anyLong())).thenReturn(null);

        var r = chainReconciliationService.reconcile(2, 1);

        assertEquals(2, r.getCheckedGlobal(), "计数为全局");
        assertEquals(1, r.getCheckedPage(), "每页 1 条，第 2 页恰好 1 条");
        assertEquals(2, r.getMissingOnChain(), "缺失仍是全局数");
        assertEquals(1, r.getDetails().size());
    }

    @Test
    @DisplayName("Mock 环境：链上恒 null 时不把全部报告误判为不一致")
    void reconcile_treatsMockNullAsMissingNotMismatch() {
        stubAll(List.of(onChainReport));
        when(blockchainService.queryReportSnapshot(anyLong())).thenReturn(null);

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(0, result.getContentMismatch());
        assertEquals(1, result.getMissingOnChain());
    }

    @Test
    @DisplayName("死信列表-只返回 DEAD 状态的消息，并带业务归属")
    void listDeadLetters_returnsOnlyDeadMessages() {
        OutboxMessage dead = OutboxMessage.builder()
                .messageId("m-1")
                .aggregateType("REPORT")
                .aggregateId(1L)
                .topic("CreateCarbonReportV2")
                .payload("{}")
                .status("DEAD")
                .retryCount(5)
                .errorMessage("peer unreachable")
                .build();
        dead.setId(1L);
        when(outboxMessageRepository.findByStatusAndDeletedFalseOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(dead), PageRequest.of(0, 10), 1));

        Page<OutboxMessage> page = chainReconciliationService.listDeadLetters(1, 10);

        assertEquals(1, page.getTotalElements());
        assertEquals("DEAD", page.getContent().get(0).getStatus());
        assertEquals(1L, page.getContent().get(0).getAggregateId());
    }

    @Test
    @DisplayName("死信重放-把 DEAD 消息复位为 PENDING 并清零重试计数，交由调度器重投")
    void replayDeadLetter_resetsToPending() {
        OutboxMessage dead = OutboxMessage.builder()
                .messageId("m-2")
                .aggregateType("REPORT")
                .aggregateId(2L)
                .topic("CreateCarbonReportV2")
                .payload("{}")
                .status("DEAD")
                .retryCount(5)
                .errorMessage("peer unreachable")
                .build();
        dead.setId(2L);
        when(outboxMessageRepository.findById(2L)).thenReturn(Optional.of(dead));
        when(outboxMessageRepository.save(any(OutboxMessage.class))).thenReturn(dead);

        OutboxMessage replayed = chainReconciliationService.replayDeadLetter(2L);

        assertNotNull(replayed);
        assertEquals("PENDING", replayed.getStatus());
        assertEquals(0, replayed.getRetryCount());
        assertTrue(replayed.getErrorMessage() == null || replayed.getErrorMessage().isBlank());
    }

    @Test
    @DisplayName("死信重放-非 DEAD 状态拒绝重放，避免误把在途消息重复提交")
    void replayDeadLetter_rejectsNonDead() {
        OutboxMessage sent = OutboxMessage.builder()
                .messageId("m-3")
                .aggregateType("REPORT")
                .aggregateId(3L)
                .topic("CreateCarbonReportV2")
                .payload("{}")
                .status("SENT")
                .retryCount(0)
                .build();
        sent.setId(3L);
        when(outboxMessageRepository.findById(3L)).thenReturn(Optional.of(sent));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.oaiss.chain.exception.BusinessException.class,
                () -> chainReconciliationService.replayDeadLetter(3L));
    }
}
