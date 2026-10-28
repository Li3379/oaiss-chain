package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.AccountPermissionList;
import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtTokenProvider;
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
        when(userRepository.findById(3L)).thenReturn(Optional.of(userToEnable));
        when(userRepository.save(any(User.class))).thenReturn(userToEnable);

        // When & Then
        mockMvc.perform(put("/admin/users/3/status")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userRepository, times(1)).findById(3L);
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(userToDisable));
        when(userRepository.save(any(User.class))).thenReturn(userToDisable);

        // When & Then
        mockMvc.perform(put("/admin/users/1/status")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户状态-用户不存在")
    void testUpdateUserStatusUserNotFound() throws Exception {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then - GlobalExceptionHandler returns 404 for RESOURCE_NOT_FOUND
        mockMvc.perform(put("/admin/users/999/status")
                        .param("status", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND));

        verify(userRepository, times(1)).findById(999L);
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

        verify(enterpriseAdmissionService).revokeCertificate(1L);
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

        verify(reviewerQualificationService).revokeCertificate(2L);
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(userToEnable));
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
}
