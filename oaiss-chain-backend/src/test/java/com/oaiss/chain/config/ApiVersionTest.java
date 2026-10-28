package com.oaiss.chain.config;

import com.oaiss.chain.interceptor.ApiVersionInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class ApiVersionTest {

    private ApiVersionInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new ApiVersionInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void defaultVersion_whenNoVersionSpecified() throws Exception {
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
        assertEquals("v1", response.getHeader("X-API-Version"));
    }

    @Test
    void headerVersion_whenAcceptVersionHeaderPresent() throws Exception {
        request.addHeader("Accept-Version", "v1");
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }

    @Test
    void headerVersion_normalizesVersion() throws Exception {
        request.addHeader("Accept-Version", "1");
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }

    @Test
    void queryVersion_whenVersionParamPresent() throws Exception {
        request.setParameter("version", "v1");
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }

    @Test
    void queryVersion_normalizesVersion() throws Exception {
        request.setParameter("version", "1");
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }

    @Test
    void headerVersion_takesPrecedenceOverQuery() throws Exception {
        request.addHeader("Accept-Version", "v1");
        request.setParameter("version", "v1");
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }

    @Test
    void unsupportedVersion_fallsBackToDefault() throws Exception {
        request.addHeader("Accept-Version", "v99");
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }

    @Test
    void responseContainsSupportedVersionsHeader() throws Exception {
        interceptor.preHandle(request, response, null);
        String supported = response.getHeader("X-API-Supported-Versions");
        assertNotNull(supported);
        assertTrue(supported.contains("v1"));
    }

    @Test
    void blankVersion_usesDefault() throws Exception {
        request.addHeader("Accept-Version", "  ");
        interceptor.preHandle(request, response, null);
        assertEquals("v1", request.getAttribute("api.version"));
    }
}
