package com.oaiss.chain.service;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.CaptchaResponse;
import com.oaiss.chain.dto.LoginRequest;
import com.oaiss.chain.dto.LoginResponse;
import com.oaiss.chain.dto.PasswordChangeRequest;
import com.oaiss.chain.dto.RegisterRequest;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.enums.UserTypeEnum;
import com.oaiss.chain.exception.AuthenticationException;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.constant.ErrorMessage;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.util.CommonUtils;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 认证服务
 * 
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CacheManager cacheManager;
    private final MetricsService metricsService;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final UserSessionService userSessionService;

    @Value("${app.security.captcha.enabled:true}")
    private boolean captchaEnabled = true;

    public void setCaptchaEnabled(boolean captchaEnabled) {
        this.captchaEnabled = captchaEnabled;
    }

    /**
     * 用户登录
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. 验证验证码（若开启验证码则必须强制校验）
        if (captchaEnabled) {
            if (request.getCaptchaKey() == null || request.getCaptcha() == null || request.getCaptcha().trim().isEmpty()) {
                throw AuthenticationException.captchaError();
            }
            validateCaptcha(request.getCaptchaKey(), request.getCaptcha());
        }

        // 2. 检查登录频率限制（独立防爆破计数器）
        if (loginAttemptService.isBlocked(request.getUsername())) {
            throw AuthenticationException.loginFailed("登录尝试过于频繁，请稍后再试");
        }

        // 3. 查找用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    loginAttemptService.recordFailedAttempt(request.getUsername());
                    return AuthenticationException.loginFailed(ErrorMessage.LOGIN_FAILED);
                });

        // 4. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailedAttempt(request.getUsername());
            throw AuthenticationException.loginFailed(ErrorMessage.LOGIN_FAILED);
        }

        // 5. 检查账号状态
        if (user.getStatus() == 0) {
            throw AuthenticationException.accountDisabled();
        }

        // 6. IP验证
        validateIp(user);

        // 7. 获取企业ID（如果是企业用户）
        Long enterpriseId = null;
        if (user.getUserType() == 1) { // 企业用户
            enterpriseId = enterpriseRepository.findByUserId(user.getId())
                    .map(Enterprise::getId)
                    .orElse(null);
        }

        // 8. 生成Token
        String roleName = getRoleName(user.getUserType());
        List<String> roles = List.of(roleName);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), roles, user.getUserType(), enterpriseId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId(), user.getUsername());

        // 9. 更新最后登录信息
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(CommonUtils.getClientIp());
        userRepository.save(user);

        // 10. 清除登录失败计数
        loginAttemptService.clearFailedAttempts(request.getUsername());

        log.info("User logged in: {} (ID: {}, Type: {}, EnterpriseId: {})",
                user.getUsername(), user.getId(), user.getUserType(), enterpriseId);

        // 11. 记录登录成功指标
        metricsService.incrementUserLogin(UserTypeEnum.fromCode(user.getUserType()).getDescription(), true);
        metricsService.incrementActiveUsers();

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .userType(user.getUserType())
                .realName(user.getRealName())
                .build();
    }

    /**
     * 用户注册
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        // 1. 校验密码一致
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw AuthenticationException.loginFailed("两次密码不一致");
        }

        // 2. 验证用户类型（禁止注册管理员和审核员）
        validateRegistrationUserType(request.getUserType());

        // 3. 检查用户名唯一
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new com.oaiss.chain.exception.BusinessException(
                    ErrorCode.ACCOUNT_EXISTS, ErrorMessage.ACCOUNT_EXISTS);
        }

        // 4. 检查手机号唯一
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new com.oaiss.chain.exception.BusinessException(
                    ErrorCode.PHONE_EXISTS, ErrorMessage.PHONE_EXISTS);
        }

        // 5. 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .realName(request.getRealName())
                .userType(request.getUserType())
                .status(1)
                .build();

        user = userRepository.save(user);

        // 6. 生成Token
        String roleName = getRoleName(user.getUserType());
        List<String> roles = List.of(roleName);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), roles, user.getUserType(), null);
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId(), user.getUsername());

        log.info("User registered: {} (ID: {}, Type: {})", 
                user.getUsername(), user.getId(), user.getUserType());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .userType(user.getUserType())
                .realName(user.getRealName())
                .build();
    }

    /**
     * 刷新Token
     */
    public LoginResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw AuthenticationException.tokenInvalid();
        }
        if (!jwtTokenProvider.validateToken(refreshToken)
                || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw AuthenticationException.tokenInvalid();
        }

        // 检查该 Refresh Token 是否已被加入黑名单（防重放）
        if (userSessionService.isTokenBlacklisted(refreshToken)) {
            log.warn("Attempted reuse of revoked refresh token");
            throw AuthenticationException.tokenInvalid();
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // 检查该会话是否在全局吊销时间之前签发（例如用户已登出或改密）
        if (userSessionService.isSessionRevoked(username, jwtTokenProvider.getIssuedAt(refreshToken))) {
            log.warn("Refresh token issued before user session revocation for: {}", username);
            throw AuthenticationException.tokenInvalid();
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(AuthenticationException::tokenInvalid);

        if (user.getStatus() == 0) {
            throw AuthenticationException.accountDisabled();
        }

        // 轮转废止旧的 Refresh Token
        userSessionService.blacklistToken(refreshToken, jwtTokenProvider.getRefreshTokenExpiration());

        // 获取企业ID（如果是企业用户）
        Long enterpriseId = null;
        if (user.getUserType() == 1) { // 企业用户
            enterpriseId = enterpriseRepository.findByUserId(user.getId())
                    .map(Enterprise::getId)
                    .orElse(null);
        }

        String roleName = getRoleName(user.getUserType());
        List<String> roles = List.of(roleName);

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), roles, user.getUserType(), enterpriseId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId(), user.getUsername());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .userType(user.getUserType())
                .realName(user.getRealName())
                .build();
    }

    /**
     * 生成验证码
     * 委托给CaptchaService生成真实图形验证码（含干扰线/点的PNG图片）
     */
    public CaptchaResponse generateCaptcha() {
        return captchaService.generateCaptcha();
    }

    /**
     * IP地址验证
     */
    public ApiResponse<Boolean> checkIp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.oaiss.chain.exception.BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        String clientIp = CommonUtils.getClientIp();
        boolean allowed = isIpAllowed(user, clientIp);

        return ApiResponse.success(allowed);
    }

    /**
     * 获取当前登录用户信息
     */
    public JwtUserDetails getCurrentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserDetails) {
            return (JwtUserDetails) auth.getPrincipal();
        }
        return null;
    }

    /**
     * 用户登出
     */
    public void logout(String username, String token) {
        if (token != null && !token.isEmpty()) {
            userSessionService.blacklistToken(token, jwtTokenProvider.getAccessTokenExpiration());
            Cache blacklist = cacheManager.getCache("tokenBlacklist");
            if (blacklist != null) {
                blacklist.put(token, System.currentTimeMillis());
            }
        }
        // 全局废止该用户在登出前签发的所有会话与 Refresh Token
        userSessionService.revokeUserSessions(username);
        log.info("User logged out: {} (session revoked, token blacklisted: {})", username, token != null);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(String username, PasswordChangeRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new com.oaiss.chain.exception.BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new com.oaiss.chain.exception.BusinessException(
                    ErrorCode.OLD_PASSWORD_ERROR, ErrorMessage.OLD_PASSWORD_ERROR);
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new com.oaiss.chain.exception.BusinessException(
                    ErrorCode.PARAM_ERROR, "两次密码不一致");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 密码修改后全局废止既有会话
        userSessionService.revokeUserSessions(username);

        log.info("User password changed and sessions revoked: {}", username);
    }

    // ==================== 私有方法 ====================

    /**
     * 允许注册的用户类型（管理员、审核员、第三方监管均只能通过后台审核创建）
     */
    private static final Set<Integer> ALLOWED_REGISTRATION_TYPES = Set.of(
            UserTypeEnum.ENTERPRISE.getCode()      // 1 - 仅允许企业用户自行注册
    );

    // ==================== 注册类型校验 ====================

    /**
     * 验证注册时的用户类型是否允许
     * 仅允许企业用户自行注册
     */
    private void validateRegistrationUserType(Integer userType) {
        if (userType == null || !ALLOWED_REGISTRATION_TYPES.contains(userType)) {
            throw new com.oaiss.chain.exception.BusinessException(
                    ErrorCode.PARAM_ERROR, "不允许注册此类型账号");
        }
    }

    private void validateCaptcha(String captchaKey, String captcha) {
        CaptchaVerifyResult result = captchaService.verifyCaptchaDetailed(captchaKey, captcha);
        switch (result) {
            case SUCCESS:
                return;
            case NOT_FOUND:
            case EXPIRED:
                throw AuthenticationException.captchaExpired();
            case WRONG_CODE:
                throw AuthenticationException.captchaError();
            default:
                throw AuthenticationException.captchaExpired();
        }
    }

    private void validateIp(User user) {
        if (user.getAllowedIps() == null || user.getAllowedIps().isEmpty()) {
            return;
        }

        String clientIp = CommonUtils.getClientIp();
        if (!isIpAllowed(user, clientIp)) {
            log.warn("IP validation failed for user {}: allowed IPs = {}, client IP = {}",
                    user.getUsername(), user.getAllowedIps(), clientIp);
            throw AuthenticationException.ipValidationFailed();
        }
    }

    private static final ObjectMapper IP_MAPPER = new ObjectMapper();

    private boolean isIpAllowed(User user, String clientIp) {
        if (user.getAllowedIps() == null || user.getAllowedIps().isEmpty()) {
            return true;
        }

        try {
            String[] ipArray = IP_MAPPER.readValue(user.getAllowedIps(), String[].class);
            return Arrays.stream(ipArray)
                    .anyMatch(ip -> ip.equals(clientIp) || ip.equals("*"));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse IP whitelist for user {}: {}", user.getId(), e.getMessage());
            return false;
        }
    }

    private String getRoleName(Integer userType) {
        return UserTypeEnum.fromCode(userType).name();
    }
}
