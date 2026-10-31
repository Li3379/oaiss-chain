package com.oaiss.chain.service;

import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.TradeAudit;
import com.oaiss.chain.enums.TradeStatusEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.TradeAuditRepository;
import com.oaiss.chain.repository.TransactionRepository;
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
 * S3 接缝：成交审核状态机。
 *
 * <p>存在理由：撮合成交原本在单事务内一口气从「创建」走到「COMPLETED + 额度转移 +
 * 碳币结算 + 发事件上链」，整个过程没有任何人工裁决的空间。审核功能要求把
 * 「结算」从「成交」里拆出来：待审的成交持有冻结资产，审核通过才真正结算，
 * 驳回则原路退回。
 *
 * <p>本接缝必须守住的四条不变量：
 * <ol>
 *   <li><b>只有待审可裁决</b>：已通过/已驳回的交易再次裁决必须失败（幂等保护的镜像：
 *       不幂等，而是明确拒绝重复裁决，避免二次结算）</li>
 *   <li><b>驳回必须全额回滚</b>：额度与碳币都回到成交前状态，分毫不差</li>
 *   <li><b>驳回必须扣分并归因</b>：信誉事件要能指回这笔交易（relatedTradeId），
 *       否则「这笔交易害你扣了分」在系统里无法回答</li>
 *   <li><b>裁决必须留痕</b>：审核人、时间、意见、命中的风险理由全部落库</li>
 * </ol>
 *
 * <p>会漏掉的：与异步通知/上链事件的时序耦合——那需要集成测试，
 * 在 S4 单独覆盖。
 */
class TradeAuditServiceTest {

    private TransactionRepository transactionRepository;
    private TradeAuditRepository tradeAuditRepository;
    private EnterpriseRepository enterpriseRepository;
    private QuotaLedgerService quotaLedgerService;
    private CreditScoreService creditScoreService;
    private CarbonCoinService carbonCoinService;
    private NotificationService notificationService;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private TradeAuditService auditService;

    private Transaction trade;
    private Enterprise buyer;
    private Enterprise seller;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        tradeAuditRepository = mock(TradeAuditRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);
        quotaLedgerService = mock(QuotaLedgerService.class);
        creditScoreService = mock(CreditScoreService.class);
        carbonCoinService = mock(CarbonCoinService.class);
        notificationService = mock(NotificationService.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);

        auditService = new TradeAuditService(transactionRepository, tradeAuditRepository,
                enterpriseRepository, quotaLedgerService, creditScoreService,
                carbonCoinService, notificationService, eventPublisher);

        buyer = new Enterprise();
        buyer.setId(20L);
        buyer.setUserId(2L);
        buyer.setCarbonQuota(new BigDecimal("5000"));
        buyer.setCarbonTradable(new BigDecimal("5000"));
        buyer.setCarbonUsed(BigDecimal.ZERO);
        buyer.setCarbonFrozen(BigDecimal.ZERO);

        seller = new Enterprise();
        seller.setId(10L);
        seller.setUserId(1L);
        seller.setCarbonQuota(new BigDecimal("5000"));
        seller.setCarbonTradable(new BigDecimal("5000"));
        seller.setCarbonUsed(BigDecimal.ZERO);
        seller.setCarbonFrozen(BigDecimal.ZERO);

        trade = new Transaction();
        trade.setId(100L);
        trade.setTradeNo("T100");
        trade.setTradeType(1);
        trade.setSellerId(1L);
        trade.setBuyerId(2L);
        trade.setQuantity(new BigDecimal("200"));
        trade.setUnitPrice(new BigDecimal("60"));
        trade.setTotalAmount(new BigDecimal("12000"));
        trade.setStatus(TradeStatusEnum.PROCESSING.getCode());
        trade.setAuditStatus(TradeAudit.AUDIT_PENDING);

        when(transactionRepository.findById(100L)).thenReturn(Optional.of(trade));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(tradeAuditRepository.save(any(TradeAudit.class))).thenAnswer(i -> i.getArgument(0));
        when(enterpriseRepository.findById(10L)).thenReturn(Optional.of(seller));
        when(enterpriseRepository.findById(20L)).thenReturn(Optional.of(buyer));
        // 交易里存的是 userId，额度账本按 enterpriseId 记账，实现需换算
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(seller));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.of(buyer));
    }

    @Test
    @DisplayName("审核通过：状态置已完成、审核态置通过、审核记录落库、并发上链事件")
    void approve_completesTrade_andRecordsAudit() {
        auditService.approve(100L, 7L, "核对无误");

        assertEquals(TradeStatusEnum.COMPLETED.getCode(), trade.getStatus(), "交易应置已完成");
        assertEquals(TradeAudit.AUDIT_APPROVED, trade.getAuditStatus(), "审核态应置通过");
        assertEquals("核对无误", trade.getAuditRemark());

        ArgumentCaptor<TradeAudit> auditCaptor = ArgumentCaptor.forClass(TradeAudit.class);
        verify(tradeAuditRepository).save(auditCaptor.capture());
        TradeAudit record = auditCaptor.getValue();
        assertEquals(TradeAudit.AUDIT_APPROVED, record.getAuditStatus());
        assertEquals(7L, record.getAuditorId());
        assertNotNull(record.getAuditedAt());
        assertEquals(100L, record.getTradeId());

        // 通过后才允许上链：事件必须在审核通过路径里发布
        verify(eventPublisher).publishEvent(any(com.oaiss.chain.event.TradeCompletedEvent.class));
    }

    @Test
    @DisplayName("审核驳回：额度与碳币全额回滚、扣信誉分并归因到该笔交易")
    void reject_rollsBack_andDeductsCreditAttributedToTrade() {
        auditService.reject(100L, 7L, "成交价异常，疑似对敲", TradeAudit.REASON_PRICE_DEVIATION);

        assertEquals(TradeStatusEnum.CANCELLED.getCode(), trade.getStatus(), "驳回后交易应置已取消");
        assertEquals(TradeAudit.AUDIT_REJECTED, trade.getAuditStatus());

        // 额度原路退回：卖方被扣减的额度回滚、买方买入的额度回滚
        verify(quotaLedgerService).rollback(eq(10L), eq(new BigDecimal("200")), anyString(), eq(7L), anyString(), eq(100L));
        verify(quotaLedgerService).rollback(eq(20L), eq(new BigDecimal("200")), anyString(), eq(7L), anyString(), eq(100L));

        // 碳币双向退回
        verify(carbonCoinService).refundRejectedTrade(eq(100L), eq(2L), eq(1L), eq(new BigDecimal("12000")));

        // 信誉扣分必须能指回这笔交易，且买卖双方各扣一次
        // （异常成交中双方都参与价格形成，只罚一方会留下靠对手方低信誉规避的口子）
        verify(creditScoreService).deductPointsForTrade(
                eq(10L), eq(6), anyString(), eq(7L), eq(100L));   // seller enterpriseId=10
        verify(creditScoreService).deductPointsForTrade(
                eq(20L), eq(6), anyString(), eq(7L), eq(100L));   // buyer enterpriseId=20
    }

    @Test
    @DisplayName("重复裁决被拒绝：已通过的交易不能再驳回")
    void approveThenReject_isRejected() {
        trade.setAuditStatus(TradeAudit.AUDIT_APPROVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditService.reject(100L, 7L, "重复驳回", TradeAudit.REASON_LARGE_AMOUNT));

        assertTrue(ex.getMessage().contains("审核") || ex.getMessage().contains("裁决"),
                "错误信息应说明该交易已裁决，实际: " + ex.getMessage());

        // 不得产生任何回滚与扣分——这是防重复结算的核心
        verify(quotaLedgerService, never()).rollback(anyLong(), any(), anyString(), anyLong(), anyString(), anyLong());
        verify(creditScoreService, never()).deductPointsForTrade(anyLong(), anyInt(), anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("非待审状态不得裁决：无需审核的交易无法驳回")
    void reject_tradeNotRequiringAudit_isRejected() {
        trade.setAuditStatus(TradeAudit.AUDIT_NOT_REQUIRED);

        assertThrows(BusinessException.class,
                () -> auditService.reject(100L, 7L, "误操作", TradeAudit.REASON_LARGE_AMOUNT));

        verify(quotaLedgerService, never()).rollback(anyLong(), any(), anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("交易不存在时报错")
    void audit_missingTrade_throws() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> auditService.approve(999L, 7L, "ok"));
    }

    @Test
    @DisplayName("审核意见不能为空（裁决必须留痕可解释）")
    void audit_blankRemark_isRejected() {
        assertThrows(BusinessException.class,
                () -> auditService.approve(100L, 7L, "   "));

        verify(tradeAuditRepository, never()).save(any(TradeAudit.class));
    }

    @Test
    @DisplayName("创建待审记录：命中风险理由全部落库，供审核员作为判断依据")
    void createPendingAudit_persistsRiskReasons() {
        TradeAudit pending = auditService.createPendingAudit(100L,
                java.util.List.of("LARGE_AMOUNT(1500>=1000)", "PRICE_DEVIATION(30%>20%)"));

        assertEquals(TradeAudit.AUDIT_PENDING, pending.getAuditStatus());
        assertEquals(100L, pending.getTradeId());
        assertTrue(pending.getRiskReasons().contains("LARGE_AMOUNT"),
                "风险理由应落库，实际: " + pending.getRiskReasons());
        assertTrue(pending.getRiskReasons().contains("PRICE_DEVIATION"));
    }
}