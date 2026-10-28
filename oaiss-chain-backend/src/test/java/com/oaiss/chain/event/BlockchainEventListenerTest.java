package com.oaiss.chain.event;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.service.BlockchainServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockchainEventListenerTest {

    @Mock
    private BlockchainServicePort blockchainService;

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private com.oaiss.chain.service.OutboxService outboxService;

    @InjectMocks
    private BlockchainEventListener blockchainEventListener;

    private CarbonReport testReport;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testReport = CarbonReport.builder()
                .enterpriseId(1L)
                .submitterId(1L)
                .accountingPeriod("2024")
                .title("2024年度碳排放报告")
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
    }

    @Test
    @DisplayName("handleReportCertified: 成功纳管至Outbox发件箱并触发处理")
    void testHandleReportCertifiedSuccess() {
        ReportCertifiedEvent event = new ReportCertifiedEvent(this, 101L, "{\"totalEmission\":100}");
        com.oaiss.chain.entity.OutboxMessage outboxMsg = com.oaiss.chain.entity.OutboxMessage.builder()
                .messageId("msg001")
                .aggregateType("REPORT")
                .aggregateId(101L)
                .build();
        outboxMsg.setId(1L);

        when(outboxService.saveOutboxMessage(eq("REPORT"), eq(101L), eq("CreateCarbonReport"), anyString()))
                .thenReturn(outboxMsg);

        blockchainEventListener.handleReportCertified(event);

        verify(outboxService, times(1)).saveOutboxMessage(eq("REPORT"), eq(101L), eq("CreateCarbonReport"), anyString());
        verify(outboxService, times(1)).processMessage(eq(1L));
    }

    @Test
    @DisplayName("handleReportCertified: 发件箱服务异常时安全降级捕获日志")
    void testHandleReportCertifiedExceptionCaught() {
        ReportCertifiedEvent event = new ReportCertifiedEvent(this, 101L, "{\"totalEmission\":100}");
        when(outboxService.saveOutboxMessage(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Outbox DB error"));

        blockchainEventListener.handleReportCertified(event);

        verify(outboxService, times(1)).saveOutboxMessage(any(), any(), any(), any());
        verify(outboxService, never()).processMessage(any());
    }

    @Test
    @DisplayName("handleTradeCompleted: 成功纳管至Outbox发件箱并触发处理")
    void testHandleTradeCompletedSuccess() {
        TradeCompletedEvent event = new TradeCompletedEvent(this, 201L, "{\"tradeNo\":\"TX20240101001\"}");
        com.oaiss.chain.entity.OutboxMessage outboxMsg = com.oaiss.chain.entity.OutboxMessage.builder()
                .messageId("msg002")
                .aggregateType("TRADE")
                .aggregateId(201L)
                .build();
        outboxMsg.setId(2L);

        when(outboxService.saveOutboxMessage(eq("TRADE"), eq(201L), eq("CreateTradeRecord"), anyString()))
                .thenReturn(outboxMsg);

        blockchainEventListener.handleTradeCompleted(event);

        verify(outboxService, times(1)).saveOutboxMessage(eq("TRADE"), eq(201L), eq("CreateTradeRecord"), anyString());
        verify(outboxService, times(1)).processMessage(eq(2L));
    }

    @Test
    @DisplayName("handleTradeCompleted: 发件箱服务异常时安全降级捕获日志")
    void testHandleTradeCompletedExceptionCaught() {
        TradeCompletedEvent event = new TradeCompletedEvent(this, 201L, "{\"tradeNo\":\"TX20240101001\"}");
        when(outboxService.saveOutboxMessage(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Outbox DB error"));

        blockchainEventListener.handleTradeCompleted(event);

        verify(outboxService, times(1)).saveOutboxMessage(any(), any(), any(), any());
        verify(outboxService, never()).processMessage(any());
    }
}
