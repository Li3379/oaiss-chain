package com.oaiss.chain.security;

import com.oaiss.chain.service.UserSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT认证过滤器
 * JWT Authentication Filter
 * 
 * 拦截请求，验证JWT令牌，设置安全上下文
 * 
 * @author OAISS Team
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CacheManager cacheManager;

    @Autowired(required = false)
    private UserSessionService userSessionService;

    @Autowired
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CacheManager cacheManager) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.cacheManager = cacheManager;
    }

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CacheManager cacheManager, UserSessionService userSessionService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.cacheManager = cacheManager;
        this.userSessionService = userSessionService;
    }

    /**
     * 白名单路径（不需要认证）
     * 注意：context-path为/api/v1，request.getRequestURI()返回完整路径
     * 所以白名单需要包含完整路径前缀
     */
    private static final List<String> WHITELIST_PATTERNS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/captcha",
            "/api/v1/auth/refresh",
            "/api/v1/auth/check-ip",
            "/api/v1/captcha/**",
            "/api/v1/actuator/health/**",
            "/api/v1/search/market-overview"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // 白名单路径直接放行
        if (isWhitelisted(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 提取Token
        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            // 检查Token是否已被加入黑名单（用户已登出或Token被废止）
            if (userSessionService != null && userSessionService.isTokenBlacklisted(token)) {
                log.debug("Token is blacklisted in userSessionService, skipping authentication");
                filterChain.doFilter(request, response);
                return;
            }

            Cache blacklist = cacheManager.getCache("tokenBlacklist");
            if (blacklist != null && blacklist.get(token) != null) {
                log.debug("Token is blacklisted, skipping authentication");
                filterChain.doFilter(request, response);
                return;
            }

            try {
                // 从Token中获取用户信息
                String username = jwtTokenProvider.getUsernameFromToken(token);
                java.util.Date issuedAt = jwtTokenProvider.getIssuedAt(token);

                // 检查用户是否在Token签发后执行过登出或改密（全局吊销）
                if (userSessionService != null && userSessionService.isSessionRevoked(username, issuedAt)) {
                    log.debug("Session revoked for user: {}, skipping authentication", username);
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                List<String> roles = jwtTokenProvider.getRolesFromToken(token);
                Integer userType = jwtTokenProvider.getUserTypeFromToken(token);
                Long enterpriseId = jwtTokenProvider.getEnterpriseIdFromToken(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = JwtUserDetails.builder()
                            .userId(userId)
                            .username(username)
                            .roles(roles)
                            .userType(userType)
                            .enterpriseId(enterpriseId)
                            .enabled(true)
                            .accountNonExpired(true)
                            .accountNonLocked(true)
                            .credentialsNonExpired(true)
                            .build();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities());

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Set authentication for user: {} (ID: {})", username, userId);
                }
            } catch (Exception e) {
                log.error("Cannot set user authentication: {}", e.getMessage());
                SecurityContextHolder.clearContext();
                EnterpriseContextHolder.clear();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 检查路径是否在白名单中
     * 使用Spring AntPathMatcher进行安全的模式匹配：
     * 1. 先拒绝格式异常路径（双斜杠、URL编码遍历）
     * 2. 规范化路径遍历序列（../、./）
     * 3. 使用AntPathMatcher匹配白名单模式
     */
    private boolean isWhitelisted(String requestPath) {
        if (requestPath == null || requestPath.isEmpty()) {
            return false;
        }
        // Reject malformed paths that AntPathMatcher would normalize
        if (requestPath.contains("//") || requestPath.contains("%2e") || requestPath.contains("%2E")) {
            return false;
        }
        String normalized = normalizePath(requestPath);
        return WHITELIST_PATTERNS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, normalized));
    }

    /**
     * 规范化路径，仅解析 ../ 和 ./ 段。
     * 保留空段（双斜杠 //）以便 AntPathMatcher 正确拒绝异常路径。
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        String[] segments = path.split("/", -1);
        List<String> resolved = new java.util.ArrayList<>();
        for (String segment : segments) {
            if (".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!resolved.isEmpty()) {
                    resolved.remove(resolved.size() - 1);
                }
            } else {
                resolved.add(segment);
            }
        }
        return String.join("/", resolved);
    }
}
