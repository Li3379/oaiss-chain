package com.oaiss.chain.interceptor;

import com.oaiss.chain.config.ApiVersionConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API版本解析拦截器
 * 从请求中解析API版本并存入request attribute
 * 
 * 解析优先级:
 * 1. Accept-Version header
 * 2. version query parameter
 * 3. URL路径中的版本 (已由Spring MVC处理)
 * 4. 默认版本
 */
@Component
public class ApiVersionInterceptor implements HandlerInterceptor {

    private static final String VERSION_ATTRIBUTE = "api.version";
    private static final String HEADER_NAME = "Accept-Version";
    private static final String PARAM_NAME = "version";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                             Object handler) throws Exception {
        String version = resolveVersion(request);
        request.setAttribute(VERSION_ATTRIBUTE, version);
        
        response.setHeader("X-API-Version", version);
        response.setHeader("X-API-Supported-Versions", 
                String.join(", ", ApiVersionConfig.SUPPORTED_VERSIONS));
        
        return true;
    }

    /**
     * 解析请求中的API版本
     */
    private String resolveVersion(HttpServletRequest request) {
        // 1. 从header解析
        String headerVersion = request.getHeader(HEADER_NAME);
        if (headerVersion != null && !headerVersion.isBlank()) {
            String normalized = normalizeVersion(headerVersion);
            if (isSupportedVersion(normalized)) {
                return normalized;
            }
        }

        // 2. 从query参数解析
        String paramVersion = request.getParameter(PARAM_NAME);
        if (paramVersion != null && !paramVersion.isBlank()) {
            String normalized = normalizeVersion(paramVersion);
            if (isSupportedVersion(normalized)) {
                return normalized;
            }
        }

        // 3. 默认版本
        return ApiVersionConfig.CURRENT_VERSION;
    }

    /**
     * 标准化版本号格式 (确保v前缀)
     */
    private String normalizeVersion(String version) {
        version = version.trim().toLowerCase();
        if (!version.startsWith("v")) {
            version = "v" + version;
        }
        return version;
    }

    /**
     * 检查版本是否受支持
     */
    private boolean isSupportedVersion(String version) {
        for (String supported : ApiVersionConfig.SUPPORTED_VERSIONS) {
            if (supported.equals(version)) {
                return true;
            }
        }
        return false;
    }
}
