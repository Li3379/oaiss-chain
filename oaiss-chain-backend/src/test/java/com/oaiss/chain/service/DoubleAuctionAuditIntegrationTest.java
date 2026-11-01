package com.oaiss.chain.service;

import com.oaiss.chain.aop.AuditLogWriter;
import com.oaiss.chain.dto.CarbonCoinAccountResponse;
import com.oaiss.chain.entity.AuctionOrder;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.MatchingResult;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.AuctionOrderStatusEnum;
import com.oaiss.chain.repository.AuctionOrderRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.MatchingResultRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S4 接缝：审核分流接入撮合链路。
 *
 * <p>存在理由：S1/S2/S3 建好了账本、分流器、审核状态机，但它们都还站在交易链路外面。
 * 本接缝是三者真正被用起来的地方，也是唯一有破坏性的改动——它要改写
 * {@code executeMatching} 里的结算顺序。
 *
 * <p>必须同时守住的两侧：
 * <ul>
 *   <li>无需审核的成交：行为必须与接入前**完全一致**（额度/碳币当场结算、当场发上链事件）</li>
 *   <li>需要审核的成交：**绝不结算、绝不上链**，只落待审态并生成审核工单</li>
 * </ul>
 * 第二条如果漏掉，就等于把「待审」做成了账面谎言——看起来在审，钱和额度已经走了。
 */
@ExtendWith(MockitoExtension.class)
class DoubleAuctionAuditIntegrationTest {

    @Mock private AuctionOrderRepository auctionOrderRepository;
    @Mock private MatchingResultRepository matchingResultRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private CarbonCoinService carbonCoinService;
    @Mock private CreditScoreService creditScoreService;
    @Mock private AuditLogWriter auditLogWriter;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private QuotaLedgerService quotaLedgerService;
    @Mock private TradeRiskAssessor tradeRiskAssessor;
    @Mock private TradeAuditService tradeAuditService;
    @Mock private PriceLimitService priceLimitService;
    @Mock private CertificateGateService certificateGateService;

    private DoubleAuctionService service;

    private AuctionOrder buyOrder;
    private AuctionOrder sellOrder;

    @BeforeEach
    void setUp() {
        service = new DoubleAuctionService(auctionOrderRepository, matchingResultRepository,
                transactionRepository, enterpriseRepository, userRepository, notificationService,
                carbonCoinService, creditScoreService, auditLogWriter, eventPublisher,
                quotaLedgerService, tradeRiskAssessor, tradeAuditService, priceLimitService, certificateGateService);

        // 证书门禁：本测试聚焦审核分流，双方默认持有效证书
        org.mockito.Mockito.lenient().when(certificateGateService.isEnterpriseAdmitted(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);

        buyOrder = AuctionOrder.builder()
                .orderNo("B001").userId(1L).direction(1)
                .quantity(new BigDecimal("100.00")).price(new BigDecimal("60.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode()).build();
        buyOrder.setId(1L);

        sellOrder = AuctionOrder.builder()
                .orderNo("S001").userId(2L).direction(2)
                .quantity(new BigDecimal("100.00")).price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode()).build();
        sellOrder.setId(2L);

        Enterprise buyerEnt = Enterprise.builder()
                .userId(1L).carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500"))
                .carbonUsed(new BigDecimal("500")).build();
        buyerEnt.setId(1L);
        Enterprise sellerEnt = Enterprise.builder()
                .userId(2L).carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500"))
                .carbonUsed(new BigDecimal("500")).build();
        sellerEnt.setId(2L);

        lenient().when(creditScoreService.checkTradePermission(anyLong())).thenReturn(true);
        lenient().when(creditScoreService.getScoresByUserIds(any())).thenReturn(Collections.emptyMap());
        lenient().when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        lenient().when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));
        lenient().when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(i -> {
            MatchingResult m = i.getArgument(0);
            if (m.getId() == null) m.setId(1L);
            return m;
        });
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            if (t.getId() == null) t.setId(100L);
            return t;
        });
        lenient().when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(buyerEnt));
        lenient().when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(sellerEnt));
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().realName("Buyer").build()));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().realName("Seller").build()));
        lenient().when(carbonCoinService.getOrCreateAccount(anyLong()))
                .thenReturn(CarbonCoinAccountResponse.builder().balance(new BigDecimal("10000")).status(1).build());
    }

    @Test
    @DisplayName("无需审核：维持原行为——当场结算、当场发上链事件")
    void noAudit_settlesImmediately_andPublishesEvent() {
        when(tradeRiskAssessor.assess(any(Transaction.class), any(), any(), any(), anyBoolean()))
                .thenReturn(new TradeRiskAssessor.Decision(false, java.util.List.of()));

        service.executeMatching();

        // 额度与碳币当场结算（原有路径不变）
        verify(carbonCoinService).deductFrozenBalance(anyLong(), any(), any(), anyLong());
        verify(carbonCoinService).sellQuota(anyLong(), any(), any(), anyLong());
        // 上链事件当场发出
        verify(eventPublisher).publishEvent(any(com.oaiss.chain.event.TradeCompletedEvent.class));
        // 不生成审核工单
        verify(tradeAuditService, never()).createPendingAudit(anyLong(), any());
    }

    @Test
    @DisplayName("需要审核：绝不结算、绝不上链，只落待审态并生成工单")
    void requiresAudit_doesNotSettle_andCreatesPendingAudit() {
        when(tradeRiskAssessor.assess(any(Transaction.class), any(), any(), any(), anyBoolean()))
                .thenReturn(new TradeRiskAssessor.Decision(true,
                        java.util.List.of("LARGE_AMOUNT(1500>=1000)")));

        service.executeMatching();

        // 核心断言：待审期间钱和额度都不能动
        verify(carbonCoinService, never()).deductFrozenBalance(anyLong(), any(), any(), anyLong());
        verify(carbonCoinService, never()).sellQuota(anyLong(), any(), any(), anyLong());
        // 未审不上链：链上存证不可修改，先上链会让链上数据成为半成品
        verify(eventPublisher, never()).publishEvent(any(com.oaiss.chain.event.TradeCompletedEvent.class));

        // 必须生成工单，否则"待审"无从审核
        ArgumentCaptor<java.util.List<String>> reasonsCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(tradeAuditService).createPendingAudit(anyLong(), reasonsCaptor.capture());
        assertTrue(reasonsCaptor.getValue().stream().anyMatch(r -> r.startsWith("LARGE_AMOUNT")));
    }

    @Test
    @DisplayName("待审成交的交易状态是处理中，不是已完成")
    void requiresAudit_tradeStatusIsProcessingNotCompleted() {
        when(tradeRiskAssessor.assess(any(Transaction.class), any(), any(), any(), anyBoolean()))
                .thenReturn(new TradeRiskAssessor.Decision(true, java.util.List.of("LOW_CREDIT")));

        service.executeMatching();

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(captor.capture());
        Transaction saved = captor.getValue();

        assertEquals(com.oaiss.chain.enums.TradeStatusEnum.PROCESSING.getCode(), saved.getStatus(),
                "待审成交应为处理中，置已完成会让人误以为已履约");
        assertEquals(com.oaiss.chain.entity.TradeAudit.AUDIT_PENDING, saved.getAuditStatus());
    }

    @Test
    @DisplayName("审核通过与驳回走审核服务，而非撮合链路二次结算")
    void auditDecisions_delegatedToAuditService() {
        when(tradeRiskAssessor.assess(any(Transaction.class), any(), any(), any(), anyBoolean()))
                .thenReturn(new TradeRiskAssessor.Decision(true, java.util.List.of("WASH_TRADING")));

        service.executeMatching();

        // 撮合只负责建立工单；结算发生在审核通过时（S3 的 approve）
        verify(tradeAuditService).createPendingAudit(anyLong(), any());
        verify(tradeAuditService, never()).approve(anyLong(), anyLong(), anyString());
        verify(tradeAuditService, never()).reject(anyLong(), anyLong(), anyString(), anyString());
    }
}