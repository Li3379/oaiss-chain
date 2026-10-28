package com.oaiss.chain.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT Cookie utility for httpOnly cookie-based token management.
 * Tokens are stored in httpOnly cookies with SameSite=Lax for XSS + CSRF protection.
 *
 * @author OAISS Team
 */
public final class JwtCookieUtil {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private JwtCookieUtil() {}

    /**
     * Set access token as httpOnly cookie.
     */
    public static void setAccessTokenCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        setCookie(response, ACCESS_TOKEN_COOKIE, token, maxAgeSeconds, true);
    }

    /**
     * Set refresh token as httpOnly cookie.
     */
    public static void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        setCookie(response, REFRESH_TOKEN_COOKIE, token, maxAgeSeconds, true);
    }

    /**
     * Clear access token cookie.
     */
    public static void clearAccessTokenCookie(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE);
    }

    /**
     * Clear refresh token cookie.
     */
    public static void clearRefreshTokenCookie(HttpServletResponse response) {
        clearCookie(response, REFRESH_TOKEN_COOKIE);
    }

    /**
     * Extract access token from httpOnly cookie.
     */
    public static String getAccessTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, ACCESS_TOKEN_COOKIE);
    }

    /**
     * Extract refresh token from httpOnly cookie.
     */
    public static String getRefreshTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_COOKIE);
    }

    private static void setCookie(HttpServletResponse response, String name, String value,
                                   long maxAgeSeconds, boolean httpOnly) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(false); // Set to true in production (HTTPS)
        cookie.setMaxAge((int) maxAgeSeconds);
        // SameSite=Lax: sent for top-level navigations and same-site requests, but not for cross-site POST.
        // This provides CSRF protection without needing a separate CSRF token.
        response.setHeader("Set-Cookie",
                name + "=" + value
                + "; Path=/"
                + "; Max-Age=" + maxAgeSeconds
                + (httpOnly ? "; HttpOnly" : "")
                + "; SameSite=Lax");
    }

    private static void clearCookie(HttpServletResponse response, String name) {
        response.setHeader("Set-Cookie",
                name + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    }

    private static String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
