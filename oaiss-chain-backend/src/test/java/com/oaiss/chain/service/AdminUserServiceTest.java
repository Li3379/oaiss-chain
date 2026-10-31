package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.AdminCreateUserRequest;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminUserService Unit Tests
 * 管理员创建/删除用户业务规则单元测试
 *
 * 需求依据:
 * - docs/raw 01 3.4.1 用户管理(A级): 操作对象+操作指令 -> 执行结果
 * - docs/raw 03: 删除用户 = 信誉分不足用户暂时退出/下线 (软删除语义, 非物理删除)
 * - 审核员(2)/管理员(4)禁止自助注册(AuthService.validateRegistrationUserType),
 *   审核员账号的创建通道唯一: 管理员创建
 * - 管理员账号(4)禁止经本端点创建(防权限扩散, 来源未记载, 保守取舍)
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserSessionService userSessionService;

    @InjectMocks
    private AdminUserService adminUserService;

    private AdminCreateUserRequest validRequest() {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername("new_reviewer");
        request.setPassword("admin123");
        request.setUserType(2);
        request.setRealName("张三");
        request.setPhone("13800000001");
        request.setEmail("new-reviewer@example.com");
        return request;
    }

    // ==================== createUser ====================

    @Test
    @DisplayName("创建用户-成功创建审核员账号(BCrypt加密+启用态)")
    void createUser_reviewer_success() {
        when(userRepository.existsByUsername("new_reviewer")).thenReturn(false);
        when(userRepository.existsByPhone("13800000001")).thenReturn(false);
        when(passwordEncoder.encode("admin123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(9L);
            return u;
        });

        User result = adminUserService.createUser(validRequest());

        assertEquals(2, result.getUserType());
        assertEquals(1, result.getStatus());
        assertEquals("$2a$10$hashed", result.getPassword());
        assertEquals(9L, result.getId());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("创建用户-企业类型成功且手机号可空")
    void createUser_enterprise_without_phone_success() {
        AdminCreateUserRequest payload = validRequest();
        payload.setUserType(1);
        payload.setPhone(null);
        when(userRepository.existsByUsername("new_reviewer")).thenReturn(false);
        when(passwordEncoder.encode("admin123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = adminUserService.createUser(payload);

        assertEquals(1, result.getUserType());
        verify(userRepository, never()).existsByPhone(any());
    }

    @Test
    @DisplayName("创建用户-禁止创建管理员账号(防权限扩散)")
    void createUser_adminType_forbidden() {
        AdminCreateUserRequest request = validRequest();
        request.setUserType(4);

        BusinessException ex = assertThrows(BusinessException.class, () -> adminUserService.createUser(request));

        assertEquals(ErrorCode.ADMIN_USER_TYPE_FORBIDDEN, ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建用户-用户名重复拒绝(对齐自助注册唯一性规则)")
    void createUser_duplicate_username_rejected() {
        AdminCreateUserRequest request = validRequest();
        when(userRepository.existsByUsername("new_reviewer")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> adminUserService.createUser(request));

        assertEquals(ErrorCode.ACCOUNT_EXISTS, ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建用户-手机号重复拒绝")
    void createUser_duplicate_phone_rejected() {
        AdminCreateUserRequest request = validRequest();
        when(userRepository.existsByUsername("new_reviewer")).thenReturn(false);
        when(userRepository.existsByPhone("13800000001")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> adminUserService.createUser(request));

        assertEquals(ErrorCode.PHONE_EXISTS, ex.getCode());
        verify(userRepository, never()).save(any());
    }

    // ==================== deleteUser (软删除=下线语义) ====================

    @Test
    @DisplayName("删除用户-软删除打标+禁用+吊销会话")
    void deleteUser_success_soft_delete() {
        User target = User.builder().username("bad_enterprise").userType(1).status(1).build();
        target.setId(7L);
        when(userRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        adminUserService.deleteUser(7L);

        assertEquals(Boolean.TRUE, target.getDeleted());
        assertEquals(0, target.getStatus());
        verify(userRepository, times(1)).save(target);
        verify(userSessionService, times(1)).revokeUserSessions("bad_enterprise");
    }

    @Test
    @DisplayName("删除用户-目标为管理员账号时拒绝")
    void deleteUser_admin_target_rejected() {
        User target = User.builder().username("another_admin").userType(4).status(1).build();
        target.setId(8L);
        when(userRepository.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class, () -> adminUserService.deleteUser(8L));

        assertEquals(ErrorCode.CANNOT_DELETE_ADMIN, ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("删除用户-目标不存在返回404语义")
    void deleteUser_not_found() {
        when(userRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> adminUserService.deleteUser(999L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("删除用户-对已软删除用户再次删除返回404语义(幂等拒绝)")
    void deleteUser_already_deleted_returns_not_found() {
        when(userRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> adminUserService.deleteUser(7L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getCode());
    }
}
