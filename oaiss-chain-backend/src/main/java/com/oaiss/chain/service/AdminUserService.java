package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.constant.ErrorMessage;
import com.oaiss.chain.dto.AdminCreateUserRequest;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AdminUserService
 * 管理员用户管理业务（创建/软删除）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;

    /**
     * 管理员创建用户。允许 1-企业/2-审核员/3-第三方；4-管理员禁止（防权限扩散）。
     * 唯一性校验对齐自助注册：用户名 -> 手机号。
     */
    @Transactional
    public User createUser(AdminCreateUserRequest request) {
        if (request.getUserType() == null || request.getUserType() < 1 || request.getUserType() > 3) {
            throw new BusinessException(ErrorCode.ADMIN_USER_TYPE_FORBIDDEN, ErrorMessage.ADMIN_USER_TYPE_FORBIDDEN);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.ACCOUNT_EXISTS, ErrorMessage.ACCOUNT_EXISTS);
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS, ErrorMessage.PHONE_EXISTS);
        }
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .realName(request.getRealName())
                .userType(request.getUserType())
                .status(1)
                .build();
        User saved = userRepository.save(user);
        log.info("Admin created user: {} (ID: {}, Type: {})", saved.getUsername(), saved.getId(), saved.getUserType());
        return saved;
    }

    /**
     * 管理员删除用户（软删除=下线语义）：deleted=true + status=0 + 吊销会话。
     * 不可删除管理员账号；对不存在/已删除统一 404 语义（幂等拒绝）。
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        if (user.getUserType() != null && user.getUserType() == 4) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_ADMIN, ErrorMessage.CANNOT_DELETE_ADMIN);
        }
        user.setDeleted(true);
        user.setStatus(0);
        userRepository.save(user);
        userSessionService.revokeUserSessions(user.getUsername());
        log.info("Admin deleted user: {} (ID: {})", user.getUsername(), userId);
    }
}
