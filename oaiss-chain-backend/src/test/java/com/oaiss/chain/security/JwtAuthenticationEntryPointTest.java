package com.oaiss.chain.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oaiss.chain.constant.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JwtAuthenticationEntryPoint unit tests")
class JwtAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("commence should return 401 JSON payload")
    void commence_shouldReturnUnauthorizedPayload() throws Exception {
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("expired"));

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());

        Map<?, ?> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals(ErrorCode.USER_NOT_LOGIN, body.get("code"));
        assertEquals("用户未登录或Token已过期，请重新登录", body.get("message"));
    }
}
