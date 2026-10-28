package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.SearchResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Transaction;
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
