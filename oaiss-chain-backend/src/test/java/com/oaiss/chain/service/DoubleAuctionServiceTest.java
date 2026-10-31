package com.oaiss.chain.service;

import com.oaiss.chain.aop.AuditLogWriter;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.AuctionOrderRequest;
import com.oaiss.chain.dto.AuctionOrderResponse;
import com.oaiss.chain.dto.CarbonCoinAccountResponse;
import com.oaiss.chain.dto.MatchingResultResponse;
import com.oaiss.chain.entity.AuctionOrder;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.MatchingResult;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.AuctionOrderStatusEnum;
import com.oaiss.chain.exception.TradeException;
import com.oaiss.chain.repository.AuctionOrderRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.MatchingResultRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoubleAuctionServiceTest {

    @Mock
    private AuctionOrderRepository auctionOrderRepository;

    @Mock
    private MatchingResultRepository matchingResultRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EnterpriseRepository enterpriseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CarbonCoinService carbonCoinService;

    @Mock
    private CreditScoreService creditScoreService;

    @Mock
    private AuditLogWriter auditLogWriter;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    // S4 新增依赖：撮合链路现在会先做风险分流，再决定当场结算还是转人工审核。
    // 本类既有 50 个用例验证的是「无需审核时的结算行为」，故默认让分流器一律放行，
    // 使原断言口径不变；审核分支由 DoubleAuctionAuditIntegrationTest 单独覆盖。
    @Mock
    private QuotaLedgerService quotaLedgerService;

    @Mock
    private TradeRiskAssessor tradeRiskAssessor;

    @Mock
    private TradeAuditService tradeAuditService;

    @InjectMocks
    private DoubleAuctionService doubleAuctionService;

    private JwtUserDetails currentUser;
    private Enterprise testEnterprise;
    private AuctionOrderRequest orderRequest;

    @BeforeEach
    void setUp() {
        currentUser = new JwtUserDetails();
        currentUser.setUserId(1L);
        currentUser.setUsername("testuser");

        testEnterprise = Enterprise.builder()
                .userId(1L)
                .carbonQuota(new BigDecimal("1000.00"))
                .carbonTradable(new BigDecimal("500.00"))
                .carbonUsed(new BigDecimal("500.00"))
                .build();
        testEnterprise.setId(1L);

        orderRequest = new AuctionOrderRequest();
        orderRequest.setDirection(1);
        orderRequest.setQuantity(new BigDecimal("100.00"));
        orderRequest.setPrice(new BigDecimal("50.00"));

        // 默认信誉分正常放行、无信誉记录按中性分排序；限制态与同价排序由各用例单独覆写
        lenient().when(creditScoreService.checkTradePermission(anyLong())).thenReturn(true);
        lenient().when(creditScoreService.getScoresByUserIds(any())).thenReturn(Collections.emptyMap());

        // 默认风险分流放行：本类验证的是「无需审核时」的既有结算行为，
        // 保持原断言口径不变（审核分支由 DoubleAuctionAuditIntegrationTest 覆盖）
        lenient().when(tradeRiskAssessor.assess(any(Transaction.class), any(), any(), any(), anyBoolean()))
                .thenReturn(new TradeRiskAssessor.Decision(false, java.util.List.of()));
    }

    @Test
    @DisplayName("placeBuyOrder succeeds")
    void testPlaceBuyOrderSuccess() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonCoinService.getOrCreateAccount(1L)).thenReturn(CarbonCoinAccountResponse.builder()
                .userId(1L)
                .balance(new BigDecimal("10000.00"))
                .status(1)
                .build());
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> {
            AuctionOrder order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        AuctionOrderResponse response = doubleAuctionService.placeBuyOrder(currentUser, orderRequest);

        assertNotNull(response);
        assertEquals(1, response.getDirection());
        assertEquals(new BigDecimal("100.00"), response.getQuantity());
        assertEquals(new BigDecimal("50.00"), response.getPrice());
        verify(carbonCoinService, times(1)).freezeBalance(eq(1L), eq(new BigDecimal("5000.00")));
        verify(auctionOrderRepository, times(1)).save(any(AuctionOrder.class));
    }

    @Test
    @DisplayName("placeBuyOrder fails when enterprise is missing")
    void testPlaceBuyOrderFailEnterpriseNotFound() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));
        verify(auctionOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeBuyOrder fails when balance is insufficient")
    void testPlaceBuyOrderFailInsufficientBalance() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonCoinService.getOrCreateAccount(1L)).thenReturn(CarbonCoinAccountResponse.builder()
                .userId(1L)
                .balance(new BigDecimal("100.00"))
                .status(1)
                .build());

        assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));
        verify(auctionOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeBuyOrder rejects negative price before repository access")
    void testPlaceBuyOrderFailNegativePrice() {
        orderRequest.setPrice(new BigDecimal("-1.00"));

        TradeException ex = assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getCode());
        verify(enterpriseRepository, never()).findByUserId(any());
        verify(auctionOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeSellOrder succeeds")
    void testPlaceSellOrderSuccess() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> {
            AuctionOrder order = invocation.getArgument(0);
            order.setId(2L);
            return order;
        });

        AuctionOrderResponse response = doubleAuctionService.placeSellOrder(currentUser, orderRequest);

        assertNotNull(response);
        assertEquals(2, response.getDirection());
        assertEquals(new BigDecimal("400.00"), testEnterprise.getCarbonTradable());
        assertEquals(new BigDecimal("100.00"), testEnterprise.getCarbonFrozen());
        verify(enterpriseRepository, times(1)).save(testEnterprise);
        verify(auctionOrderRepository, times(1)).save(any(AuctionOrder.class));
    }

    @Test
    @DisplayName("placeSellOrder fails when quantity exceeds tradable quota")
    void testPlaceSellOrderFailInsufficientQuota() {
        orderRequest.setQuantity(new BigDecimal("1000.00"));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(TradeException.class, () ->
                doubleAuctionService.placeSellOrder(currentUser, orderRequest));
        verify(auctionOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeSellOrder rejects zero quantity before repository access")
    void testPlaceSellOrderFailZeroQuantity() {
        orderRequest.setQuantity(BigDecimal.ZERO);

        TradeException ex = assertThrows(TradeException.class, () ->
                doubleAuctionService.placeSellOrder(currentUser, orderRequest));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getCode());
        verify(enterpriseRepository, never()).findByUserId(any());
        verify(auctionOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeBuyOrder rejects when user already has active sell order (wash trading prevention)")
    void testPlaceBuyOrderRejectsWhenActiveSellOrderExists() {
        // 信誉闸门与自成交检查都先要定位到企业，因此必须提供企业桩；
        // 否则会先抛 4002「企业不存在」，测不到洗售拦截本身。
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(auctionOrderRepository.existsByUserIdAndDirectionAndStatusInAndDeletedFalse(eq(1L), eq(2), anyList()))
                .thenReturn(true);

        TradeException ex = assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("同一主体禁止同时买卖"));
        verify(auctionOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("placeSellOrder rejects when user already has active buy order (wash trading prevention)")
    void testPlaceSellOrderRejectsWhenActiveBuyOrderExists() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(auctionOrderRepository.existsByUserIdAndDirectionAndStatusInAndDeletedFalse(eq(1L), eq(1), anyList()))
                .thenReturn(true);

        TradeException ex = assertThrows(TradeException.class, () ->
                doubleAuctionService.placeSellOrder(currentUser, orderRequest));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("同一主体禁止同时买卖"));
        verify(auctionOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("credit gate precedes wash-trading check so a frozen account sees the real reason")
    void testCreditGatePrecedesWashTradingCheck() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(creditScoreService.checkTradePermission(1L)).thenReturn(false);
        // 刻意也把自成交条件设为 true：若闸门未前置，抛出的会是 PARAM_ERROR 而不是信誉限制。
        // 闸门生效时该桩不会被消费，故用 lenient 表明它是反证桩而非必需桩。
        lenient().when(auctionOrderRepository.existsByUserIdAndDirectionAndStatusInAndDeletedFalse(eq(1L), eq(2), anyList()))
                .thenReturn(true);

        TradeException ex = assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));

        assertEquals(ErrorCode.CREDIT_TRADE_RESTRICTED, ex.getCode());
        assertTrue(ex.getMessage().contains("信誉分过低"));
    }

    @Test
    @DisplayName("executeMatching blocks wash trading when buy and sell order belong to same user")
    void testExecuteMatchingBlocksWashTradingSameUser() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B20240101001")
                .userId(1L)
                .direction(1)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("60.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        // 卖单也是同一个用户 1L
        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S20240101001")
                .userId(1L)
                .direction(2)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));

        var results = doubleAuctionService.executeMatching();

        assertNotNull(results);
        assertTrue(results.isEmpty(), "同一主体的买单和卖单必须被撮合引擎强力阻断，不应产生撮合成交");
        verify(matchingResultRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(carbonCoinService, never()).deductFrozenBalance(any(), any(), any(), any());
        verify(carbonCoinService, never()).sellQuota(any(), any(), any(), any());
        verify(auditLogWriter, atLeastOnce()).saveAsync(any());
    }

    @Test
    @DisplayName("executeMatching returns empty list when there is no opportunity")
    void testExecuteMatchingNoOpportunity() {
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Collections.emptyList());
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Collections.emptyList());

        var results = doubleAuctionService.executeMatching();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("executeMatching settles one match")
    void testExecuteMatchingSuccess() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B20240101001")
                .userId(1L)
                .direction(1)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("60.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S20240101001")
                .userId(2L)
                .direction(2)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        Enterprise buyerEnterprise = Enterprise.builder()
                .userId(1L)
                .carbonQuota(new BigDecimal("1000.00"))
                .carbonTradable(new BigDecimal("500.00"))
                .carbonUsed(new BigDecimal("500.00"))
                .build();
        buyerEnterprise.setId(1L);

        Enterprise sellerEnterprise = Enterprise.builder()
                .userId(2L)
                .carbonQuota(new BigDecimal("1000.00"))
                .carbonTradable(new BigDecimal("500.00"))
                .carbonUsed(new BigDecimal("500.00"))
                .build();
        sellerEnterprise.setId(2L);

        User buyer = User.builder().realName("Buyer").build();
        buyer.setId(1L);
        User seller = User.builder().realName("Seller").build();
        seller.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult match = invocation.getArgument(0);
            match.setId(1L);
            return match;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(buyerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(sellerEnterprise));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(matchingResultRepository, atLeastOnce()).save(any(MatchingResult.class));
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
        // 断言碳币货款两讫结算 (DVP): 买方扣除已冻结资金，卖方增加资金
        verify(carbonCoinService, times(1)).deductFrozenBalance(eq(1L), any(), any(), any());
        verify(carbonCoinService, times(1)).sellQuota(eq(2L), any(), any(), any());
        // 断言区块链存证事件发布
        verify(eventPublisher, times(1)).publishEvent(any(com.oaiss.chain.event.TradeCompletedEvent.class));
        // FUNC-11: 撮合成交后 batch 通知买卖双方
        verify(notificationService, atLeastOnce()).sendBatchNotificationsAsync(anyList(), anyString(), anyString(), eq("TRADE"));
    }

    @Test
    @DisplayName("listOrders returns paged responses")
    void testListOrders() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B20240101001")
                .userId(1L)
                .direction(1)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listOrders(null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("listMyOrders returns current user orders")
    void testListMyOrders() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B20240101001")
                .userId(1L)
                .direction(1)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByUserIdAndDeletedFalse(eq(1L), any(Pageable.class))).thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listMyOrders(currentUser, null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("listMatchingResults returns user-related settlements")
    void testListMatchingResults() {
        MatchingResult match = MatchingResult.builder()
                .matchNo("MT20240101001")
                .buyOrderId(1L)
                .sellOrderId(2L)
                .buyerId(1L)
                .sellerId(2L)
                .matchedQuantity(new BigDecimal("100.00"))
                .settlementPrice(new BigDecimal("55.00"))
                .totalAmount(new BigDecimal("5500.00"))
                .build();
        match.setId(1L);

        User buyer = User.builder().realName("Buyer").build();
        buyer.setId(1L);
        User seller = User.builder().realName("Seller").build();
        seller.setId(2L);

        Page<MatchingResult> page = new PageImpl<>(Arrays.asList(match));
        when(matchingResultRepository.findByUserIdRelated(eq(1L), any(Pageable.class))).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(buyer, seller));

        Page<MatchingResultResponse> result = doubleAuctionService.listMatchingResults(currentUser, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("executeMatching uses DistributedLock instead of synchronized")
    void testExecuteMatchingHasDistributedLock() throws Exception {
        var method = DoubleAuctionService.class.getMethod("executeMatching");
        var annotation = method.getAnnotation(com.oaiss.chain.annotation.DistributedLock.class);

        assertNotNull(annotation,
                "executeMatching() must have @DistributedLock annotation for distributed concurrency control");
        assertEquals("'auction:matching'", annotation.key(),
                "@DistributedLock key must be 'auction:matching'");
        assertFalse(java.lang.reflect.Modifier.isSynchronized(method.getModifiers()),
                "executeMatching() must NOT be synchronized; use @DistributedLock instead");
    }

    @Test
    @DisplayName("validateOrderRequest rejects null request")
    void testValidateOrderRequestNull() {
        assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, null));
    }

    @Test
    @DisplayName("validateOrderRequest rejects null quantity")
    void testValidateOrderRequestNullQuantity() {
        orderRequest.setQuantity(null);

        assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));
    }

    @Test
    @DisplayName("validateOrderRequest rejects null price")
    void testValidateOrderRequestNullPrice() {
        orderRequest.setPrice(null);

        assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));
    }

    @Test
    @DisplayName("validateOrderRequest rejects zero price")
    void testValidateOrderRequestZeroPrice() {
        orderRequest.setPrice(BigDecimal.ZERO);

        assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));
    }

    @Test
    @DisplayName("placeSellOrder fails when enterprise is missing")
    void testPlaceSellOrderFailEnterpriseNotFound() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(TradeException.class, () ->
                doubleAuctionService.placeSellOrder(currentUser, orderRequest));
        verify(auctionOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("listOrders returns paged responses with direction and status filter")
    void testListOrdersWithDirectionAndStatus() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B20240101001")
                .userId(1L)
                .direction(1)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByDirectionAndStatusAndDeletedFalse(eq(1), eq(0), any(Pageable.class)))
                .thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listOrders(1, 0, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("买入", result.getContent().get(0).getDirectionText());
    }

    @Test
    @DisplayName("listMyOrders with direction filter only")
    void testListMyOrdersWithDirectionOnly() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B20240101001")
                .userId(1L)
                .direction(2)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByUserIdAndDirectionAndDeletedFalse(eq(1L), eq(2), any(Pageable.class)))
                .thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listMyOrders(currentUser, 2, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("卖出", result.getContent().get(0).getDirectionText());
    }

    @Test
    @DisplayName("listMyOrders with direction and status filter")
    void testListMyOrdersWithDirectionAndStatus() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B20240101001")
                .userId(1L)
                .direction(1)
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByUserIdAndDirectionAndStatusAndDeletedFalse(
                eq(1L), eq(1), eq(0), any(Pageable.class))).thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listMyOrders(currentUser, 1, 0, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("executeMatching returns empty when only buy orders exist")
    void testExecuteMatchingOnlyBuyOrders() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B001").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Collections.emptyList());

        var results = doubleAuctionService.executeMatching();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("executeMatching returns empty when only sell orders exist")
    void testExecuteMatchingOnlySellOrders() {
        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S001").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Collections.emptyList());
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));

        var results = doubleAuctionService.executeMatching();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("executeMatching does not match when buy price < sell price")
    void testExecuteMatchingNoMatchDueToPrice() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B001").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("40"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S001").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));

        var results = doubleAuctionService.executeMatching();

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(matchingResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("executeMatching handles partial match correctly")
    void testExecuteMatchingPartialMatch() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B001").userId(1L).direction(1)
                .quantity(new BigDecimal("200")).price(new BigDecimal("60"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S001").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        Enterprise buyerEnterprise = Enterprise.builder().userId(1L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        buyerEnterprise.setId(1L);
        Enterprise sellerEnterprise = Enterprise.builder().userId(2L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        sellerEnterprise.setId(2L);

        User buyer = User.builder().realName("Buyer").build();
        buyer.setId(1L);
        User seller = User.builder().realName("Seller").build();
        seller.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult match = invocation.getArgument(0);
            match.setId(1L);
            return match;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(buyerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(sellerEnterprise));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertNotNull(results);
        assertEquals(1, results.size());
        // Buy order should be partially matched (100 of 200)
        assertEquals(new BigDecimal("100"), buyOrder.getMatchedQuantity());
        // Sell order should be fully matched
        assertEquals(new BigDecimal("100"), sellOrder.getMatchedQuantity());
    }

    @Test
    @DisplayName("placeSellOrder rejects negative price before repository access")
    void testPlaceSellOrderFailNegativePrice() {
        orderRequest.setPrice(new BigDecimal("-1.00"));

        TradeException ex = assertThrows(TradeException.class, () ->
                doubleAuctionService.placeSellOrder(currentUser, orderRequest));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getCode());
        verify(enterpriseRepository, never()).findByUserId(any());
    }

    // ==================== Additional branch coverage tests (batch 2) ====================

    @Test
    @DisplayName("executeMatching-卖单完全匹配后sellRemaining<=0不再继续")
    void testExecuteMatchingSellFullyConsumed() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B001").userId(1L).direction(1)
                .quantity(new BigDecimal("50")).price(new BigDecimal("60"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S001").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        Enterprise buyerEnterprise = Enterprise.builder().userId(1L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        buyerEnterprise.setId(1L);
        Enterprise sellerEnterprise = Enterprise.builder().userId(2L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        sellerEnterprise.setId(2L);

        User buyer = User.builder().realName("Buyer").build();
        buyer.setId(1L);
        User seller = User.builder().realName("Seller").build();
        seller.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult match = invocation.getArgument(0);
            match.setId(1L);
            return match;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(buyerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(sellerEnterprise));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertEquals(1, results.size());
        assertEquals(new BigDecimal("50"), buyOrder.getMatchedQuantity());
        assertEquals(new BigDecimal("50"), sellOrder.getMatchedQuantity());
    }

    @Test
    @DisplayName("executeMatching-买单完全匹配后buyRemaining<=0退出while循环")
    void testExecuteMatchingBuyFullyConsumed() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B002").userId(1L).direction(1)
                .quantity(new BigDecimal("50")).price(new BigDecimal("60"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S002").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        Enterprise buyerEnterprise = Enterprise.builder().userId(1L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        buyerEnterprise.setId(1L);
        Enterprise sellerEnterprise = Enterprise.builder().userId(2L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        sellerEnterprise.setId(2L);

        User buyer = User.builder().realName("Buyer").build();
        buyer.setId(1L);
        User seller = User.builder().realName("Seller").build();
        seller.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult match = invocation.getArgument(0);
            match.setId(1L);
            return match;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(buyerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(sellerEnterprise));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("executeMatching-多个买单匹配多个卖单")
    void testExecuteMatchingMultipleBuysAndSells() {
        AuctionOrder buyOrder1 = AuctionOrder.builder()
                .orderNo("B003").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("70"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder1.setId(1L);

        AuctionOrder buyOrder2 = AuctionOrder.builder()
                .orderNo("B004").userId(3L).direction(1)
                .quantity(new BigDecimal("50")).price(new BigDecimal("55"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder2.setId(3L);

        AuctionOrder sellOrder1 = AuctionOrder.builder()
                .orderNo("S003").userId(2L).direction(2)
                .quantity(new BigDecimal("60")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder1.setId(2L);

        AuctionOrder sellOrder2 = AuctionOrder.builder()
                .orderNo("S004").userId(4L).direction(2)
                .quantity(new BigDecimal("80")).price(new BigDecimal("60"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder2.setId(4L);

        Enterprise enterprise1 = Enterprise.builder().userId(1L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build();
        enterprise1.setId(1L);
        Enterprise enterprise2 = Enterprise.builder().userId(2L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build();
        enterprise2.setId(2L);
        Enterprise enterprise3 = Enterprise.builder().userId(3L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build();
        enterprise3.setId(3L);
        Enterprise enterprise4 = Enterprise.builder().userId(4L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build();
        enterprise4.setId(4L);

        User user1 = User.builder().realName("User1").build(); user1.setId(1L);
        User user2 = User.builder().realName("User2").build(); user2.setId(2L);
        User user3 = User.builder().realName("User3").build(); user3.setId(3L);
        User user4 = User.builder().realName("User4").build(); user4.setId(4L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder1, buyOrder2));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder1, sellOrder2));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult m = invocation.getArgument(0);
            m.setId(m.getBuyOrderId());
            return m;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(tx.getSellerId());
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(enterprise1));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(enterprise2));
        when(enterpriseRepository.findByUserId(4L)).thenReturn(Optional.of(enterprise4));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(userRepository.findById(4L)).thenReturn(Optional.of(user4));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        // buyOrder1(100@70) matches sellOrder1(60@50)=60, then sellOrder2(80@60)=40 → buyOrder1 matched 100
        // buyOrder2(50@55) matches sellOrder2 remaining(40@60) → can't match (55 < 60), so no match for buyOrder2
        assertTrue(results.size() >= 1);
    }

    @Test
    @DisplayName("executeMatching-卖单部分已匹配时sellRemaining为0跳过")
    void testExecuteMatchingSellOrderAlreadyFullyMatched() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B005").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("60"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        // Sell order already fully matched
        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S005").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(new BigDecimal("100")).status(AuctionOrderStatusEnum.FULLY_MATCHED.getCode())
                .build();
        sellOrder.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("executeMatching-买单和卖单价格恰好相等时撮合成功")
    void testExecuteMatchingEqualPrice() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B006").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("55"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S006").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("55"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        Enterprise buyerEnterprise = Enterprise.builder().userId(1L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        buyerEnterprise.setId(1L);
        Enterprise sellerEnterprise = Enterprise.builder().userId(2L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        sellerEnterprise.setId(2L);

        User buyer = User.builder().realName("Buyer").build(); buyer.setId(1L);
        User seller = User.builder().realName("Seller").build(); seller.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult m = invocation.getArgument(0); m.setId(1L); return m;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0); tx.setId(1L); return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(buyerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(sellerEnterprise));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertEquals(1, results.size());
        assertEquals(new BigDecimal("55.00"), results.get(0).getSettlementPrice());
    }

    @Test
    @DisplayName("toOrderResponse-matchedQuantity为null时remainingQuantity等于quantity")
    void testToOrderResponseNullMatchedQuantity() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B007").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(null)  // null matchedQuantity
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listOrders(null, null, 1, 10);
        assertNotNull(result);
        assertEquals(new BigDecimal("100"), result.getContent().get(0).getRemainingQuantity());
    }

    @Test
    @DisplayName("safeAuctionStatusText-未知状态返回空字符串")
    void testSafeAuctionStatusTextUnknown() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B008").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(99)  // unknown status
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listOrders(null, null, 1, 10);
        assertEquals("", result.getContent().get(0).getStatusText());
    }

    @Test
    @DisplayName("safeMatchingStatusText-未知状态返回空字符串")
    void testSafeMatchingStatusTextUnknown() {
        MatchingResult match = MatchingResult.builder()
                .matchNo("MT001").buyOrderId(1L).sellOrderId(2L)
                .buyerId(1L).sellerId(2L)
                .matchedQuantity(new BigDecimal("100"))
                .settlementPrice(new BigDecimal("55"))
                .totalAmount(new BigDecimal("5500"))
                .status(99)  // unknown status
                .build();
        match.setId(1L);

        User buyer = User.builder().realName("Buyer").build(); buyer.setId(1L);
        User seller = User.builder().realName("Seller").build(); seller.setId(2L);

        Page<MatchingResult> page = new PageImpl<>(Arrays.asList(match));
        when(matchingResultRepository.findByUserIdRelated(eq(1L), any(Pageable.class))).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(buyer, seller));

        Page<MatchingResultResponse> result = doubleAuctionService.listMatchingResults(currentUser, 1, 10);
        assertEquals("", result.getContent().get(0).getStatusText());
    }

    @Test
    @DisplayName("listMyOrders-方向筛选仅返回对应方向订单")
    void testListMyOrdersDirectionOnly() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("S007").userId(1L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByUserIdAndDirectionAndDeletedFalse(eq(1L), eq(2), any(Pageable.class)))
                .thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listMyOrders(currentUser, 2, null, 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("卖出", result.getContent().get(0).getDirectionText());
    }

    @Test
    @DisplayName("listOrders-不带方向和状态筛选返回全部订单")
    void testListOrdersWithoutFilters() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B009").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(1L);

        Page<AuctionOrder> page = new PageImpl<>(Arrays.asList(order));
        when(auctionOrderRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(page);

        Page<AuctionOrderResponse> result = doubleAuctionService.listOrders(null, null, 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("executeMatching-买单已部分匹配时继续匹配剩余量")
    void testExecuteMatchingBuyOrderPartiallyMatched() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B010").userId(1L).direction(1)
                .quantity(new BigDecimal("200")).price(new BigDecimal("60"))
                .matchedQuantity(new BigDecimal("50"))  // already partially matched
                .status(AuctionOrderStatusEnum.PARTIALLY_MATCHED.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S008").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        Enterprise buyerEnterprise = Enterprise.builder().userId(1L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        buyerEnterprise.setId(1L);
        Enterprise sellerEnterprise = Enterprise.builder().userId(2L)
                .carbonQuota(new BigDecimal("1000")).carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500"))
                .build();
        sellerEnterprise.setId(2L);

        User buyer = User.builder().realName("Buyer").build(); buyer.setId(1L);
        User seller = User.builder().realName("Seller").build(); seller.setId(2L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult m = invocation.getArgument(0); m.setId(1L); return m;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0); tx.setId(1L); return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(buyerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(sellerEnterprise));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertEquals(1, results.size());
        // buyOrder: 200 - 50 = 150 remaining, matches 100 from sellOrder → buyOrder matchedQuantity = 50+100 = 150
        assertEquals(new BigDecimal("150"), buyOrder.getMatchedQuantity());
    }

    // ==================== Escrow 资产冻结与撤单解冻测试 ====================

    @Test
    @DisplayName("cancelOrder-撤销买单成功并全额解冻剩余资金")
    void testCancelOrderSuccessBuy() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B099").userId(1L).direction(1)
                .quantity(new BigDecimal("100.00")).price(new BigDecimal("50.00"))
                .matchedQuantity(new BigDecimal("20.00")) // 已成交20，剩余80未成交
                .status(AuctionOrderStatusEnum.PARTIALLY_MATCHED.getCode())
                .build();
        buyOrder.setId(99L);

        when(auctionOrderRepository.findById(99L)).thenReturn(Optional.of(buyOrder));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionOrderResponse response = doubleAuctionService.cancelOrder(currentUser, 99L);

        assertNotNull(response);
        assertEquals(AuctionOrderStatusEnum.CANCELLED.getCode(), response.getStatus());
        // 剩余未成交80吨，单价50，解冻金额应为 4000.00
        verify(carbonCoinService, times(1)).unfreezeBalance(eq(1L), eq(new BigDecimal("4000.00")));
    }

    @Test
    @DisplayName("cancelOrder-撤销卖单成功并全额解冻剩余碳配额")
    void testCancelOrderSuccessSell() {
        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S099").userId(1L).direction(2)
                .quantity(new BigDecimal("100.00")).price(new BigDecimal("50.00"))
                .matchedQuantity(BigDecimal.ZERO) // 0成交，剩余100未成交
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(99L);

        Enterprise enterprise = Enterprise.builder()
                .userId(1L)
                .carbonQuota(new BigDecimal("1000.00"))
                .carbonTradable(new BigDecimal("400.00"))
                .carbonFrozen(new BigDecimal("100.00"))
                .build();
        enterprise.setId(1L);

        when(auctionOrderRepository.findById(99L)).thenReturn(Optional.of(sellOrder));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(enterprise));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionOrderResponse response = doubleAuctionService.cancelOrder(currentUser, 99L);

        assertNotNull(response);
        assertEquals(AuctionOrderStatusEnum.CANCELLED.getCode(), response.getStatus());
        // 剩余100配额全额解冻，frozen变为0，tradable变为500
        assertEquals(new BigDecimal("0.00"), enterprise.getCarbonFrozen());
        assertEquals(new BigDecimal("500.00"), enterprise.getCarbonTradable());
        verify(enterpriseRepository, times(1)).save(enterprise);
    }

    @Test
    @DisplayName("cancelOrder-非本人挂单禁止撤销")
    void testCancelOrderFailNotOwner() {
        AuctionOrder order = AuctionOrder.builder()
                .orderNo("B099").userId(2L).direction(1) // 用户ID为2
                .quantity(new BigDecimal("100.00")).price(new BigDecimal("50.00"))
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        order.setId(99L);

        when(auctionOrderRepository.findById(99L)).thenReturn(Optional.of(order));

        assertThrows(TradeException.class, () ->
                doubleAuctionService.cancelOrder(currentUser, 99L));
        verify(auctionOrderRepository, never()).save(any());
    }

    // ==================== 信誉分交易闸门 ====================

    @Test
    @DisplayName("placeBuyOrder-信誉分被限制交易时拒绝挂单")
    void testPlaceBuyOrderFailCreditRestricted() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(creditScoreService.checkTradePermission(1L)).thenReturn(false);

        TradeException ex = assertThrows(TradeException.class, () ->
                doubleAuctionService.placeBuyOrder(currentUser, orderRequest));

        assertEquals(ErrorCode.CREDIT_TRADE_RESTRICTED, ex.getCode());
        assertTrue(ex.getMessage().contains("信誉分过低"));
        verify(auctionOrderRepository, never()).save(any());
        // 闸门必须在冻结碳币之前拦截
        verify(carbonCoinService, never()).freezeBalance(any(), any());
    }

    @Test
    @DisplayName("placeSellOrder-信誉分被限制交易时拒绝挂单")
    void testPlaceSellOrderFailCreditRestricted() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(creditScoreService.checkTradePermission(1L)).thenReturn(false);

        TradeException ex = assertThrows(TradeException.class, () ->
                doubleAuctionService.placeSellOrder(currentUser, orderRequest));

        assertEquals(ErrorCode.CREDIT_TRADE_RESTRICTED, ex.getCode());
        verify(auctionOrderRepository, never()).save(any());
        // 闸门必须在冻结配额之前拦截
        verify(enterpriseRepository, never()).save(any());
        assertEquals(new BigDecimal("500.00"), testEnterprise.getCarbonTradable());
    }

    // ==================== 同价时信誉优先 ====================

    @Test
    @DisplayName("executeMatching-同价时信誉分高的卖方优先成交")
    void testExecuteMatchingSamePricePrefersHigherCreditSeller() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B100").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        // 两个同价卖单：低信誉(用户2)在仓储返回中排在前，高信誉(用户3)排在后
        AuctionOrder lowCreditSeller = AuctionOrder.builder()
                .orderNo("S100").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        lowCreditSeller.setId(2L);

        AuctionOrder highCreditSeller = AuctionOrder.builder()
                .orderNo("S101").userId(3L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        highCreditSeller.setId(3L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(lowCreditSeller, highCreditSeller));
        when(creditScoreService.getScoresByUserIds(any()))
                .thenReturn(java.util.Map.of(1L, 90, 2L, 55, 3L, 95));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult m = invocation.getArgument(0);
            m.setId(1L);
            return m;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(
                Enterprise.builder().userId(1L).carbonQuota(new BigDecimal("1000"))
                        .carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build()));
        when(enterpriseRepository.findByUserId(3L)).thenReturn(Optional.of(
                Enterprise.builder().userId(3L).carbonQuota(new BigDecimal("1000"))
                        .carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build()));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertEquals(1, results.size());
        // 100 吨需求由高信誉卖方(用户3)全额吃下，低信誉卖方(用户2)一分未得
        assertEquals(new BigDecimal("100"), highCreditSeller.getMatchedQuantity());
        assertEquals(BigDecimal.ZERO, lowCreditSeller.getMatchedQuantity());
        verify(carbonCoinService).sellQuota(eq(3L), any(), any(), any());
        verify(carbonCoinService, never()).sellQuota(eq(2L), any(), any(), any());
    }

    @Test
    @DisplayName("executeMatching-价格优先仍高于信誉：低价卖方先成交")
    void testExecuteMatchingPriceStillTakesPrecedenceOverCredit() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B101").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("60"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        // 低价卖方信誉低，高价卖方信誉高：价格优先原则不变，低价卖方先成交
        AuctionOrder cheapLowCredit = AuctionOrder.builder()
                .orderNo("S102").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        cheapLowCredit.setId(2L);

        AuctionOrder priceyHighCredit = AuctionOrder.builder()
                .orderNo("S103").userId(3L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("55"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        priceyHighCredit.setId(3L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(cheapLowCredit, priceyHighCredit));
        when(creditScoreService.getScoresByUserIds(any()))
                .thenReturn(java.util.Map.of(1L, 90, 2L, 30, 3L, 100));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult m = invocation.getArgument(0);
            m.setId(1L);
            return m;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(
                Enterprise.builder().userId(1L).carbonQuota(new BigDecimal("1000"))
                        .carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build()));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(
                Enterprise.builder().userId(2L).carbonQuota(new BigDecimal("1000"))
                        .carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build()));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertEquals(1, results.size());
        assertEquals(new BigDecimal("100"), cheapLowCredit.getMatchedQuantity());
        assertEquals(BigDecimal.ZERO, priceyHighCredit.getMatchedQuantity());
    }

    @Test
    @DisplayName("executeMatching-查不到信誉分时按中性分处理且不影响撮合")
    void testExecuteMatchingMissingCreditFallsBackToNeutral() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B102").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellerWithoutScore = AuctionOrder.builder()
                .orderNo("S104").userId(99L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellerWithoutScore.setId(2L);

        // 各侧保留第二个挂单，确保真正走入信誉分次级排序逻辑
        AuctionOrder buyOrderNoMatch = AuctionOrder.builder()
                .orderNo("B102b").userId(4L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("40"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrderNoMatch.setId(4L);

        AuctionOrder sellerTooExpensive = AuctionOrder.builder()
                .orderNo("S104b").userId(5L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("60"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellerTooExpensive.setId(5L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder, buyOrderNoMatch));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellerWithoutScore, sellerTooExpensive));
        // 买卖双方均无信誉记录
        when(creditScoreService.getScoresByUserIds(any())).thenReturn(Collections.emptyMap());
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult m = invocation.getArgument(0);
            m.setId(1L);
            return m;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(
                Enterprise.builder().userId(1L).carbonQuota(new BigDecimal("1000"))
                        .carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build()));
        when(enterpriseRepository.findByUserId(99L)).thenReturn(Optional.of(
                Enterprise.builder().userId(99L).carbonQuota(new BigDecimal("1000"))
                        .carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build()));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertEquals(1, results.size(), "信誉分缺失不得阻断撮合");
    }

    @Test
    @DisplayName("executeMatching-信誉分服务异常时降级为中性分，撮合不中断")
    void testExecuteMatchingCreditServiceFailureDoesNotBreakMatching() {
        AuctionOrder buyOrder = AuctionOrder.builder()
                .orderNo("B103").userId(1L).direction(1)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        buyOrder.setId(1L);

        AuctionOrder sellOrder = AuctionOrder.builder()
                .orderNo("S105").userId(2L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("50"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrder.setId(2L);

        // 第二个卖单确保候选数 >= 2，从而真正触发信誉分批量查询并验证异常降级
        AuctionOrder sellOrderTooExpensive = AuctionOrder.builder()
                .orderNo("S106").userId(3L).direction(2)
                .quantity(new BigDecimal("100")).price(new BigDecimal("80"))
                .matchedQuantity(BigDecimal.ZERO).status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();
        sellOrderTooExpensive.setId(3L);

        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(eq(1), anyList()))
                .thenReturn(Arrays.asList(buyOrder));
        when(auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(eq(2), anyList()))
                .thenReturn(Arrays.asList(sellOrder, sellOrderTooExpensive));
        when(creditScoreService.getScoresByUserIds(any()))
                .thenThrow(new IllegalStateException("credit db down"));
        when(matchingResultRepository.save(any(MatchingResult.class))).thenAnswer(invocation -> {
            MatchingResult m = invocation.getArgument(0);
            m.setId(1L);
            return m;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(
                Enterprise.builder().userId(1L).carbonQuota(new BigDecimal("1000"))
                        .carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build()));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(
                Enterprise.builder().userId(2L).carbonQuota(new BigDecimal("1000"))
                        .carbonTradable(new BigDecimal("500")).carbonUsed(new BigDecimal("500")).build()));
        when(auctionOrderRepository.save(any(AuctionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = doubleAuctionService.executeMatching();

        assertEquals(1, results.size(), "信誉分服务异常不得阻断撮合");
    }
}
