package com.oaiss.chain.service;

import com.oaiss.chain.dto.TradeRequest;
import com.oaiss.chain.dto.TradeResponse;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.TradeStatusEnum;
import com.oaiss.chain.enums.TradeTypeEnum;
import com.oaiss.chain.exception.TradeException;
import com.oaiss.chain.repository.EnterpriseRepository;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * TradeService 单元测试
 * TradeService Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EnterpriseRepository enterpriseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MetricsService metricsService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CarbonCoinService carbonCoinService;

    @Mock
    private CreditScoreService creditScoreService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TradeService tradeService;

    private Transaction testTransaction;
    private Enterprise sellerEnterprise;
    private Enterprise buyerEnterprise;
    private JwtUserDetails currentUser;
    private TradeRequest tradeRequest;

    @BeforeEach
    void setUp() {
        testTransaction = Transaction.builder()
                .tradeNo("TX20240101001")
                .tradeType(TradeTypeEnum.P2P.getCode())
                .sellerId(1L)
                .buyerId(2L)
                .quantity(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("5000.00"))
                .status(TradeStatusEnum.PENDING.getCode())
                .build();
        testTransaction.setId(1L);

        sellerEnterprise = Enterprise.builder()
                .userId(1L)
                .carbonQuota(new BigDecimal("1000.00"))
                .carbonTradable(new BigDecimal("500.00"))
                .carbonUsed(new BigDecimal("500.00"))
                .build();
        sellerEnterprise.setId(1L);

        buyerEnterprise = Enterprise.builder()
                .userId(2L)
                .carbonQuota(new BigDecimal("500.00"))
                .carbonTradable(new BigDecimal("300.00"))
                .carbonUsed(new BigDecimal("200.00"))
                .build();
        buyerEnterprise.setId(2L);

        currentUser = new JwtUserDetails();
        currentUser.setUserId(1L);
        currentUser.setUsername("testuser");

        tradeRequest = new TradeRequest();
        tradeRequest.setTradeType(TradeTypeEnum.P2P.getCode());
        tradeRequest.setSellerId(1L);
        tradeRequest.setBuyerId(2L);
        tradeRequest.setQuantity(new BigDecimal("100.00"));
        tradeRequest.setUnitPrice(new BigDecimal("50.00"));

        // 默认信誉分正常放行；限制态由各用例单独覆写
        lenient().when(creditScoreService.checkTradePermission(anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("创建P2P交易成功")
    void testCreateP2PTradeSuccess() {
        // Given
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        // When
        TradeResponse response = tradeService.createP2PTrade(currentUser, tradeRequest);

        // Then
        assertNotNull(response);
        assertEquals(TradeTypeEnum.P2P.getCode(), response.getTradeType());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("创建P2P交易失败-双方相同")
    void testCreateP2PTradeFailSameParty() {
        // Given
        tradeRequest.setSellerId(1L);
        tradeRequest.setBuyerId(1L);

        // When & Then
        assertThrows(TradeException.class, () -> tradeService.createP2PTrade(currentUser, tradeRequest));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建P2P交易失败-配额不足")
    void testCreateP2PTradeFailInsufficientQuota() {
        // Given
        tradeRequest.setQuantity(new BigDecimal("1000.00")); // More than tradable (500)
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));

        // When & Then
        assertThrows(TradeException.class, () -> tradeService.createP2PTrade(currentUser, tradeRequest));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建拍卖挂单成功")
    void testCreateAuctionOrderSuccess() {
        // Given
        tradeRequest.setTradeType(TradeTypeEnum.AUCTION.getCode());
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        // When
        TradeResponse response = tradeService.createAuctionOrder(currentUser, tradeRequest);

        // Then
        assertNotNull(response);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("确认交易成功")
    void testConfirmTradeSuccess() {
        // Given
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(buyerEnterprise));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(enterpriseRepository.save(any(Enterprise.class))).thenReturn(sellerEnterprise);

        // When
        TradeResponse response = tradeService.confirmTrade(1L, 1L);

        // Then
        assertNotNull(response);
        verify(enterpriseRepository, times(2)).save(any(Enterprise.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class)); // PROCESSING + COMPLETED
        verify(carbonCoinService, times(1)).buyQuota(eq(2L), any(), any(), eq(1L));
        verify(carbonCoinService, times(1)).sellQuota(eq(1L), any(), any(), eq(1L));
        verify(eventPublisher, times(1)).publishEvent(any(com.oaiss.chain.event.TradeCompletedEvent.class));
    }

    @Test
    @DisplayName("确认交易失败-交易不存在")
    void testConfirmTradeFailNotFound() {
        // Given
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(TradeException.class, () -> tradeService.confirmTrade(999L, 1L));
    }

    @Test
    @DisplayName("取消交易成功")
    void testCancelTradeSuccess() {
        // Given
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        // When
        TradeResponse response = tradeService.cancelTrade(1L, currentUser);

        // Then
        assertNotNull(response);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        // FUNC-11: 取消后通知对手方
        verify(notificationService).sendNotificationAsync(anyLong(), anyString(), anyString(), eq("TRADE"), eq(1L), eq("TRADE"));
    }

    @Test
    @DisplayName("获取交易详情成功")
    void testGetTradeSuccess() {
        // Given
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        // When
        TradeResponse response = tradeService.getTrade(1L, currentUser);

        // Then
        assertNotNull(response);
        assertEquals("TX20240101001", response.getTradeNo());
    }

    @Test
    @DisplayName("获取交易详情失败-不存在")
    void testGetTradeFailNotFound() {
        // Given
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(TradeException.class, () -> tradeService.getTrade(999L, currentUser));
    }

    // ========== TDD Cycle 1: C4 P2P交易越权漏洞 ==========

    @Test
    @DisplayName("P2P交易失败-当前用户不是卖方(越权)")
    void testCreateP2PTradeFailSellerNotCurrentUser() {
        // 当前用户 ID=1，但请求中 sellerId=999（冒充他人卖方）
        tradeRequest.setSellerId(999L);
        tradeRequest.setBuyerId(2L);

        assertThrows(TradeException.class,
                () -> tradeService.createP2PTrade(currentUser, tradeRequest));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("分页查询交易")
    void testListTrades() {
        // Given
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(seller, buyer));

        // When
        Page<TradeResponse> result = tradeService.listTrades(null, null, null, null, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("查询我的交易时将身份和关键词筛选透传到仓储")
    void testListMyTradesPassesKeywordAndIdentityFilters() {
        // Given
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer Co").build();
        buyer.setId(2L);

        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                eq(1L), eq(TradeTypeEnum.P2P.getCode()), eq(TradeStatusEnum.PENDING.getCode()),
                eq("TRX"), eq("Buyer"), eq("seller"), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(seller, buyer));

        // When
        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser,
                TradeTypeEnum.P2P.getCode(),
                TradeStatusEnum.PENDING.getCode(),
                " TRX ",
                " Buyer ",
                "SELLER",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                1,
                10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(transactionRepository, times(1)).findByUserIdRelated(
                eq(1L), eq(TradeTypeEnum.P2P.getCode()), eq(TradeStatusEnum.PENDING.getCode()),
                eq("TRX"), eq("Buyer"), eq("seller"), any(), any(), any(Pageable.class));
    }

    // ==================== H6: Trade Confirmation Race Condition ====================

    @Test
    @DisplayName("确认交易失败-交易已完成（防止重复确认竞态）")
    void testConfirmTradeFailAlreadyCompleted() {
        // Given: trade is already COMPLETED
        testTransaction.setStatus(TradeStatusEnum.COMPLETED.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        // When & Then: should reject immediately, no quota changes
        assertThrows(TradeException.class, () -> tradeService.confirmTrade(1L, 1L));
        verify(enterpriseRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("确认交易失败-交易处理中（防止并发确认）")
    void testConfirmTradeFailProcessing() {
        // Given: trade is already PROCESSING (another request started confirmation)
        testTransaction.setStatus(TradeStatusEnum.PROCESSING.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        // When & Then: should reject due to PROCESSING status
        assertThrows(TradeException.class, () -> tradeService.confirmTrade(1L, 1L));
        // Quotas should NOT be modified
        verify(enterpriseRepository, never()).save(any());
    }

    @Test
    @DisplayName("确认交易失败-交易已取消")
    void testConfirmTradeFailCancelled() {
        // Given: trade is CANCELLED
        testTransaction.setStatus(TradeStatusEnum.CANCELLED.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        // When & Then
        assertThrows(TradeException.class, () -> tradeService.confirmTrade(1L, 1L));
        verify(enterpriseRepository, never()).save(any());
    }

    @Test
    @DisplayName("确认交易成功-待处理状态应允许确认")
    void testConfirmTradeSuccessPendingStatus() {
        // Given: trade is PENDING (valid for confirmation)
        testTransaction.setStatus(TradeStatusEnum.PENDING.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(buyerEnterprise));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(enterpriseRepository.save(any(Enterprise.class))).thenReturn(sellerEnterprise);

        // When & Then: should succeed
        TradeResponse response = tradeService.confirmTrade(1L, 1L);
        assertNotNull(response);
    }

    // ==================== Additional coverage tests ====================

    @Test
    @DisplayName("创建P2P交易失败-交易类型不匹配")
    void testCreateP2PTradeFailWrongTradeType() {
        tradeRequest.setTradeType(TradeTypeEnum.AUCTION.getCode()); // Not P2P

        assertThrows(TradeException.class,
                () -> tradeService.createP2PTrade(currentUser, tradeRequest));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建P2P交易失败-买方ID为空")
    void testCreateP2PTradeFailNullBuyerId() {
        tradeRequest.setBuyerId(null);

        assertThrows(Exception.class,
                () -> tradeService.createP2PTrade(currentUser, tradeRequest));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("确认交易失败-用户既不是买方也不是卖方")
    void testConfirmTradeFailUserNotParticipant() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        // userId=999 is neither seller(1) nor buyer(2)
        assertThrows(TradeException.class, () -> tradeService.confirmTrade(1L, 999L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("确认交易失败-买方企业不存在")
    void testConfirmTradeFailBuyerEnterpriseNotFound() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        assertThrows(TradeException.class, () -> tradeService.confirmTrade(1L, 1L));
    }

    @Test
    @DisplayName("取消交易失败-交易已完成不可取消")
    void testCancelTradeFailCompleted() {
        testTransaction.setStatus(TradeStatusEnum.COMPLETED.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        assertThrows(TradeException.class,
                () -> tradeService.cancelTrade(1L, currentUser));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("取消交易失败-交易已取消不可再取消")
    void testCancelTradeFailAlreadyCancelled() {
        testTransaction.setStatus(TradeStatusEnum.CANCELLED.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        assertThrows(TradeException.class,
                () -> tradeService.cancelTrade(1L, currentUser));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("取消交易失败-交易不存在")
    void testCancelTradeFailNotFound() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(TradeException.class,
                () -> tradeService.cancelTrade(999L, currentUser));
    }

    @Test
    @DisplayName("获取交易详情-管理员可查看所有交易")
    void testGetTradeAdminCanViewAll() {
        currentUser.setRoles(java.util.List.of("ADMIN"));

        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        TradeResponse response = tradeService.getTrade(1L, currentUser);

        assertNotNull(response);
    }

    @Test
    @DisplayName("获取交易详情-非参与者无权查看")
    void testGetTradeFailNonParticipantNoPermission() {
        JwtUserDetails nonParticipant = new JwtUserDetails();
        nonParticipant.setUserId(999L);
        nonParticipant.setUsername("outsider");
        nonParticipant.setRoles(java.util.List.of("ENTERPRISE"));

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        assertThrows(Exception.class,
                () -> tradeService.getTrade(1L, nonParticipant));
    }

    @Test
    @DisplayName("创建拍卖挂单失败-企业不存在")
    void testCreateAuctionOrderFailEnterpriseNotFound() {
        tradeRequest.setTradeType(TradeTypeEnum.AUCTION.getCode());
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(TradeException.class,
                () -> tradeService.createAuctionOrder(currentUser, tradeRequest));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建拍卖挂单失败-配额不足")
    void testCreateAuctionOrderFailInsufficientQuota() {
        tradeRequest.setTradeType(TradeTypeEnum.AUCTION.getCode());
        tradeRequest.setQuantity(new BigDecimal("9999.00"));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));

        assertThrows(TradeException.class,
                () -> tradeService.createAuctionOrder(currentUser, tradeRequest));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("P2P交易-默认卖方为当前用户")
    void testCreateP2PTradeDefaultSeller() {
        tradeRequest.setSellerId(null); // No sellerId, should default to currentUser
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        TradeResponse response = tradeService.createP2PTrade(currentUser, tradeRequest);

        assertNotNull(response);
        verify(transactionRepository).save(any(Transaction.class));
    }

    // ==================== Additional branch coverage tests ====================

    @Test
    @DisplayName("P2P交易失败-卖方企业不存在")
    void testCreateP2PTradeFailSellerEnterpriseNotFound() {
        tradeRequest.setSellerId(1L);
        tradeRequest.setBuyerId(2L);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(TradeException.class, () -> tradeService.createP2PTrade(currentUser, tradeRequest));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("确认交易失败-卖方企业不存在")
    void testConfirmTradeFailSellerEnterpriseNotFound() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        assertThrows(TradeException.class, () -> tradeService.confirmTrade(1L, 1L));
    }

    @Test
    @DisplayName("获取交易详情-买方为0时buyerName显示待定")
    void testGetTradeBuyerIdZero() {
        testTransaction.setBuyerId(0L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));

        currentUser.setRoles(java.util.List.of("ADMIN"));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertNotNull(response);
        assertEquals("待定", response.getBuyerName());
    }

    @Test
    @DisplayName("获取交易详情-审核员可查看所有交易")
    void testGetTradeReviewerCanViewAll() {
        currentUser.setRoles(java.util.List.of("REVIEWER"));

        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertNotNull(response);
    }

    @Test
    @DisplayName("获取交易详情-第三方监管可查看所有交易")
    void testGetTradeThirdPartyCanViewAll() {
        currentUser.setRoles(java.util.List.of("THIRD_PARTY"));

        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertNotNull(response);
    }

    @Test
    @DisplayName("获取交易详情-买方可查看自己的交易")
    void testGetTradeBuyerCanViewOwnTrade() {
        currentUser.setUserId(2L);
        currentUser.setRoles(java.util.List.of("ENTERPRISE"));

        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertNotNull(response);
    }

    @Test
    @DisplayName("获取交易详情-卖方用户名解析失败时显示未知")
    void testGetTradeSellerNameNotFound() {
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        currentUser.setRoles(java.util.List.of("ADMIN"));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertNotNull(response);
        assertEquals("未知", response.getSellerName());
    }

    @Test
    @DisplayName("获取交易详情-买方用户名解析失败时显示未知")
    void testGetTradeBuyerNameNotFound() {
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        currentUser.setRoles(java.util.List.of("ADMIN"));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertNotNull(response);
        assertEquals("未知", response.getBuyerName());
    }

    @Test
    @DisplayName("列表交易-买方ID为0时buyerName显示待定")
    void testListTradesBuyerIdZero() {
        testTransaction.setBuyerId(0L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(seller));

        Page<TradeResponse> result = tradeService.listTrades(null, null, null, null, 1, 10);
        assertEquals(1, result.getContent().size());
        assertEquals("待定", result.getContent().get(0).getBuyerName());
    }

    @Test
    @DisplayName("列表我的交易-buyerId为0时buyerName显示待定")
    void testListMyTradesBuyerIdZero() {
        testTransaction.setBuyerId(0L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(seller));

        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser, null, null, null, null, null, null, null, 1, 10);
        assertEquals(1, result.getContent().size());
        assertEquals("待定", result.getContent().get(0).getBuyerName());
    }

    @Test
    @DisplayName("normalizeFilterValue-空字符串返回null")
    void testNormalizeFilterValueEmpty() {
        testTransaction.setBuyerId(0L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                any(), any(), any(), isNull(), isNull(), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(seller));

        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser, null, null, "  ", "  ", "invalid_identity", null, null, 1, 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("normalizeIdentity-无效identity返回null")
    void testNormalizeIdentityInvalid() {
        testTransaction.setBuyerId(2L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                any(), any(), any(), any(), any(), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(seller, buyer));

        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser, null, null, null, null, "invalid", null, null, 1, 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("normalizeIdentity-buyer身份")
    void testNormalizeIdentityBuyer() {
        testTransaction.setBuyerId(2L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                any(), any(), any(), any(), any(), eq("buyer"), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(Arrays.asList(seller, buyer));

        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser, null, null, null, null, "BUYER", null, null, 1, 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("safeTradeTypeText-未知交易类型返回未知")
    void testSafeTradeTypeTextUnknown() {
        testTransaction.setTradeType(99);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));

        currentUser.setRoles(java.util.List.of("ADMIN"));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertEquals("未知", response.getTradeTypeText());
    }

    @Test
    @DisplayName("safeTradeStatusText-未知交易状态返回未知")
    void testSafeTradeStatusTextUnknown() {
        testTransaction.setStatus(99);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));

        currentUser.setRoles(java.util.List.of("ADMIN"));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertEquals("未知", response.getStatusText());
    }

    @Test
    @DisplayName("确认交易-买方也能确认交易")
    void testConfirmTradeByBuyer() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(enterpriseRepository.findByUserId(2L)).thenReturn(Optional.of(buyerEnterprise));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(enterpriseRepository.save(any(Enterprise.class))).thenReturn(sellerEnterprise);

        TradeResponse response = tradeService.confirmTrade(1L, 2L); // buyer confirms
        assertNotNull(response);
        // FUNC-11: 确认后通知卖方 (sellerId=1, 非确认方)
        verify(notificationService).sendNotificationAsync(eq(1L), anyString(), anyString(), eq("TRADE"), eq(1L), eq("TRADE"));
    }

    // ==================== Additional branch coverage tests (batch 2) ====================

    @Test
    @DisplayName("cancelTrade-PROCESSING状态可取消")
    void testCancelTradeProcessingStatus() {
        testTransaction.setStatus(TradeStatusEnum.PROCESSING.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        TradeResponse response = tradeService.cancelTrade(1L, currentUser);
        assertNotNull(response);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("cancelTrade-FAILED状态不可取消")
    void testCancelTradeFailedStatus() {
        testTransaction.setStatus(TradeStatusEnum.FAILED.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        assertThrows(TradeException.class, () -> tradeService.cancelTrade(1L, currentUser));
    }

    @Test
    @DisplayName("confirmTrade-用户既不是买方也不是卖方但非PENDING状态")
    void testConfirmTradeFailNotParticipantNonPending() {
        testTransaction.setStatus(TradeStatusEnum.COMPLETED.getCode());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        assertThrows(TradeException.class, () -> tradeService.confirmTrade(1L, 999L));
    }

    @Test
    @DisplayName("getTrade-ENTERPRISE角色非参与者不可查看")
    void testGetTradeEnterpriseNonParticipantCannotView() {
        JwtUserDetails enterpriseUser = new JwtUserDetails();
        enterpriseUser.setUserId(999L);
        enterpriseUser.setUsername("outsider");
        enterpriseUser.setRoles(java.util.List.of("ENTERPRISE"));

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

        assertThrows(Exception.class, () -> tradeService.getTrade(1L, enterpriseUser));
    }

    @Test
    @DisplayName("getTrade-卖方参与者可查看")
    void testGetTradeSellerCanViewOwnTrade() {
        currentUser.setUserId(1L);
        currentUser.setRoles(java.util.List.of("ENTERPRISE"));

        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertNotNull(response);
    }

    @Test
    @DisplayName("createP2PTrade-sellerId为null时默认使用当前用户")
    void testCreateP2PTradeWithNullSellerId() {
        tradeRequest.setSellerId(null);
        tradeRequest.setBuyerId(2L);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        TradeResponse response = tradeService.createP2PTrade(currentUser, tradeRequest);
        assertNotNull(response);
    }

    @Test
    @DisplayName("listMyTrades-所有filter为null时透传null")
    void testListMyTradesAllNullFilters() {
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        Page<Transaction> page = new PageImpl<>(java.util.Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(java.util.Arrays.asList(seller));

        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser, null, null, null, null, null, null, null, 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("listTrades-买卖方都有名称")
    void testListTradesWithBothNames() {
        User seller = User.builder().username("seller").realName("Seller Name").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer Name").build();
        buyer.setId(2L);

        Page<Transaction> page = new PageImpl<>(java.util.Arrays.asList(testTransaction));
        when(transactionRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(java.util.Arrays.asList(seller, buyer));

        Page<TradeResponse> result = tradeService.listTrades(null, null, null, null, 1, 10);
        assertEquals(1, result.getContent().size());
        assertEquals("Seller Name", result.getContent().get(0).getSellerName());
        assertEquals("Buyer Name", result.getContent().get(0).getBuyerName());
    }

    @Test
    @DisplayName("toResponse-buyerId为0时buyerName显示待定")
    void testToResponseBuyerIdZero() {
        testTransaction.setBuyerId(0L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));

        currentUser.setRoles(java.util.List.of("ADMIN"));

        TradeResponse response = tradeService.getTrade(1L, currentUser);
        assertNotNull(response);
        assertEquals("待定", response.getBuyerName());
    }

    @Test
    @DisplayName("normalizeIdentity-buyer身份（小写）")
    void testNormalizeIdentityBuyerLowercase() {
        testTransaction.setBuyerId(2L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        Page<Transaction> page = new PageImpl<>(java.util.Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                any(), any(), any(), any(), any(), eq("buyer"), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(java.util.Arrays.asList(seller, buyer));

        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser, null, null, null, null, "buyer", null, null, 1, 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("listMyTrades-buyerId为0时buyerName显示待定")
    void testListMyTradesBuyerIdZeroV2() {
        testTransaction.setBuyerId(0L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        Page<Transaction> page = new PageImpl<>(java.util.Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(java.util.Arrays.asList(seller));

        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser, null, null, null, null, null, null, null, 1, 10);
        assertEquals("待定", result.getContent().get(0).getBuyerName());
    }

    @Test
    @DisplayName("listTrades-buyerId为0时buyerName显示待定")
    void testListTradesBuyerIdZeroV2() {
        testTransaction.setBuyerId(0L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);

        Page<Transaction> page = new PageImpl<>(java.util.Arrays.asList(testTransaction));
        when(transactionRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(java.util.Arrays.asList(seller));

        Page<TradeResponse> result = tradeService.listTrades(null, null, null, null, 1, 10);
        assertEquals("待定", result.getContent().get(0).getBuyerName());
    }

    @Test
    @DisplayName("normalizeIdentity-SELLER身份（大写转小写）")
    void testNormalizeIdentitySellerUppercase() {
        testTransaction.setBuyerId(2L);
        User seller = User.builder().username("seller").realName("Seller").build();
        seller.setId(1L);
        User buyer = User.builder().username("buyer").realName("Buyer").build();
        buyer.setId(2L);

        Page<Transaction> page = new PageImpl<>(java.util.Arrays.asList(testTransaction));
        when(transactionRepository.findByUserIdRelated(
                any(), any(), any(), any(), any(), eq("seller"), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(java.util.Arrays.asList(seller, buyer));

        Page<TradeResponse> result = tradeService.listMyTrades(
                currentUser, null, null, null, null, "SELLER", null, null, 1, 10);
        assertNotNull(result);
    }

    // ==================== 信誉分交易闸门 ====================

    @Test
    @DisplayName("P2P交易-信誉分被限制交易时拒绝创建")
    void testCreateP2PTradeFailCreditRestricted() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(creditScoreService.checkTradePermission(1L)).thenReturn(false);

        TradeException ex = assertThrows(TradeException.class,
                () -> tradeService.createP2PTrade(currentUser, tradeRequest));

        assertEquals(com.oaiss.chain.constant.ErrorCode.CREDIT_TRADE_RESTRICTED, ex.getCode());
        assertTrue(ex.getMessage().contains("信誉分过低"));
        verify(transactionRepository, never()).save(any());
        // 闸门必须在冻结配额之前拦截
        assertEquals(new BigDecimal("500.00"), sellerEnterprise.getCarbonTradable());
    }

    @Test
    @DisplayName("P2P交易-账户被冻结时拒绝创建")
    void testCreateP2PTradeFailAccountFrozen() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(creditScoreService.checkTradePermission(1L)).thenReturn(false);

        assertThrows(TradeException.class, () -> tradeService.createP2PTrade(currentUser, tradeRequest));
        verify(enterpriseRepository, never()).save(any(Enterprise.class));
    }

    @Test
    @DisplayName("拍卖挂单-信誉分被限制交易时拒绝创建")
    void testCreateAuctionOrderFailCreditRestricted() {
        tradeRequest.setTradeType(TradeTypeEnum.AUCTION.getCode());
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(sellerEnterprise));
        when(creditScoreService.checkTradePermission(1L)).thenReturn(false);

        TradeException ex = assertThrows(TradeException.class,
                () -> tradeService.createAuctionOrder(currentUser, tradeRequest));

        assertEquals(com.oaiss.chain.constant.ErrorCode.CREDIT_TRADE_RESTRICTED, ex.getCode());
        verify(transactionRepository, never()).save(any());
    }
}
