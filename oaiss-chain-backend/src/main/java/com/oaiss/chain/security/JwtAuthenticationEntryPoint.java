package com.oaiss.chain.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.ApiResponse;

import java.io.IOException;

/**
 * JWT认证入口点
 * 处理未认证的请求（返回401）
 * 
 * @author OAISS Team
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        log.warn("Unauthorized request: {} - {}", request.getRequestURI(), authException.getMessage());

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.USER_NOT_LOGIN,
                "用户未登录或Token已过期，请重新登录");

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
