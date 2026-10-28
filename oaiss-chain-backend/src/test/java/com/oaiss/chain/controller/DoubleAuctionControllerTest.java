package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.AuctionOrderRequest;
import com.oaiss.chain.dto.AuctionOrderResponse;
import com.oaiss.chain.dto.MatchingResultResponse;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.DoubleAuctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DoubleAuctionController.class)
@AutoConfigureMockMvc(addFilters = false)
class DoubleAuctionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DoubleAuctionService doubleAuctionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private JwtUserDetails enterpriseUser;
    private JwtUserDetails adminUser;
    private AuctionOrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        enterpriseUser = JwtUserDetails.builder()
                .userId(1L)
                .username("enterprise")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enterpriseId(1L)
                .enabled(true)
                .build();

        adminUser = JwtUserDetails.builder()
                .userId(2L)
                .username("admin")
                .userType(5)
                .roles(List.of("ADMIN"))
                .enabled(true)
                .build();

        orderResponse = AuctionOrderResponse.builder()
                .id(1L)
                .orderNo("AU20250101001")
                .direction(1)
                .price(new BigDecimal("100.00"))
                .quantity(new BigDecimal("1000"))
                .status(0)
                .createdAt(LocalDateTime.now())
                .build();

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("提交买入挂单成功")
    void testPlaceBuyOrderSuccess() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                enterpriseUser, null, List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuctionOrderRequest request = new AuctionOrderRequest();
        request.setDirection(1);
        request.setPrice(new BigDecimal("100.00"));
        request.setQuantity(new BigDecimal("1000"));

        when(doubleAuctionService.placeBuyOrder(any(JwtUserDetails.class), any(AuctionOrderRequest.class)))
                .thenReturn(orderResponse);

        mockMvc.perform(post("/auction/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(doubleAuctionService).placeBuyOrder(any(JwtUserDetails.class), any(AuctionOrderRequest.class));
    }

    @Test
    @DisplayName("提交买入挂单失败-负价格参数校验")
    void testPlaceBuyOrderValidationFailNegativePrice() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                enterpriseUser, null, List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuctionOrderRequest request = new AuctionOrderRequest();
        request.setDirection(1);
        request.setPrice(new BigDecimal("-1.00"));
        request.setQuantity(new BigDecimal("1000"));

        mockMvc.perform(post("/auction/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        verify(doubleAuctionService, never()).placeBuyOrder(any(), any());
    }

    @Test
    @DisplayName("提交卖出挂单成功")
    void testPlaceSellOrderSuccess() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                enterpriseUser, null, List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuctionOrderRequest request = new AuctionOrderRequest();
        request.setDirection(2);
        request.setPrice(new BigDecimal("100.00"));
        request.setQuantity(new BigDecimal("1000"));

        AuctionOrderResponse sellResponse = AuctionOrderResponse.builder()
                .id(2L)
                .orderNo("AU20250101002")
                .direction(2)
                .price(new BigDecimal("100.00"))
                .quantity(new BigDecimal("1000"))
                .status(0)
                .build();

        when(doubleAuctionService.placeSellOrder(any(JwtUserDetails.class), any(AuctionOrderRequest.class)))
                .thenReturn(sellResponse);

        mockMvc.perform(post("/auction/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.direction").value(2));

        verify(doubleAuctionService).placeSellOrder(any(JwtUserDetails.class), any(AuctionOrderRequest.class));
    }

    @Test
    @DisplayName("提交卖出挂单失败-零数量参数校验")
    void testPlaceSellOrderValidationFailZeroQuantity() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                enterpriseUser, null, List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuctionOrderRequest request = new AuctionOrderRequest();
        request.setDirection(2);
        request.setPrice(new BigDecimal("100.00"));
        request.setQuantity(BigDecimal.ZERO);

        mockMvc.perform(post("/auction/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        verify(doubleAuctionService, never()).placeSellOrder(any(), any());
    }

    @Test
    @DisplayName("执行撮合成功")
    void testExecuteMatchingSuccess() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                adminUser, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        MatchingResultResponse result = MatchingResultResponse.builder()
                .id(1L)
                .buyOrderId(1L)
                .sellOrderId(2L)
                .matchedQuantity(new BigDecimal("500"))
                .settlementPrice(new BigDecimal("100.00"))
                .build();

        when(doubleAuctionService.executeMatching()).thenReturn(List.of(result));

        mockMvc.perform(post("/auction/match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1));

        verify(doubleAuctionService).executeMatching();
    }

    @Test
    @DisplayName("查询挂单列表成功")
    void testListOrdersSuccess() throws Exception {
        Page<AuctionOrderResponse> page = new PageImpl<>(List.of(orderResponse));
        when(doubleAuctionService.listOrders(eq(null), eq(null), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/auction/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1));

        verify(doubleAuctionService).listOrders(null, null, 1, 20);
    }

    @Test
    @DisplayName("查询挂单列表-带筛选条件")
    void testListOrdersWithFilters() throws Exception {
        Page<AuctionOrderResponse> page = new PageImpl<>(List.of(orderResponse));
        when(doubleAuctionService.listOrders(eq(1), eq(0), eq(1), eq(10))).thenReturn(page);

        mockMvc.perform(get("/auction/orders")
                        .param("direction", "1")
                        .param("status", "0")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(doubleAuctionService).listOrders(1, 0, 1, 10);
    }

    @Test
    @DisplayName("查询我的挂单成功")
    void testListMyOrdersSuccess() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                enterpriseUser, null, List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Page<AuctionOrderResponse> page = new PageImpl<>(List.of(orderResponse));
        when(doubleAuctionService.listMyOrders(any(JwtUserDetails.class), eq(null), eq(null), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/auction/my-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(doubleAuctionService).listMyOrders(any(JwtUserDetails.class), eq(null), eq(null), eq(1), eq(20));
    }

    @Test
    @DisplayName("查询撮合结果成功")
    void testListMatchingResultsSuccess() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                enterpriseUser, null, List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        MatchingResultResponse result = MatchingResultResponse.builder()
                .id(1L)
                .buyOrderId(1L)
                .sellOrderId(2L)
                .matchedQuantity(new BigDecimal("500"))
                .settlementPrice(new BigDecimal("100.00"))
                .build();

        Page<MatchingResultResponse> page = new PageImpl<>(List.of(result));
        when(doubleAuctionService.listMatchingResults(any(JwtUserDetails.class), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/auction/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(doubleAuctionService).listMatchingResults(any(JwtUserDetails.class), eq(1), eq(20));
    }
}
