package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.ThirdPartyOrg;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.ThirdPartyService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ThirdPartyController Unit Tests
 * 第三方监管控制器单元测试
 */
@WebMvcTest(controllers = ThirdPartyController.class)
@AutoConfigureMockMvc(addFilters = false)
class ThirdPartyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ThirdPartyService thirdPartyService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private JwtUserDetails thirdPartyUser;
    private ThirdPartyOrg testOrg;

    @BeforeEach
    void setUp() {
        thirdPartyUser = JwtUserDetails.builder()
                .userId(4L)
                .username("third_party_user")
                .userType(5)
                .roles(List.of("THIRD_PARTY"))
                .enabled(true)
                .build();

        testOrg = ThirdPartyOrg.builder()
                .userId(4L)
                .orgName("测试监管机构")
                .orgCode("TP-001")
                .orgType(1)
                .contactPerson("张三")
                .contactPhone("13800138000")
                .accessLevel(1)
                .status(1)
                .build();

        SecurityContextHolder.clearContext();
    }

    private void setAuthentication() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(thirdPartyUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_THIRD_PARTY")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // ==================== GET /third-party/org-info Tests ====================

    @Test
    @DisplayName("获取机构信息成功")
    void testGetOrgInfoSuccess() throws Exception {
        // Given
        setAuthentication();
        when(thirdPartyService.getCurrentOrg(any(JwtUserDetails.class)))
                .thenReturn(testOrg);

        // When & Then
        mockMvc.perform(get("/third-party/org-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orgName").value("测试监管机构"))
                .andExpect(jsonPath("$.data.orgCode").value("TP-001"));

        verify(thirdPartyService, times(1)).getCurrentOrg(any(JwtUserDetails.class));
    }

    @Test
    @DisplayName("获取机构信息失败-机构不存在")
    void testGetOrgInfoNotFound() throws Exception {
        // Given
        setAuthentication();
        when(thirdPartyService.getCurrentOrg(any(JwtUserDetails.class)))
                .thenThrow(new BusinessException(1002, "第三方机构信息不存在"));

        // When & Then
        mockMvc.perform(get("/third-party/org-info"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1002));

        verify(thirdPartyService, times(1)).getCurrentOrg(any(JwtUserDetails.class));
    }

    // ==================== GET /third-party/carbon-reports Tests ====================

    @Test
    @DisplayName("查询碳报告成功-默认分页")
    void testQueryCarbonReportsDefaultPaging() throws Exception {
        // Given
        setAuthentication();
        CarbonReport report = CarbonReport.builder()
                .reportNo("CR-2024-001")
                .enterpriseId(1L)
                .title("2024年度碳报告")
                .build();
        Page<CarbonReport> page = new PageImpl<>(List.of(report), PageRequest.of(0, 10), 1);

        when(thirdPartyService.queryCarbonReports(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(ApiResponse.success(page));

        // When & Then
        mockMvc.perform(get("/third-party/carbon-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].reportNo").value("CR-2024-001"));

        verify(thirdPartyService, times(1)).queryCarbonReports(
                any(JwtUserDetails.class), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询碳报告成功-带筛选条件")
    void testQueryCarbonReportsWithFilters() throws Exception {
        // Given
        setAuthentication();
        Page<CarbonReport> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        when(thirdPartyService.queryCarbonReports(
                any(JwtUserDetails.class), eq(1L), eq(1), eq("年度"), eq(2), eq(5)))
                .thenReturn(ApiResponse.success(page));

        // When & Then
        mockMvc.perform(get("/third-party/carbon-reports")
                        .param("enterpriseId", "1")
                        .param("status", "1")
                        .param("keyword", "年度")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(thirdPartyService, times(1)).queryCarbonReports(
                any(JwtUserDetails.class), eq(1L), eq(1), eq("年度"), eq(2), eq(5));
    }

    // ==================== GET /third-party/statistics Tests ====================

    @Test
    @DisplayName("获取统计数据成功")
    void testGetStatisticsSuccess() throws Exception {
        // Given
        setAuthentication();
        Map<String, Object> stats = Map.of(
                "orgName", "测试监管机构",
                "accessLevel", 1,
                "totalReports", 50L,
                "pendingReports", 5L
        );
        when(thirdPartyService.getStatistics(any(JwtUserDetails.class)))
                .thenReturn(ApiResponse.success(stats));

        // When & Then
        mockMvc.perform(get("/third-party/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orgName").value("测试监管机构"))
                .andExpect(jsonPath("$.data.totalReports").value(50));

        verify(thirdPartyService, times(1)).getStatistics(any(JwtUserDetails.class));
    }

    @Test
    @DisplayName("获取统计数据失败-机构不存在")
    void testGetStatisticsNotFound() throws Exception {
        // Given
        setAuthentication();
        when(thirdPartyService.getStatistics(any(JwtUserDetails.class)))
                .thenThrow(new BusinessException(1002, "第三方机构信息不存在"));

        // When & Then
        mockMvc.perform(get("/third-party/statistics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1002));

        verify(thirdPartyService, times(1)).getStatistics(any(JwtUserDetails.class));
    }

    // ==================== PUT /third-party/contact Tests ====================

    @Test
    @DisplayName("更新联系方式成功")
    void testUpdateContactSuccess() throws Exception {
        // Given
        setAuthentication();
        when(thirdPartyService.updateContact(
                any(JwtUserDetails.class), eq("李四"), eq("13900139000")))
                .thenReturn(ApiResponse.success());

        // When & Then
        mockMvc.perform(put("/third-party/contact")
                        .param("contactPerson", "李四")
                        .param("contactPhone", "13900139000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(thirdPartyService, times(1)).updateContact(
                any(JwtUserDetails.class), eq("李四"), eq("13900139000"));
    }

    @Test
    @DisplayName("更新联系方式成功-仅更新联系人")
    void testUpdateContactPartialUpdate() throws Exception {
        // Given
        setAuthentication();
        when(thirdPartyService.updateContact(
                any(JwtUserDetails.class), eq("王五"), isNull()))
                .thenReturn(ApiResponse.success());

        // When & Then
        mockMvc.perform(put("/third-party/contact")
                        .param("contactPerson", "王五"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(thirdPartyService, times(1)).updateContact(
                any(JwtUserDetails.class), eq("王五"), isNull());
    }

    @Test
    @DisplayName("更新联系方式失败-机构不存在")
    void testUpdateContactNotFound() throws Exception {
        // Given
        setAuthentication();
        when(thirdPartyService.updateContact(
                any(JwtUserDetails.class), eq("李四"), eq("13900139000")))
                .thenThrow(new BusinessException(1002, "第三方机构信息不存在"));

        // When & Then
        mockMvc.perform(put("/third-party/contact")
                        .param("contactPerson", "李四")
                        .param("contactPhone", "13900139000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1002));

        verify(thirdPartyService, times(1)).updateContact(
                any(JwtUserDetails.class), eq("李四"), eq("13900139000"));
    }
}
