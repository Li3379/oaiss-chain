package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.CarbonNeutralProjectRequest;
import com.oaiss.chain.dto.CarbonNeutralProjectResponse;
import com.oaiss.chain.dto.ProjectVerificationRequest;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CarbonNeutralProjectService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CarbonNeutralProjectController Unit Tests
 * 碳中和项目控制器单元测试
 */
@WebMvcTest(controllers = CarbonNeutralProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class CarbonNeutralProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CarbonNeutralProjectService projectService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private JwtUserDetails normalUser;
    private JwtUserDetails adminUser;
    private CarbonNeutralProjectResponse testResponse;

    @BeforeEach
    void setUp() {
        normalUser = JwtUserDetails.builder()
                .userId(1L)
                .username("enterprise_user")
                .userType(2)
                .roles(List.of("ENTERPRISE"))
                .enabled(true)
                .build();

        adminUser = JwtUserDetails.builder()
                .userId(2L)
                .username("admin_user")
                .userType(1)
                .roles(List.of("ADMIN"))
                .enabled(true)
                .build();

        testResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .projectType(1)
                .projectTypeName("碳汇项目")
                .ownerId(1L)
                .ownerName("测试企业")
                .description("测试碳汇项目")
                .location("云南")
                .expectedReduction(new BigDecimal("1000.00"))
                .investmentAmount(new BigDecimal("500000.00"))
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2028, 12, 31))
                .status(0)
                .statusText("筹备中")
                .certStatus(0)
                .certStatusText("未认证")
                .issuedCredits(BigDecimal.ZERO)
                .usedCredits(BigDecimal.ZERO)
                .availableCredits(BigDecimal.ZERO)
                .verificationStatus(0)
                .verificationStatusText("未核证")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(JwtUserDetails user) {
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // ==================== POST /carbon-neutral Tests ====================

    @Test
    @DisplayName("创建碳中和项目成功")
    void testCreateProjectSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectRequest request = CarbonNeutralProjectRequest.builder()
                .projectName("林业碳汇项目")
                .projectType(1)
                .description("测试碳汇项目")
                .location("云南")
                .expectedReduction(new BigDecimal("1000.00"))
                .investmentAmount(new BigDecimal("500000.00"))
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2028, 12, 31))
                .build();

        when(projectService.createProject(any(JwtUserDetails.class), any(CarbonNeutralProjectRequest.class)))
                .thenReturn(testResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.projectName").value("林业碳汇项目"))
                .andExpect(jsonPath("$.data.projectType").value(1))
                .andExpect(jsonPath("$.data.projectNo").value("CNP202605050001"));

        verify(projectService, times(1))
                .createProject(any(JwtUserDetails.class), any(CarbonNeutralProjectRequest.class));
    }

    @Test
    @DisplayName("创建项目失败-未找到关联企业")
    void testCreateProjectEnterpriseNotFound() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectRequest request = CarbonNeutralProjectRequest.builder()
                .projectName("林业碳汇项目")
                .projectType(1)
                .expectedReduction(new BigDecimal("1000.00"))
                .build();

        when(projectService.createProject(any(JwtUserDetails.class), any(CarbonNeutralProjectRequest.class)))
                .thenThrow(new BusinessException(3001, "未找到关联企业信息"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3001))
                .andExpect(jsonPath("$.message").value("未找到关联企业信息"));

        verify(projectService, times(1))
                .createProject(any(JwtUserDetails.class), any(CarbonNeutralProjectRequest.class));
    }

    // ==================== PUT /carbon-neutral/{id} Tests ====================

    @Test
    @DisplayName("更新项目成功")
    void testUpdateProjectSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectRequest request = CarbonNeutralProjectRequest.builder()
                .projectName("更新后的碳汇项目")
                .projectType(1)
                .expectedReduction(new BigDecimal("2000.00"))
                .build();

        CarbonNeutralProjectResponse updatedResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("更新后的碳汇项目")
                .projectType(1)
                .status(0)
                .statusText("筹备中")
                .build();

        when(projectService.updateProject(any(JwtUserDetails.class), eq(1L), any(CarbonNeutralProjectRequest.class)))
                .thenReturn(updatedResponse);

        // When & Then
        mockMvc.perform(put("/carbon-neutral/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.projectName").value("更新后的碳汇项目"));

        verify(projectService, times(1))
                .updateProject(any(JwtUserDetails.class), eq(1L), any(CarbonNeutralProjectRequest.class));
    }

    @Test
    @DisplayName("更新项目失败-项目不存在")
    void testUpdateProjectNotFound() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectRequest request = CarbonNeutralProjectRequest.builder()
                .projectName("更新后的碳汇项目")
                .projectType(1)
                .build();

        when(projectService.updateProject(any(JwtUserDetails.class), eq(999L), any(CarbonNeutralProjectRequest.class)))
                .thenThrow(new BusinessException(3002, "项目不存在"));

        // When & Then
        mockMvc.perform(put("/carbon-neutral/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").value("项目不存在"));

        verify(projectService, times(1))
                .updateProject(any(JwtUserDetails.class), eq(999L), any(CarbonNeutralProjectRequest.class));
    }

    // ==================== GET /carbon-neutral/{id} Tests ====================

    @Test
    @DisplayName("获取项目详情成功")
    void testGetProjectSuccess() throws Exception {
        // Given
        when(projectService.getProject(1L)).thenReturn(testResponse);

        // When & Then
        mockMvc.perform(get("/carbon-neutral/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.projectNo").value("CNP202605050001"))
                .andExpect(jsonPath("$.data.projectName").value("林业碳汇项目"));

        verify(projectService, times(1)).getProject(1L);
    }

    @Test
    @DisplayName("获取项目详情失败-项目不存在")
    void testGetProjectNotFound() throws Exception {
        // Given
        when(projectService.getProject(999L))
                .thenThrow(new BusinessException(3002, "项目不存在"));

        // When & Then
        mockMvc.perform(get("/carbon-neutral/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").value("项目不存在"));

        verify(projectService, times(1)).getProject(999L);
    }

    // ==================== GET /carbon-neutral/search Tests ====================

    @Test
    @DisplayName("搜索项目成功-默认参数")
    void testSearchProjectsDefault() throws Exception {
        // Given
        Page<CarbonNeutralProjectResponse> page = new PageImpl<>(
                List.of(testResponse), PageRequest.of(0, 20), 1);

        when(projectService.searchProjects(isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon-neutral/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].projectName").value("林业碳汇项目"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(projectService, times(1))
                .searchProjects(isNull(), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("搜索项目成功-带筛选条件")
    void testSearchProjectsWithFilters() throws Exception {
        // Given
        Page<CarbonNeutralProjectResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(0, 10), 0);

        when(projectService.searchProjects(eq(1), eq(3), eq("碳汇"), eq(1), eq(10)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon-neutral/search")
                        .param("projectType", "1")
                        .param("status", "3")
                        .param("keyword", "碳汇")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(projectService, times(1))
                .searchProjects(eq(1), eq(3), eq("碳汇"), eq(1), eq(10));
    }

    // ==================== GET /carbon-neutral/my Tests ====================

    @Test
    @DisplayName("获取我的项目成功")
    void testGetMyProjectsSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        Page<CarbonNeutralProjectResponse> page = new PageImpl<>(
                List.of(testResponse), PageRequest.of(0, 20), 1);

        when(projectService.getMyProjects(any(JwtUserDetails.class), isNull(), eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon-neutral/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].projectName").value("林业碳汇项目"));

        verify(projectService, times(1))
                .getMyProjects(any(JwtUserDetails.class), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("获取我的项目成功-带状态过滤")
    void testGetMyProjectsWithStatus() throws Exception {
        // Given
        setAuthentication(normalUser);
        Page<CarbonNeutralProjectResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);

        when(projectService.getMyProjects(any(JwtUserDetails.class), eq(3), eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon-neutral/my")
                        .param("status", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(projectService, times(1))
                .getMyProjects(any(JwtUserDetails.class), eq(3), eq(1), eq(20));
    }

    // ==================== POST /carbon-neutral/{id}/submit Tests ====================

    @Test
    @DisplayName("提交审核成功")
    void testSubmitForReviewSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectResponse submittedResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .status(1)
                .statusText("待审核")
                .build();

        when(projectService.submitForReview(any(JwtUserDetails.class), eq(1L)))
                .thenReturn(submittedResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusText").value("待审核"));

        verify(projectService, times(1))
                .submitForReview(any(JwtUserDetails.class), eq(1L));
    }

    @Test
    @DisplayName("提交审核失败-项目状态不允许提交")
    void testSubmitForReviewInvalidStatus() throws Exception {
        // Given
        setAuthentication(normalUser);
        when(projectService.submitForReview(any(JwtUserDetails.class), eq(1L)))
                .thenThrow(new BusinessException(3003, "当前项目状态不允许提交审核"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/submit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003));

        verify(projectService, times(1))
                .submitForReview(any(JwtUserDetails.class), eq(1L));
    }

    // ==================== POST /carbon-neutral/{id}/review Tests ====================

    @Test
    @DisplayName("审核项目通过成功")
    void testReviewProjectApproveSuccess() throws Exception {
        // Given
        setAuthentication(adminUser);
        CarbonNeutralProjectResponse approvedResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .status(2)
                .statusText("审核通过")
                .reviewComment("符合要求")
                .build();

        when(projectService.reviewProject(any(JwtUserDetails.class), eq(1L), eq(true), eq("符合要求")))
                .thenReturn(approvedResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true, \"comment\": \"符合要求\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.statusText").value("审核通过"));

        verify(projectService, times(1))
                .reviewProject(any(JwtUserDetails.class), eq(1L), eq(true), eq("符合要求"));
    }

    @Test
    @DisplayName("审核项目拒绝成功")
    void testReviewProjectRejectSuccess() throws Exception {
        // Given
        setAuthentication(adminUser);
        CarbonNeutralProjectResponse rejectedResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .status(6)
                .statusText("审核拒绝")
                .reviewComment("材料不全")
                .build();

        when(projectService.reviewProject(any(JwtUserDetails.class), eq(1L), eq(false), eq("材料不全")))
                .thenReturn(rejectedResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": false, \"comment\": \"材料不全\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(6))
                .andExpect(jsonPath("$.data.statusText").value("审核拒绝"));

        verify(projectService, times(1))
                .reviewProject(any(JwtUserDetails.class), eq(1L), eq(false), eq("材料不全"));
    }

    @Test
    @DisplayName("审核项目失败-项目不在待审核状态")
    void testReviewProjectInvalidStatus() throws Exception {
        // Given
        setAuthentication(adminUser);
        when(projectService.reviewProject(any(JwtUserDetails.class), eq(1L), eq(true), isNull()))
                .thenThrow(new BusinessException(3003, "项目不在待审核状态"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003))
                .andExpect(jsonPath("$.message").value("项目不在待审核状态"));

        verify(projectService, times(1))
                .reviewProject(any(JwtUserDetails.class), eq(1L), eq(true), isNull());
    }

    // ==================== POST /carbon-neutral/{id}/start Tests ====================

    @Test
    @DisplayName("启动项目实施成功")
    void testStartImplementationSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectResponse implementingResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .status(3)
                .statusText("实施中")
                .build();

        when(projectService.startImplementation(any(JwtUserDetails.class), eq(1L)))
                .thenReturn(implementingResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.statusText").value("实施中"));

        verify(projectService, times(1))
                .startImplementation(any(JwtUserDetails.class), eq(1L));
    }

    @Test
    @DisplayName("启动项目实施失败-只有审核通过的项目可以启动")
    void testStartImplementationInvalidStatus() throws Exception {
        // Given
        setAuthentication(normalUser);
        when(projectService.startImplementation(any(JwtUserDetails.class), eq(1L)))
                .thenThrow(new BusinessException(3003, "只有审核通过的项目可以启动实施"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003));

        verify(projectService, times(1))
                .startImplementation(any(JwtUserDetails.class), eq(1L));
    }

    // ==================== POST /carbon-neutral/{id}/submit-verification Tests ====================

    @Test
    @DisplayName("提交核证申请成功")
    void testSubmitForVerificationSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectResponse verifPendingResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .status(3)
                .statusText("实施中")
                .verificationStatus(1)
                .verificationStatusText("核证中")
                .verifierId(10L)
                .build();

        when(projectService.submitForVerification(any(JwtUserDetails.class), eq(1L), eq(10L)))
                .thenReturn(verifPendingResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/submit-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verifierId\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.verificationStatus").value(1))
                .andExpect(jsonPath("$.data.verifierId").value(10));

        verify(projectService, times(1))
                .submitForVerification(any(JwtUserDetails.class), eq(1L), eq(10L));
    }

    @Test
    @DisplayName("提交核证申请失败-项目不在可核证状态")
    void testSubmitForVerificationInvalidStatus() throws Exception {
        // Given
        setAuthentication(normalUser);
        when(projectService.submitForVerification(any(JwtUserDetails.class), eq(1L), eq(10L)))
                .thenThrow(new BusinessException(3003, "项目不在可核证状态"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/submit-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verifierId\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003));

        verify(projectService, times(1))
                .submitForVerification(any(JwtUserDetails.class), eq(1L), eq(10L));
    }

    // ==================== POST /carbon-neutral/verify Tests ====================

    @Test
    @DisplayName("核证项目成功")
    void testVerifyProjectSuccess() throws Exception {
        // Given
        setAuthentication(adminUser);
        CarbonNeutralProjectResponse verifiedResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .status(4)
                .statusText("已完成")
                .verificationStatus(2)
                .verificationStatusText("已核证")
                .actualReduction(new BigDecimal("950.00"))
                .issuedCredits(new BigDecimal("950.00"))
                .build();

        when(projectService.verifyProject(any(JwtUserDetails.class), any(ProjectVerificationRequest.class)))
                .thenReturn(verifiedResponse);

        String requestJson = objectMapper.writeValueAsString(
                ProjectVerificationRequest.builder()
                        .projectId(1L)
                        .verifiedReduction(new BigDecimal("950.00"))
                        .verificationReport("核证报告")
                        .monitoringData("监测数据")
                        .build());

        // When & Then
        mockMvc.perform(post("/carbon-neutral/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.verificationStatus").value(2))
                .andExpect(jsonPath("$.data.issuedCredits").value(950));

        verify(projectService, times(1))
                .verifyProject(any(JwtUserDetails.class), any(ProjectVerificationRequest.class));
    }

    @Test
    @DisplayName("核证项目失败-项目不在待核证状态")
    void testVerifyProjectInvalidStatus() throws Exception {
        // Given
        setAuthentication(adminUser);
        when(projectService.verifyProject(any(JwtUserDetails.class), any(ProjectVerificationRequest.class)))
                .thenThrow(new BusinessException(3003, "项目不在待核证状态"));

        String requestJson = objectMapper.writeValueAsString(
                ProjectVerificationRequest.builder()
                        .projectId(1L)
                        .verifiedReduction(new BigDecimal("950.00"))
                        .build());

        // When & Then
        mockMvc.perform(post("/carbon-neutral/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003));

        verify(projectService, times(1))
                .verifyProject(any(JwtUserDetails.class), any(ProjectVerificationRequest.class));
    }

    // ==================== POST /carbon-neutral/{id}/use-credits Tests ====================

    @Test
    @DisplayName("使用碳信用成功")
    void testUseCreditsSuccess() throws Exception {
        // Given
        CarbonNeutralProjectResponse creditsUsedResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .issuedCredits(new BigDecimal("950.00"))
                .usedCredits(new BigDecimal("100.00"))
                .availableCredits(new BigDecimal("850.00"))
                .build();

        setAuthentication(normalUser);
        when(projectService.useCredits(any(JwtUserDetails.class), eq(1L), any(BigDecimal.class), isNull()))
                .thenReturn(creditsUsedResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/use-credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.usedCredits").value(100))
                .andExpect(jsonPath("$.data.availableCredits").value(850));

        verify(projectService, times(1)).useCredits(any(JwtUserDetails.class), eq(1L), any(BigDecimal.class), isNull());
    }

    @Test
    @DisplayName("使用碳信用失败-可用碳信用不足")
    void testUseCreditsInsufficient() throws Exception {
        // Given
        setAuthentication(normalUser);
        when(projectService.useCredits(any(JwtUserDetails.class), eq(1L), any(BigDecimal.class), isNull()))
                .thenThrow(new BusinessException(3004, "可用碳信用不足"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/use-credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 9999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3004))
                .andExpect(jsonPath("$.message").value("可用碳信用不足"));

        verify(projectService, times(1)).useCredits(any(JwtUserDetails.class), eq(1L), any(BigDecimal.class), isNull());
    }

    // ==================== PUT /carbon-neutral/{id}/monitoring Tests ====================

    @Test
    @DisplayName("更新监测数据成功")
    void testUpdateMonitoringSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectResponse monitoringResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .monitoringData("最新监测数据")
                .lastMonitoringDate(LocalDate.now())
                .build();

        when(projectService.updateMonitoring(any(JwtUserDetails.class), eq(1L), eq("最新监测数据")))
                .thenReturn(monitoringResponse);

        // When & Then
        mockMvc.perform(put("/carbon-neutral/1/monitoring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringData\": \"最新监测数据\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.monitoringData").value("最新监测数据"));

        verify(projectService, times(1))
                .updateMonitoring(any(JwtUserDetails.class), eq(1L), eq("最新监测数据"));
    }

    @Test
    @DisplayName("更新监测数据失败-项目不存在")
    void testUpdateMonitoringNotFound() throws Exception {
        // Given
        setAuthentication(normalUser);
        when(projectService.updateMonitoring(any(JwtUserDetails.class), eq(999L), eq("监测数据")))
                .thenThrow(new BusinessException(3002, "项目不存在"));

        // When & Then
        mockMvc.perform(put("/carbon-neutral/999/monitoring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monitoringData\": \"监测数据\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3002));

        verify(projectService, times(1))
                .updateMonitoring(any(JwtUserDetails.class), eq(999L), eq("监测数据"));
    }

    // ==================== POST /carbon-neutral/{id}/apply-certification Tests ====================

    @Test
    @DisplayName("申请认证成功")
    void testApplyForCertificationSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectResponse certPendingResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .certStatus(1)
                .certStatusText("认证中")
                .certOrg("中国质量认证中心")
                .build();

        when(projectService.applyForCertification(any(JwtUserDetails.class), eq(1L), eq("中国质量认证中心")))
                .thenReturn(certPendingResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/apply-certification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certOrg\": \"中国质量认证中心\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.certStatus").value(1))
                .andExpect(jsonPath("$.data.certOrg").value("中国质量认证中心"));

        verify(projectService, times(1))
                .applyForCertification(any(JwtUserDetails.class), eq(1L), eq("中国质量认证中心"));
    }

    @Test
    @DisplayName("申请认证失败-项目需先完成核证")
    void testApplyForCertificationNotVerified() throws Exception {
        // Given
        setAuthentication(normalUser);
        when(projectService.applyForCertification(any(JwtUserDetails.class), eq(1L), eq("管理员")))
                .thenThrow(new BusinessException(3003, "项目需先完成核证"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/apply-certification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certOrg\": \"管理员\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003))
                .andExpect(jsonPath("$.message").value("项目需先完成核证"));

        verify(projectService, times(1))
                .applyForCertification(any(JwtUserDetails.class), eq(1L), eq("管理员"));
    }

    // ==================== POST /carbon-neutral/{id}/certify Tests ====================

    @Test
    @DisplayName("完成认证成功")
    void testCompleteCertificationSuccess() throws Exception {
        // Given
        CarbonNeutralProjectResponse certifiedResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .certStatus(2)
                .certStatusText("已认证")
                .certNo("CERT-2026-001")
                .certDate(LocalDate.now())
                .build();

        when(projectService.completeCertification(eq(1L), eq("CERT-2026-001")))
                .thenReturn(certifiedResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certNo\": \"CERT-2026-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.certStatus").value(2))
                .andExpect(jsonPath("$.data.certStatusText").value("已认证"))
                .andExpect(jsonPath("$.data.certNo").value("CERT-2026-001"));

        verify(projectService, times(1)).completeCertification(eq(1L), eq("CERT-2026-001"));
    }

    @Test
    @DisplayName("完成认证失败-项目不在待认证状态")
    void testCompleteCertificationInvalidStatus() throws Exception {
        // Given
        when(projectService.completeCertification(eq(1L), eq("CERT-2026-001")))
                .thenThrow(new BusinessException(3003, "项目不在待认证状态"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/certify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"certNo\": \"CERT-2026-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3003))
                .andExpect(jsonPath("$.message").value("项目不在待认证状态"));

        verify(projectService, times(1)).completeCertification(eq(1L), eq("CERT-2026-001"));
    }

    // ==================== POST /carbon-neutral/{id}/terminate Tests ====================

    @Test
    @DisplayName("终止项目成功")
    void testTerminateProjectSuccess() throws Exception {
        // Given
        setAuthentication(normalUser);
        CarbonNeutralProjectResponse terminatedResponse = CarbonNeutralProjectResponse.builder()
                .id(1L)
                .projectNo("CNP202605050001")
                .projectName("林业碳汇项目")
                .status(5)
                .statusText("已终止")
                .reviewComment("项目终止: 资金不足")
                .build();

        when(projectService.terminateProject(any(JwtUserDetails.class), eq(1L), eq("资金不足")))
                .thenReturn(terminatedResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/terminate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"资金不足\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.statusText").value("已终止"));

        verify(projectService, times(1))
                .terminateProject(any(JwtUserDetails.class), eq(1L), eq("资金不足"));
    }

    @Test
    @DisplayName("终止项目失败-项目不存在")
    void testTerminateProjectNotFound() throws Exception {
        // Given
        setAuthentication(normalUser);
        when(projectService.terminateProject(any(JwtUserDetails.class), eq(999L), eq("原因")))
                .thenThrow(new BusinessException(3002, "项目不存在"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/999/terminate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"原因\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3002));

        verify(projectService, times(1))
                .terminateProject(any(JwtUserDetails.class), eq(999L), eq("原因"));
    }

    // ==================== GET /carbon-neutral/pending-verification Tests ====================

    @Test
    @DisplayName("获取待核证项目列表成功")
    void testGetPendingVerificationSuccess() throws Exception {
        // Given
        setAuthentication(adminUser);
        Page<CarbonNeutralProjectResponse> page = new PageImpl<>(
                List.of(testResponse), PageRequest.of(0, 20), 1);

        // 控制器对「纯 ADMIN」走全局池分支（getAllPendingVerificationProjects），
        // 因为 ADMIN 不是被指派人，按 verifierId 过滤会恒为空。adminUser 的 roles 只有
        // ADMIN，因此这里必须 mock 全局池方法，否则返回 null → data 缺 content。
        when(projectService.getAllPendingVerificationProjects(eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon-neutral/pending-verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].projectName").value("林业碳汇项目"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(projectService, times(1))
                .getAllPendingVerificationProjects(eq(1), eq(20));
    }

    @Test
    @DisplayName("获取待核证项目列表成功-空结果")
    void testGetPendingVerificationEmpty() throws Exception {
        // Given
        setAuthentication(adminUser);
        Page<CarbonNeutralProjectResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);

        // 同 testGetPendingVerificationSuccess：纯 ADMIN 走全局池分支
        when(projectService.getAllPendingVerificationProjects(eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon-neutral/pending-verification")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(projectService, times(1))
                .getAllPendingVerificationProjects(eq(1), eq(20));
    }

    @Test
    @DisplayName("纯 REVIEWER 走按 verifierId 过滤的分支")
    void testGetPendingVerificationAsReviewerUsesVerifierFilter() throws Exception {
        // REVIEWER 不是 ADMIN，控制器应走 getPendingVerificationProjects(verifierId, ...)
        JwtUserDetails reviewerUser = JwtUserDetails.builder()
                .userId(9L)
                .username("reviewer_user")
                .userType(2)
                .roles(List.of("REVIEWER"))
                .enabled(true)
                .build();
        setAuthentication(reviewerUser);

        Page<CarbonNeutralProjectResponse> page = new PageImpl<>(
                List.of(testResponse), PageRequest.of(0, 20), 1);
        when(projectService.getPendingVerificationProjects(eq(9L), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/carbon-neutral/pending-verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(projectService, times(1))
                .getPendingVerificationProjects(eq(9L), eq(1), eq(20));
        verify(projectService, never()).getAllPendingVerificationProjects(any(), any());
    }

    @Test
    @DisplayName("纯 ADMIN（非 REVIEWER）走全局池分支")
    void testGetPendingVerificationAsAdmin() throws Exception {
        // Given: 显式 stub 角色判定，把「ADMIN 且非 REVIEWER」这一分支条件锁死，
        // 不依赖 roles 列表的内容推导
        JwtUserDetails adminOnly = mock(JwtUserDetails.class);
        when(adminOnly.isAdmin()).thenReturn(true);
        when(adminOnly.isReviewer()).thenReturn(false);
        when(adminOnly.getUserId()).thenReturn(2L);
        when(adminOnly.getRoles()).thenReturn(List.of("ADMIN"));
        setAuthentication(adminOnly);

        Page<CarbonNeutralProjectResponse> page = new PageImpl<>(
                List.of(testResponse), PageRequest.of(0, 20), 1);
        when(projectService.getAllPendingVerificationProjects(eq(1), eq(20)))
                .thenReturn(page);

        // When & Then
        mockMvc.perform(get("/carbon-neutral/pending-verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].projectName").value("林业碳汇项目"));

        verify(projectService, times(1)).getAllPendingVerificationProjects(eq(1), eq(20));
        verify(projectService, never()).getPendingVerificationProjects(any(), any(), any());
    }

    @Test
    @DisplayName("待核证端点仅 REVIEWER/ADMIN 可达")
    void testGetPendingVerificationForbidden() throws Exception {
        // @WebMvcTest 切片不加载 SecurityConfig，方法级 @PreAuthorize 在切片内不生效，
        // 因此企业角色发请求会返回 200 而非 403。这里直接断言端点声明的权限表达式，
        // 与 role-matrix-verify.mjs 门禁共同保证 ENTERPRISE 被拦截。
        var handler = CarbonNeutralProjectController.class.getMethod(
                "getPendingVerification", JwtUserDetails.class, Integer.class, Integer.class);
        PreAuthorize preAuthorize = handler.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('REVIEWER') or hasRole('ADMIN')");
    }
}
