package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.PasswordChangeRequest;
import com.oaiss.chain.dto.UserInfoResponse;
import com.oaiss.chain.dto.UserProfileUpdateRequest;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.UserTypeEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 * UserService Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private JwtUserDetails currentUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("encodedPassword");
        testUser.setRealName("Test User");
        testUser.setPhone("13800138000");
        testUser.setEmail("test@example.com");
        testUser.setUserType(UserTypeEnum.ENTERPRISE.getCode());
        testUser.setStatus(1);
        testUser.setDeleted(false);
        testUser.setLastLoginAt(LocalDateTime.now());
        testUser.setLastLoginIp("127.0.0.1");
        testUser.setCreatedAt(LocalDateTime.now());

        currentUser = JwtUserDetails.builder()
                .userId(1L)
                .username("testuser")
                .build();
    }

    @Test
    @DisplayName("获取当前用户信息成功")
    void testGetCurrentUserInfoSuccess() {
        // Given
        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);

        // When
        UserInfoResponse response = userService.getCurrentUserInfo(currentUser);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("testuser", response.getUsername());
        assertEquals("Test User", response.getRealName());
        verify(userRepository, times(1)).findByIdAndDeletedFalse(1L);
    }

    @Test
    @DisplayName("获取当前用户信息失败-用户不存在")
    void testGetCurrentUserInfoFailUserNotFound() {
        // Given
        doReturn(Optional.empty()).when(userRepository).findByIdAndDeletedFalse(1L);

        // When & Then
        assertThrows(BusinessException.class, () -> userService.getCurrentUserInfo(currentUser));
        verify(userRepository, times(1)).findByIdAndDeletedFalse(1L);
    }

    @Test
    @DisplayName("根据ID获取用户信息-本人可见完整信息")
    void testGetUserByIdSuccessForSelf() {
        // Given
        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);

        // When
        UserInfoResponse response = userService.getUserById(1L, currentUser);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("13800138000", response.getPhone());
        assertEquals("test@example.com", response.getEmail());
        verify(userRepository, times(1)).findByIdAndDeletedFalse(1L);
    }

    @Test
    @DisplayName("根据ID获取用户信息-管理员可见完整信息")
    void testGetUserByIdSuccessForAdmin() {
        // Given
        JwtUserDetails adminUser = JwtUserDetails.builder()
                .userId(99L)
                .username("admin")
                .roles(java.util.List.of("ADMIN"))
                .build();
        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);

        // When
        UserInfoResponse response = userService.getUserById(1L, adminUser);

        // Then
        assertNotNull(response);
        assertEquals("13800138000", response.getPhone());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    @DisplayName("根据ID获取用户信息-其他已登录用户越权访问抛出权限拒绝异常")
    void testGetUserByIdForbiddenForOtherUser() {
        // Given
        JwtUserDetails otherUser = JwtUserDetails.builder()
                .userId(2L)
                .username("other")
                .roles(java.util.List.of("REVIEWER"))
                .build();

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.getUserById(1L, otherUser));
        assertEquals(ErrorCode.PERMISSION_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("更新用户资料成功")
    void testUpdateProfileSuccess() {
        // Given
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setRealName("New Name");
        request.setEmail("new@example.com");

        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);
        doReturn(testUser).when(userRepository).save(any(User.class));

        // When
        UserInfoResponse response = userService.updateProfile(currentUser, request);

        // Then
        assertNotNull(response);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户资料失败-手机号已存在")
    void testUpdateProfileFailPhoneExists() {
        // Given
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setPhone("13900139000");

        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);
        doReturn(true).when(userRepository).existsByPhone("13900139000");

        // When & Then
        assertThrows(BusinessException.class, () -> userService.updateProfile(currentUser, request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("修改密码成功")
    void testChangePasswordSuccess() {
        // Given
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("oldPassword");
        request.setNewPassword("newPassword");
        request.setConfirmPassword("newPassword");

        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);
        doReturn(true).when(passwordEncoder).matches("oldPassword", "encodedPassword");
        doReturn("newEncodedPassword").when(passwordEncoder).encode("newPassword");
        doReturn(testUser).when(userRepository).save(any(User.class));

        // When
        userService.changePassword(currentUser, request);

        // Then
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("修改密码失败-两次密码不一致")
    void testChangePasswordFailPasswordMismatch() {
        // Given
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("oldPassword");
        request.setNewPassword("newPassword1");
        request.setConfirmPassword("newPassword2");

        // When & Then
        assertThrows(BusinessException.class, () -> userService.changePassword(currentUser, request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("修改密码失败-原密码错误")
    void testChangePasswordFailWrongOldPassword() {
        // Given
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("wrongOldPassword");
        request.setNewPassword("newPassword");
        request.setConfirmPassword("newPassword");

        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);
        doReturn(false).when(passwordEncoder).matches("wrongOldPassword", "encodedPassword");

        // When & Then
        assertThrows(BusinessException.class, () -> userService.changePassword(currentUser, request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("检查用户名可用-可用")
    void testIsUsernameAvailableTrue() {
        // Given
        doReturn(false).when(userRepository).existsByUsername("newuser");

        // When
        boolean available = userService.isUsernameAvailable("newuser");

        // Then
        assertTrue(available);
    }

    @Test
    @DisplayName("检查用户名可用-不可用")
    void testIsUsernameAvailableFalse() {
        // Given
        doReturn(true).when(userRepository).existsByUsername("existinguser");

        // When
        boolean available = userService.isUsernameAvailable("existinguser");

        // Then
        assertFalse(available);
    }

    @Test
    @DisplayName("检查邮箱可用-可用")
    void testIsEmailAvailableTrue() {
        // Given
        doReturn(false).when(userRepository).existsByEmail("new@example.com");

        // When
        boolean available = userService.isEmailAvailable("new@example.com");

        // Then
        assertTrue(available);
    }

    @Test
    @DisplayName("检查邮箱可用-不可用")
    void testIsEmailAvailableFalse() {
        // Given
        doReturn(true).when(userRepository).existsByEmail("existing@example.com");

        // When
        boolean available = userService.isEmailAvailable("existing@example.com");

        // Then
        assertFalse(available);
    }

    @Test
    @DisplayName("更新用户资料-手机号与当前相同时不检查重复")
    void testUpdateProfileSamePhoneNoDuplicateCheck() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setPhone("13800138000"); // same as current

        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);
        doReturn(testUser).when(userRepository).save(any(User.class));

        UserInfoResponse response = userService.updateProfile(currentUser, request);

        assertNotNull(response);
        verify(userRepository, never()).existsByPhone(anyString());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户资料-更新头像和地址")
    void testUpdateProfileAvatarAndAddress() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setAvatar("http://example.com/avatar.png");
        request.setAddress("New Address");

        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);
        doReturn(testUser).when(userRepository).save(any(User.class));

        UserInfoResponse response = userService.updateProfile(currentUser, request);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户资料-更新公司名")
    void testUpdateProfileCompany() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setCompany("New Company");

        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);
        doReturn(testUser).when(userRepository).save(any(User.class));

        UserInfoResponse response = userService.updateProfile(currentUser, request);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("更新用户资料失败-用户不存在")
    void testUpdateProfileFailUserNotFound() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setRealName("New Name");

        doReturn(Optional.empty()).when(userRepository).findByIdAndDeletedFalse(1L);

        assertThrows(BusinessException.class, () -> userService.updateProfile(currentUser, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("修改密码失败-用户不存在")
    void testChangePasswordFailUserNotFound() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("old");
        request.setNewPassword("new");
        request.setConfirmPassword("new");

        doReturn(Optional.empty()).when(userRepository).findByIdAndDeletedFalse(1L);

        assertThrows(BusinessException.class, () -> userService.changePassword(currentUser, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getUserById-用户不存在抛出异常")
    void testGetUserByIdFailUserNotFound() {
        JwtUserDetails adminUser = JwtUserDetails.builder()
                .userId(1L)
                .username("admin")
                .roles(java.util.List.of("ADMIN"))
                .build();
        doReturn(Optional.empty()).when(userRepository).findByIdAndDeletedFalse(99L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.getUserById(99L, adminUser));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("getUserById-null currentUser抛出权限拒绝异常")
    void testGetUserByIdNullCurrentUser() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.getUserById(1L, null));
        assertEquals(ErrorCode.PERMISSION_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("resolveUserTypeDescription-未知类型返回默认值")
    void testResolveUserTypeDescriptionUnknownType() {
        testUser.setUserType(999);
        doReturn(Optional.of(testUser)).when(userRepository).findByIdAndDeletedFalse(1L);

        UserInfoResponse response = userService.getCurrentUserInfo(currentUser);

        assertNotNull(response);
        assertEquals("未知", response.getUserTypeDesc());
    }

    @Test
    @DisplayName("测试用户在线停留时间上报与统计获取")
    void testRecordStayTimeAndGetStats() {
        Long userId = 100L;
        userService.recordStayTime(userId, 120L);
        userService.recordStayTime(userId, 30L);

        java.util.Map<String, Object> stats = userService.getStayTimeStats(userId);
        assertNotNull(stats);
        assertEquals(150L, stats.get("totalStaySeconds"));
        assertEquals("0小时2分钟30秒", stats.get("formattedStayTime"));
    }
}
