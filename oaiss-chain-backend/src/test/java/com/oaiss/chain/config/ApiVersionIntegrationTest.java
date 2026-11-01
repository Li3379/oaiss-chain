package com.oaiss.chain.config;

import com.oaiss.chain.interceptor.ApiVersionInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R116: API versioning integration tests.
 * Verifies Accept-Version header negotiation and response headers.
 */
class ApiVersionIntegrationTest {

    private final ApiVersionInterceptor interceptor = new ApiVersionInterceptor();

    @Test
    @DisplayName("响应包含X-API-Version header")  
    void responseContainsApiVersionHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, null);
        assertEquals("v1", response.getHeader("X-API-Version"));
    }

    @Test
    @DisplayName("响应包含X-API-Supported-Versions header")
    void responseContainsSupportedVersionsHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, null);
        String supported = response.getHeader("X-API-Supported-Versions");
        assertNotNull(supported);
        assertTrue(supported.contains("v1"));
    }

    @Test
    @DisplayName("版本号大小写不敏感")
    void versionCaseInsensitive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Version", "V1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }

    @Test
    @DisplayName("空版本号回退到默认")
    void emptyVersionFallsBackToDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Version", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }

    @Test
    @DisplayName("API版本常量正确")
    void apiVersionConstants() {
        assertEquals("v1", ApiVersionConfig.CURRENT_VERSION);
        assertEquals(java.util.List.of("v1"), ApiVersionConfig.SUPPORTED_VERSIONS);
        assertEquals("/api", ApiVersionConfig.API_PREFIX);
    }

    @Test
    @DisplayName("支持版本列表不可被调用方篡改")
    void supportedVersionsIsImmutable() {
        // 原先是 public static final String[]：final 只锁引用，内容可改。
        // 任何一处 ApiVersionConfig.SUPPORTED_VERSIONS[0] = "v9" 都会让
        // 版本协商静默接受一个从未实现的版本。改为不可变 List 后必须抛异常。
        assertThrows(
                UnsupportedOperationException.class,
                () -> ApiVersionConfig.SUPPORTED_VERSIONS.add("v9"));
    }
}
