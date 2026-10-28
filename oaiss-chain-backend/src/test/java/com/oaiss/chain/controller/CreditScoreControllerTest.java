package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.CreditDeductionRequest;
import com.oaiss.chain.dto.CreditEventResponse;
import com.oaiss.chain.dto.CreditScoreResponse;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CreditScoreService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CreditScoreController Unit Tests
 * 信誉评分控制器单元测试
 */
@WebMvcTest(controllers = CreditScoreController.class)
@AutoConfigureMockMvc(addFilters = false)
class CreditScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreditScoreService creditScoreService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private JwtUserDetails adminUserDetails;
    private JwtUserDetails reviewerUserDetails;
    private JwtUserDetails enterpriseUserDetails;
    private CreditScoreResponse creditScoreResponse;

    @BeforeEach
    void setUp() {
        adminUserDetails = JwtUserDetails.builder()
                .userId(1L)
                .username("admin")
                .userType(1)
                .roles(List.of("ADMIN"))
                .enterpriseId(1L)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        reviewerUserDetails = JwtUserDetails.builder()
                .userId(2L)
                .username("reviewer")
                .userType(1)
                .roles(List.of("REVIEWER"))
                .enterpriseId(2L)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        enterpriseUserDetails = JwtUserDetails.builder()
                .userId(3L)
                .username("enterprise")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enterpriseId(3L)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        creditScoreResponse = CreditScoreResponse.builder()
                .id(1L)
                .enterpriseId(1L)
                .enterpriseName("Test Enterprise")
                .score(100)
                .level("EXCELLENT")
                .tradeRestricted(false)
                .accountFrozen(false)
                .lastEvaluatedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now().minusDays(30))
                .build();

        SecurityContextHolder.clearContext();
    }

    // ==================== Get Score Tests ====================

    @Test
    @DisplayName("查询企业信誉分-成功测试")
    void testGetScoreSuccess() throws Exception {
        // Given
        when(creditScoreService.getScore(1L)).thenReturn(creditScoreResponse);

        // When & Then
        mockMvc.perform(get("/credit/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enterpriseId").value(1))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.level").value("EXCELLENT"));

        verify(creditScoreService, times(1)).getScore(1L);
    }

    @Test
    @DisplayName("查询企业信誉分-企业不存在")
    void testGetScoreEnterpriseNotFound() throws Exception {
        // Given
        when(creditScoreService.getScore(999L))
                .thenThrow(new BusinessException(404, "企业不存在"));

        // When & Then
        mockMvc.perform(get("/credit/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(creditScoreService, times(1)).getScore(999L);
    }

    // ==================== Get Credit History Tests ====================

    @Test
    @DisplayName("查询信誉事件历史-成功测试")
    void testGetCreditHistorySuccess() throws Exception {
        // Given
        CreditEventResponse eventResponse = CreditEventResponse.builder()
                .id(1L)
                .enterpriseId(1L)
                .eventType(1)
                .eventTypeName("数据造假")
                .eventDescription("提交虚假数据")
                .pointsChanged(-20)
                .scoreBefore(100)
                .scoreAfter(80)
                .triggeredBy(1L)
                .triggeredByName("Admin User")
                .triggeredAt(LocalDateTime.now())
                .build();

        Page<CreditEventResponse> page = new PageImpl<>(List.of(eventResponse), PageRequest.of(0, 20), 1);
        when(creditScoreService.getCreditHistory(eq(1L), eq(null), eq(1), eq(20))).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/credit/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].enterpriseId").value(1));

        verify(creditScoreService, times(1)).getCreditHistory(1L, null, 1, 20);
    }

    @Test
    @DisplayName("查询信誉事件历史-按事件类型筛选")
    void testGetCreditHistoryFilterByEventType() throws Exception {
        // Given
        CreditEventResponse eventResponse = CreditEventResponse.builder()
                .id(1L)
                .enterpriseId(1L)
                .eventType(1)
                .eventTypeName("数据造假")
                .eventDescription("提交虚假数据")
                .pointsChanged(-20)
                .scoreBefore(100)
                .scoreAfter(80)
                .triggeredBy(1L)
                .triggeredByName("Admin User")
                .triggeredAt(LocalDateTime.now())
                .build();

        Page<CreditEventResponse> page = new PageImpl<>(List.of(eventResponse), PageRequest.of(0, 20), 1);
        when(creditScoreService.getCreditHistory(eq(1L), eq(1), eq(1), eq(20))).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/credit/1/history")
                        .param("eventType", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].eventType").value(1));

        verify(creditScoreService, times(1)).getCreditHistory(1L, 1, 1, 20);
    }

    // ==================== Deduct Points Tests ====================

    @Test
    @DisplayName("扣除信誉分-成功测试-管理员")
    void testDeductPointsSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreditDeductionRequest request = new CreditDeductionRequest();
        request.setEnterpriseId(1L);
        request.setEventType(1);
        request.setDescription("数据造假");
        request.setRelatedReportId(100L);

        CreditScoreResponse response = CreditScoreResponse.builder()
                .id(1L)
                .enterpriseId(1L)
                .enterpriseName("Test Enterprise")
                .score(80)
                .level("GOOD")
                .tradeRestricted(false)
                .accountFrozen(false)
                .build();

        when(creditScoreService.deductPoints(eq(1L), eq(1), eq("数据造假"), eq(1L), eq(100L)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/credit/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(80));

        verify(creditScoreService, times(1)).deductPoints(1L, 1, "数据造假", 1L, 100L);
    }

    @Test
    @DisplayName("扣除信誉分-成功测试-审核员")
    void testDeductPointsSuccessReviewer() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreditDeductionRequest request = new CreditDeductionRequest();
        request.setEnterpriseId(1L);
        request.setEventType(2);
        request.setDescription("迟交报告");

        CreditScoreResponse response = CreditScoreResponse.builder()
                .id(1L)
                .enterpriseId(1L)
                .enterpriseName("Test Enterprise")
                .score(95)
                .level("EXCELLENT")
                .tradeRestricted(false)
                .accountFrozen(false)
                .build();

        when(creditScoreService.deductPoints(eq(1L), eq(2), eq("迟交报告"), eq(2L), eq(null)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/credit/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(95));

        verify(creditScoreService, times(1)).deductPoints(1L, 2, "迟交报告", 2L, null);
    }

    // ==================== Add Bonus Tests ====================

    @Test
    @DisplayName("添加信誉奖励分-成功测试")
    void testAddBonusSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreditScoreResponse response = CreditScoreResponse.builder()
                .id(1L)
                .enterpriseId(1L)
                .enterpriseName("Test Enterprise")
                .score(100)
                .level("EXCELLENT")
                .tradeRestricted(false)
                .accountFrozen(false)
                .build();

        when(creditScoreService.addBonusPoints(eq(1L), eq(10), eq("积极参与碳交易"), eq(1L)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/credit/bonus")
                        .param("enterpriseId", "1")
                        .param("points", "10")
                        .param("description", "积极参与碳交易"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(100));

        verify(creditScoreService, times(1)).addBonusPoints(1L, 10, "积极参与碳交易", 1L);
    }

    // ==================== Evaluate Level Tests ====================

    @Test
    @DisplayName("重新评估信誉等级-成功测试")
    void testEvaluateLevelSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreditScoreResponse response = CreditScoreResponse.builder()
                .id(1L)
                .enterpriseId(1L)
                .enterpriseName("Test Enterprise")
                .score(85)
                .level("GOOD")
                .tradeRestricted(false)
                .accountFrozen(false)
                .lastEvaluatedAt(LocalDateTime.now())
                .build();

        when(creditScoreService.evaluateLevel(1L)).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/credit/evaluate/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.level").value("GOOD"));

        verify(creditScoreService, times(1)).evaluateLevel(1L);
    }

    // ==================== Get Restricted Enterprises Tests ====================

    @Test
    @DisplayName("查询被限制交易的企业-成功测试")
    void testGetRestrictedEnterprisesSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreditScoreResponse restrictedResponse = CreditScoreResponse.builder()
                .id(2L)
                .enterpriseId(2L)
                .enterpriseName("Restricted Enterprise")
                .score(35)
                .level("POOR")
                .tradeRestricted(true)
                .accountFrozen(false)
                .build();

        when(creditScoreService.getRestrictedEnterprises()).thenReturn(List.of(restrictedResponse));

        // When & Then
        mockMvc.perform(get("/credit/restricted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].tradeRestricted").value(true));

        verify(creditScoreService, times(1)).getRestrictedEnterprises();
    }

    // ==================== Get Frozen Enterprises Tests ====================

    @Test
    @DisplayName("查询被冻结的企业-成功测试")
    void testGetFrozenEnterprisesSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreditScoreResponse frozenResponse = CreditScoreResponse.builder()
                .id(3L)
                .enterpriseId(3L)
                .enterpriseName("Frozen Enterprise")
                .score(15)
                .level("CRITICAL")
                .tradeRestricted(true)
                .accountFrozen(true)
                .build();

        when(creditScoreService.getFrozenEnterprises()).thenReturn(List.of(frozenResponse));

        // When & Then
        mockMvc.perform(get("/credit/frozen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].accountFrozen").value(true));

        verify(creditScoreService, times(1)).getFrozenEnterprises();
    }

    // ==================== Check Trade Permission Tests ====================

    @Test
    @DisplayName("检查交易权限-允许交易")
    void testCheckTradePermissionAllowed() throws Exception {
        // Given
        when(creditScoreService.checkTradePermission(1L)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/credit/check-permission/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(creditScoreService, times(1)).checkTradePermission(1L);
    }

    @Test
    @DisplayName("检查交易权限-禁止交易")
    void testCheckTradePermissionDenied() throws Exception {
        // Given
        when(creditScoreService.checkTradePermission(2L)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/credit/check-permission/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));

        verify(creditScoreService, times(1)).checkTradePermission(2L);
    }

    // ==================== Get My Score Tests ====================

    @Test
    @DisplayName("获取当前用户企业信誉分-成功测试")
    void testGetMyScoreSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(creditScoreService.getScoreByUserId(3L)).thenReturn(creditScoreResponse);

        // When & Then
        mockMvc.perform(get("/credit/my-score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.level").value("EXCELLENT"));

        verify(creditScoreService, times(1)).getScoreByUserId(3L);
    }

    // ==================== Get My Credit History Tests ====================

    @Test
    @DisplayName("获取当前用户企业信誉历史-成功测试")
    void testGetMyCreditHistorySuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreditEventResponse eventResponse = CreditEventResponse.builder()
                .id(1L)
                .enterpriseId(3L)
                .eventType(1)
                .eventTypeName("数据造假")
                .eventDescription("提交虚假数据")
                .pointsChanged(-20)
                .scoreBefore(100)
                .scoreAfter(80)
                .triggeredBy(1L)
                .triggeredByName("Admin User")
                .triggeredAt(LocalDateTime.now())
                .build();

        Page<CreditEventResponse> page = new PageImpl<>(List.of(eventResponse), PageRequest.of(0, 20), 1);
        when(creditScoreService.getCreditHistoryByUserId(eq(3L), isNull(), eq(1), eq(20))).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/credit/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].enterpriseId").value(3));

        verify(creditScoreService, times(1)).getCreditHistoryByUserId(3L, null, 1, 20);
    }

    @Test
    @DisplayName("获取当前用户企业信誉历史-按事件类型筛选")
    void testGetMyCreditHistoryFilterByEventType() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CreditEventResponse eventResponse = CreditEventResponse.builder()
                .id(2L)
                .enterpriseId(3L)
                .eventType(2)
                .eventTypeName("迟交报告")
                .eventDescription("报告提交超过期限")
                .pointsChanged(-10)
                .scoreBefore(80)
                .scoreAfter(70)
                .triggeredBy(1L)
                .triggeredByName("Admin User")
                .triggeredAt(LocalDateTime.now())
                .build();

        Page<CreditEventResponse> page = new PageImpl<>(List.of(eventResponse), PageRequest.of(0, 20), 1);
        when(creditScoreService.getCreditHistoryByUserId(eq(3L), eq(2), eq(1), eq(20))).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/credit/history")
                        .param("eventType", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].eventType").value(2));

        verify(creditScoreService, times(1)).getCreditHistoryByUserId(3L, 2, 1, 20);
    }

    // ==================== Get Score Ranking Tests ====================

    @Test
    @DisplayName("获取信誉排名列表-成功测试")
    void testGetScoreRankingSuccess() throws Exception {
        // Given
        CreditScoreResponse rankingResponse = CreditScoreResponse.builder()
                .id(1L)
                .enterpriseId(1L)
                .enterpriseName("Top Enterprise")
                .score(100)
                .level("EXCELLENT")
                .tradeRestricted(false)
                .accountFrozen(false)
                .build();

        Page<CreditScoreResponse> page = new PageImpl<>(List.of(rankingResponse), PageRequest.of(0, 20), 1);
        when(creditScoreService.getScoreRanking(1, 20)).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/credit/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].score").value(100));

        verify(creditScoreService, times(1)).getScoreRanking(1, 20);
    }
}
