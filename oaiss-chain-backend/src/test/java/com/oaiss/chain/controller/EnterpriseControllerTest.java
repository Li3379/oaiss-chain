package com.oaiss.chain.controller;

import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.EnterpriseAdmission;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnterpriseController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EnterpriseController unit tests")
class EnterpriseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnterpriseService enterpriseService;

    @MockBean
    private EnterpriseAdmissionService enterpriseAdmissionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private void setAuthentication() {
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(1L)
                .username("enterprise")
                .enterpriseId(88L)
                .roles(List.of("ENTERPRISE"))
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }

    @Test
    @DisplayName("GET /enterprise/admission/my should throw when enterpriseId is null")
    void getMyAdmission_shouldThrowWhenEnterpriseIdIsNull() throws Exception {
        JwtUserDetails userNoEnterprise = JwtUserDetails.builder()
                .userId(2L)
                .username("enterprise_no_id")
                .enterpriseId(null)
                .roles(List.of("ENTERPRISE"))
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userNoEnterprise, null, userNoEnterprise.getAuthorities())
        );

        mockMvc.perform(get("/enterprise/admission/my")
                        .principal(() -> "enterprise_no_id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("GET /enterprise/admission/my should return admissions")
    void getMyAdmission_shouldReturnAdmissions() throws Exception {
        setAuthentication();
        EnterpriseAdmission admission = EnterpriseAdmission.builder()
                .enterpriseId(88L)
                .certificateNo("EA-001")
                .build();
        when(enterpriseAdmissionService.getMyCertificate(88L)).thenReturn(List.of(admission));

        mockMvc.perform(get("/enterprise/admission/my")
                        .principal(() -> "enterprise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].certificateNo").value("EA-001"));
    }

    @Test
    @DisplayName("GET /enterprise/info should return current enterprise info")
    void getEnterpriseInfo_shouldReturnEnterpriseInfo() throws Exception {
        setAuthentication();
        Enterprise enterprise = Enterprise.builder().enterpriseName("测试企业").build();
        when(enterpriseService.getEnterpriseInfo(1L)).thenReturn(enterprise);

        mockMvc.perform(get("/enterprise/info")
                        .principal(() -> "enterprise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseName").value("测试企业"));
    }

    @Test
    @DisplayName("GET /enterprise/quota should return quota info")
    void getQuotaInfo_shouldReturnQuotaInfo() throws Exception {
        setAuthentication();
        when(enterpriseService.getQuotaInfo(1L)).thenReturn(Map.of("totalQuota", 1000, "availableQuota", 600));

        mockMvc.perform(get("/enterprise/quota")
                        .principal(() -> "enterprise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuota").value(1000))
                .andExpect(jsonPath("$.data.availableQuota").value(600));
    }

    @Test
    @DisplayName("PUT /enterprise/contact should delegate update")
    void updateContact_shouldDelegateUpdate() throws Exception {
        setAuthentication();
        mockMvc.perform(put("/enterprise/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactPerson\":\"张三\",\"contactPhone\":\"13800138000\"}")
                        .principal(() -> "enterprise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("联系方式更新成功"));

        verify(enterpriseService).updateContact(eq(1L), eq("张三"), eq("13800138000"));
    }

    @Test
    @DisplayName("GET /enterprise/{id} should return public enterprise info")
    void getEnterpriseById_shouldReturnEnterprise() throws Exception {
        Enterprise enterprise = Enterprise.builder().enterpriseName("公开企业").build();
        when(enterpriseService.getEnterpriseById(5L)).thenReturn(enterprise);

        mockMvc.perform(get("/enterprise/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseName").value("公开企业"));
    }
}
