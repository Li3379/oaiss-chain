package com.oaiss.chain.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oaiss.chain.constant.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JwtAccessDeniedHandler unit tests")
class JwtAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("handle should return 403 JSON payload")
    void handle_shouldReturnForbiddenPayload() throws Exception {
        JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());

        Map<?, ?> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals(ErrorCode.PERMISSION_DENIED, body.get("code"));
        assertEquals("无权限访问该资源", body.get("message"));
    }
}
