package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.constant.ErrorMessage;
import com.oaiss.chain.entity.AccountPermissionList;
import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController Unit Tests
 * 管理后台控制器单元测试
 */
@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.oaiss.chain.repository.AccountPermissionListRepository permissionRepository;

    @MockBean
    private com.oaiss.chain.service.EnterpriseAdmissionService enterpriseAdmissionService;

    @MockBean
    private com.oaiss.chain.service.ReviewerQualificationService reviewerQualificationService;

    @MockBean
    private com.oaiss.chain.service.AdminUserService adminUserService;

    @MockBean
    private com.oaiss.chain.service.CreditScoreService creditScoreService;

    @MockBean
    private com.oaiss.chain.service.ChainReconciliationService chainReconciliationService;

    // 组件1：AdminController 新增行业基准值维护端点，构造器多了一个依赖。
    // @WebMvcTest 切片的 bean 集合由 controller 构造器决定，漏了它整个上下文起不来
    // ——41 个用例一起红，报错指向「第九个构造参数没有候选 bean」。
    @MockBean
    private com.oaiss.chain.service.IndustryBenchmarkService industryBenchmarkService;

    // W4b：新增 /admin/quota/{reportId}/revoke-retry 端点，构造器再多一个依赖。
    // 同样的坑再踩一次：@WebMvcTest 的 bean 集合由构造器决定，漏了整类红。
    @MockBean
    private com.oaiss.chain.service.QuotaAllocationService quotaAllocationService;

    private User testUser1;
    private User testUser2;
    private User testUser3;

    @BeforeEach
    void setUp() {
        testUser1 = User.builder()
                .username("enterprise_user")
                .realName("企业用户")
                .phone("13800138001")
                .email("enterprise@example.com")
                .userType(1)
                .status(1)
                .build();
        testUser1.setId(1L);

        testUser2 = User.builder()
                .username("reviewer_user")
                .realName("审核员")
                .phone("13800138002")
                .email("reviewer@example.com")
                .userType(2)
                .status(1)
                .build();
        testUser2.setId(2L);

        testUser3 = User.builder()
                .username("disabled_user")
                .realName("禁用用户")
                .phone("13800138003")
                .email("disabled@example.com")
                .userType(1)
                .status(0)
                .build();
        testUser3.setId(3L);
    }

    // ==================== List Users Tests ====================

    @Test
    @DisplayName("查询用户列表-成功返回所有用户")
    void testListUsersSuccess() throws Exception {
        // Given
        List<User> users = List.of(testUser1, testUser2, testUser3);
        Page<User> userPage = new PageImpl<>(users);
        when(userRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(userPage);

        // When & Then
        mockMvc.perform(get("/admin/users")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].username").value("enterprise_user"))
                .andExpect(jsonPath("$.data.content[1].username").value("reviewer_user"))
                .andExpect(jsonPath("$.data.content[2].username").value("disabled_user"));

        verify(userRepository, times(1)).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("查询用户列表-按用户类型筛选")
    void testListUsersFilterByUserType() throws Exception {
        // Given
        List<User> enterpriseUsers = List.of(testUser1, testUser3);
        Page<User> userPage = new PageImpl<>(enterpriseUsers);
        when(userRepository.findByUserTypeAndDeletedFalse(eq(1), any(Pageable.class))).thenReturn(userPage);

        // When & Then
        mockMvc.perform(get("/admin/users")
                        .param("userType", "1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));

        verify(userRepository, times(1)).findByUserTypeAndDeletedFalse(eq(1), any(Pageable.class));
        verify(userRepository, never()).findByStatusAndDeletedFalse(any(), any());
        verify(userRepository, never()).findByDeletedFalse(any());
    }

    @Test
    @DisplayName("查询用户列表-按状态筛选")
    void testListUsersFilterByStatus() throws Exception {
        // Given
        List<User> activeUsers = List.of(testUser1, testUser2);
        Page<User> userPage = new PageImpl<>(activeUsers);
        when(userRepository.findByStatusAndDeletedFalse(eq(1), any(Pageable.class))).thenReturn(userPage);

        // When & Then
        mockMvc.perform(get("/admin/users")
                        .param("status", "1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));

        verify(userRepository, times(1)).findByStatusAndDeletedFalse(eq(1), any(Pageable.class));
        verify(userRepository, never()).findByUserTypeAndDeletedFalse(any(), any());
        verify(userRepository, never()).findByDeletedFalse(any());
    }

    @Test
    @DisplayName("查询用户列表-分页功能正常")
    void testListUsersPagination() throws Exception {
        // Given
        List<User> users = List.of(testUser1);
        Page<User> userPage = new PageImpl<>(users);
        when(userRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(userPage);

        // When & Then - 验证分页参数
        mockMvc.perform(get("/admin/users")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());

        verify(userRepository, times(1)).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("查询用户列表-空列表")
    void testListUsersEmptyList() throws Exception {
        // Given
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(emptyPage);

        // When & Then
        mockMvc.perform(get("/admin/users")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        verify(userRepository, times(1)).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("查询用户列表-默认分页参数")
    void testListUsersDefaultPagination() throws Exception {
        // Given
        List<User> users = List.of(testUser1, testUser2);
        Page<User> userPage = new PageImpl<>(users);
        when(userRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(userPage);

        // When & Then - 不传分页参数，使用默认值
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());

        verify(userRepository, times(1)).findByDeletedFalse(any(Pageable.class));
    }

    // ==================== Update User Status Tests ====================

    @Test
    @DisplayName("更新用户状态-成功启用用户")
    void testUpdateUserStatusSuccessEnable() throws Exception {
        // Given
        User userToEnable = User.builder()
                .username("disabled_user")
                .status(0)
                .build();
        userToEnable.setId(3L);
        when(userRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(userToEnable));
        when(userRepository.save(any(User.class))).thenReturn(userToEnable);

        // When & Then
        mockMvc.perform(put("/admin/users/3/status")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userRepository, times(1)).findByIdAndDeletedFalse(3L);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户状态-成功禁用用户")
    void testUpdateUserStatusSuccessDisable() throws Exception {
        // Given
        User userToDisable = User.builder()
                .username("active_user")
                .status(1)
                .build();
        userToDisable.setId(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(userToDisable));
        when(userRepository.save(any(User.class))).thenReturn(userToDisable);

        // When & Then
        mockMvc.perform(put("/admin/users/1/status")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userRepository, times(1)).findByIdAndDeletedFalse(1L);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户状态-用户不存在")
    void testUpdateUserStatusUserNotFound() throws Exception {
        // Given
        when(userRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        // When & Then - GlobalExceptionHandler returns 404 for RESOURCE_NOT_FOUND
        mockMvc.perform(put("/admin/users/999/status")
                        .param("status", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND));

        verify(userRepository, times(1)).findByIdAndDeletedFalse(999L);
        verify(userRepository, never()).save(any());
    }

    // ==================== Dashboard Tests ====================

    @Test
    @DisplayName("获取仪表板数据-成功")
    void testGetDashboardSuccess() throws Exception {
        // Given
        when(userRepository.count()).thenReturn(100L);
        Page<User> activePage = new PageImpl<>(List.of(testUser1, testUser2));
        when(userRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(activePage);

        // When & Then
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(100))
                .andExpect(jsonPath("$.data.activeUsers").value(2));

        verify(userRepository, times(1)).count();
        verify(userRepository, times(1)).findByDeletedFalse(any(Pageable.class));
    }

    @Test
    @DisplayName("获取仪表板数据-无用户")
    void testGetDashboardNoUsers() throws Exception {
        // Given
        when(userRepository.count()).thenReturn(0L);
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(emptyPage);

        // When & Then
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(0))
                .andExpect(jsonPath("$.data.activeUsers").value(0));

        verify(userRepository, times(1)).count();
        verify(userRepository, times(1)).findByDeletedFalse(any(Pageable.class));
    }

    // ==================== Statistics Tests ====================

    @Test
    @DisplayName("获取系统统计数据-成功")
    void testGetStatisticsSuccess() throws Exception {
        // Given
        when(userRepository.count()).thenReturn(100L);

        // 企业用户
        Page<User> enterprisePage = new PageImpl<>(List.of(testUser1));
        when(userRepository.findByUserTypeAndDeletedFalse(eq(1), any(Pageable.class))).thenReturn(enterprisePage);

        // 审核员
        Page<User> reviewerPage = new PageImpl<>(List.of(testUser2));
        when(userRepository.findByUserTypeAndDeletedFalse(eq(2), any(Pageable.class))).thenReturn(reviewerPage);

        // 第三方
        Page<User> thirdPartyPage = new PageImpl<>(List.of());
        when(userRepository.findByUserTypeAndDeletedFalse(eq(3), any(Pageable.class))).thenReturn(thirdPartyPage);

        // When & Then
        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(100))
                .andExpect(jsonPath("$.data.enterpriseCount").value(1))
                .andExpect(jsonPath("$.data.reviewerCount").value(1))
                .andExpect(jsonPath("$.data.thirdPartyCount").value(0));

        verify(userRepository, times(1)).count();
        verify(userRepository, times(1)).findByUserTypeAndDeletedFalse(eq(1), any(Pageable.class));
        verify(userRepository, times(1)).findByUserTypeAndDeletedFalse(eq(2), any(Pageable.class));
        verify(userRepository, times(1)).findByUserTypeAndDeletedFalse(eq(3), any(Pageable.class));
    }

    @Test
    @DisplayName("获取系统统计数据-无用户数据")
    void testGetStatisticsNoUsers() throws Exception {
        // Given
        when(userRepository.count()).thenReturn(0L);

        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findByUserTypeAndDeletedFalse(any(Integer.class), any(Pageable.class))).thenReturn(emptyPage);

        // When & Then
        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(0))
                .andExpect(jsonPath("$.data.enterpriseCount").value(0))
                .andExpect(jsonPath("$.data.reviewerCount").value(0))
                .andExpect(jsonPath("$.data.thirdPartyCount").value(0));

        verify(userRepository, times(1)).count();
    }

    @Test
    @DisplayName("获取系统统计数据-大量用户")
    void testGetStatisticsLargeNumbers() throws Exception {
        // Given
        when(userRepository.count()).thenReturn(10000L);

        // 创建包含多个用户的页面
        List<User> manyUsers = List.of(testUser1, testUser2, testUser3);
        Page<User> userPage = new PageImpl<>(manyUsers);
        when(userRepository.findByUserTypeAndDeletedFalse(any(Integer.class), any(Pageable.class))).thenReturn(userPage);

        // When & Then
        mockMvc.perform(get("/admin/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").value(10000));

        verify(userRepository, times(1)).count();
    }

    @Test
    @DisplayName("获取系统配置-成功")
    void testGetConfigSuccess() throws Exception {
        mockMvc.perform(get("/admin/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.systemName").exists())
                .andExpect(jsonPath("$.data.enableBlockChain").value(true));
    }

    @Test
    @DisplayName("获取权限列表-成功")
    void testGetPermissionsSuccess() throws Exception {
        AccountPermissionList permission = AccountPermissionList.builder()
                .permissionCode("admin:user:list")
                .permissionName("用户列表")
                .sortOrder(1)
                .build();
        when(permissionRepository.findByDeletedFalseOrderBySortOrderAsc()).thenReturn(List.of(permission));

        mockMvc.perform(get("/admin/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].permissionCode").value("admin:user:list"));
    }

    @Test
    @DisplayName("签发企业准入证书-成功")
    void testIssueAdmissionSuccess() throws Exception {
        EnterpriseAdmission admission = EnterpriseAdmission.builder()
                .enterpriseId(1L)
                .certificateNo("EA-001")
                .build();
        when(enterpriseAdmissionService.issueCertificate(1L)).thenReturn(admission);

        mockMvc.perform(post("/admin/enterprise-admission/1/issue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateNo").value("EA-001"));
    }

    @Test
    @DisplayName("吊销企业准入证书-成功")
    void testRevokeAdmissionSuccess() throws Exception {
        mockMvc.perform(delete("/admin/enterprise-admission/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(enterpriseAdmissionService).revokeCertificate(1L, null);
    }

    @Test
    @DisplayName("查询企业准入证书列表-成功")
    void testListAdmissionsSuccess() throws Exception {
        EnterpriseAdmission admission = EnterpriseAdmission.builder()
                .enterpriseId(1L)
                .certificateNo("EA-002")
                .build();
        when(enterpriseAdmissionService.listCertificates(eq(1), eq(2), eq(5)))
                .thenReturn(new PageImpl<>(List.of(admission)));

        mockMvc.perform(get("/admin/enterprise-admission")
                        .param("status", "1")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].certificateNo").value("EA-002"));
    }

    @Test
    @DisplayName("签发审核员资格证-成功")
    void testIssueQualificationSuccess() throws Exception {
        ReviewerQualification qualification = ReviewerQualification.builder()
                .reviewerId(2L)
                .certificateNo("RV-001")
                .qualificationType("碳核查")
                .build();
        when(reviewerQualificationService.issueCertificate(2L)).thenReturn(qualification);

        mockMvc.perform(post("/admin/reviewer-qualification/2/issue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateNo").value("RV-001"));
    }

    @Test
    @DisplayName("吊销审核员资格证-成功")
    void testRevokeQualificationSuccess() throws Exception {
        mockMvc.perform(delete("/admin/reviewer-qualification/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(reviewerQualificationService).revokeCertificate(2L, null);
    }

    @Test
    @DisplayName("查询审核员资格证列表-成功")
    void testListQualificationsSuccess() throws Exception {
        ReviewerQualification qualification = ReviewerQualification.builder()
                .reviewerId(2L)
                .certificateNo("RV-002")
                .qualificationType("碳审计")
                .build();
        when(reviewerQualificationService.listCertificates(eq(1), eq(1), eq(10)))
                .thenReturn(new PageImpl<>(List.of(qualification)));

        mockMvc.perform(get("/admin/reviewer-qualification")
                        .param("status", "1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].certificateNo").value("RV-002"));
    }

    // ==================== Branch Coverage: Self-Disable Check ====================

    @Test
    @DisplayName("更新用户状态-管理员禁用自己的账号应失败")
    void testUpdateUserStatus_SelfDisable_ShouldFail() throws Exception {
        // The controller checks: currentUser != null && currentUser.getUserId().equals(userId) && status == 0
        // When all three conditions are true, it throws CANNOT_DISABLE_SELF
        // Since filters are disabled, @AuthenticationPrincipal will be null in this test setup.
        // We need to test the branch where currentUser is NOT null and tries to disable self.
        // With @WebMvcTest and addFilters=false, @AuthenticationPrincipal resolves from SecurityContext.

        // Set up an admin user in SecurityContext
        com.oaiss.chain.security.JwtUserDetails adminUser =
                com.oaiss.chain.security.JwtUserDetails.builder()
                        .userId(1L)
                        .username("admin_user")
                        .userType(4)
                        .roles(List.of("ADMIN"))
                        .enabled(true)
                        .accountNonExpired(true)
                        .accountNonLocked(true)
                        .credentialsNonExpired(true)
                        .build();
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        adminUser, null, adminUser.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        // When & Then - trying to disable own account (userId=1, status=0)
        mockMvc.perform(put("/admin/users/1/status")
                        .param("status", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CANNOT_DISABLE_SELF));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新用户状态-管理员启用自己的账号应成功")
    void testUpdateUserStatus_SelfEnable_ShouldSucceed() throws Exception {
        // status=1 (enable self) should NOT trigger the self-disable check
        com.oaiss.chain.security.JwtUserDetails adminUser =
                com.oaiss.chain.security.JwtUserDetails.builder()
                        .userId(1L)
                        .username("admin_user")
                        .userType(4)
                        .roles(List.of("ADMIN"))
                        .enabled(true)
                        .accountNonExpired(true)
                        .accountNonLocked(true)
                        .credentialsNonExpired(true)
                        .build();
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        adminUser, null, adminUser.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        User userToEnable = User.builder().username("admin_user").status(0).build();
        userToEnable.setId(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(userToEnable));
        when(userRepository.save(any(User.class))).thenReturn(userToEnable);

        mockMvc.perform(put("/admin/users/1/status")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("查询用户列表-同时传userType和status只按userType筛选")
    void testListUsers_UserTypeTakesPrecedenceOverStatus() throws Exception {
        // The code: if (userType != null) { ... } else if (status != null) { ... }
        // When both are provided, userType branch is taken
        List<User> users = List.of(testUser1);
        Page<User> userPage = new PageImpl<>(users);
        when(userRepository.findByUserTypeAndDeletedFalse(eq(1), any(Pageable.class))).thenReturn(userPage);

        mockMvc.perform(get("/admin/users")
                        .param("userType", "1")
                        .param("status", "1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userRepository, times(1)).findByUserTypeAndDeletedFalse(eq(1), any(Pageable.class));
        verify(userRepository, never()).findByStatusAndDeletedFalse(any(), any());
    }

    @Test
    @DisplayName("查询用户列表-仅传status参数筛选")
    void testListUsers_OnlyStatusFilter() throws Exception {
        List<User> activeUsers = List.of(testUser1, testUser2);
        Page<User> userPage = new PageImpl<>(activeUsers);
        when(userRepository.findByStatusAndDeletedFalse(eq(1), any(Pageable.class))).thenReturn(userPage);

        mockMvc.perform(get("/admin/users")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(2));

        verify(userRepository, times(1)).findByStatusAndDeletedFalse(eq(1), any(Pageable.class));
        verify(userRepository, never()).findByUserTypeAndDeletedFalse(any(), any());
        verify(userRepository, never()).findByDeletedFalse(any());
    }

    // ==================== Create User Tests ====================

    @Test
    @DisplayName("创建用户-成功创建审核员账号")
    void testCreateUserSuccess() throws Exception {
        User created = User.builder()
                .username("new_reviewer")
                .userType(2)
                .status(1)
                .build();
        created.setId(9L);
        when(adminUserService.createUser(any(com.oaiss.chain.dto.AdminCreateUserRequest.class))).thenReturn(created);

        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", "new_reviewer",
                                "password", "admin123",
                                "userType", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.username").value("new_reviewer"));

        verify(adminUserService, times(1)).createUser(any(com.oaiss.chain.dto.AdminCreateUserRequest.class));
    }

    @Test
    @DisplayName("创建用户-参数校验失败(用户名为空)返回400")
    void testCreateUserValidationError() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", "",
                                "password", "admin123",
                                "userType", 2))))
                .andExpect(status().isBadRequest());

        verify(adminUserService, never()).createUser(any());
    }

    @Test
    @DisplayName("创建用户-业务异常透传(禁建管理员)")
    void testCreateUserAdminTypeForbidden() throws Exception {
        when(adminUserService.createUser(any(com.oaiss.chain.dto.AdminCreateUserRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.ADMIN_USER_TYPE_FORBIDDEN, ErrorMessage.ADMIN_USER_TYPE_FORBIDDEN));

        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", "x_admin",
                                "password", "admin123",
                                "userType", 4))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ADMIN_USER_TYPE_FORBIDDEN));
    }

    // ==================== Delete User Tests ====================

    @Test
    @DisplayName("删除用户-成功(软删除下线语义)")
    void testDeleteUserSuccess() throws Exception {
        mockMvc.perform(delete("/admin/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(adminUserService, times(1)).deleteUser(7L);
    }

    @Test
    @DisplayName("删除用户-管理员删除自己应失败")
    void testDeleteUser_Self_ShouldFail() throws Exception {
        com.oaiss.chain.security.JwtUserDetails adminUser =
                com.oaiss.chain.security.JwtUserDetails.builder()
                        .userId(1L)
                        .username("admin_user")
                        .userType(4)
                        .roles(List.of("ADMIN"))
                        .enabled(true)
                        .accountNonExpired(true)
                        .accountNonLocked(true)
                        .credentialsNonExpired(true)
                        .build();
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        adminUser, null, adminUser.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(delete("/admin/users/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CANNOT_DELETE_SELF));

        verify(adminUserService, never()).deleteUser(any());
    }

    @Test
    @DisplayName("删除用户-业务异常透传(目标是管理员)")
    void testDeleteUserAdminTarget() throws Exception {
        doThrow(new BusinessException(ErrorCode.CANNOT_DELETE_ADMIN, ErrorMessage.CANNOT_DELETE_ADMIN))
                .when(adminUserService).deleteUser(8L);

        mockMvc.perform(delete("/admin/users/8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CANNOT_DELETE_ADMIN));
    }

    // ==================== Keyword Search Tests ====================

    @Test
    @DisplayName("查询用户列表-keyword参数转发仓储组合查询")
    void testListUsersKeywordForwards() throws Exception {
        List<User> users = List.of(testUser1);
        Page<User> userPage = new PageImpl<>(users);
        when(userRepository.searchUsers(eq("coal"), isNull(), isNull(), any(Pageable.class))).thenReturn(userPage);

        mockMvc.perform(get("/admin/users")
                        .param("keyword", "coal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("enterprise_user"));

        verify(userRepository, times(1)).searchUsers(eq("coal"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
@DisplayName("查询用户列表-keyword与userType组合查询")
    void testListUsersKeywordWithUserType() throws Exception {
        List<User> users = List.of(testUser1);
        Page<User> userPage = new PageImpl<>(users);
        when(userRepository.searchUsers(eq("coal"), eq(1), isNull(), any(Pageable.class))).thenReturn(userPage);

        mockMvc.perform(get("/admin/users")
                        .param("keyword", "coal")
                        .param("userType", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userRepository, times(1)).searchUsers(eq("coal"), eq(1), isNull(), any(Pageable.class));
    }

    // ==================== Chain Ops (reconciliation / dead letters) ====================

    @Test
    @DisplayName("链上对账-返回七类差异统计与明细（计数为全局口径）")
    void testReconcileChainReturnsClassification() throws Exception {
        var result = com.oaiss.chain.service.ChainReconciliationService.ReconciliationResult.builder()
                .checkedGlobal(2).checkedPage(2)
                .matched(1).missingOnChain(1)
                .insufficientData(0).historicalV1(0).contentMismatch(0)
                .unverifiable(0).historicalMock(0)
                .details(List.of(java.util.Map.of("reportId", 1, "status", "MATCHED")))
                .total(2)
                .build();
        when(chainReconciliationService.reconcile(1, 10)).thenReturn(result);

        mockMvc.perform(get("/admin/blockchain/reconciliation")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(1))
                .andExpect(jsonPath("$.data.missingOnChain").value(1))
                .andExpect(jsonPath("$.data.checkedGlobal").value(2))
                .andExpect(jsonPath("$.data.insufficientData").value(0))
                .andExpect(jsonPath("$.data.historicalV1").value(0))
                .andExpect(jsonPath("$.data.details").isArray());

        verify(chainReconciliationService).reconcile(1, 10);
    }

    @Test
    @DisplayName("死信列表-返回分页数据")
    void testListDeadLetters() throws Exception {
        com.oaiss.chain.entity.OutboxMessage dead = com.oaiss.chain.entity.OutboxMessage.builder()
                .messageId("m-1").aggregateType("REPORT").aggregateId(1L)
                .topic("CreateCarbonReportV2").payload("{}").status("DEAD").retryCount(5).build();
        dead.setId(1L);
        Page<com.oaiss.chain.entity.OutboxMessage> page = new PageImpl<>(List.of(dead));
        when(chainReconciliationService.listDeadLetters(1, 10)).thenReturn(page);

        mockMvc.perform(get("/admin/outbox/dead-letters")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].messageId").value("m-1"))
                .andExpect(jsonPath("$.data.content[0].status").value("DEAD"));

        verify(chainReconciliationService).listDeadLetters(1, 10);
    }

    @Test
    @DisplayName("死信重放-成功返回复位后的消息")
    void testReplayDeadLetter() throws Exception {
        com.oaiss.chain.entity.OutboxMessage replayed = com.oaiss.chain.entity.OutboxMessage.builder()
                .messageId("m-2").aggregateType("REPORT").aggregateId(2L)
                .topic("CreateCarbonReportV2").payload("{}").status("PENDING").retryCount(0).build();
        replayed.setId(2L);
        when(chainReconciliationService.replayDeadLetter(2L)).thenReturn(replayed);

        mockMvc.perform(post("/admin/outbox/{id}/replay", 2L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(chainReconciliationService).replayDeadLetter(2L);
    }

    @Test
    @DisplayName("死信重放-非 DEAD 状态返回参数错误")
    void testReplayDeadLetterRejectsNonDead() throws Exception {
        when(chainReconciliationService.replayDeadLetter(3L))
                .thenThrow(BusinessException.of(ErrorCode.PARAM_ERROR, "error.outbox.notDead"));

        mockMvc.perform(post("/admin/outbox/{id}/replay", 3L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ==================== Delist Tests (R-03) ====================

    @Test
    @DisplayName("下市处罚-成功(软删除下线语义之外的交易禁入)")
    void testDelistUserSuccess() throws Exception {
        mockMvc.perform(post("/admin/users/7/delist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(creditScoreService, times(1)).delistEnterpriseByUser(eq(7L), any());
    }

    @Test
    @DisplayName("下市处罚-重复下市业务异常透传(2017)")
    void testDelistUserAlreadyDelisted() throws Exception {
        doThrow(new BusinessException(2017, "error.delist.state.invalid"))
                .when(creditScoreService).delistEnterpriseByUser(eq(7L), any());

        mockMvc.perform(post("/admin/users/7/delist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2017));
    }

    @Test
    @DisplayName("恢复上市-成功")
    void testRestoreUserSuccess() throws Exception {
        mockMvc.perform(delete("/admin/users/7/delist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(creditScoreService, times(1)).restoreEnterpriseByUser(eq(7L), any());
    }

    // ==================== W4b：配额冲正重试 ====================

    @Test
    @DisplayName("配额冲正重试-成功调用冲正服务并传当前管理员 id")
    void testRetryQuotaRevokeSuccess() throws Exception {
        // 该端点是 AdminController 里唯一需要操作人 id 的（冲正要记 operatorId），
        // 其它端点不取 principal，所以本类没有统一认证 setup，这里显式注入。
        var admin = JwtUserDetails.builder().userId(1L).username("admin")
                .roles(java.util.List.of("ADMIN"))
                .build();
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        admin, null, admin.getAuthorities()));

        mockMvc.perform(post("/admin/quota/263/revoke-retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(quotaAllocationService, times(1)).revokeForReport(eq(263L), eq(1L));
    }

    @Test
    @DisplayName("配额冲正重试-冲正保护抛出时透传业务错误码")
    void testRetryQuotaRevokeProtectionRejects() throws Exception {
        var admin = JwtUserDetails.builder().userId(1L).username("admin")
                .roles(java.util.List.of("ADMIN"))
                .build();
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        admin, null, admin.getAuthorities()));

        // 冲正本体有保护条件（已动用/存在冻结配额时拒绝），端点不得绕过，
        // 只负责把错误原样透传给运维看到原因。
        doThrow(new BusinessException(1001, "该企业存在冻结配额 155 吨（挂单待售中），不可冲正"))
                .when(quotaAllocationService).revokeForReport(eq(262L), any());

        mockMvc.perform(post("/admin/quota/262/revoke-retry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
