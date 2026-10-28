package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.OutboxMessage;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.OutboxMessageRepository;
import com.oaiss.chain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private BlockchainServicePort blockchainService;

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private OutboxService outboxService;

    private CarbonReport testReport;
    private Transaction testTransaction;
    private OutboxMessage reportMessage;
    private OutboxMessage tradeMessage;

    @BeforeEach
    void setUp() {
        testReport = CarbonReport.builder()
                .enterpriseId(1L)
                .submitterId(1L)
                .accountingPeriod("2024")
                .title("2024年度报告")
                .reportType(2)
                .emissionData("{\"totalEmission\":100}")
                .status(3)
                .build();
        testReport.setId(101L);

        testTransaction = Transaction.builder()
                .tradeNo("TX20240101001")
                .sellerId(1L)
                .buyerId(2L)
                .status(2)
                .build();
        testTransaction.setId(201L);

        reportMessage = OutboxMessage.builder()
                .messageId("msg-report-101")
                .aggregateType(OutboxService.AGGREGATE_REPORT)
                .aggregateId(101L)
                .topic("CreateCarbonReport")
                .payload("{\"totalEmission\":100}")
                .status(OutboxService.STATUS_PENDING)
                .retryCount(0)
                .build();
        reportMessage.setId(1L);

        tradeMessage = OutboxMessage.builder()
                .messageId("msg-trade-201")
                .aggregateType(OutboxService.AGGREGATE_TRADE)
                .aggregateId(201L)
                .topic("CreateTradeRecord")
                .payload("{\"tradeNo\":\"TX20240101001\"}")
                .status(OutboxService.STATUS_PENDING)
                .retryCount(0)
                .build();
        tradeMessage.setId(2L);
    }

    @Test
    @DisplayName("saveOutboxMessage-成功在本地事务中持久化PENDING消息")
    void testSaveOutboxMessageSuccess() {
        when(outboxMessageRepository.save(any(OutboxMessage.class))).thenAnswer(inv -> {
            OutboxMessage msg = inv.getArgument(0);
            msg.setId(10L);
            return msg;
        });

        OutboxMessage result = outboxService.saveOutboxMessage(
                OutboxService.AGGREGATE_REPORT, 101L, "CreateCarbonReport", "{}");

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(OutboxService.STATUS_PENDING, result.getStatus());
        assertEquals(0, result.getRetryCount());
        assertNotNull(result.getMessageId());
        verify(outboxMessageRepository, times(1)).save(any(OutboxMessage.class));
    }

    @Test
    @DisplayName("processMessage-报告存证成功回写哈希并标记SENT")
    void testProcessMessageReportSuccess() {
        when(outboxMessageRepository.findById(1L)).thenReturn(Optional.of(reportMessage));
        when(blockchainService.commitReportToChain(eq(101L), anyString())).thenReturn("tx_report_hash_101");
        when(carbonReportRepository.findById(101L)).thenReturn(Optional.of(testReport));
        when(outboxMessageRepository.save(any(OutboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean ok = outboxService.processMessage(1L);

        assertTrue(ok);
        assertEquals(OutboxService.STATUS_SENT, reportMessage.getStatus());
        assertNull(reportMessage.getErrorMessage());
        assertEquals("tx_report_hash_101", testReport.getBlockchainTxHash());
        assertNotNull(testReport.getOnChainAt());
        verify(carbonReportRepository, times(1)).save(testReport);
        verify(outboxMessageRepository, times(1)).save(reportMessage);
    }

    @Test
    @DisplayName("processMessage-交易存证成功回写哈希并标记SENT")
    void testProcessMessageTradeSuccess() {
        when(outboxMessageRepository.findById(2L)).thenReturn(Optional.of(tradeMessage));
        when(blockchainService.commitTradeToChain(eq(201L), anyString())).thenReturn("tx_trade_hash_201");
        when(transactionRepository.findById(201L)).thenReturn(Optional.of(testTransaction));
        when(outboxMessageRepository.save(any(OutboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean ok = outboxService.processMessage(2L);

        assertTrue(ok);
        assertEquals(OutboxService.STATUS_SENT, tradeMessage.getStatus());
        assertNull(tradeMessage.getErrorMessage());
        assertEquals("tx_trade_hash_201", testTransaction.getBlockchainTxHash());
        verify(transactionRepository, times(1)).save(testTransaction);
        verify(outboxMessageRepository, times(1)).save(tradeMessage);
    }

    @Test
    @DisplayName("processMessage-上链异常时触发指数退避重试并标记FAILED")
    void testProcessMessageRetryBackoff() {
        when(outboxMessageRepository.findById(1L)).thenReturn(Optional.of(reportMessage));
        when(blockchainService.commitReportToChain(eq(101L), anyString()))
                .thenThrow(new RuntimeException("Fabric network timeout"));
        when(outboxMessageRepository.save(any(OutboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean ok = outboxService.processMessage(1L);

        assertFalse(ok);
        assertEquals(OutboxService.STATUS_FAILED, reportMessage.getStatus());
        assertEquals(1, reportMessage.getRetryCount());
        assertNotNull(reportMessage.getNextRetryAt());
        assertTrue(reportMessage.getNextRetryAt().isAfter(LocalDateTime.now()));
        assertEquals("Fabric network timeout", reportMessage.getErrorMessage());
        verify(outboxMessageRepository, times(1)).save(reportMessage);
    }

    @Test
    @DisplayName("processMessage-达到5次重试上限转入DEAD死信队列")
    void testProcessMessageMaxRetriesToDeadLetter() {
        reportMessage.setRetryCount(4); // 下一次为第5次
        when(outboxMessageRepository.findById(1L)).thenReturn(Optional.of(reportMessage));
        when(blockchainService.commitReportToChain(eq(101L), anyString()))
                .thenThrow(new RuntimeException("Persistent chaincode error"));
        when(outboxMessageRepository.save(any(OutboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean ok = outboxService.processMessage(1L);

        assertFalse(ok);
        assertEquals(OutboxService.STATUS_DEAD, reportMessage.getStatus());
        assertEquals(5, reportMessage.getRetryCount());
        assertNull(reportMessage.getNextRetryAt());
        assertEquals("Persistent chaincode error", reportMessage.getErrorMessage());
        verify(outboxMessageRepository, times(1)).save(reportMessage);
    }

    @Test
    @DisplayName("scheduleRetryPendingMessages-扫描重试队列并自动处理")
    void testScheduleRetryPendingMessages() {
        when(outboxMessageRepository.findPendingOrRetryableMessages(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(reportMessage));
        when(outboxMessageRepository.findById(1L)).thenReturn(Optional.of(reportMessage));
        when(blockchainService.commitReportToChain(eq(101L), anyString())).thenReturn("tx_recovered_hash");
        when(carbonReportRepository.findById(101L)).thenReturn(Optional.of(testReport));
        when(outboxMessageRepository.save(any(OutboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        outboxService.scheduleRetryPendingMessages();

        verify(outboxMessageRepository, times(1)).findPendingOrRetryableMessages(any(), any());
        assertEquals(OutboxService.STATUS_SENT, reportMessage.getStatus());
    }

    @Test
    @DisplayName("scheduleRetryPendingMessages-无待处理消息时安全返回")
    void testScheduleRetryPendingMessagesEmpty() {
        when(outboxMessageRepository.findPendingOrRetryableMessages(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        outboxService.scheduleRetryPendingMessages();

        verify(outboxMessageRepository, times(1)).findPendingOrRetryableMessages(any(), any());
        verify(blockchainService, never()).commitReportToChain(anyLong(), anyString());
    }
}
