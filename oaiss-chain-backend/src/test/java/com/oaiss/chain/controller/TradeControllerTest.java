package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.*;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TradeController Unit Tests
 * 碳交易控制器单元测试
 */
@WebMvcTest(controllers = TradeController.class)
@AutoConfigureMockMvc(addFilters = false)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradeService tradeService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private JwtUserDetails enterpriseUser;
    private JwtUserDetails adminUser;
    private JwtUserDetails thirdPartyUser;
    private TradeRequest tradeRequest;
    private TradeResponse tradeResponse;

    @BeforeEach
    void setUp() {
        enterpriseUser = JwtUserDetails.builder()
                .userId(1L)
                .username("enterprise_user")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enterpriseId(1L)
                .enabled(true)
                .build();

        adminUser = JwtUserDetails.builder()
                .userId(3L)
                .username("admin_user")
                .userType(4)
                .roles(List.of("ADMIN"))
                .enabled(true)
                .build();

        thirdPartyUser = JwtUserDetails.builder()
                .userId(4L)
                .username("third_party_user")
                .userType(5)
                .roles(List.of("THIRD_PARTY"))
                .enabled(true)
                .build();

        tradeRequest = TradeRequest.builder()
                .tradeType(1)
                .buyerId(2L)
                .sellerId(1L)
                .quantity(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("50.00"))
                .remark("P2P交易测试")
                .build();

        tradeResponse = TradeResponse.builder()
                .id(1L)
                .tradeNo("TR-2024-001")
                .tradeType(1)
                .tradeTypeText("P2P交易")
                .sellerId(1L)
                .sellerName("卖方企业")
                .buyerId(2L)
                .buyerName("买方企业")
                .quantity(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("5000.00"))
                .status(0)
                .statusText("待审核")
                .remark("P2P交易测试")
                .createdAt(LocalDateTime.now())
                .build();

        SecurityContextHolder.clearContext();
    }

    // ==================== Create P2P Trade Tests ====================

    @Test
    @DisplayName("创建P2P交易成功测试")
    void testCreateP2PTradeSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(tradeService.createP2PTrade(any(JwtUserDetails.class), any(TradeRequest.class)))
                .thenReturn(tradeResponse);

        // When & Then
        mockMvc.perform(post("/trade/p2p")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("交易创建成功"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.tradeNo").value("TR-2024-001"));

        verify(tradeService, times(1)).createP2PTrade(any(JwtUserDetails.class), any(TradeRequest.class));
    }

    @Test
    @DisplayName("创建P2P交易失败-无权限")
    void testCreateP2PTradeForbidden() throws Exception {
        // Given - Admin cannot create P2P trades directly
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        when(tradeService.createP2PTrade(any(JwtUserDetails.class), any(TradeRequest.class)))
                .thenThrow(new BusinessException(403, "无权限创建P2P交易"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(post("/trade/p2p")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(tradeService, times(1)).createP2PTrade(any(JwtUserDetails.class), any(TradeRequest.class));
    }

    @Test
    @DisplayName("创建P2P交易失败-碳配额不足")
    void testCreateP2PTradeFailInsufficientQuota() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(tradeService.createP2PTrade(any(JwtUserDetails.class), any(TradeRequest.class)))
                .thenThrow(new BusinessException(400, "碳配额不足"));

        // When & Then
        mockMvc.perform(post("/trade/p2p")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeRequest)))
                .andExpect(status().isBadRequest());

        verify(tradeService, times(1)).createP2PTrade(any(JwtUserDetails.class), any(TradeRequest.class));
    }

    @Test
    @DisplayName("创建P2P交易失败-参数验证失败-交易类型为空")
    void testCreateP2PTradeValidationFailTypeNull() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TradeRequest invalidRequest = TradeRequest.builder()
                .tradeType(null)
                .quantity(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("50.00"))
                .build();

        // When & Then
        mockMvc.perform(post("/trade/p2p")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(tradeService, never()).createP2PTrade(any(), any());
    }

    @Test
    @DisplayName("创建P2P交易失败-参数验证失败-数量为空")
    void testCreateP2PTradeValidationFailQuantityNull() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TradeRequest invalidRequest = TradeRequest.builder()
                .tradeType(1)
                .quantity(null)
                .unitPrice(new BigDecimal("50.00"))
                .build();

        // When & Then
        mockMvc.perform(post("/trade/p2p")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(tradeService, never()).createP2PTrade(any(), any());
    }

    @Test
    @DisplayName("创建P2P交易失败-参数验证失败-单价为负数")
    void testCreateP2PTradeValidationFailNegativePrice() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TradeRequest invalidRequest = TradeRequest.builder()
                .tradeType(1)
                .quantity(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("-10.00"))
                .build();

        // When & Then
        mockMvc.perform(post("/trade/p2p")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(tradeService, never()).createP2PTrade(any(), any());
    }

    // ==================== Create Auction Order Tests ====================

    @Test
    @DisplayName("创建拍卖挂单成功测试")
    void testCreateAuctionOrderSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TradeResponse auctionResponse = TradeResponse.builder()
                .id(2L)
                .tradeNo("AU-2024-001")
                .tradeType(2)
                .tradeTypeText("拍卖")
                .status(0)
                .build();

        when(tradeService.createAuctionOrder(any(JwtUserDetails.class), any(TradeRequest.class)))
                .thenReturn(auctionResponse);

        TradeRequest auctionRequest = TradeRequest.builder()
                .tradeType(2)
                .quantity(new BigDecimal("200.00"))
                .unitPrice(new BigDecimal("60.00"))
                .build();

        // When & Then
        mockMvc.perform(post("/trade/auction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auctionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("挂单创建成功"))
                .andExpect(jsonPath("$.data.tradeType").value(2));

        verify(tradeService, times(1)).createAuctionOrder(any(JwtUserDetails.class), any(TradeRequest.class));
    }

    @Test
    @DisplayName("创建拍卖挂单失败-无权限")
    void testCreateAuctionOrderForbidden() throws Exception {
        // Given
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(thirdPartyUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_THIRD_PARTY")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TradeRequest auctionRequest = TradeRequest.builder()
                .tradeType(2)
                .quantity(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("50.00"))
                .build();

        // Mock service to throw authorization exception
        when(tradeService.createAuctionOrder(any(JwtUserDetails.class), any(TradeRequest.class)))
                .thenThrow(new BusinessException(403, "无权限创建拍卖挂单"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(post("/trade/auction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auctionRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(tradeService, times(1)).createAuctionOrder(any(JwtUserDetails.class), any(TradeRequest.class));
    }

    // ==================== Confirm Trade Tests ====================

    @Test
    @DisplayName("确认交易成功测试")
    void testConfirmTradeSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TradeResponse confirmedResponse = TradeResponse.builder()
                .id(1L)
                .status(1)
                .statusText("已完成")
                .completedAt(LocalDateTime.now())
                .build();

        when(tradeService.confirmTrade(1L, 1L)).thenReturn(confirmedResponse);

        // When & Then
        mockMvc.perform(post("/trade/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("交易完成"))
                .andExpect(jsonPath("$.data.status").value(1));

        verify(tradeService, times(1)).confirmTrade(1L, 1L);
    }

    @Test
    @DisplayName("确认交易失败-交易状态不允许确认")
    void testConfirmTradeFailInvalidStatus() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(tradeService.confirmTrade(1L, 1L))
                .thenThrow(new BusinessException(400, "交易状态不允许确认"));

        // When & Then
        mockMvc.perform(post("/trade/1/confirm"))
                .andExpect(status().isBadRequest());

        verify(tradeService, times(1)).confirmTrade(1L, 1L);
    }

    @Test
    @DisplayName("确认交易失败-交易不存在")
    void testConfirmTradeFailNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(tradeService.confirmTrade(999L, 1L))
                .thenThrow(new BusinessException(404, "交易不存在"));

        // When & Then - GlobalExceptionHandler converts BusinessException to HTTP 400
        mockMvc.perform(post("/trade/999/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(tradeService, times(1)).confirmTrade(999L, 1L);
    }

    // ==================== Cancel Trade Tests ====================

    @Test
    @DisplayName("取消交易成功测试")
    void testCancelTradeSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TradeResponse cancelledResponse = TradeResponse.builder()
                .id(1L)
                .status(2)
                .statusText("已取消")
                .build();

        when(tradeService.cancelTrade(eq(1L), any(JwtUserDetails.class)))
                .thenReturn(cancelledResponse);

        // When & Then
        mockMvc.perform(post("/trade/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("交易已取消"))
                .andExpect(jsonPath("$.data.status").value(2));

        verify(tradeService, times(1)).cancelTrade(eq(1L), any(JwtUserDetails.class));
    }

    @Test
    @DisplayName("取消交易失败-交易状态不允许取消")
    void testCancelTradeFailInvalidStatus() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(tradeService.cancelTrade(eq(1L), any(JwtUserDetails.class)))
                .thenThrow(new BusinessException(400, "已完成的交易不可取消"));

        // When & Then
        mockMvc.perform(post("/trade/1/cancel"))
                .andExpect(status().isBadRequest());

        verify(tradeService, times(1)).cancelTrade(eq(1L), any(JwtUserDetails.class));
    }

    @Test
    @DisplayName("取消交易失败-无权限")
    void testCancelTradeFailForbidden() throws Exception {
        // Given - Third party cannot cancel trades
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(thirdPartyUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_THIRD_PARTY")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        when(tradeService.cancelTrade(eq(1L), any(JwtUserDetails.class)))
                .thenThrow(new BusinessException(403, "无权限取消交易"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(post("/trade/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(tradeService, times(1)).cancelTrade(eq(1L), any(JwtUserDetails.class));
    }

    @Test
    @DisplayName("取消交易失败-非交易发起方")
    void testCancelTradeFailNotInitiator() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(tradeService.cancelTrade(eq(1L), any(JwtUserDetails.class)))
                .thenThrow(new BusinessException(403, "无权限取消此交易"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(post("/trade/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(tradeService, times(1)).cancelTrade(eq(1L), any(JwtUserDetails.class));
    }

    // ==================== Get Trade Tests ====================

    @Test
    @DisplayName("获取交易详情成功测试")
    void testGetTradeSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(tradeService.getTrade(eq(1L), any(JwtUserDetails.class)))
                .thenReturn(tradeResponse);

        // When & Then
        mockMvc.perform(get("/trade/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.tradeNo").value("TR-2024-001"));

        verify(tradeService, times(1)).getTrade(eq(1L), any(JwtUserDetails.class));
    }

    @Test
    @DisplayName("获取交易详情失败-交易不存在")
    void testGetTradeFailNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(tradeService.getTrade(eq(999L), any(JwtUserDetails.class)))
                .thenThrow(new BusinessException(404, "交易不存在"));

        // When & Then - GlobalExceptionHandler converts BusinessException to HTTP 400
        mockMvc.perform(get("/trade/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(tradeService, times(1)).getTrade(eq(999L), any(JwtUserDetails.class));
    }

    // ==================== List Trades Tests ====================

    @Test
    @DisplayName("查询交易列表成功测试")
    void testListTradesSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<TradeResponse> page = new PageImpl<>(List.of(tradeResponse));
        when(tradeService.listTrades(isNull(), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/trade/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").value(1));

        verify(tradeService, times(1)).listTrades(isNull(), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询交易列表-带筛选条件")
    void testListTradesWithFilters() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(thirdPartyUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_THIRD_PARTY")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<TradeResponse> page = new PageImpl<>(List.of(tradeResponse));
        when(tradeService.listTrades(eq(1L), eq(2L), eq(1), eq(1), eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/trade/list")
                        .param("sellerId", "1")
                        .param("buyerId", "2")
                        .param("tradeType", "1")
                        .param("status", "1")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(tradeService, times(1)).listTrades(eq(1L), eq(2L), eq(1), eq(1), eq(1), eq(20));
    }

    @Test
    @DisplayName("查询交易列表失败-无权限")
    void testListTradesForbidden() throws Exception {
        // Given - Enterprise cannot list all trades
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        when(tradeService.listTrades(isNull(), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenThrow(new BusinessException(403, "无权限查询所有交易"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(get("/trade/list"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(tradeService, times(1)).listTrades(isNull(), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    // ==================== My Trades Tests ====================

    @Test
    @DisplayName("查询我的交易成功测试")
    void testListMyTradesSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<TradeResponse> page = new PageImpl<>(List.of(tradeResponse));
        when(tradeService.listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/trade/my-trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());

        verify(tradeService, times(1)).listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询我的交易-带筛选条件")
    void testListMyTradesWithFilters() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<TradeResponse> page = new PageImpl<>(List.of(tradeResponse));
        when(tradeService.listMyTrades(
                any(JwtUserDetails.class), eq(1), eq(1), eq("TR-2024"), eq("Buyer"), eq("seller"), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/trade/my-trades")
                        .param("tradeType", "1")
                        .param("status", "1")
                        .param("tradeNo", "TR-2024")
                        .param("keyword", "Buyer")
                        .param("identity", "seller")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(tradeService, times(1)).listMyTrades(
                any(JwtUserDetails.class), eq(1), eq(1), eq("TR-2024"), eq("Buyer"), eq("seller"), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("查询我的交易失败-无权限")
    void testListMyTradesForbidden() throws Exception {
        // Given - Admin cannot access my-trades (admin uses /list)
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(adminUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        when(tradeService.listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenThrow(new BusinessException(403, "无权限查询我的交易"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(get("/trade/my-trades"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(tradeService, times(1)).listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    // ==================== Branch Coverage: parseDateTime ====================

    @Test
    @DisplayName("查询我的交易-传入日期格式startTime应解析为当天零点")
    void testListMyTrades_DateOnlyStartTime() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<TradeResponse> page = new PageImpl<>(List.of(tradeResponse));
        // parseDateTime("2024-06-15") -> LocalDate.parse -> 2024-06-15T00:00
        when(tradeService.listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(java.time.LocalDateTime.of(2024, 6, 15, 0, 0, 0)), isNull(), eq(1), eq(10)))
                .thenReturn(page);

        mockMvc.perform(get("/trade/my-trades")
                        .param("startTime", "2024-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(tradeService).listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(java.time.LocalDateTime.of(2024, 6, 15, 0, 0, 0)), isNull(), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询我的交易-传入无效时间字符串应解析为null")
    void testListMyTrades_InvalidTimeFormat() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<TradeResponse> page = new PageImpl<>(List.of(tradeResponse));
        // parseDateTime("not-a-date") -> fails both patterns -> returns null
        when(tradeService.listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(page);

        mockMvc.perform(get("/trade/my-trades")
                        .param("startTime", "not-a-date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(tradeService).listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询我的交易-传入完整日期时间格式应正确解析")
    void testListMyTrades_FullDateTimeFormat() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<TradeResponse> page = new PageImpl<>(List.of(tradeResponse));
        java.time.LocalDateTime expectedStart = java.time.LocalDateTime.of(2024, 6, 15, 10, 30, 0);
        java.time.LocalDateTime expectedEnd = java.time.LocalDateTime.of(2024, 6, 20, 18, 0, 0);
        when(tradeService.listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(expectedStart), eq(expectedEnd), eq(1), eq(10)))
                .thenReturn(page);

        mockMvc.perform(get("/trade/my-trades")
                        .param("startTime", "2024-06-15 10:30:00")
                        .param("endTime", "2024-06-20 18:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(tradeService).listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(expectedStart), eq(expectedEnd), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询我的交易-传入空白字符串时间应视为null")
    void testListMyTrades_BlankTimeString() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<TradeResponse> page = new PageImpl<>(List.of(tradeResponse));
        // parseDateTime("   ") -> isBlank() -> returns null
        when(tradeService.listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(page);

        mockMvc.perform(get("/trade/my-trades")
                        .param("startTime", "   ")
                        .param("endTime", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(tradeService).listMyTrades(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1), eq(10));
    }
}
