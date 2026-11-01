package com.oaiss.chain.controller;

import com.oaiss.chain.dto.EnterpriseSearchResponse;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.EnterpriseAdmissionService;
import com.oaiss.chain.service.EnterpriseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 交易对手搜索端点契约测试。
 *
 * <p>本测试**只验契约与字段白名单**，不验 JPQL 语义：
 * <ul>
 *   <li>JPQL（含 selfId 的 NULL-safe）由 {@code EnterpriseRepositoryTest}（@DataJpaTest 真库）覆盖</li>
 *   <li>此处 Service 被 {@code @MockBean}，任何「返回非空」的断言都只反映 stub，
 *       不能用来证明查询正确性</li>
 * </ul>
 *
 * <p>**反向断言是本测试的核心**：AC-14a 要求响应中不得出现联系方式、经营与配额字段。
 * 仅断言「必需字段存在」不足以防回归——新增字段时会静默通过。
 */
@WebMvcTest(controllers = EnterpriseController.class)
@AutoConfigureMockMvc(addFilters = false)
class EnterpriseSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnterpriseService enterpriseService;

    @MockBean
    private EnterpriseAdmissionService enterpriseAdmissionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private void authenticateAs(Long userId, String role, Long enterpriseId) {
        JwtUserDetails principal = JwtUserDetails.builder()
                .userId(userId)
                .username("u" + userId)
                .userType(role.equals("ADMIN") ? 4 : 1)
                .roles(List.of(role))
                .enterpriseId(enterpriseId)
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private EnterpriseSearchResponse sample() {
        return EnterpriseSearchResponse.builder()
                .enterpriseId(10L)
                .userId(2L)
                .enterpriseName("绿色能源科技有限公司")
                .creditCode("91110108MA01ABCDE9")
                .creditScore(100)
                .build();
    }

    @Test
    @DisplayName("AC-14：响应只含 5 个白名单字段")
    void search_returnsWhitelistFields() throws Exception {
        authenticateAs(1L, "ENTERPRISE", 5L);
        when(enterpriseService.searchForCounterparty(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/enterprise/search").param("keyword", "绿色"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].enterpriseId").value(10))
                .andExpect(jsonPath("$.data.content[0].userId").value(2))
                .andExpect(jsonPath("$.data.content[0].enterpriseName").value("绿色能源科技有限公司"))
                .andExpect(jsonPath("$.data.content[0].creditCode").value("91110108MA01ABCDE9"))
                .andExpect(jsonPath("$.data.content[0].creditScore").value(100));
    }

    @Test
    @DisplayName("AC-14a：反向断言——敏感字段一律不出现")
    void search_doesNotLeakSensitiveFields() throws Exception {
        authenticateAs(1L, "ENTERPRISE", 5L);
        when(enterpriseService.searchForCounterparty(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        String body = mockMvc.perform(get("/enterprise/search").param("keyword", "绿色"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 反向断言：这些字段名一旦出现在响应里，说明 DTO 被改宽或误用了实体
        for (String forbidden : List.of("address", "contactPerson", "contactPhone",
                "licenseUrl", "carbonQuota", "carbonTradable", "carbonFrozen", "carbonUsed")) {
            assertFalse(body.contains("\"" + forbidden + "\""),
                    "响应中不得出现敏感字段: " + forbidden + "，实际响应: " + body);
        }
    }

    @Test
    @DisplayName("AC-13b：把当前登录企业的 enterpriseId 传给 Service 用于排除自身")
    void search_passesSelfEnterpriseId() throws Exception {
        authenticateAs(1L, "ENTERPRISE", 77L);
        when(enterpriseService.searchForCounterparty(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/enterprise/search").param("keyword", "绿色"))
                .andExpect(status().isOk());

        verify(enterpriseService).searchForCounterparty(eq("绿色"), eq(77L), any(), any());
    }

    @Test
    @DisplayName("AC-13c：管理员无关联企业时 enterpriseId 传 null（Repository 侧 NULL-safe）")
    void search_adminPassesNullEnterpriseId() throws Exception {
        authenticateAs(1L, "ADMIN", null);
        when(enterpriseService.searchForCounterparty(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/enterprise/search").param("keyword", "绿色"))
                .andExpect(status().isOk());

        verify(enterpriseService).searchForCounterparty(eq("绿色"), isNull(), any(), any());
    }

    @Test
    @DisplayName("授权：仅 ENTERPRISE 与 ADMIN；审核员/监管不得使用该通道")
    void search_authorizationExpression() {
        Method m = java.util.Arrays.stream(EnterpriseController.class.getMethods())
                .filter(x -> x.getName().equals("searchCounterparties"))
                .findFirst().orElseThrow();

        PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "端点必须显式声明授权");
        String expr = annotation.value();
        assertTrue(expr.contains("ENTERPRISE"), "企业需要选对手方，实际: " + expr);
        assertTrue(expr.contains("ADMIN"), "管理员签证书时也要选企业，实际: " + expr);
        assertFalse(expr.contains("REVIEWER"), "审核员不需要企业搜索通道，实际: " + expr);
        assertFalse(expr.contains("THIRD_PARTY"), "监管为只读核查，实际: " + expr);
    }
}