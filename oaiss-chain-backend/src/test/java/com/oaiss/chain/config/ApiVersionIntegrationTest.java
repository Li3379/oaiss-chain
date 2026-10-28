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
        assertArrayEquals(new String[]{"v1"}, ApiVersionConfig.SUPPORTED_VERSIONS);
        assertEquals("/api", ApiVersionConfig.API_PREFIX);
    }
}
