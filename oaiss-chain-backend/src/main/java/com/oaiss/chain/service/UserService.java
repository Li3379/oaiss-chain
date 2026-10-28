package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.constant.ErrorMessage;
import com.oaiss.chain.dto.PasswordChangeRequest;
import com.oaiss.chain.dto.UserInfoResponse;
import com.oaiss.chain.dto.UserProfileUpdateRequest;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.UserTypeEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户服务
 * 处理用户个人中心相关操作
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ConcurrentHashMap<Long, Long> inMemoryStaySeconds = new ConcurrentHashMap<>();

    private static final String STAY_DURATION_KEY_PREFIX = "user:stay_duration:";

    /**
     * 获取当前用户信息
     */
    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUserInfo(JwtUserDetails currentUser) {
        User user = userRepository.findByIdAndDeletedFalse(currentUser.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        return toUserInfoResponse(user);
    }

    /**
     * 根据用户ID获取信息
     */
    @Transactional(readOnly = true)
    public UserInfoResponse getUserById(Long userId, JwtUserDetails currentUser) {
        if (!canViewSensitiveUserInfo(currentUser, userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorMessage.PERMISSION_DENIED);
        }

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        return toUserInfoResponse(user);
    }

    /**
     * 更新用户资料
     */
    @Transactional
    public UserInfoResponse updateProfile(JwtUserDetails currentUser,
            UserProfileUpdateRequest request) {

        User user = userRepository.findByIdAndDeletedFalse(currentUser.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        // 检查手机号唯一性
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new BusinessException(
                        ErrorCode.PHONE_EXISTS, ErrorMessage.PHONE_EXISTS);
            }
            user.setPhone(request.getPhone());
        }

        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getCompany() != null) {
            user.setCompany(request.getCompany());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }

        user = userRepository.save(user);
        log.info("User profile updated: {}", user.getUsername());

        return toUserInfoResponse(user);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(JwtUserDetails currentUser, PasswordChangeRequest request) {
        // 1. 校验新密码一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "两次密码不一致");
        }

        // 2. 查找用户
        User user = userRepository.findByIdAndDeletedFalse(currentUser.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        // 3. 验证原密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(
                    ErrorCode.OLD_PASSWORD_ERROR, ErrorMessage.OLD_PASSWORD_ERROR);
        }

        // 4. 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("User password changed: {}", user.getUsername());
    }

    /**
     * 检查用户名是否可用
     */
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    /**
     * 检查邮箱是否可用
     */
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    // ==================== 私有方法 ====================

    private UserInfoResponse toUserInfoResponse(User user) {
        String userTypeDesc = resolveUserTypeDescription(user.getUserType());

        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .company(user.getCompany())
                .address(user.getAddress())
                .userType(user.getUserType())
                .userTypeDesc(userTypeDesc)
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .createdAt(user.getCreatedAt())
                .totalStaySeconds(getTotalStaySeconds(user.getId()))
                .build();
    }

    private UserInfoResponse toPublicUserInfoResponse(User user) {
        String userTypeDesc = resolveUserTypeDescription(user.getUserType());

        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .company(user.getCompany())
                .userType(user.getUserType())
                .userTypeDesc(userTypeDesc)
                .build();
    }

    private boolean canViewSensitiveUserInfo(JwtUserDetails currentUser, Long targetUserId) {
        return currentUser != null
                && (currentUser.isAdmin() || targetUserId.equals(currentUser.getUserId()));
    }

    private String resolveUserTypeDescription(Integer userType) {
        String userTypeDesc = "";
        try {
            userTypeDesc = UserTypeEnum.fromCode(userType).getDescription();
        } catch (Exception ignored) {
            userTypeDesc = "未知";
        }
        return userTypeDesc;
    }

    /**
     * 记录/累加用户在线停留时长（秒）
     *
     * @param userId          用户ID
     * @param durationSeconds 本次增量停留时长（秒）
     */
    public void recordStayTime(Long userId, long durationSeconds) {
        if (userId == null || durationSeconds <= 0) {
            return;
        }
        StringRedisTemplate redisTemplate = getRedisTemplate();
        if (redisTemplate != null) {
            try {
                String key = STAY_DURATION_KEY_PREFIX + userId;
                redisTemplate.opsForValue().increment(key, durationSeconds);
            } catch (Exception e) {
                log.warn("Redis累加用户停留时长异常, 回退内存记录: userId={}, error={}", userId, e.getMessage());
                inMemoryStaySeconds.merge(userId, durationSeconds, Long::sum);
            }
        } else {
            inMemoryStaySeconds.merge(userId, durationSeconds, Long::sum);
        }
    }

    /**
     * 获取用户累计在线停留时长（秒）
     *
     * @param userId 用户ID
     * @return 累计秒数
     */
    public Long getTotalStaySeconds(Long userId) {
        if (userId == null) {
            return 0L;
        }
        StringRedisTemplate redisTemplate = getRedisTemplate();
        if (redisTemplate != null) {
            try {
                String key = STAY_DURATION_KEY_PREFIX + userId;
                String val = redisTemplate.opsForValue().get(key);
                if (val != null) {
                    return Long.parseLong(val);
                }
            } catch (Exception e) {
                log.warn("Redis读取用户停留时长异常: userId={}, error={}", userId, e.getMessage());
            }
        }
        return inMemoryStaySeconds.getOrDefault(userId, 0L);
    }

    /**
     * 获取用户停留时间统计字典
     */
    public Map<String, Object> getStayTimeStats(Long userId) {
        long totalSeconds = getTotalStaySeconds(userId);
        long minutes = totalSeconds / 60;
        long hours = minutes / 60;
        return Map.of(
                "userId", userId,
                "totalStaySeconds", totalSeconds,
                "formattedStayTime", String.format("%d小时%d分钟%d秒", hours, minutes % 60, totalSeconds % 60)
        );
    }

    private StringRedisTemplate getRedisTemplate() {
        return redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
    }
}
