package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.CarbonPredictionRequest;
import com.oaiss.chain.dto.CarbonPredictionResponse;
import com.oaiss.chain.dto.EmissionRatingRequest;
import com.oaiss.chain.entity.EmissionRating;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CarbonPredictionService;
import com.oaiss.chain.service.EmissionRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EmissionController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmissionRatingService ratingService;

    @MockBean
    private CarbonPredictionService predictionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EnterpriseRepository enterpriseRepository;

    private EmissionRating emissionRating;

    @BeforeEach
    void setUp() {
        emissionRating = new EmissionRating();
        emissionRating.setId(1L);
        emissionRating.setEnterpriseId(1L);
        emissionRating.setRatingYear("2024");
        emissionRating.setRatingLevel("A");
        emissionRating.setTotalEmission(new BigDecimal("10000.00"));
        emissionRating.setEmissionIntensity(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("获取我的评级成功")
    void testGetMyRatingSuccess() throws Exception {
        // Given
        JwtUserDetails enterpriseUser = JwtUserDetails.builder()
                .userId(1L)
                .username("enterprise_user")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enterpriseId(1L)
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));

        Enterprise enterprise = new Enterprise();
        enterprise.setId(1L);
        enterprise.setUserId(1L);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(java.util.Optional.of(enterprise));
        when(ratingService.getEnterpriseRatings(1L)).thenReturn(List.of(emissionRating));

        mockMvc.perform(get("/emission/my-rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1));

        verify(ratingService).getEnterpriseRatings(1L);
    }

    @Test
    @DisplayName("获取我的评级-企业不存在")
    void testGetMyRatingEnterpriseNotFound() throws Exception {
        // Given
        JwtUserDetails enterpriseUser = JwtUserDetails.builder()
                .userId(99L)
                .username("enterprise_user")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(enterpriseUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));

        when(enterpriseRepository.findByUserId(99L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/emission/my-rating"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("获取企业评级历史成功")
    void testGetRatingsSuccess() throws Exception {
        when(ratingService.getEnterpriseRatings(1L)).thenReturn(List.of(emissionRating));

        mockMvc.perform(get("/emission/ratings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].ratingLevel").value("A"));

        verify(ratingService).getEnterpriseRatings(1L);
    }

    @Test
    @DisplayName("生成评级成功")
    void testCreateRatingSuccess() throws Exception {
        EmissionRatingRequest request = new EmissionRatingRequest();
        request.setEnterpriseId(1L);
        request.setYear("2024");
        request.setTotalEmission(new BigDecimal("10000.00"));
        request.setRevenue(new BigDecimal("1000000.00"));
        request.setRatedBy(1L);

        when(ratingService.rateEnterprise(eq(1L), eq("2024"), any(BigDecimal.class), any(BigDecimal.class), eq(1L)))
                .thenReturn(emissionRating);

        mockMvc.perform(post("/emission/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ratingLevel").value("A"));

        verify(ratingService).rateEnterprise(1L, "2024", new BigDecimal("10000.00"), new BigDecimal("1000000.00"), 1L);
    }

    /**
     * 评级写入是管理员/审核员的评定动作，不得对任何登录用户开放。
     *
     * 历史缺陷（2026-10-31 发现）：该端点类级与方法级均无 @PreAuthorize，
     * 落到 SecurityConfig.anyRequest().authenticated()，导致**任意登录角色**
     * （含企业、第三方监管）可创建/覆盖任意企业的年度碳排放评级。
     * role-matrix-verify.mjs 早期只扫方法级注解，未能拦截。
     *
     * 注意断言层级：本用例是 @WebMvcTest + addFilters=false 切片，
     * **不加载方法级安全**（@PreAuthorize 不会执行），因此这里断言的是
     * 「授权声明必须存在于方法上」这一契约事实；真正的方法级拒绝行为
     * 由 {@code EndpointAuthorizationTest} 在启用 Method Security 的上下文中断言。
     */
    @Test
    @DisplayName("生成评级-必须显式声明授权角色")
    void testCreateRatingDeclaresExplicitAuthorization() throws Exception {
        java.lang.reflect.Method createRating = EmissionController.class.getMethod(
                "createRating", EmissionRatingRequest.class);

        org.springframework.security.access.prepost.PreAuthorize pre =
                createRating.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);

        assertNotNull(pre, "POST /emission/ratings 必须显式声明 @PreAuthorize，"
                + "否则任意登录角色可覆盖任意企业评级");
        assertTrue(pre.value().contains("ADMIN") && pre.value().contains("REVIEWER"),
                "评级评定主体应限定为 ADMIN 或 REVIEWER，实际: " + pre.value());
    }

    @Test
    @DisplayName("获取行业排名成功")
    void testGetRankingsSuccess() throws Exception {
        when(ratingService.getIndustryRanking("2024")).thenReturn(List.of(emissionRating));

        mockMvc.perform(get("/emission/rankings/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].ratingYear").value("2024"));

        verify(ratingService).getIndustryRanking("2024");
    }

    @Test
    @DisplayName("AI碳排放预测成功")
    void testPredictSuccess() throws Exception {
        CarbonPredictionRequest request = new CarbonPredictionRequest();
        request.setEnterpriseId(1L);
        request.setPredictMonths(3);

        CarbonPredictionResponse.PredictionPoint point1 = CarbonPredictionResponse.PredictionPoint.builder()
                .period("M+1")
                .predictedEmission(new BigDecimal("9000.00"))
                .build();
        CarbonPredictionResponse.PredictionPoint point2 = CarbonPredictionResponse.PredictionPoint.builder()
                .period("M+2")
                .predictedEmission(new BigDecimal("8500.00"))
                .build();

        CarbonPredictionResponse response = CarbonPredictionResponse.builder()
                .enterpriseId(1L)
                .predictions(List.of(point1, point2))
                .confidence(0.85)
                .build();

        when(predictionService.predict(any(CarbonPredictionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/emission/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enterpriseId").value(1))
                .andExpect(jsonPath("$.data.confidence").value(0.85));

        verify(predictionService).predict(any(CarbonPredictionRequest.class));
    }
}