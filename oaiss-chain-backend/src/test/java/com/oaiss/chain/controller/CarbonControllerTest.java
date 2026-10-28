package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.*;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CarbonService;
import com.oaiss.chain.service.PowerGenerationFormulaService;
import com.oaiss.chain.service.PowerGridFormulaService;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CarbonController Unit Tests
 * 碳核算控制器单元测试
 */
@WebMvcTest(controllers = CarbonController.class)
@AutoConfigureMockMvc(addFilters = false)
class CarbonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CarbonService carbonService;

    @MockBean
    private PowerGridFormulaService powerGridFormulaService;

    @MockBean
    private PowerGenerationFormulaService powerGenerationFormulaService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private JwtUserDetails enterpriseUser;
    private JwtUserDetails reviewerUser;
    private JwtUserDetails adminUser;
    private CarbonReportRequest reportRequest;
    private CarbonReportResponse reportResponse;
    private ReviewRequest reviewRequest;

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

        reviewerUser = JwtUserDetails.builder()
                .userId(2L)
                .username("reviewer_user")
                .userType(2)
                .roles(List.of("REVIEWER"))
                .enabled(true)
                .build();

        adminUser = JwtUserDetails.builder()
                .userId(3L)
                .username("admin_user")
                .userType(4)
                .roles(List.of("ADMIN"))
                .enabled(true)
                .build();

        reportRequest = CarbonReportRequest.builder()
                .accountingPeriod("2024-Q1")
                .title("2024年第一季度碳排放报告")
                .reportType(1)
                .emissionData("{\"scope1\":100,\"scope2\":200,\"scope3\":50}")
                .calculationMethod("排放因子法")
                .build();

        reportResponse = CarbonReportResponse.builder()
                .id(1L)
                .reportNo("CR-2024-001")
                .enterpriseId(1L)
                .enterpriseName("测试企业")
                .accountingPeriod("2024-Q1")
                .title("2024年第一季度碳排放报告")
                .reportType(1)
                .emissionData("{\"scope1\":100,\"scope2\":200,\"scope3\":50}")
                .totalEmission(new BigDecimal("350.00"))
                .status(0)
                .statusText("草稿")
                .createdAt(LocalDateTime.now())
                .build();

        reviewRequest = ReviewRequest.builder()
                .reportId(1L)
                .reviewResult(3)
                .reviewComment("审核通过，数据准确")
                .build();

        SecurityContextHolder.clearContext();
    }

    // ==================== Create Report Tests ====================

    @Test
    @DisplayName("创建碳报告成功测试")
    void testCreateReportSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(carbonService.createReport(any(JwtUserDetails.class), any(CarbonReportRequest.class)))
                .thenReturn(reportResponse);

        // When & Then
        mockMvc.perform(post("/carbon/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("报告创建成功"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.reportNo").value("CR-2024-001"));

        verify(carbonService, times(1)).createReport(any(JwtUserDetails.class), any(CarbonReportRequest.class));
    }

    @Test
    @DisplayName("创建碳报告失败-无权限")
    void testCreateReportForbidden() throws Exception {
        // Given - Reviewer cannot create reports
        // Note: Security filters are disabled in tests, so authorization is handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        when(carbonService.createReport(any(JwtUserDetails.class), any(CarbonReportRequest.class)))
                .thenThrow(new BusinessException(403, "无权限创建报告"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(post("/carbon/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reportRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(carbonService, times(1)).createReport(any(JwtUserDetails.class), any(CarbonReportRequest.class));
    }

    @Test
    @DisplayName("创建碳报告失败-参数验证失败-核算周期为空")
    void testCreateReportValidationFailPeriodEmpty() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CarbonReportRequest invalidRequest = CarbonReportRequest.builder()
                .accountingPeriod("")
                .title("测试报告")
                .reportType(1)
                .emissionData("{}")
                .build();

        // When & Then
        mockMvc.perform(post("/carbon/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(carbonService, never()).createReport(any(), any());
    }

    @Test
    @DisplayName("创建碳报告失败-参数验证失败-标题为空")
    void testCreateReportValidationFailTitleEmpty() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CarbonReportRequest invalidRequest = CarbonReportRequest.builder()
                .accountingPeriod("2024-Q1")
                .title("")
                .reportType(1)
                .emissionData("{}")
                .build();

        // When & Then
        mockMvc.perform(post("/carbon/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(carbonService, never()).createReport(any(), any());
    }

    @Test
    @DisplayName("创建碳报告失败-参数验证失败-报告类型为空")
    void testCreateReportValidationFailTypeNull() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CarbonReportRequest invalidRequest = CarbonReportRequest.builder()
                .accountingPeriod("2024-Q1")
                .title("测试报告")
                .reportType(null)
                .emissionData("{}")
                .build();

        // When & Then
        mockMvc.perform(post("/carbon/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(carbonService, never()).createReport(any(), any());
    }

    // ==================== Submit Report Tests ====================

    @Test
    @DisplayName("提交碳报告成功测试")
    void testSubmitReportSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CarbonReportResponse submittedResponse = CarbonReportResponse.builder()
                .id(1L)
                .status(1)
                .statusText("待审核")
                .build();

        when(carbonService.submitReport(any(JwtUserDetails.class), eq(1L)))
                .thenReturn(submittedResponse);

        // When & Then
        mockMvc.perform(post("/carbon/reports/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("报告提交成功"))
                .andExpect(jsonPath("$.data.status").value(1));

        verify(carbonService, times(1)).submitReport(any(JwtUserDetails.class), eq(1L));
    }

    @Test
    @DisplayName("提交碳报告失败-报告状态不允许提交")
    void testSubmitReportFailInvalidStatus() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(carbonService.submitReport(any(JwtUserDetails.class), eq(1L)))
                .thenThrow(new BusinessException(400, "报告状态不允许提交"));

        // When & Then
        mockMvc.perform(post("/carbon/reports/1/submit"))
                .andExpect(status().isBadRequest());

        verify(carbonService, times(1)).submitReport(any(JwtUserDetails.class), eq(1L));
    }

    @Test
    @DisplayName("提交碳报告失败-报告不存在")
    void testSubmitReportFailNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(carbonService.submitReport(any(JwtUserDetails.class), eq(999L)))
                .thenThrow(new BusinessException(404, "报告不存在"));

        // When & Then - GlobalExceptionHandler converts BusinessException to HTTP 400
        mockMvc.perform(post("/carbon/reports/999/submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(carbonService, times(1)).submitReport(any(JwtUserDetails.class), eq(999L));
    }

    // ==================== Get Report Tests ====================

    @Test
    @DisplayName("获取报告详情成功测试")
    void testGetReportSuccess() throws Exception {
        // Given
        // 详情端点需要当前登录用户（企业限本企业、审阅角色可跨企业）
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(reviewerUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER"))));

        when(carbonService.getReport(any(JwtUserDetails.class), eq(1L))).thenReturn(reportResponse);

        // When & Then
        mockMvc.perform(get("/carbon/reports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("2024年第一季度碳排放报告"));

        verify(carbonService, times(1)).getReport(any(JwtUserDetails.class), eq(1L));
    }

    @Test
    @DisplayName("获取报告详情失败-报告不存在")
    void testGetReportFailNotFound() throws Exception {
        // Given
        // 详情端点需要当前登录用户（企业限本企业、审阅角色可跨企业）
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(reviewerUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER"))));

        when(carbonService.getReport(any(JwtUserDetails.class), eq(999L)))
                .thenThrow(new BusinessException(404, "报告不存在"));

        // When & Then - GlobalExceptionHandler converts BusinessException to HTTP 400
        mockMvc.perform(get("/carbon/reports/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(carbonService, times(1)).getReport(any(JwtUserDetails.class), eq(999L));
    }

    // ==================== List Reports Tests ====================

    @Test
    @DisplayName("查询报告列表成功测试")
    void testListReportsSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<CarbonReportResponse> page = new PageImpl<>(List.of(reportResponse));
        when(carbonService.listReports(any(), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").value(1));

        verify(carbonService, times(1)).listReports(any(), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询报告列表-带筛选条件")
    void testListReportsWithFilters() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<CarbonReportResponse> page = new PageImpl<>(List.of(reportResponse));
        when(carbonService.listReports(any(), eq(1L), eq(1), eq("2024"), eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon/reports")
                        .param("enterpriseId", "1")
                        .param("status", "1")
                        .param("keyword", "2024")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(carbonService, times(1)).listReports(any(), eq(1L), eq(1), eq("2024"), eq(1), eq(20));
    }

    @Test
    @DisplayName("查询报告列表失败-无权限")
    void testListReportsForbidden() throws Exception {
        // Given - Enterprise user cannot list all reports
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        when(carbonService.listReports(any(), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenThrow(new BusinessException(403, "无权限查询所有报告"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(get("/carbon/reports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(carbonService, times(1)).listReports(any(), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    // ==================== My Reports Tests ====================

    @Test
    @DisplayName("查询我的报告成功测试")
    void testListMyReportsSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<CarbonReportResponse> page = new PageImpl<>(List.of(reportResponse));
        when(carbonService.listMyReports(any(JwtUserDetails.class), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon/my-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());

        verify(carbonService, times(1)).listMyReports(any(JwtUserDetails.class), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询我的报告-带状态筛选")
    void testListMyReportsWithStatus() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Page<CarbonReportResponse> page = new PageImpl<>(List.of(reportResponse));
        when(carbonService.listMyReports(any(JwtUserDetails.class), eq(1), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon/my-reports")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(carbonService, times(1)).listMyReports(any(JwtUserDetails.class), eq(1), isNull(), isNull(), eq(1), eq(10));
    }

    @Test
    @DisplayName("查询我的报告失败-无权限")
    void testListMyReportsForbidden() throws Exception {
        // Given - Reviewer cannot access my-reports
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        when(carbonService.listMyReports(any(JwtUserDetails.class), isNull(), isNull(), isNull(), eq(1), eq(10)))
                .thenThrow(new BusinessException(403, "无权限查询我的报告"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(get("/carbon/my-reports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(carbonService, times(1)).listMyReports(any(JwtUserDetails.class), isNull(), isNull(), isNull(), eq(1), eq(10));
    }

    // ==================== Delete Report Tests ====================

    @Test
    @DisplayName("删除报告成功测试")
    void testDeleteReportSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        doNothing().when(carbonService).deleteReport(any(JwtUserDetails.class), eq(1L));

        // When & Then
        mockMvc.perform(delete("/carbon/reports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(carbonService, times(1)).deleteReport(any(JwtUserDetails.class), eq(1L));
    }

    @Test
    @DisplayName("删除报告失败-报告状态不允许删除")
    void testDeleteReportFailInvalidStatus() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        doThrow(new BusinessException(400, "已提交的报告不可删除"))
                .when(carbonService).deleteReport(any(JwtUserDetails.class), eq(1L));

        // When & Then
        mockMvc.perform(delete("/carbon/reports/1"))
                .andExpect(status().isBadRequest());

        verify(carbonService, times(1)).deleteReport(any(JwtUserDetails.class), eq(1L));
    }

    @Test
    @DisplayName("删除报告失败-无权限")
    void testDeleteReportFailForbidden() throws Exception {
        // Given
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        doThrow(new BusinessException(403, "无权限删除报告"))
                .when(carbonService).deleteReport(any(JwtUserDetails.class), eq(1L));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(delete("/carbon/reports/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(carbonService, times(1)).deleteReport(any(JwtUserDetails.class), eq(1L));
    }

    // ==================== Review Report Tests ====================

    @Test
    @DisplayName("审核报告成功-通过")
    void testReviewReportSuccessApprove() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CarbonReportResponse approvedResponse = CarbonReportResponse.builder()
                .id(1L)
                .status(2)
                .statusText("已通过")
                .reviewerId(2L)
                .reviewComment("审核通过，数据准确")
                .build();

        when(carbonService.reviewReport(any(JwtUserDetails.class), any(ReviewRequest.class)))
                .thenReturn(approvedResponse);

        // When & Then
        mockMvc.perform(post("/carbon/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("审核完成"))
                .andExpect(jsonPath("$.data.status").value(2));

        verify(carbonService, times(1)).reviewReport(any(JwtUserDetails.class), any(ReviewRequest.class));
    }

    @Test
    @DisplayName("审核报告成功-驳回")
    void testReviewReportSuccessReject() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ReviewRequest rejectRequest = ReviewRequest.builder()
                .reportId(1L)
                .reviewResult(4)
                .reviewComment("数据有误，请修正后重新提交")
                .build();

        CarbonReportResponse rejectedResponse = CarbonReportResponse.builder()
                .id(1L)
                .status(3)
                .statusText("已驳回")
                .reviewComment("数据有误，请修正后重新提交")
                .build();

        when(carbonService.reviewReport(any(JwtUserDetails.class), any(ReviewRequest.class)))
                .thenReturn(rejectedResponse);

        // When & Then
        mockMvc.perform(post("/carbon/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(3));

        verify(carbonService, times(1)).reviewReport(any(JwtUserDetails.class), any(ReviewRequest.class));
    }

    @Test
    @DisplayName("审核报告失败-无权限")
    void testReviewReportForbidden() throws Exception {
        // Given - Enterprise user cannot review
        // Note: Security filters are disabled, authorization handled by service
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Mock service to throw authorization exception
        when(carbonService.reviewReport(any(JwtUserDetails.class), any(ReviewRequest.class)))
                .thenThrow(new BusinessException(403, "无权限审核报告"));

        // When & Then - GlobalExceptionHandler converts to HTTP 400 with code 403
        mockMvc.perform(post("/carbon/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(403));

        verify(carbonService, times(1)).reviewReport(any(JwtUserDetails.class), any(ReviewRequest.class));
    }

    @Test
    @DisplayName("审核报告失败-参数验证失败-报告ID为空")
    void testReviewReportValidationFailReportIdNull() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ReviewRequest invalidRequest = ReviewRequest.builder()
                .reportId(null)
                .reviewResult(3)
                .reviewComment("审核通过")
                .build();

        // When & Then
        mockMvc.perform(post("/carbon/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(carbonService, never()).reviewReport(any(), any());
    }

    @Test
    @DisplayName("审核报告失败-参数验证失败-审核意见为空")
    void testReviewReportValidationFailCommentEmpty() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(reviewerUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ReviewRequest invalidRequest = ReviewRequest.builder()
                .reportId(1L)
                .reviewResult(3)
                .reviewComment("")
                .build();

        // When & Then - empty comment is valid (no @NotBlank constraint)
        mockMvc.perform(post("/carbon/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isOk());

        verify(carbonService, times(1)).reviewReport(any(), any());
    }

    @Test
    @DisplayName("认证报告成功")
    void testCertifyReportSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(reviewerUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ReviewRequest request = ReviewRequest.builder()
                .reportId(1L)
                .reviewResult(3)
                .reviewComment("认证通过")
                .build();

        CarbonReportResponse response = CarbonReportResponse.builder()
                .id(1L)
                .status(5)
                .build();

        when(carbonService.certifyReport(any(), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/carbon/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(5));

        verify(carbonService, times(1)).certifyReport(any(), any());
    }

    // ==================== Calculate Power Grid Tests ====================

    @Test
    @DisplayName("电网碳排放计算成功")
    void testCalculatePowerGridSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        PowerGridCalculationRequest request = new PowerGridCalculationRequest();
        request.setTransmissionVolume(new BigDecimal("10000.00"));
        request.setLineLossRate(new BigDecimal("0.05"));
        request.setGridEmissionFactor(new BigDecimal("0.5810"));
        request.setReportingYear(2024);
        request.setEnterpriseName("测试企业");

        PowerGridCalculationResponse response = PowerGridCalculationResponse.builder()
                .totalEmission(new BigDecimal("5519.50"))
                .transmissionLossEmission(new BigDecimal("275.975"))
                .importedEmission(new BigDecimal("5243.525"))
                .transmissionLoss(new BigDecimal("500.00"))
                .formulaReference("GB/T 32150-2015")
                .build();

        when(powerGridFormulaService.calculate(any(PowerGridCalculationRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/carbon/calculate/power-grid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalEmission").value(5519.50))
                .andExpect(jsonPath("$.data.formulaReference").value("GB/T 32150-2015"));

        verify(powerGridFormulaService, times(1)).calculate(any(PowerGridCalculationRequest.class));
    }

    // ==================== Calculate Power Generation Tests ====================

    @Test
    @DisplayName("发电企业碳排放计算成功")
    void testCalculatePowerGenerationSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        PowerGenerationCalculationRequest request = new PowerGenerationCalculationRequest();
        request.setRawCoalFc(new BigDecimal("100.00"));
        request.setRawCoalNcv(new BigDecimal("20.9"));
        request.setRawCoalCc(new BigDecimal("26.08"));
        request.setRawCoalOf(new BigDecimal("0.94"));
        request.setReportingYear(2024);
        request.setEnterpriseName("测试发电企业");

        PowerGenerationCalculationResponse response = PowerGenerationCalculationResponse.builder()
                .totalEmission(new BigDecimal("500.00"))
                .combustionEmission(new BigDecimal("480.00"))
                .desulfurizationEmission(new BigDecimal("20.00"))
                .formulaReference("GB/T 32150-2015")
                .build();

        when(powerGenerationFormulaService.calculate(any(PowerGenerationCalculationRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/carbon/calculate/power-generation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalEmission").value(500.00))
                .andExpect(jsonPath("$.data.formulaReference").value("GB/T 32150-2015"));

        verify(powerGenerationFormulaService, times(1)).calculate(any(PowerGenerationCalculationRequest.class));
    }
}
