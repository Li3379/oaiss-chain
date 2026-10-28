package com.oaiss.chain.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.ApiResponse;

import java.io.IOException;

/**
 * JWT访问拒绝处理器
 * 处理已认证但无权限的请求（返回403）
 * 
 * @author OAISS Team
 */
@Slf4j
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Access denied: {} - {}", request.getRequestURI(), accessDeniedException.getMessage());

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.PERMISSION_DENIED,
                "无权限访问该资源");

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
