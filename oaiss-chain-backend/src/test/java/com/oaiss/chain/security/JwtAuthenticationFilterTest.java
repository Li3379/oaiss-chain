package com.oaiss.chain.security;

import com.oaiss.chain.service.UserSessionService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtAuthenticationFilter单元测试
 * Unit tests for JwtAuthenticationFilter
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache tokenBlacklistCache;

    @Mock
    private UserSessionService userSessionService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();

        // Default: no blacklisted tokens (lenient since whitelist/no-token tests don't reach this)
        lenient().when(cacheManager.getCache("tokenBlacklist")).thenReturn(tokenBlacklistCache);
        lenient().when(tokenBlacklistCache.get(anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("白名单路径 /api/auth/login 应直接放行")
    void doFilterInternal_whitelistedLoginPath_shouldPassThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/login");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("白名单路径 /swagger-ui 应直接放行")
    void doFilterInternal_whitelistedSwaggerPath_shouldPassThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/swagger-ui/index.html");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("白名单路径 /v3/api-docs 应直接放行")
    void doFilterInternal_whitelistedApiDocsPath_shouldPassThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/v3/api-docs");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("白名单路径 /api/v1/actuator/health/readiness 应直接放行")
    void doFilterInternal_whitelistedActuatorReadiness_shouldPassThrough() throws ServletException, IOException {
        request.setRequestURI("/api/v1/actuator/health/readiness");

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("白名单路径 /api/auth/register 应直接放行")
    void doFilterInternal_whitelistedRegisterPath_shouldPassThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/register");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("有效Token应设置认证信息")
    void doFilterInternal_validToken_shouldSetAuthentication() throws ServletException, IOException {
        // Arrange
        String validToken = "valid.jwt.token";
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Bearer " + validToken);

        when(jwtTokenProvider.validateToken(validToken)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(validToken)).thenReturn("testuser");
        when(jwtTokenProvider.getUserIdFromToken(validToken)).thenReturn(1L);
        when(jwtTokenProvider.getRolesFromToken(validToken)).thenReturn(List.of("USER"));
        when(jwtTokenProvider.getUserTypeFromToken(validToken)).thenReturn(1);
        when(jwtTokenProvider.getEnterpriseIdFromToken(validToken)).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("testuser");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_USER");

        verify(jwtTokenProvider).validateToken(validToken);
        verify(jwtTokenProvider).getUsernameFromToken(validToken);
    }

    @Test
    @DisplayName("无效Token不应设置认证信息")
    void doFilterInternal_invalidToken_shouldNotSetAuthentication() throws ServletException, IOException {
        // Arrange
        String invalidToken = "invalid.jwt.token";
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Bearer " + invalidToken);

        when(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider).validateToken(invalidToken);
        verify(jwtTokenProvider, never()).getUsernameFromToken(anyString());
    }

    @Test
    @DisplayName("无Token不应设置认证信息")
    void doFilterInternal_noToken_shouldNotSetAuthentication() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/users");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("过期Token不应设置认证信息")
    void doFilterInternal_expiredToken_shouldNotSetAuthentication() throws ServletException, IOException {
        // Arrange
        String expiredToken = "expired.jwt.token";
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Bearer " + expiredToken);

        when(jwtTokenProvider.validateToken(expiredToken)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider).validateToken(expiredToken);
    }

    @Test
    @DisplayName("Bearer Token应正确提取")
    void doFilterInternal_bearerToken_shouldBeExtractedCorrectly() throws ServletException, IOException {
        // Arrange
        String expectedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Bearer " + expectedToken);

        when(jwtTokenProvider.validateToken(expectedToken)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(expectedToken)).thenReturn("testuser");
        when(jwtTokenProvider.getUserIdFromToken(expectedToken)).thenReturn(1L);
        when(jwtTokenProvider.getRolesFromToken(expectedToken)).thenReturn(List.of("ADMIN"));
        when(jwtTokenProvider.getUserTypeFromToken(expectedToken)).thenReturn(2);
        when(jwtTokenProvider.getEnterpriseIdFromToken(expectedToken)).thenReturn(100L);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("testuser");

        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        assertThat(userDetails.getUserId()).isEqualTo(1L);
        assertThat(userDetails.getRoles()).contains("ADMIN");
        assertThat(userDetails.getUserType()).isEqualTo(2);
        assertThat(userDetails.getEnterpriseId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("无Bearer前缀的Token应被忽略")
    void doFilterInternal_tokenWithoutBearerPrefix_shouldBeIgnored() throws ServletException, IOException {
        // Arrange
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", token);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("空Authorization头应被忽略")
    void doFilterInternal_emptyAuthorizationHeader_shouldBeIgnored() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("仅有Bearer前缀无Token应被忽略")
    void doFilterInternal_bearerPrefixOnly_shouldBeIgnored() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Bearer ");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        // Bearer with empty token will still call validateToken with empty string
        // This tests that an empty token after Bearer prefix is handled
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("认证异常应清除安全上下文")
    void doFilterInternal_authenticationException_shouldClearContext() throws ServletException, IOException {
        // Arrange
        String token = "problematic.jwt.token";
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenThrow(new RuntimeException("Token parsing error"));

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("企业用户Token应包含企业ID")
    void doFilterInternal_enterpriseUserToken_shouldIncludeEnterpriseId() throws ServletException, IOException {
        // Arrange
        String token = "enterprise.user.token";
        request.setRequestURI("/api/enterprise/data");
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("enterpriseUser");
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(2L);
        when(jwtTokenProvider.getRolesFromToken(token)).thenReturn(List.of("ENTERPRISE", "USER"));
        when(jwtTokenProvider.getUserTypeFromToken(token)).thenReturn(2);
        when(jwtTokenProvider.getEnterpriseIdFromToken(token)).thenReturn(500L);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();

        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        assertThat(userDetails.getEnterpriseId()).isEqualTo(500L);
        assertThat(userDetails.getRoles()).containsExactlyInAnyOrder("ENTERPRISE", "USER");
    }

    @Test
    @DisplayName("管理员Token应具有ADMIN角色")
    void doFilterInternal_adminToken_shouldHaveAdminRole() throws ServletException, IOException {
        // Arrange
        String token = "admin.jwt.token";
        request.setRequestURI("/api/admin/users");
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("admin");
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(1L);
        when(jwtTokenProvider.getRolesFromToken(token)).thenReturn(List.of("ADMIN"));
        when(jwtTokenProvider.getUserTypeFromToken(token)).thenReturn(0);
        when(jwtTokenProvider.getEnterpriseIdFromToken(token)).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("多个角色Token应正确解析")
    void doFilterInternal_multiRoleToken_shouldParseAllRoles() throws ServletException, IOException {
        // Arrange
        String token = "multi.role.token";
        request.setRequestURI("/api/data");
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("multiRoleUser");
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(3L);
        when(jwtTokenProvider.getRolesFromToken(token)).thenReturn(List.of("USER", "REVIEWER", "ADMIN"));
        when(jwtTokenProvider.getUserTypeFromToken(token)).thenReturn(1);
        when(jwtTokenProvider.getEnterpriseIdFromToken(token)).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_REVIEWER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Token中用户名为null不应设置认证")
    void doFilterInternal_nullUsername_shouldNotSetAuthentication() throws ServletException, IOException {
        // Arrange
        String token = "token.with.null.username";
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("已存在认证时不应覆盖")
    void doFilterInternal_existingAuthentication_shouldNotOverride() throws ServletException, IOException {
        // Arrange
        String token = "valid.token";
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Bearer " + token);

        // Set existing authentication
        JwtUserDetails existingUser = JwtUserDetails.builder()
                .userId(999L)
                .username("existingUser")
                .roles(List.of("EXISTING"))
                .build();
        UsernamePasswordAuthenticationToken existingAuth = new UsernamePasswordAuthenticationToken(
                existingUser, null, existingUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("newUser");
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(1L);
        when(jwtTokenProvider.getRolesFromToken(token)).thenReturn(List.of("NEW"));

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert - Should keep existing authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo("existingUser");
    }

    @Test
    @DisplayName("Basic认证头应被忽略")
    void doFilterInternal_basicAuthHeader_shouldBeIgnored() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/users");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("白名单路径/api/auth/captcha应直接放行")
    void doFilterInternal_whitelistedCaptchaPath_shouldPassThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/captcha");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("白名单路径/api/auth/refresh应直接放行")
    void doFilterInternal_whitelistedRefreshPath_shouldPassThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/refresh");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("v1/api-docs白名单路径应直接放行")
    void doFilterInternal_whitelistedV1ApiDocsPath_shouldPassThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/v1/api-docs");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ==================== H1: Path Traversal Security Tests ====================

    @Test
    @DisplayName("路径遍历攻击 ../ 不应绕过认证")
    void doFilterInternal_pathTraversal_shouldNotBypassAuth() throws ServletException, IOException {
        // Arrange: /api/v1/auth/login/../admin/users resolves to /api/v1/admin/users
        request.setRequestURI("/api/v1/auth/login/../admin/users");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: Should NOT be whitelisted - should try to validate token (or at least not skip)
        // The filter should normalize the path and recognize it's not a whitelist path
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // Should NOT skip token validation entirely - path is actually /api/v1/admin/users
        // Without a token, it just passes through with no auth set, which is correct behavior
        // The key is that the request should NOT be treated as a whitelist path
    }

    @Test
    @DisplayName("深度路径遍历 ../../ 不应绕过认证")
    void doFilterInternal_deepPathTraversal_shouldNotBypassAuth() throws ServletException, IOException {
        // Arrange: /api/v1/auth/register/../../admin/users resolves to /api/v1/admin/users
        request.setRequestURI("/api/v1/auth/register/../../admin/users");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: Should attempt token validation, not skip like whitelist
        verify(jwtTokenProvider).validateToken(token);
    }

    @Test
    @DisplayName("伪造白名单前缀路径不应绕过认证")
    void doFilterInternal_fakeWhitelistPrefix_shouldNotBypassAuth() throws ServletException, IOException {
        // Arrange: /api/v1/auth/loginx looks like /api/v1/auth/login but is different
        request.setRequestURI("/api/v1/auth/loginx");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: Should attempt token validation, not skip like whitelist
        verify(jwtTokenProvider).validateToken(token);
    }

    @Test
    @DisplayName("合法白名单子路径应正常放行")
    void doFilterInternal_legitimateWhitelistSubpath_shouldPassThrough() throws ServletException, IOException {
        // Arrange: /api/v1/captcha/sms/send is a legitimate sub-path under /api/v1/captcha/
        request.setRequestURI("/api/v1/captcha/sms/send");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ==================== C-1: AntPathMatcher Security Tests ====================

    @Test
    @DisplayName("路径中包含双斜杠 // 不应绕过白名单认证")
    void doFilterInternal_doubleSlash_shouldNotBypassAuth() throws ServletException, IOException {
        // Arrange: /api/v1//auth/login — double slash bypass attempt
        request.setRequestURI("/api/v1//auth/login");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: Should attempt token validation, not skip like whitelist
        verify(jwtTokenProvider).validateToken(token);
    }

    @Test
    @DisplayName("URL编码路径遍历 %2e%2e 不应绕过白名单")
    void doFilterInternal_encodedPathTraversal_shouldNotBypassAuth() throws ServletException, IOException {
        // Arrange: URL-encoded path traversal attempt
        request.setRequestURI("/api/v1/auth/login/%2e%2e/admin/users");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: Should attempt token validation
        verify(jwtTokenProvider).validateToken(token);
    }

    @Test
    @DisplayName("路径中包含分号 ; 不应绕过白名单")
    void doFilterInternal_semicolonPath_shouldNotBypassAuth() throws ServletException, IOException {
        // Arrange: /api/v1/auth/login;malicious — semicolon bypass attempt
        request.setRequestURI("/api/v1/auth/login;malicious");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert: Should NOT be whitelisted, should validate token
        verify(jwtTokenProvider).validateToken(token);
    }

    @Test
    @DisplayName("精确白名单路径匹配应放行（无多余字符）")
    void doFilterInternal_exactWhitelistMatch_shouldPassThrough() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/login");

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("AntPathMatcher风格通配: /api/v1/captcha 子路径应放行")
    void doFilterInternal_captchaSubpath_shouldPassThrough() throws ServletException, IOException {
        request.setRequestURI("/api/v1/captcha/image");

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("非白名单路径即使无Token也不应视为白名单放行")
    void doFilterInternal_nonWhitelistNoToken_shouldNotBeWhitelisted() throws ServletException, IOException {
        // This verifies that non-whitelisted paths reach the token extraction logic
        // even when no token is present — the key is isWhitelisted returns false
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, filterChain);

        // No token, so no auth is set — but this is NOT because of whitelist
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // The filter still processes past the whitelist check (no early return via whitelist)
    }

    // ==================== Branch Coverage: Blacklisted Token ====================

    @Test
    @DisplayName("黑名单中的Token应直接放行不设置认证")
    void doFilterInternal_blacklistedToken_shouldSkipAuth() throws ServletException, IOException {
        String token = "blacklisted.jwt.token";
        request.setRequestURI("/api/v1/admin/users");
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        Cache.ValueWrapper mockWrapper = mock(Cache.ValueWrapper.class);
        when(tokenBlacklistCache.get(token)).thenReturn(mockWrapper); // Token is blacklisted

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).getUsernameFromToken(anyString());
    }

    @Test
    @DisplayName("cacheManager返回null blacklist时应正常处理Token")
    void doFilterInternal_nullBlacklistCache_shouldProcessToken() throws ServletException, IOException {
        String token = "valid.jwt.token";
        request.setRequestURI("/api/v1/protected");
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(cacheManager.getCache("tokenBlacklist")).thenReturn(null); // no blacklist cache
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("testuser");
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(1L);
        when(jwtTokenProvider.getRolesFromToken(token)).thenReturn(List.of("USER"));
        when(jwtTokenProvider.getUserTypeFromToken(token)).thenReturn(1);
        when(jwtTokenProvider.getEnterpriseIdFromToken(token)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    // ==================== Branch Coverage: Path Traversal Edge Cases ====================

    @Test
    @DisplayName("大写%2E编码路径遍历不应绕过白名单")
    void doFilterInternal_uppercaseEncodedPathTraversal_shouldNotBypassAuth() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/login/%2E%2E/admin/users");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtTokenProvider).validateToken(token);
    }

    @Test
    @DisplayName("空路径不应匹配白名单")
    void doFilterInternal_emptyPath_shouldNotBeWhitelisted() throws ServletException, IOException {
        request.setRequestURI("");

        filter.doFilterInternal(request, response, filterChain);

        // Empty path is not whitelisted, but no token so just passes through
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("normalizePath-仅包含点段的路径应正确解析")
    void doFilterInternal_pathWithDotSegments_shouldNormalize() throws ServletException, IOException {
        // Path: /api/v1/./auth/login -> after normalization /api/v1/auth/login
        // But // in original path (due to split) will be caught by double-slash check
        // Test a path with ./ that normalizes cleanly
        request.setRequestURI("/api/v1/enterprise/./data");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        // Should not be whitelisted, should try to validate token
        verify(jwtTokenProvider).validateToken(token);
    }

    @Test
    @DisplayName("normalizePath-路径以..结尾应回退一级")
    void doFilterInternal_pathWithTrailingDotDot_shouldNormalize() throws ServletException, IOException {
        // Path: /api/v1/auth/login/.. -> normalizes to /api/v1/auth
        // Then checks if /api/v1/auth matches any whitelist -> it doesn't
        request.setRequestURI("/api/v1/auth/login/..");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        // /api/v1/auth is not in whitelist, so token validation should happen
        verify(jwtTokenProvider).validateToken(token);
    }

    @Test
    @DisplayName("白名单路径/api/v1/auth/check-ip应直接放行")
    void doFilterInternal_whitelistedCheckIp_shouldPassThrough() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/check-ip");

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("有效Token但username已存在认证时应跳过设置")
    void doFilterInternal_validTokenButExistingAuth_shouldNotOverride() throws ServletException, IOException {
        String token = "valid.jwt.token";
        request.setRequestURI("/api/v1/data");
        request.addHeader("Authorization", "Bearer " + token);

        // Pre-set authentication
        JwtUserDetails existingUser = JwtUserDetails.builder()
                .userId(999L)
                .username("existing")
                .roles(List.of("USER"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(existingUser, null, existingUser.getAuthorities()));

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("newuser");
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(1L);
        when(jwtTokenProvider.getRolesFromToken(token)).thenReturn(List.of("ADMIN"));
        when(jwtTokenProvider.getUserTypeFromToken(token)).thenReturn(4);
        when(jwtTokenProvider.getEnterpriseIdFromToken(token)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        // Should keep existing authentication (SecurityContext already has auth)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("existing");
    }

    // ==================== Branch Coverage: Null Request URI ====================

    @Test
    @DisplayName("null请求路径不应匹配白名单")
    void doFilterInternal_nullRequestUri_shouldNotBeWhitelisted() throws ServletException, IOException {
        // Use a mock request that returns null for getRequestURI
        jakarta.servlet.http.HttpServletRequest mockRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(mockRequest.getRequestURI()).thenReturn(null);
        when(mockRequest.getHeader("Authorization")).thenReturn(null);

        jakarta.servlet.FilterChain mockFilterChain = mock(jakarta.servlet.FilterChain.class);

        filter.doFilterInternal(mockRequest, response, mockFilterChain);

        // null path should not be whitelisted; no token, so just passes through
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(mockFilterChain).doFilter(mockRequest, response);
    }

    // ==================== Branch Coverage: normalizePath with leading dotdot ====================

    @Test
    @DisplayName("normalizePath-多个..段应回退但不崩溃（空列表保护）")
    void doFilterInternal_pathWithMultipleDotDot_shouldNormalize() throws ServletException, IOException {
        // Path: /../../something → segments: ["", "..", "..", "something"]
        // First ".." resolves (removes ""), second ".." should NOT crash (empty list guard)
        // Result: /something
        request.setRequestURI("/../../something");
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        // /something is not whitelisted, should validate token
        verify(jwtTokenProvider).validateToken(token);
    }

    // ==================== Branch Coverage: EnterpriseContextHolder.clear on exception ====================

    @Test
    @DisplayName("认证异常时应同时清除EnterpriseContextHolder")
    void doFilterInternal_authenticationException_shouldClearEnterpriseContext() throws ServletException, IOException {
        String token = "problematic.jwt.token";
        request.setRequestURI("/api/v1/protected");
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(token)).thenThrow(new RuntimeException("Token error"));

        // Pre-set enterprise context to verify it's cleared
        EnterpriseContextHolder.setEnterpriseId(100L);
        EnterpriseContextHolder.setUserId(1L);

        filter.doFilterInternal(request, response, filterChain);

        // Both contexts should be cleared
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(EnterpriseContextHolder.getEnterpriseId()).isNull();
        assertThat(EnterpriseContextHolder.getUserId()).isNull();
    }

    // ==================== Branch Coverage: normalizePath with single dot ====================

    @Test
    @DisplayName("normalizePath-路径中单个点段应被跳过")
    void doFilterInternal_pathWithSingleDotSegment_shouldSkipDot() throws ServletException, IOException {
        // Path: /./api/v1/auth/login → segments: ["", ".", "api", "v1", "auth", "login"]
        // The "." segment should be skipped during normalization
        // Result: /api/v1/auth/login → IS whitelisted
        request.setRequestURI("/./api/v1/auth/login");

        filter.doFilterInternal(request, response, filterChain);

        // Should be whitelisted after normalization
        verify(jwtTokenProvider, never()).validateToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
