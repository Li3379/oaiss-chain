package com.oaiss.chain.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT工具类
 * JWT Utilities
 * 
 * @author OAISS Team
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成访问令牌
     *
     * @param userId       用户ID
     * @param username     用户名
     * @param roles        角色列表
     * @return JWT令牌
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles);
        claims.put("type", "access");

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成访问令牌（包含企业ID）
     *
     * @param userId       用户ID
     * @param username     用户名
     * @param roles        角色列表
     * @param userType     用户类型
     * @param enterpriseId 企业ID（可选）
     * @return JWT令牌
     */
    public String generateAccessToken(Long userId, String username, List<String> roles, 
                                       Integer userType, Long enterpriseId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles);
        claims.put("userType", userType);
        claims.put("type", "access");
        if (enterpriseId != null) {
            claims.put("enterpriseId", enterpriseId);
        }

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成刷新令牌
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return 刷新令牌
     */
    public String generateRefreshToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析令牌
     *
     * @param token JWT令牌
     * @return Claims
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.error("JWT token parse failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证令牌有效性
     *
     * @param token JWT令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        } catch (JwtException e) {
            log.error("JWT validation failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 从令牌中获取用户名
     *
     * @param token JWT令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 从令牌中获取用户ID
     *
     * @param token JWT令牌
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object userId = claims.get("userId");
            if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            } else if (userId instanceof Long) {
                return (Long) userId;
            }
        }
        return null;
    }

    /**
     * 从令牌中获取角色列表
     *
     * @param token JWT令牌
     * @return 角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object roles = claims.get("roles");
            if (roles instanceof List) {
                return (List<String>) roles;
            }
        }
        return List.of();
    }

    /**
     * 从令牌中获取用户类型
     *
     * @param token JWT令牌
     * @return 用户类型
     */
    public Integer getUserTypeFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object userType = claims.get("userType");
            if (userType instanceof Integer) {
                return (Integer) userType;
            }
        }
        return null;
    }

    /**
     * 从令牌中获取企业ID
     *
     * @param token JWT令牌
     * @return 企业ID
     */
    public Long getEnterpriseIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object enterpriseId = claims.get("enterpriseId");
            if (enterpriseId instanceof Integer) {
                return ((Integer) enterpriseId).longValue();
            } else if (enterpriseId instanceof Long) {
                return (Long) enterpriseId;
            }
        }
        return null;
    }

    /**
     * 检查令牌是否过期
     *
     * @param token JWT令牌
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims != null) {
                return claims.getExpiration().before(new Date());
            }
        } catch (ExpiredJwtException e) {
            return true;
        }
        return true;
    }

    /**
     * 检查是否为刷新令牌
     *
     * @param token JWT令牌
     * @return 是否为刷新令牌
     */
    public boolean isRefreshToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            Object type = claims.get("type");
            return "refresh".equals(type);
        }
        return false;
    }

    /**
     * 从令牌中获取签发时间
     *
     * @param token JWT令牌
     * @return 签发时间
     */
    public Date getIssuedAt(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getIssuedAt() : null;
    }

    /**
     * 获取访问令牌过期时间（毫秒）
     */
    public Long getAccessTokenExpiration() {
        return jwtExpiration;
    }

    /**
     * 获取刷新令牌过期时间（毫秒）
     */
    public Long getRefreshTokenExpiration() {
        return refreshExpiration;
    }
}
