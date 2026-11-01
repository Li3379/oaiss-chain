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
                transactionRepository, objectMapper);
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
    @DisplayName("已标记为脏数据的报告不再计入缺失，单独归类 HISTORICAL_MOCK")
    void reconcile_classifiesMarkedMockRecordsSeparately() {
        onChainReport.setChainMark("HISTORICAL_MOCK");
        onChainReport.setChainMarkReason("Mock 模式生成，链上不存在");
        when(carbonReportRepository.findByBlockchainTxHashIsNotNullAndDeletedFalse(any()))
                .thenReturn(new PageImpl<>(List.of(onChainReport), PageRequest.of(0, 10), 1));

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getHistoricalMock());
        assertEquals(0, result.getMissingOnChain(), "已标记的脏数据不应继续报缺失");
        assertEquals("HISTORICAL_MOCK", result.getDetails().get(0).get("status"));
        // 已标记的记录无须再去查链——那正是标记的意义：省掉必然失败的往返
        verify(blockchainService, never()).queryReportSnapshot(anyLong());
    }

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

    @Test
    @DisplayName("对账-链上与库内一致时归类为 MATCHED")
    void reconcile_marksMatchedWhenChainSnapshotAgrees() {
        when(carbonReportRepository.findByBlockchainTxHashIsNotNullAndDeletedFalse(any()))
                .thenReturn(new PageImpl<>(List.of(onChainReport), PageRequest.of(0, 10), 1));
        when(blockchainService.queryReportSnapshot(1L))
                .thenReturn("{\"reportId\":\"1\",\"totalEmission\":\"100.00\",\"txHash\":\"tx-001\"}");

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getMatched());
        assertEquals(0, result.getMissingOnChain());
        assertEquals(0, result.getContentMismatch());
    }

    @Test
    @DisplayName("对账-库内有 txHash 但链上查无记录时归类为 MISSING_ON_CHAIN")
    void reconcile_marksMissingWhenChainHasNoRecord() {
        when(carbonReportRepository.findByBlockchainTxHashIsNotNullAndDeletedFalse(any()))
                .thenReturn(new PageImpl<>(List.of(onChainReport), PageRequest.of(0, 10), 1));
        when(blockchainService.queryReportSnapshot(1L)).thenReturn(null);

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getMissingOnChain());
        assertEquals(1, result.getDetails().size());
        assertEquals("MISSING_ON_CHAIN", result.getDetails().get(0).get("status"));
    }

    @Test
    @DisplayName("对账-链上总量与库内不一致时归类为 CONTENT_MISMATCH 并给出两侧数值")
    void reconcile_marksContentMismatch() {
        when(carbonReportRepository.findByBlockchainTxHashIsNotNullAndDeletedFalse(any()))
                .thenReturn(new PageImpl<>(List.of(onChainReport), PageRequest.of(0, 10), 1));
        when(blockchainService.queryReportSnapshot(1L))
                .thenReturn("{\"reportId\":\"1\",\"totalEmission\":\"150.00\",\"txHash\":\"tx-001\"}");

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(1, result.getContentMismatch());
        assertEquals("100.00", result.getDetails().get(0).get("dbTotalEmission"));
        assertEquals("150.00", result.getDetails().get(0).get("chainTotalEmission"));
    }

    @Test
    @DisplayName("对账-链上返回空数组（Mock 环境）时不把全部报告误判为不一致")
    void reconcile_treatsMockNullAsMissingNotMismatch() {
        when(carbonReportRepository.findByBlockchainTxHashIsNotNullAndDeletedFalse(any()))
                .thenReturn(new PageImpl<>(List.of(onChainReport), PageRequest.of(0, 10), 1));
        when(blockchainService.queryReportSnapshot(anyLong())).thenReturn(null);

        var result = chainReconciliationService.reconcile(1, 10);

        assertEquals(0, result.getContentMismatch());
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
