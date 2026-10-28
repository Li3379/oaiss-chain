package com.oaiss.chain.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Collections;

import com.oaiss.chain.dto.EnterpriseInferenceResponse;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.service.ml.EnterpriseInferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnterpriseInferenceController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EnterpriseInferenceController Unit Tests")
class EnterpriseInferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnterpriseInferenceService enterpriseInferenceService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/predict/enterprise/{id}/inference - success")
    @WithMockUser(roles = "ENTERPRISE")
    void inferEnterprise_success() throws Exception {
        EnterpriseInferenceResponse response = EnterpriseInferenceResponse.builder()
                .enterpriseId(1L)
                .complianceStatus("compliant")
                .confidence(0.92)
                .anomalyScore(0.15)
                .isAnomaly(false)
                .riskFactors(Collections.emptyList())
                .modelVersion("1.0.0")
                .build();

        when(enterpriseInferenceService.inferEnterprise(1L)).thenReturn(response);

        mockMvc.perform(get("/predict/enterprise/1/inference")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complianceStatus").value("compliant"))
                .andExpect(jsonPath("$.data.confidence").value(0.92))
                .andExpect(jsonPath("$.data.enterpriseId").value(1));

        verify(enterpriseInferenceService).inferEnterprise(1L);
    }

    @Test
    @DisplayName("GET /api/v1/predict/enterprise/{id}/inference - ADMIN role allowed")
    @WithMockUser(roles = "ADMIN")
    void inferEnterprise_adminRole_allowed() throws Exception {
        EnterpriseInferenceResponse response = EnterpriseInferenceResponse.builder()
                .enterpriseId(2L)
                .complianceStatus("at_risk")
                .confidence(0.65)
                .anomalyScore(-0.3)
                .isAnomaly(true)
                .riskFactors(Collections.singletonList("Low credit score"))
                .modelVersion("1.0.0")
                .build();

        when(enterpriseInferenceService.inferEnterprise(2L)).thenReturn(response);

        mockMvc.perform(get("/predict/enterprise/2/inference")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complianceStatus").value("at_risk"));
    }

}