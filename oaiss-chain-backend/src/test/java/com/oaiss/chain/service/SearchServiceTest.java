package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.SearchResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SearchService 单元测试
 * SearchService Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EnterpriseRepository enterpriseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BlockchainServicePort blockchainService;

    @InjectMocks
    private SearchService searchService;

    private CarbonReport testReport;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        testReport = CarbonReport.builder()
                .reportNo("CR20240101001")
                .enterpriseId(1L)
                .title("Test Carbon Report")
                .accountingPeriod("2024-Q1")
                .totalEmission(new BigDecimal("1000.00"))
                .status(1)
                .build();
        testReport.setId(1L);
        testReport.setCreatedAt(LocalDateTime.now());

        testTransaction = Transaction.builder()
                .tradeNo("TX20240101001")
                .tradeType(1)
                .sellerId(1L)
                .buyerId(2L)
                .quantity(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("5000.00"))
                .status(1)
                .build();
        testTransaction.setId(1L);
        testTransaction.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("搜索碳报告成功")
    void testSearchCarbonReportsSuccess() {
        // Given
        Page<CarbonReport> page = new PageImpl<>(Arrays.asList(testReport));
        when(carbonReportRepository.search(eq(null), eq(null), eq("test"), any(Pageable.class)))
                .thenReturn(page);

        // When
        Page<SearchResponse.CarbonReportSummary> result = 
            searchService.searchCarbonReports("test", null, null, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("CR20240101001", result.getContent().get(0).getReportNo());
    }

    @Test
    @DisplayName("搜索碳报告-无结果")
    void testSearchCarbonReportsEmpty() {
        // Given
        Page<CarbonReport> page = new PageImpl<>(Collections.emptyList());
        when(carbonReportRepository.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        // When
        Page<SearchResponse.CarbonReportSummary> result = 
            searchService.searchCarbonReports("nonexistent", null, null, 1, 10);

        // Then
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("搜索交易记录成功")
    void testSearchTradesSuccess() {
        // Given
        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.searchWithUserBound(eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);

        // When
        Page<SearchResponse.TradeSummary> result = 
            searchService.searchTrades(null, null, null, null, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("TX20240101001", result.getContent().get(0).getTradeNo());
    }

    @Test
    @DisplayName("搜索交易记录-按卖家筛选")
    void testSearchTradesBySeller() {
        // Given
        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.searchWithUserBound(eq(null), eq(1L), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);

        // When
        Page<SearchResponse.TradeSummary> result = 
            searchService.searchTrades(1L, null, null, null, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getSellerId());
    }

    @Test
    @DisplayName("搜索交易记录-企业用户强制限定参与方租户边界")
    void testSearchTradesEnterpriseUserBound() {
        // Given
        JwtUserDetails entUser = JwtUserDetails.builder()
                .userId(100L)
                .username("enterprise001")
                .roles(List.of("ENTERPRISE"))
                .build();
        Page<Transaction> page = new PageImpl<>(Arrays.asList(testTransaction));
        when(transactionRepository.searchWithUserBound(eq(100L), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);

        // When
        Page<SearchResponse.TradeSummary> result = 
            searchService.searchTrades(entUser, null, null, null, null, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(transactionRepository).searchWithUserBound(eq(100L), eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("搜索交易记录-企业用户看到的买卖双方为脱敏别名，同一企业跨记录稳定")
    void testSearchTradesEnterpriseUserMasked() {
        // Given：同一卖方参与两笔交易，卖方显示名必须一致
        Transaction secondTrade = Transaction.builder()
                .tradeNo("TX20240101002")
                .tradeType(2)
                .sellerId(1L)
                .buyerId(3L)
                .quantity(new BigDecimal("10.00"))
                .unitPrice(new BigDecimal("60.00"))
                .totalAmount(new BigDecimal("600.00"))
                .status(2)
                .build();
        secondTrade.setId(2L);

        JwtUserDetails entUser = JwtUserDetails.builder()
                .userId(1L)
                .username("enterprise001")
                .roles(List.of("ENTERPRISE"))
                .build();
        Page<Transaction> page = new PageImpl<>(List.of(testTransaction, secondTrade));
        when(transactionRepository.searchWithUserBound(eq(1L), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);

        // 交易表存用户 ID：1L -> 企业 11L，2L -> 企业 22L，3L -> 企业 33L
        when(enterpriseRepository.findByUserIdInAndDeletedFalse(any()))
                .thenReturn(List.of(enterprise(11L, 1L), enterprise(22L, 2L), enterprise(33L, 3L)));

        // When
        Page<SearchResponse.TradeSummary> result =
            searchService.searchTrades(entUser, null, null, null, null, 1, 10);

        // Then
        List<SearchResponse.TradeSummary> rows = result.getContent();
        assertEquals(2, rows.size());
        for (SearchResponse.TradeSummary row : rows) {
            assertTrue(row.getSellerName().startsWith("匿名企业-"), "企业角色看到真实名称即为越权");
            assertTrue(row.getBuyerName().startsWith("匿名企业-"));
        }
        // 同一企业跨记录必须映射到同一别名
        assertEquals(rows.get(0).getSellerName(), rows.get(1).getSellerName());
        // 同一笔交易内买卖双方别名必然不同
        assertNotEquals(rows.get(0).getSellerName(), rows.get(0).getBuyerName());
        // 不泄露任何真实企业名称
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("搜索交易记录-审核员同样只看到脱敏别名")
    void testSearchTradesReviewerMasked() {
        JwtUserDetails reviewer = JwtUserDetails.builder()
                .userId(200L)
                .username("reviewer001")
                .roles(List.of("REVIEWER"))
                .build();
        Page<Transaction> page = new PageImpl<>(List.of(testTransaction));
        // 审核员不受租户边界限制，但同样只看脱敏数据
        when(transactionRepository.searchWithUserBound(eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);
        when(enterpriseRepository.findByUserIdInAndDeletedFalse(any()))
                .thenReturn(List.of(enterprise(11L, 1L), enterprise(22L, 2L)));

        Page<SearchResponse.TradeSummary> result =
            searchService.searchTrades(reviewer, null, null, null, null, 1, 10);

        assertEquals(1, result.getContent().size());
        assertTrue(result.getContent().get(0).getSellerName().startsWith("匿名企业-"));
        assertTrue(result.getContent().get(0).getBuyerName().startsWith("匿名企业-"));
    }

    @Test
    @DisplayName("搜索交易记录-管理员与第三方监管可见真实企业名称")
    void testSearchTradesRegulatorSeesRealNames() {
        User seller = new User();
        seller.setId(1L);
        seller.setRealName("华能发电有限公司");
        User buyer = new User();
        buyer.setId(2L);
        buyer.setRealName("国网能源有限公司");

        for (JwtUserDetails regulator : List.of(
                JwtUserDetails.builder().userId(900L).username("admin").roles(List.of("ADMIN")).build(),
                JwtUserDetails.builder().userId(901L).username("thirdparty001").roles(List.of("THIRD_PARTY")).build())) {

            reset(transactionRepository, userRepository);
            when(transactionRepository.searchWithUserBound(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(testTransaction)));
            when(userRepository.findAllById(any())).thenReturn(List.of(seller, buyer));

            Page<SearchResponse.TradeSummary> result =
                    searchService.searchTrades(regulator, null, null, null, null, 1, 10);

            assertEquals(1, result.getContent().size());
            assertEquals("华能发电有限公司", result.getContent().get(0).getSellerName());
            assertEquals("国网能源有限公司", result.getContent().get(0).getBuyerName());
            // 监管穿透不需要任何脱敏映射
            verify(enterpriseRepository, never()).findByUserIdInAndDeletedFalse(any());
        }
    }

    @Test
    @DisplayName("搜索交易记录-无当前用户（内部调用）时按脱敏口径返回，不泄露真实名称")
    void testSearchTradesAnonymousByDefault() {
        Page<Transaction> page = new PageImpl<>(List.of(testTransaction));
        when(transactionRepository.searchWithUserBound(eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);
        when(enterpriseRepository.findByUserIdInAndDeletedFalse(any()))
                .thenReturn(List.of(enterprise(11L, 1L), enterprise(22L, 2L)));

        Page<SearchResponse.TradeSummary> result = searchService.searchTrades(null, null, null, null, 1, 10);

        assertTrue(result.getContent().get(0).getSellerName().startsWith("匿名企业-"));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("搜索交易记录-拍卖挂单买方待定时名称为「待定」而非匿名别名")
    void testSearchTradesPendingBuyerName() {
        Transaction auctionOrder = Transaction.builder()
                .tradeNo("TX-AUCTION-001")
                .tradeType(1)
                .sellerId(1L)
                .buyerId(0L)
                .quantity(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("5000.00"))
                .status(0)
                .build();
        auctionOrder.setId(9L);

        when(transactionRepository.searchWithUserBound(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(auctionOrder)));
        when(enterpriseRepository.findByUserIdInAndDeletedFalse(any()))
                .thenReturn(List.of(enterprise(11L, 1L)));

        Page<SearchResponse.TradeSummary> result = searchService.searchTrades(null, null, null, null, 1, 10);

        assertEquals("待定", result.getContent().get(0).getBuyerName());
        assertTrue(result.getContent().get(0).getSellerName().startsWith("匿名企业-"));
    }

    private static Enterprise enterprise(Long enterpriseId, Long userId) {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);
        enterprise.setUserId(userId);
        return enterprise;
    }

    @Test
    @DisplayName("搜索碳报告-企业用户未关联企业时抛出权限拒绝异常")
    void testSearchCarbonReportsEnterpriseUserNoEnterprise() {
        JwtUserDetails entUser = JwtUserDetails.builder()
                .userId(100L)
                .username("enterprise001")
                .roles(List.of("ENTERPRISE"))
                .build();
        when(enterpriseRepository.findByUserId(100L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> searchService.searchCarbonReports(entUser, "test", null, null, 1, 10));
        assertEquals(ErrorCode.PERMISSION_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("搜索碳报告-企业用户强制限定为自身企业ID防跨租户窥探")
    void testSearchCarbonReportsEnterpriseUserForcedEnterpriseId() {
        JwtUserDetails entUser = JwtUserDetails.builder()
                .userId(100L)
                .username("enterprise001")
                .roles(List.of("ENTERPRISE"))
                .build();
        Enterprise mockEnterprise = new Enterprise();
        mockEnterprise.setId(88L);
        when(enterpriseRepository.findByUserId(100L)).thenReturn(Optional.of(mockEnterprise));

        Page<CarbonReport> page = new PageImpl<>(Arrays.asList(testReport));
        when(carbonReportRepository.search(eq(88L), eq(null), eq("test"), any(Pageable.class)))
                .thenReturn(page);

        // Even if attacker passes enterpriseId = 999L, targetEnterpriseId is forced to 88L
        Page<SearchResponse.CarbonReportSummary> result = 
            searchService.searchCarbonReports(entUser, "test", 999L, null, 1, 10);

        assertNotNull(result);
        verify(carbonReportRepository).search(eq(88L), eq(null), eq("test"), any(Pageable.class));
    }

    @Test
    @DisplayName("搜索交易记录-无结果")
    void testSearchTradesEmpty() {
        // Given
        Page<Transaction> page = new PageImpl<>(Collections.emptyList());
        when(transactionRepository.searchWithUserBound(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        // When
        Page<SearchResponse.TradeSummary> result = 
            searchService.searchTrades(null, null, null, null, 1, 10);

        // Then
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("获取市场概览成功")
    void testGetMarketOverviewSuccess() {
        // Given
        when(enterpriseRepository.count()).thenReturn(100L);
        when(carbonReportRepository.count()).thenReturn(500L);
        when(transactionRepository.count()).thenReturn(200L);
        when(transactionRepository.sumTotalQuantity()).thenReturn(new BigDecimal("125000.50"));
        when(transactionRepository.sumTotalAmount()).thenReturn(new BigDecimal("6250025.00"));
        when(carbonReportRepository.sumTotalEmission()).thenReturn(new BigDecimal("980000.00"));

        Map<String, Object> conn = Map.of(
                "connected", true,
                "mode", "MOCK",
                "channel", "carbon-channel",
                "chaincode", "carbon-chaincode",
                "peers", 4
        );
        when(blockchainService.checkConnection()).thenReturn(conn);

        // When
        SearchResponse.MarketOverview overview = searchService.getMarketOverview();

        // Then
        assertNotNull(overview);
        assertEquals(100L, overview.getTotalEnterprises());
        assertEquals(500L, overview.getTotalCarbonReports());
        assertEquals(200L, overview.getTotalTransactions());
        assertEquals(new BigDecimal("125000.50"), overview.getTotalTradingVolume());
        assertEquals(new BigDecimal("6250025.00"), overview.getTotalTradeAmount());
        assertEquals(new BigDecimal("980000.00"), overview.getTotalEmissionQuota());
        assertTrue(overview.getChainOnline());
        assertEquals("MOCK", overview.getChainMode());
        assertEquals("ONLINE", overview.getNetworkStatus());
        assertEquals(4, overview.getActiveNodes());
        assertEquals(11, overview.getAvgLatencyMs());
        assertNotNull(overview.getQueryTime());
    }

    @Test
    @DisplayName("获取市场概览-零数据")
    void testGetMarketOverviewZeroData() {
        // Given
        when(enterpriseRepository.count()).thenReturn(0L);
        when(carbonReportRepository.count()).thenReturn(0L);
        when(transactionRepository.count()).thenReturn(0L);
        when(transactionRepository.sumTotalQuantity()).thenReturn(null);
        when(transactionRepository.sumTotalAmount()).thenReturn(null);
        when(carbonReportRepository.sumTotalEmission()).thenReturn(null);
        when(blockchainService.checkConnection()).thenReturn(null);

        // When
        SearchResponse.MarketOverview overview = searchService.getMarketOverview();

        // Then
        assertNotNull(overview);
        assertEquals(0L, overview.getTotalEnterprises());
        assertEquals(0L, overview.getTotalCarbonReports());
        assertEquals(0L, overview.getTotalTransactions());
        assertEquals(BigDecimal.ZERO, overview.getTotalTradingVolume());
        assertEquals(BigDecimal.ZERO, overview.getTotalTradeAmount());
        assertEquals(BigDecimal.ZERO, overview.getTotalEmissionQuota());
    }
}
