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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

            // ==================== POST /carbon-neutral/verify Tests ====================

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
        when(projectService.useCredits(any(JwtUserDetails.class), eq(1L), any(BigDecimal.class)))
                .thenReturn(creditsUsedResponse);

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/use-credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.usedCredits").value(100))
                .andExpect(jsonPath("$.data.availableCredits").value(850));

        verify(projectService, times(1)).useCredits(any(JwtUserDetails.class), eq(1L), any(BigDecimal.class));
    }

    @Test
    @DisplayName("使用碳信用失败-可用碳信用不足")
    void testUseCreditsInsufficient() throws Exception {
        // Given
        setAuthentication(normalUser);
        when(projectService.useCredits(any(JwtUserDetails.class), eq(1L), any(BigDecimal.class)))
                .thenThrow(new BusinessException(3004, "可用碳信用不足"));

        // When & Then
        mockMvc.perform(post("/carbon-neutral/1/use-credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 9999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3004))
                .andExpect(jsonPath("$.message").value("可用碳信用不足"));

        verify(projectService, times(1)).useCredits(any(JwtUserDetails.class), eq(1L), any(BigDecimal.class));
    }

    // ==================== PUT /carbon-neutral/{id}/monitoring Tests ====================

            // ==================== POST /carbon-neutral/{id}/apply-certification Tests ====================

            // ==================== POST /carbon-neutral/{id}/certify Tests ====================

            // ==================== POST /carbon-neutral/{id}/terminate Tests ====================

            // ==================== GET /carbon-neutral/pending-verification Tests ====================

            }
