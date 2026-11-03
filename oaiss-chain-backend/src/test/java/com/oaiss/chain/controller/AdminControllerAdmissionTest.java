package com.oaiss.chain.controller;

import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.repository.AccountPermissionListRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.service.EnterpriseAdmissionService;
import com.oaiss.chain.service.ReviewerQualificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController 企业准入证书和审核员资格证管理端点单元测试
 *
 * Note: /my endpoints have been moved to EnterpriseController and ReviewerController
 * to avoid class-level @PreAuthorize("hasRole('ADMIN')") AND conflict (CR-01 fix).
 */
@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerAdmissionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnterpriseAdmissionService enterpriseAdmissionService;

    @MockBean
    private com.oaiss.chain.service.CreditScoreService creditScoreService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private AccountPermissionListRepository permissionRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ReviewerQualificationService reviewerQualificationService;

    @MockBean
    private com.oaiss.chain.service.AdminUserService adminUserService;

    @MockBean
    private com.oaiss.chain.service.ChainReconciliationService chainReconciliationService;

    // 组件1：AdminController 构造器新增 IndustryBenchmarkService。
    // @WebMvcTest 切片漏 bean 时的表现是「整个上下文起不来」，不是单条用例失败。
    @MockBean
    private com.oaiss.chain.service.IndustryBenchmarkService industryBenchmarkService;

    // W4b：构造器新增 QuotaAllocationService（冲正重试端点）。
    @MockBean
    private com.oaiss.chain.service.QuotaAllocationService quotaAllocationService;

    private EnterpriseAdmission testAdmission;
    private ReviewerQualification testQualification;

    @BeforeEach
    void setUp() {
        testAdmission = EnterpriseAdmission.builder()
                .enterpriseId(1L)
                .certificateNo("EA-20260515-123456")
                .issuedDate(LocalDate.now())
                .status(1)
                .build();
        testAdmission.setId(1L);

        testQualification = ReviewerQualification.builder()
                .reviewerId(1L)
                .qualificationType("碳排放审核资质")
                .certificateNo("RQ-20260515-123456")
                .issuingAuthority("OAISS管理中心")
                .issuedDate(LocalDate.now())
                .status(1)
                .build();
        testQualification.setId(1L);
    }

    @Test
    @DisplayName("签发准入证书-管理员成功签发")
    void testIssueAdmission_asAdmin_returns200() throws Exception {
        when(enterpriseAdmissionService.issueCertificate(1L)).thenReturn(testAdmission);

        mockMvc.perform(post("/admin/enterprise-admission/1/issue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.certificateNo").value("EA-20260515-123456"));

        verify(enterpriseAdmissionService).issueCertificate(1L);
    }

    @Test
    @DisplayName("签发审核员资格证-管理员成功签发")
    void testIssueQualification_asAdmin_returns200() throws Exception {
        when(reviewerQualificationService.issueCertificate(1L)).thenReturn(testQualification);

        mockMvc.perform(post("/admin/reviewer-qualification/1/issue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.certificateNo").value("RQ-20260515-123456"));

        verify(reviewerQualificationService).issueCertificate(1L);
    }

    @Test
    @DisplayName("吊销准入证书-管理员成功吊销")
    void testRevokeAdmission_asAdmin_returns200() throws Exception {
        doNothing().when(enterpriseAdmissionService).revokeCertificate(1L, null);

        mockMvc.perform(delete("/admin/enterprise-admission/1"))
                .andExpect(status().isOk());

        verify(enterpriseAdmissionService).revokeCertificate(1L, null);
    }
}