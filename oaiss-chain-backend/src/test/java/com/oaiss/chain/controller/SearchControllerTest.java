package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.SearchResponse;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchService searchService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private SearchResponse.CarbonReportSummary reportSummary;
    private SearchResponse.TradeSummary tradeSummary;
    private SearchResponse.MarketOverview marketOverview;

    @BeforeEach
    void setUp() {
        reportSummary = SearchResponse.CarbonReportSummary.builder()
                .id(1L)
                .reportNo("RPT-2024-001")
                .enterpriseId(1L)
                .title("2024年度碳报告")
                .status(2)
                .build();

        tradeSummary = SearchResponse.TradeSummary.builder()
                .id(1L)
                .tradeNo("TRD-2024-001")
                .tradeType(1)
                .sellerId(1L)
                .buyerId(2L)
                .quantity(new BigDecimal("1000"))
                .unitPrice(new BigDecimal("100.00"))
                .build();

        marketOverview = SearchResponse.MarketOverview.builder()
                .totalEnterprises(50L)
                .totalCarbonReports(100L)
                .totalTransactions(200L)
                .totalTradingVolume(new BigDecimal("50000.00"))
                .totalTradeAmount(new BigDecimal("2500000.00"))
                .totalEmissionQuota(new BigDecimal("120000.00"))
                .activeNodes(4)
                .nodeCount(3248L)
                .networkStatus("ONLINE")
                .networkAvailability("99.96%")
                .distributedAvailability("99.96%")
                .complianceGrade("A+")
                .avgLatencyMs(11)
                .averageLatencyMs(11)
                .maxLatencyMs(49)
                .chainOnline(true)
                .chainMode("MOCK")
                .channelName("carbon-channel")
                .blockHeight(10000L)
                .chaincode("carbon-chaincode")
                .build();
    }

    @Test
    @DisplayName("搜索碳报告成功")
    void testSearchReportsSuccess() throws Exception {
        Page<SearchResponse.CarbonReportSummary> page = new PageImpl<>(List.of(reportSummary));
        when(searchService.searchCarbonReports(any(), isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/search/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1));

        verify(searchService).searchCarbonReports(any(), isNull(), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("搜索碳报告-带筛选条件")
    void testSearchReportsWithFilters() throws Exception {
        Page<SearchResponse.CarbonReportSummary> page = new PageImpl<>(List.of(reportSummary));
        when(searchService.searchCarbonReports(any(), eq("年度"), eq(1L), eq(2), eq(1), eq(10))).thenReturn(page);

        mockMvc.perform(get("/search/reports")
                        .param("keyword", "年度")
                        .param("enterpriseId", "1")
                        .param("status", "2")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(searchService).searchCarbonReports(any(), eq("年度"), eq(1L), eq(2), eq(1), eq(10));
    }

    @Test
    @DisplayName("搜索交易记录成功")
    void testSearchTradesSuccess() throws Exception {
        Page<SearchResponse.TradeSummary> page = new PageImpl<>(List.of(tradeSummary));
        when(searchService.searchTrades(any(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/search/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1));

        verify(searchService).searchTrades(any(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("搜索交易记录-带筛选条件")
    void testSearchTradesWithFilters() throws Exception {
        Page<SearchResponse.TradeSummary> page = new PageImpl<>(List.of(tradeSummary));
        when(searchService.searchTrades(any(), eq(1L), eq(2L), eq(1), eq(1), eq(1), eq(10))).thenReturn(page);

        mockMvc.perform(get("/search/trades")
                        .param("sellerId", "1")
                        .param("buyerId", "2")
                        .param("tradeType", "1")
                        .param("status", "1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(searchService).searchTrades(any(), eq(1L), eq(2L), eq(1), eq(1), eq(1), eq(10));
    }

    @Test
    @DisplayName("获取市场概览成功")
    void testGetMarketOverviewSuccess() throws Exception {
        when(searchService.getMarketOverview()).thenReturn(marketOverview);

        mockMvc.perform(get("/search/market-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalEnterprises").value(50))
                .andExpect(jsonPath("$.data.totalCarbonReports").value(100))
                .andExpect(jsonPath("$.data.totalTransactions").value(200))
                .andExpect(jsonPath("$.data.totalTradingVolume").value(50000.00))
                .andExpect(jsonPath("$.data.totalTradeAmount").value(2500000.00))
                .andExpect(jsonPath("$.data.totalEmissionQuota").value(120000.00))
                .andExpect(jsonPath("$.data.activeNodes").value(4))
                .andExpect(jsonPath("$.data.nodeCount").value(3248))
                .andExpect(jsonPath("$.data.networkStatus").value("ONLINE"))
                .andExpect(jsonPath("$.data.networkAvailability").value("99.96%"))
                .andExpect(jsonPath("$.data.complianceGrade").value("A+"))
                .andExpect(jsonPath("$.data.avgLatencyMs").value(11))
                .andExpect(jsonPath("$.data.maxLatencyMs").value(49))
                .andExpect(jsonPath("$.data.chainOnline").value(true))
                .andExpect(jsonPath("$.data.chainMode").value("MOCK"))
                .andExpect(jsonPath("$.data.channelName").value("carbon-channel"))
                .andExpect(jsonPath("$.data.blockHeight").value(10000))
                .andExpect(jsonPath("$.data.chaincode").value("carbon-chaincode"));

        verify(searchService).getMarketOverview();
    }

    @Test
    @DisplayName("验证接口授权控制：敏感接口受 isAuthenticated 保护，大盘概览允许匿名访问")
    void testSecurityAnnotations() throws NoSuchMethodException {
        // 类级别不应有 @PreAuthorize 全局拦截，确保公开方法可被匿名访问
        org.junit.jupiter.api.Assertions.assertFalse(
                SearchController.class.isAnnotationPresent(org.springframework.security.access.prepost.PreAuthorize.class));

        // /reports 与 /trades 必须标注 @PreAuthorize("isAuthenticated()") 保护多租户数据隔离
        var reportsMethod = SearchController.class.getMethod("searchReports",
                com.oaiss.chain.security.JwtUserDetails.class, String.class, Long.class, Integer.class, Integer.class, Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(
                reportsMethod.isAnnotationPresent(org.springframework.security.access.prepost.PreAuthorize.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                "isAuthenticated()",
                reportsMethod.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value());

        var tradesMethod = SearchController.class.getMethod("searchTrades",
                com.oaiss.chain.security.JwtUserDetails.class, Long.class, Long.class, Integer.class, Integer.class, Integer.class, Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(
                tradesMethod.isAnnotationPresent(org.springframework.security.access.prepost.PreAuthorize.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                "isAuthenticated()",
                tradesMethod.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value());

        // /market-overview 不得有 @PreAuthorize，确保匿名访客直通
        var overviewMethod = SearchController.class.getMethod("getMarketOverview");
        org.junit.jupiter.api.Assertions.assertFalse(
                overviewMethod.isAnnotationPresent(org.springframework.security.access.prepost.PreAuthorize.class));
    }
}