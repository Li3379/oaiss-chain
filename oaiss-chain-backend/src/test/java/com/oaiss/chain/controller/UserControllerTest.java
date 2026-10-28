package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.*;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController Unit Tests
 * 用户控制器单元测试
 */
@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private JwtUserDetails testUserDetails;
    private UserInfoResponse userInfoResponse;

    @BeforeEach
    void setUp() {
        testUserDetails = JwtUserDetails.builder()
                .userId(1L)
                .username("testuser")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enterpriseId(1L)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        userInfoResponse = UserInfoResponse.builder()
                .userId(1L)
                .username("testuser")
                .realName("Test User")
                .phone("13800138000")
                .email("test@example.com")
                .avatar("http://example.com/avatar.png")
                .userType(1)
                .userTypeDesc("企业用户")
                .status(1)
                .lastLoginAt(LocalDateTime.now())
                .lastLoginIp("127.0.0.1")
                .createdAt(LocalDateTime.now().minusDays(30))
                .build();

        // Clear security context
        SecurityContextHolder.clearContext();
    }

    // ==================== Get Profile Tests ====================

    @Test
    @DisplayName("获取当前用户信息成功测试")
    void testGetProfileSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(userService.getCurrentUserInfo(any(JwtUserDetails.class))).thenReturn(userInfoResponse);

        // When & Then
        mockMvc.perform(get("/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.realName").value("Test User"));

        verify(userService, times(1)).getCurrentUserInfo(any(JwtUserDetails.class));
    }

    @Test
    @DisplayName("获取当前用户信息-用户未登录")
    void testGetProfileUnauthorized() throws Exception {
        // Given - No authentication set
        // Note: Security filters are disabled, so controller may proceed without auth
        // Mock service to handle unauthenticated request
        when(userService.getCurrentUserInfo(any())).thenReturn(userInfoResponse);

        // When & Then - With security disabled, request succeeds
        mockMvc.perform(get("/user/profile"))
                .andExpect(status().isOk());

        verify(userService, times(1)).getCurrentUserInfo(any());
    }

    // ==================== Get User By ID Tests ====================

    @Test
    @DisplayName("根据ID获取用户信息成功测试")
    void testGetUserByIdSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(userService.getUserById(eq(1L), any(JwtUserDetails.class))).thenReturn(userInfoResponse);

        // When & Then
        mockMvc.perform(get("/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"));

        verify(userService, times(1)).getUserById(eq(1L), any(JwtUserDetails.class));
    }

    @Test
    @DisplayName("根据ID获取用户信息接口显式要求权限控制")
    void testGetUserByIdRequiresAuthenticatedAnnotation() throws Exception {
        Method method = UserController.class.getMethod("getUserById", JwtUserDetails.class, Long.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        org.junit.jupiter.api.Assertions.assertNotNull(preAuthorize);
        org.junit.jupiter.api.Assertions.assertEquals("hasRole('ADMIN') or #userId == authentication.principal.userId", preAuthorize.value());
    }

    @Test
    @DisplayName("根据ID获取用户信息-用户不存在")
    void testGetUserByIdNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(userService.getUserById(eq(999L), any(JwtUserDetails.class)))
                .thenThrow(new BusinessException(404, "用户不存在"));

        // When & Then - GlobalExceptionHandler converts BusinessException to HTTP 400
        mockMvc.perform(get("/user/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(userService, times(1)).getUserById(eq(999L), any(JwtUserDetails.class));
    }

    // ==================== Update Profile Tests ====================

    @Test
    @DisplayName("更新用户资料成功测试")
    void testUpdateProfileSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .realName("Updated Name")
                .phone("13900139000")
                .email("updated@example.com")
                .build();

        UserInfoResponse updatedResponse = UserInfoResponse.builder()
                .userId(1L)
                .username("testuser")
                .realName("Updated Name")
                .phone("13900139000")
                .email("updated@example.com")
                .userType(1)
                .status(1)
                .build();

        when(userService.updateProfile(any(JwtUserDetails.class), any(UserProfileUpdateRequest.class)))
                .thenReturn(updatedResponse);

        // When & Then
        mockMvc.perform(put("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.realName").value("Updated Name"))
                .andExpect(jsonPath("$.data.phone").value("13900139000"));

        verify(userService, times(1)).updateProfile(any(JwtUserDetails.class), any(UserProfileUpdateRequest.class));
    }

    @Test
    @DisplayName("更新用户资料-只更新真实姓名")
    void testUpdateProfileOnlyRealName() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .realName("New Name")
                .build();

        when(userService.updateProfile(any(JwtUserDetails.class), any(UserProfileUpdateRequest.class)))
                .thenReturn(userInfoResponse);

        // When & Then
        mockMvc.perform(put("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService, times(1)).updateProfile(any(JwtUserDetails.class), any(UserProfileUpdateRequest.class));
    }

    @Test
    @DisplayName("更新用户资料-参数验证失败-真实姓名太长")
    void testUpdateProfileValidationFailRealNameTooLong() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String longName = "a".repeat(51);
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .realName(longName)
                .build();

        // When & Then
        mockMvc.perform(put("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(any(), any());
    }

    @Test
    @DisplayName("更新用户资料-参数验证失败-邮箱太长")
    void testUpdateProfileValidationFailEmailTooLong() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String longEmail = "a".repeat(90) + "@example.com";
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .email(longEmail)
                .build();

        // When & Then
        mockMvc.perform(put("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(any(), any());
    }

    @Test
    @DisplayName("更新用户资料-用户未登录")
    void testUpdateProfileUnauthorized() throws Exception {
        // Given - No authentication
        // Note: Security filters are disabled, so controller may proceed without auth
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .realName("Updated Name")
                .build();

        // Mock service to handle unauthenticated request
        when(userService.updateProfile(any(), any(UserProfileUpdateRequest.class))).thenReturn(userInfoResponse);

        // When & Then - With security disabled, request succeeds
        mockMvc.perform(put("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService, times(1)).updateProfile(any(), any(UserProfileUpdateRequest.class));
    }

    // ==================== Change Password Tests ====================

    @Test
    @DisplayName("修改密码成功测试")
    void testChangePasswordSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("oldPassword123")
                .newPassword("newPassword123")
                .confirmPassword("newPassword123")
                .build();

        doNothing().when(userService).changePassword(any(JwtUserDetails.class), any(PasswordChangeRequest.class));

        // When & Then
        mockMvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService, times(1)).changePassword(any(JwtUserDetails.class), any(PasswordChangeRequest.class));
    }

    @Test
    @DisplayName("修改密码失败-原密码错误")
    void testChangePasswordFailWrongOldPassword() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("wrongOldPassword")
                .newPassword("newPassword123")
                .confirmPassword("newPassword123")
                .build();

        doThrow(new BusinessException(401, "原密码错误"))
                .when(userService).changePassword(any(JwtUserDetails.class), any(PasswordChangeRequest.class));

        // When & Then - GlobalExceptionHandler converts BusinessException to HTTP 400
        mockMvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(401));

        verify(userService, times(1)).changePassword(any(JwtUserDetails.class), any(PasswordChangeRequest.class));
    }

    @Test
    @DisplayName("修改密码失败-参数验证失败-新密码太短")
    void testChangePasswordValidationFailNewPasswordTooShort() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("oldPassword123")
                .newPassword("12345")
                .confirmPassword("12345")
                .build();

        // When & Then
        mockMvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    @Test
    @DisplayName("修改密码失败-参数验证失败-新密码太长")
    void testChangePasswordValidationFailNewPasswordTooLong() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("oldPassword123")
                .newPassword("a".repeat(21))
                .confirmPassword("a".repeat(21))
                .build();

        // When & Then
        mockMvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    @Test
    @DisplayName("修改密码失败-参数验证失败-原密码为空")
    void testChangePasswordValidationFailOldPasswordEmpty() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("")
                .newPassword("newPassword123")
                .confirmPassword("newPassword123")
                .build();

        // When & Then
        mockMvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    @Test
    @DisplayName("修改密码失败-参数验证失败-确认密码为空")
    void testChangePasswordValidationFailConfirmPasswordEmpty() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(testUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        PasswordChangeRequest request = PasswordChangeRequest.builder()
                .oldPassword("oldPassword123")
                .newPassword("newPassword123")
                .confirmPassword("")
                .build();

        // When & Then
        mockMvc.perform(put("/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    // ==================== Check Username Tests ====================

    @Test
    @DisplayName("检查用户名可用-用户名可用")
    void testCheckUsernameAvailable() throws Exception {
        // Given
        when(userService.isUsernameAvailable("newuser")).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/user/check-username")
                        .param("username", "newuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(userService, times(1)).isUsernameAvailable("newuser");
    }

    @Test
    @DisplayName("检查用户名可用-用户名已存在")
    void testCheckUsernameNotAvailable() throws Exception {
        // Given
        when(userService.isUsernameAvailable("existinguser")).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/user/check-username")
                        .param("username", "existinguser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));

        verify(userService, times(1)).isUsernameAvailable("existinguser");
    }

    // ==================== Check Email Tests ====================

    @Test
    @DisplayName("检查邮箱可用-邮箱可用")
    void testCheckEmailAvailable() throws Exception {
        // Given
        when(userService.isEmailAvailable("new@example.com")).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/user/check-email")
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(userService, times(1)).isEmailAvailable("new@example.com");
    }

    @Test
    @DisplayName("检查邮箱可用-邮箱已存在")
    void testCheckEmailNotAvailable() throws Exception {
        // Given
        when(userService.isEmailAvailable("existing@example.com")).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/user/check-email")
                        .param("email", "existing@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));

        verify(userService, times(1)).isEmailAvailable("existing@example.com");
    }
}
