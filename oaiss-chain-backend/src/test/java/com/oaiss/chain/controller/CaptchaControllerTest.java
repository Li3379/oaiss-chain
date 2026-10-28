package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.CaptchaResponse;
import com.oaiss.chain.dto.CaptchaSendRequest;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CaptchaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CaptchaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CaptchaService captchaService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private CaptchaResponse captchaResponse;

    @BeforeEach
    void setUp() {
        captchaResponse = CaptchaResponse.builder()
                .captchaKey("captcha-uuid-123")
                .captchaImage("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
                .expiresIn(300L)
                .build();
    }

    @Test
    @DisplayName("生成图形验证码成功")
    void testGenerateSuccess() throws Exception {
        when(captchaService.generateCaptcha()).thenReturn(captchaResponse);

        mockMvc.perform(get("/captcha/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.captchaKey").value("captcha-uuid-123"))
                .andExpect(jsonPath("$.data.captchaImage").exists());

        verify(captchaService).generateCaptcha();
    }

    @Test
    @DisplayName("验证验证码成功")
    void testVerifySuccess() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("captchaKey", "captcha-uuid-123");
        body.put("captchaCode", "1234");

        when(captchaService.verifyCaptcha("captcha-uuid-123", "1234")).thenReturn(true);

        mockMvc.perform(post("/captcha/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(captchaService).verifyCaptcha("captcha-uuid-123", "1234");
    }

    @Test
    @DisplayName("验证验证码失败")
    void testVerifyFail() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("captchaKey", "captcha-uuid-123");
        body.put("captchaCode", "wrong");

        when(captchaService.verifyCaptcha("captcha-uuid-123", "wrong")).thenReturn(false);

        mockMvc.perform(post("/captcha/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));

        verify(captchaService).verifyCaptcha("captcha-uuid-123", "wrong");
    }

    @Test
    @DisplayName("发送短信验证码成功")
    void testSendSmsCodeSuccess() throws Exception {
        doNothing().when(captchaService).sendSmsCode("13800138000", 1);

        CaptchaSendRequest request = CaptchaSendRequest.builder()
                .target("13800138000").type(1).build();

        mockMvc.perform(post("/captcha/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("验证码已发送"));

        verify(captchaService).sendSmsCode("13800138000", 1);
    }

    @Test
    @DisplayName("发送短信验证码-默认类型")
    void testSendSmsCodeDefaultType() throws Exception {
        doNothing().when(captchaService).sendSmsCode("13800138000", 1);

        CaptchaSendRequest request = CaptchaSendRequest.builder()
                .target("13800138000").build();

        mockMvc.perform(post("/captcha/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(captchaService).sendSmsCode("13800138000", 1);
    }

    @Test
    @DisplayName("发送邮箱验证码成功")
    void testSendEmailCodeSuccess() throws Exception {
        doNothing().when(captchaService).sendEmailCode("user@example.com", 1);

        CaptchaSendRequest request = CaptchaSendRequest.builder()
                .target("user@example.com").type(1).build();

        mockMvc.perform(post("/captcha/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("验证码已发送至邮箱"));

        verify(captchaService).sendEmailCode("user@example.com", 1);
    }

    @Test
    @DisplayName("发送邮箱验证码-默认类型")
    void testSendEmailCodeDefaultType() throws Exception {
        doNothing().when(captchaService).sendEmailCode("user@example.com", 1);

        CaptchaSendRequest request = CaptchaSendRequest.builder()
                .target("user@example.com").build();

        mockMvc.perform(post("/captcha/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(captchaService).sendEmailCode("user@example.com", 1);
    }
}
