package com.oaiss.chain.security;

import com.oaiss.chain.controller.EmissionController;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.EmissionRatingRequest;
import com.oaiss.chain.service.CarbonPredictionService;
import com.oaiss.chain.service.EmissionRatingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 端点级方法安全（@PreAuthorize）真实生效性测试。
 *
 * <p>存在理由（2026-10-31 补）：全仓所有 {@code @WebMvcTest} 控制器测试均带
 * {@code @AutoConfigureMockMvc(addFilters = false)}，该切片不会创建方法级安全代理，
 * 因此 {@code @PreAuthorize} 在任何既有控制器测试里都不会被执行——
 * 「给端点加了注解」与「注解真的会拒绝越权调用」是两件不同的事，
 * 而此前只有前者被测过（如 SearchControllerTest 的反射断言）。
 *
 * <p>这里不使用 MockMvc（切片环境下 AOP 代理不稳定），而是直接构建一个
 * 极小的 {@link AnnotationConfigApplicationContext}，仅注册受测控制器与
 * {@code @EnableMethodSecurity}，然后以不同身份调用同一方法，
 * 断言越权调用抛 {@link AccessDeniedException}、授权角色放行。
 */
class EndpointAuthorizationTest {

    @Configuration
    @EnableMethodSecurity
    static class MinimalConfig {
        @Bean
        EmissionRatingService emissionRatingService() {
            return Mockito.mock(EmissionRatingService.class);
        }

        @Bean
        CarbonPredictionService carbonPredictionService() {
            return Mockito.mock(CarbonPredictionService.class);
        }

        @Bean
        com.oaiss.chain.repository.EnterpriseRepository enterpriseRepository() {
            return Mockito.mock(com.oaiss.chain.repository.EnterpriseRepository.class);
        }

        @Bean
        EmissionController emissionController(EmissionRatingService ratingService,
                                              CarbonPredictionService predictionService,
                                              com.oaiss.chain.repository.EnterpriseRepository enterpriseRepository) {
            return new EmissionController(ratingService, predictionService, enterpriseRepository);
        }
    }

    private AnnotationConfigApplicationContext newContext() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(MinimalConfig.class);
        ctx.refresh();
        return ctx;
    }

    private void authenticateAs(String role) {
        JwtUserDetails principal = JwtUserDetails.builder()
                .userId(1L)
                .username(role.toLowerCase() + "_user")
                .userType(role.equals("ENTERPRISE") ? 1 : 4)
                .roles(List.of(role))
                .enterpriseId(1L)
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private EmissionRatingRequest ratingRequest() {
        EmissionRatingRequest request = new EmissionRatingRequest();
        request.setEnterpriseId(1L);
        request.setYear("2024");
        request.setTotalEmission(new BigDecimal("10000.00"));
        request.setRevenue(new BigDecimal("1000000.00"));
        request.setRatedBy(1L);
        return request;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("生成评级-企业角色被方法级安全拒绝")
    void createRating_enterpriseRole_isDenied() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            EmissionController controller = ctx.getBean(EmissionController.class);
            authenticateAs("ENTERPRISE");

            assertThrows(AccessDeniedException.class,
                    () -> controller.createRating(ratingRequest()),
                    "企业角色不应能写入任意企业的年度评级");
        }
    }

    @Test
    @DisplayName("生成评级-第三方监管角色被方法级安全拒绝")
    void createRating_thirdPartyRole_isDenied() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            EmissionController controller = ctx.getBean(EmissionController.class);
            authenticateAs("THIRD_PARTY");

            assertThrows(AccessDeniedException.class,
                    () -> controller.createRating(ratingRequest()),
                    "第三方监管为只读核查角色，不应能写入企业评级");
        }
    }

    @Test
    @DisplayName("生成评级-管理员角色放行")
    void createRating_adminRole_isAllowed() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            EmissionController controller = ctx.getBean(EmissionController.class);
            authenticateAs("ADMIN");

            ApiResponse<?> response = controller.createRating(ratingRequest());
            assertNotNull(response, "管理员调用应正常返回");
        }
    }
}