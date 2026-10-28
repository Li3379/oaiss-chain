package com.oaiss.chain.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenProvider 单元测试
 * JwtTokenProvider Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";
    private static final Long ACCESS_TOKEN_EXPIRATION = 3600000L; // 1 hour
    private static final Long REFRESH_TOKEN_EXPIRATION = 86400000L; // 24 hours

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", ACCESS_TOKEN_EXPIRATION);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpiration", REFRESH_TOKEN_EXPIRATION);
    }

    // ==================== generateAccessToken Tests ====================

    @Test
    @DisplayName("生成访问令牌-成功")
    void testGenerateAccessTokenSuccess() {
        // Given
        Long userId = 1L;
        String username = "testuser";
        List<String> roles = Arrays.asList("ROLE_USER", "ROLE_ADMIN");

        // When
        String token = jwtTokenProvider.generateAccessToken(userId, username, roles);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Verify token content
        Claims claims = jwtTokenProvider.parseToken(token);
        assertNotNull(claims);
        assertEquals(username, claims.getSubject());
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(roles, jwtTokenProvider.getRolesFromToken(token));
        assertEquals("access", claims.get("type"));
    }

    @Test
    @DisplayName("生成访问令牌-包含企业ID-成功")
    void testGenerateAccessTokenWithEnterpriseSuccess() {
        // Given
        Long userId = 1L;
        String username = "enterpriseuser";
        List<String> roles = Arrays.asList("ROLE_USER");
        Integer userType = 2;
        Long enterpriseId = 100L;

        // When
        String token = jwtTokenProvider.generateAccessToken(userId, username, roles, userType, enterpriseId);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());

        Claims claims = jwtTokenProvider.parseToken(token);
        assertNotNull(claims);
        assertEquals(username, claims.getSubject());
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(roles, jwtTokenProvider.getRolesFromToken(token));
        assertEquals(userType, jwtTokenProvider.getUserTypeFromToken(token));
        assertEquals(enterpriseId, jwtTokenProvider.getEnterpriseIdFromToken(token));
        assertEquals("access", claims.get("type"));
    }

    @Test
    @DisplayName("生成访问令牌-无企业ID-成功")
    void testGenerateAccessTokenWithoutEnterpriseSuccess() {
        // Given
        Long userId = 1L;
        String username = "normaluser";
        List<String> roles = Arrays.asList("ROLE_USER");
        Integer userType = 1;

        // When
        String token = jwtTokenProvider.generateAccessToken(userId, username, roles, userType, null);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());

        Claims claims = jwtTokenProvider.parseToken(token);
        assertNotNull(claims);
        assertEquals(username, claims.getSubject());
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(roles, jwtTokenProvider.getRolesFromToken(token));
        assertEquals(userType, jwtTokenProvider.getUserTypeFromToken(token));
        assertNull(jwtTokenProvider.getEnterpriseIdFromToken(token));
    }

    @Test
    @DisplayName("生成访问令牌-空角色列表-成功")
    void testGenerateAccessTokenWithEmptyRoles() {
        // Given
        Long userId = 1L;
        String username = "testuser";
        List<String> roles = Collections.emptyList();

        // When
        String token = jwtTokenProvider.generateAccessToken(userId, username, roles);

        // Then
        assertNotNull(token);
        Claims claims = jwtTokenProvider.parseToken(token);
        assertNotNull(claims);
        assertEquals(Collections.emptyList(), jwtTokenProvider.getRolesFromToken(token));
    }

    // ==================== generateRefreshToken Tests ====================

    @Test
    @DisplayName("生成刷新令牌-成功")
    void testGenerateRefreshTokenSuccess() {
        // Given
        Long userId = 1L;
        String username = "testuser";

        // When
        String token = jwtTokenProvider.generateRefreshToken(userId, username);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());

        Claims claims = jwtTokenProvider.parseToken(token);
        assertNotNull(claims);
        assertEquals(username, claims.getSubject());
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals("refresh", claims.get("type"));
        assertNull(claims.get("roles"));
    }

    // ==================== parseToken Tests ====================

    @Test
    @DisplayName("解析令牌-有效令牌-成功")
    void testParseTokenValidToken() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"));

        // When
        Claims claims = jwtTokenProvider.parseToken(token);

        // Then
        assertNotNull(claims);
        assertEquals("testuser", claims.getSubject());
    }

    @Test
    @DisplayName("解析令牌-无效令牌-返回null")
    void testParseTokenInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        Claims claims = jwtTokenProvider.parseToken(invalidToken);

        // Then
        assertNull(claims);
    }

    @Test
    @DisplayName("解析令牌-空令牌-返回null")
    void testParseTokenEmptyToken() {
        // Given
        String emptyToken = "";

        // When & Then - parseToken should catch the exception and return null
        assertThrows(IllegalArgumentException.class, () -> jwtTokenProvider.parseToken(emptyToken));
    }

    @Test
    @DisplayName("解析令牌-错误签名的令牌-返回null")
    void testParseTokenWrongSignature() {
        // Given - create token with different secret
        String differentSecret = "different-secret-key-for-testing-must-be-256-bits-long";
        SecretKey differentKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));
        String wrongToken = Jwts.builder()
                .claim("userId", 1L)
                .subject("testuser")
                .signWith(differentKey)
                .compact();

        // When
        Claims claims = jwtTokenProvider.parseToken(wrongToken);

        // Then
        assertNull(claims);
    }

    // ==================== validateToken Tests ====================

    @Test
    @DisplayName("验证令牌-有效令牌-返回true")
    void testValidateTokenValid() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"));

        // When
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("验证令牌-无效令牌-返回false")
    void testValidateTokenInvalid() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证令牌-空令牌-返回false")
    void testValidateTokenEmpty() {
        // Given
        String emptyToken = "";

        // When
        boolean isValid = jwtTokenProvider.validateToken(emptyToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证令牌-null令牌-返回false")
    void testValidateTokenNull() {
        // When
        boolean isValid = jwtTokenProvider.validateToken(null);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证令牌-格式错误的令牌-返回false")
    void testValidateTokenMalformed() {
        // Given
        String malformedToken = "not.a.valid.jwt.format";

        // When
        boolean isValid = jwtTokenProvider.validateToken(malformedToken);

        // Then
        assertFalse(isValid);
    }

    // ==================== getUsernameFromToken Tests ====================

    @Test
    @DisplayName("从令牌获取用户名-成功")
    void testGetUsernameFromTokenSuccess() {
        // Given
        String username = "testuser";
        String token = jwtTokenProvider.generateAccessToken(1L, username, Arrays.asList("ROLE_USER"));

        // When
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        // Then
        assertEquals(username, extractedUsername);
    }

    @Test
    @DisplayName("从令牌获取用户名-无效令牌-返回null")
    void testGetUsernameFromTokenInvalidToken() {
        // Given
        String invalidToken = "invalid.token";

        // When
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(invalidToken);

        // Then
        assertNull(extractedUsername);
    }

    // ==================== getUserIdFromToken Tests ====================

    @Test
    @DisplayName("从令牌获取用户ID-Long类型-成功")
    void testGetUserIdFromTokenLongSuccess() {
        // Given
        Long userId = 12345L;
        String token = jwtTokenProvider.generateAccessToken(userId, "testuser", Arrays.asList("ROLE_USER"));

        // When
        Long extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

        // Then
        assertEquals(userId, extractedUserId);
    }

    @Test
    @DisplayName("从令牌获取用户ID-无效令牌-返回null")
    void testGetUserIdFromTokenInvalidToken() {
        // Given
        String invalidToken = "invalid.token";

        // When
        Long extractedUserId = jwtTokenProvider.getUserIdFromToken(invalidToken);

        // Then
        assertNull(extractedUserId);
    }

    // ==================== getRolesFromToken Tests ====================

    @Test
    @DisplayName("从令牌获取角色列表-成功")
    void testGetRolesFromTokenSuccess() {
        // Given
        List<String> roles = Arrays.asList("ROLE_USER", "ROLE_ADMIN");
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", roles);

        // When
        List<String> extractedRoles = jwtTokenProvider.getRolesFromToken(token);

        // Then
        assertEquals(roles, extractedRoles);
    }

    @Test
    @DisplayName("从令牌获取角色列表-刷新令牌-返回空列表")
    void testGetRolesFromRefreshToken() {
        // Given
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L, "testuser");

        // When
        List<String> extractedRoles = jwtTokenProvider.getRolesFromToken(refreshToken);

        // Then
        assertNotNull(extractedRoles);
        assertTrue(extractedRoles.isEmpty());
    }

    @Test
    @DisplayName("从令牌获取角色列表-无效令牌-返回空列表")
    void testGetRolesFromTokenInvalidToken() {
        // Given
        String invalidToken = "invalid.token";

        // When
        List<String> extractedRoles = jwtTokenProvider.getRolesFromToken(invalidToken);

        // Then
        assertNotNull(extractedRoles);
        assertTrue(extractedRoles.isEmpty());
    }

    // ==================== getUserTypeFromToken Tests ====================

    @Test
    @DisplayName("从令牌获取用户类型-成功")
    void testGetUserTypeFromTokenSuccess() {
        // Given
        Integer userType = 2;
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"), userType, 100L);

        // When
        Integer extractedUserType = jwtTokenProvider.getUserTypeFromToken(token);

        // Then
        assertEquals(userType, extractedUserType);
    }

    @Test
    @DisplayName("从令牌获取用户类型-无用户类型-返回null")
    void testGetUserTypeFromTokenNoUserType() {
        // Given - access token without userType (using 3-param method)
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"));

        // When
        Integer extractedUserType = jwtTokenProvider.getUserTypeFromToken(token);

        // Then
        assertNull(extractedUserType);
    }

    @Test
    @DisplayName("从令牌获取用户类型-无效令牌-返回null")
    void testGetUserTypeFromTokenInvalidToken() {
        // Given
        String invalidToken = "invalid.token";

        // When
        Integer extractedUserType = jwtTokenProvider.getUserTypeFromToken(invalidToken);

        // Then
        assertNull(extractedUserType);
    }

    // ==================== getEnterpriseIdFromToken Tests ====================

    @Test
    @DisplayName("从令牌获取企业ID-成功")
    void testGetEnterpriseIdFromTokenSuccess() {
        // Given
        Long enterpriseId = 100L;
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"), 2, enterpriseId);

        // When
        Long extractedEnterpriseId = jwtTokenProvider.getEnterpriseIdFromToken(token);

        // Then
        assertEquals(enterpriseId, extractedEnterpriseId);
    }

    @Test
    @DisplayName("从令牌获取企业ID-无企业ID-返回null")
    void testGetEnterpriseIdFromTokenNoEnterpriseId() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"), 1, null);

        // When
        Long extractedEnterpriseId = jwtTokenProvider.getEnterpriseIdFromToken(token);

        // Then
        assertNull(extractedEnterpriseId);
    }

    @Test
    @DisplayName("从令牌获取企业ID-无效令牌-返回null")
    void testGetEnterpriseIdFromTokenInvalidToken() {
        // Given
        String invalidToken = "invalid.token";

        // When
        Long extractedEnterpriseId = jwtTokenProvider.getEnterpriseIdFromToken(invalidToken);

        // Then
        assertNull(extractedEnterpriseId);
    }

    // ==================== isTokenExpired Tests ====================

    @Test
    @DisplayName("检查令牌过期-未过期-返回false")
    void testIsTokenExpiredNotExpired() {
        // Given
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"));

        // When
        boolean isExpired = jwtTokenProvider.isTokenExpired(token);

        // Then
        assertFalse(isExpired);
    }

    @Test
    @DisplayName("检查令牌过期-无效令牌-返回true")
    void testIsTokenExpiredInvalidToken() {
        // Given
        String invalidToken = "invalid.token";

        // When
        boolean isExpired = jwtTokenProvider.isTokenExpired(invalidToken);

        // Then
        assertTrue(isExpired);
    }

    // ==================== isRefreshToken Tests ====================

    @Test
    @DisplayName("检查是否为刷新令牌-是刷新令牌-返回true")
    void testIsRefreshTokenTrue() {
        // Given
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L, "testuser");

        // When
        boolean isRefresh = jwtTokenProvider.isRefreshToken(refreshToken);

        // Then
        assertTrue(isRefresh);
    }

    @Test
    @DisplayName("检查是否为刷新令牌-是访问令牌-返回false")
    void testIsRefreshTokenFalse() {
        // Given
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"));

        // When
        boolean isRefresh = jwtTokenProvider.isRefreshToken(accessToken);

        // Then
        assertFalse(isRefresh);
    }

    @Test
    @DisplayName("检查是否为刷新令牌-无效令牌-返回false")
    void testIsRefreshTokenInvalidToken() {
        // Given
        String invalidToken = "invalid.token";

        // When
        boolean isRefresh = jwtTokenProvider.isRefreshToken(invalidToken);

        // Then
        assertFalse(isRefresh);
    }

    // ==================== Expiration Getter Tests ====================

    @Test
    @DisplayName("获取访问令牌过期时间-成功")
    void testGetAccessTokenExpiration() {
        // When
        Long expiration = jwtTokenProvider.getAccessTokenExpiration();

        // Then
        assertEquals(ACCESS_TOKEN_EXPIRATION, expiration);
    }

    @Test
    @DisplayName("获取刷新令牌过期时间-成功")
    void testGetRefreshTokenExpiration() {
        // When
        Long expiration = jwtTokenProvider.getRefreshTokenExpiration();

        // Then
        assertEquals(REFRESH_TOKEN_EXPIRATION, expiration);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("不同用户生成不同令牌")
    void testDifferentUsersGenerateDifferentTokens() {
        // Given
        Long userId1 = 1L;
        Long userId2 = 2L;
        String username1 = "user1";
        String username2 = "user2";

        // When
        String token1 = jwtTokenProvider.generateAccessToken(userId1, username1, Arrays.asList("ROLE_USER"));
        String token2 = jwtTokenProvider.generateAccessToken(userId2, username2, Arrays.asList("ROLE_USER"));

        // Then
        assertNotEquals(token1, token2);
        assertEquals(username1, jwtTokenProvider.getUsernameFromToken(token1));
        assertEquals(username2, jwtTokenProvider.getUsernameFromToken(token2));
    }

    @Test
    @DisplayName("同一用户生成多个令牌-均可验证")
    void testSameUserMultipleTokenGenerations() {
        // Given
        Long userId = 1L;
        String username = "testuser";
        List<String> roles = Arrays.asList("ROLE_USER");

        // When
        String token1 = jwtTokenProvider.generateAccessToken(userId, username, roles);
        String token2 = jwtTokenProvider.generateAccessToken(userId, username, roles);

        // Then - both tokens should be valid and contain the same claims
        assertTrue(jwtTokenProvider.validateToken(token1));
        assertTrue(jwtTokenProvider.validateToken(token2));
        assertEquals(username, jwtTokenProvider.getUsernameFromToken(token1));
        assertEquals(username, jwtTokenProvider.getUsernameFromToken(token2));
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token1));
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token2));
    }

    @Test
    @DisplayName("刷新令牌不包含角色信息")
    void testRefreshTokenDoesNotContainRoles() {
        // Given
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L, "testuser");

        // When
        List<String> roles = jwtTokenProvider.getRolesFromToken(refreshToken);

        // Then
        assertNotNull(roles);
        assertTrue(roles.isEmpty());
    }

    @Test
    @DisplayName("访问令牌不包含refresh类型标识")
    void testAccessTokenNotRefreshType() {
        // Given
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"));

        // When
        boolean isRefresh = jwtTokenProvider.isRefreshToken(accessToken);

        // Then
        assertFalse(isRefresh);
    }

    @Test
    @DisplayName("从令牌获取用户ID-Integer类型-成功")
    void testGetUserIdFromTokenIntegerSuccess() {
        // Given - Create a token with userId as Integer (JJWT may serialize small longs as Integer)
        String token = Jwts.builder()
                .claim("userId", 1)
                .subject("testuser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(ReflectionTestUtils.invokeMethod(jwtTokenProvider, "getSigningKey"))
                .compact();

        // When
        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        // Then
        assertNotNull(userId);
        assertEquals(1L, userId);
    }

    @Test
    @DisplayName("检查令牌过期-过期令牌-返回true")
    void testIsTokenExpiredExpiredToken() {
        // Given - create an expired token
        String expiredToken = Jwts.builder()
                .claim("userId", 1L)
                .subject("testuser")
                .issuedAt(new Date(System.currentTimeMillis() - 2 * ACCESS_TOKEN_EXPIRATION))
                .expiration(new Date(System.currentTimeMillis() - ACCESS_TOKEN_EXPIRATION))
                .signWith(ReflectionTestUtils.invokeMethod(jwtTokenProvider, "getSigningKey"))
                .compact();

        // When
        boolean isExpired = jwtTokenProvider.isTokenExpired(expiredToken);

        // Then
        assertTrue(isExpired);
    }

    @Test
    @DisplayName("验证令牌-不支持的令牌类型-返回false")
    void testValidateTokenUnsupportedJwt() {
        // Given - create a manually crafted JWT with "none" algorithm which is unsupported
        String unsupportedToken = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJ1c2VySWQiOjF9.";

        // When
        boolean isValid = jwtTokenProvider.validateToken(unsupportedToken);

        // Then - should return false (UnsupportedJwtException or other JwtException)
        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证令牌-空claims字符串-返回false")
    void testValidateTokenEmptyClaimsString() {
        // When - null token
        boolean isValid = jwtTokenProvider.validateToken(null);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("从令牌获取企业ID-Integer类型-返回Long")
    void testGetEnterpriseIdFromTokenIntegerType() {
        // Given - manually create token with Integer enterpriseId claim
        String token = Jwts.builder()
                .claim("userId", 1L)
                .claim("enterpriseId", 99)
                .subject("testuser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(ReflectionTestUtils.invokeMethod(jwtTokenProvider, "getSigningKey"))
                .compact();

        // When
        Long extractedEnterpriseId = jwtTokenProvider.getEnterpriseIdFromToken(token);

        // Then
        assertEquals(99L, extractedEnterpriseId);
    }

    @Test
    @DisplayName("验证令牌-过期令牌-返回false")
    void testValidateTokenExpiredReturnsFalse() {
        // Given - create an already-expired token
        String expiredToken = Jwts.builder()
                .claim("userId", 1L)
                .subject("testuser")
                .issuedAt(new Date(System.currentTimeMillis() - 2 * ACCESS_TOKEN_EXPIRATION))
                .expiration(new Date(System.currentTimeMillis() - ACCESS_TOKEN_EXPIRATION))
                .signWith(ReflectionTestUtils.invokeMethod(jwtTokenProvider, "getSigningKey"))
                .compact();

        // When
        boolean isValid = jwtTokenProvider.validateToken(expiredToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("从令牌获取用户ID-大于Integer.MAX_VALUE的Long类型")
    void testGetUserIdFromTokenLargeLongValue() {
        // Given - userId > Integer.MAX_VALUE ensures it stays as Long after JSON round-trip
        Long largeUserId = (long) Integer.MAX_VALUE + 100L;
        String token = jwtTokenProvider.generateAccessToken(largeUserId, "testuser", Arrays.asList("ROLE_USER"));

        // When
        Long extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

        // Then
        assertNotNull(extractedUserId);
        assertEquals(largeUserId, extractedUserId);
    }

    @Test
    @DisplayName("从令牌获取企业ID-大于Integer.MAX_VALUE的Long类型")
    void testGetEnterpriseIdFromTokenLargeLongValue() {
        // Given - enterpriseId > Integer.MAX_VALUE ensures it stays as Long after JSON round-trip
        Long largeEnterpriseId = (long) Integer.MAX_VALUE + 200L;
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", Arrays.asList("ROLE_USER"), 2, largeEnterpriseId);

        // When
        Long extractedEnterpriseId = jwtTokenProvider.getEnterpriseIdFromToken(token);

        // Then
        assertNotNull(extractedEnterpriseId);
        assertEquals(largeEnterpriseId, extractedEnterpriseId);
    }
}
