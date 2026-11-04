package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.CarbonNeutralProjectRequest;
import com.oaiss.chain.dto.CarbonNeutralProjectResponse;
import com.oaiss.chain.dto.ProjectVerificationRequest;
import com.oaiss.chain.entity.CarbonNeutralProject;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Reviewer;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.CreditEventTypeEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonNeutralProjectRepository;
import com.oaiss.chain.repository.CreditEventRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.ReviewerRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CarbonNeutralProjectServiceTest {

    @Mock
    private CarbonNeutralProjectRepository projectRepository;

    @Mock
    private EnterpriseRepository enterpriseRepository;

    @Mock
    private ReviewerRepository reviewerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreditEventRepository creditEventRepository;

    @Mock
    private CreditScoreService creditScoreService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CarbonNeutralProjectService service;

    private JwtUserDetails testUser;
    private Enterprise testEnterprise;
    private CarbonNeutralProject testProject;
    private CarbonNeutralProjectRequest projectRequest;

    @BeforeEach
    void setUp() {
        testUser = JwtUserDetails.builder()
                .userId(1L)
                .username("testuser")
                .password("password")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enterpriseId(1L)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        
        testEnterprise = new Enterprise();
        testEnterprise.setId(1L);
        testEnterprise.setUserId(1L);
        testEnterprise.setEnterpriseName("Test Enterprise");
        testEnterprise.setCreditCode("123456789012345678");

        testProject = new CarbonNeutralProject();
        testProject.setProjectNo("CNP202401010001");
        testProject.setProjectName("Test Project");
        testProject.setProjectType(1);
        testProject.setOwnerId(1L);
        testProject.setDescription("Test Description");
        testProject.setExpectedReduction(BigDecimal.valueOf(1000));
        testProject.setStatus(CarbonNeutralProjectService.STATUS_DRAFT);
        testProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_NONE);
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_NONE);
        testProject.setIssuedCredits(BigDecimal.ZERO);
        testProject.setUsedCredits(BigDecimal.ZERO);

        projectRequest = new CarbonNeutralProjectRequest();
        projectRequest.setProjectName("New Project");
        projectRequest.setProjectType(1);
        projectRequest.setDescription("Description");
        projectRequest.setExpectedReduction(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("创建项目成功")
    void testCreateProjectSuccess() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.createProject(testUser, projectRequest);

        assertNotNull(response);
        assertEquals("Test Project", response.getProjectName());
        verify(projectRepository).save(any());
    }

    @Test
    @DisplayName("创建项目失败-未找到企业")
    void testCreateProjectFailNoEnterprise() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.createProject(testUser, projectRequest));
    }

    @Test
    @DisplayName("更新项目成功")
    void testUpdateProjectSuccess() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.updateProject(testUser, 1L, projectRequest);

        assertNotNull(response);
        verify(projectRepository).save(any());
    }

    @Test
    @DisplayName("更新项目失败-项目不存在")
    void testUpdateProjectFailProjectNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.updateProject(testUser, 1L, projectRequest));
    }

    @Test
    @DisplayName("更新项目失败-无权限")
    void testUpdateProjectFailNoPermission() {
        testProject.setOwnerId(2L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.updateProject(testUser, 1L, projectRequest));
    }

    @Test
    @DisplayName("更新项目失败-状态不允许")
    void testUpdateProjectFailInvalidStatus() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_PENDING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.updateProject(testUser, 1L, projectRequest));
    }

    @Test
    @DisplayName("提交审核成功")
    void testSubmitForReviewSuccess() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.submitForReview(testUser, 1L);

        assertNotNull(response);
        verify(projectRepository).save(any());
    }

    @Test
    @DisplayName("审核项目通过")
    void testReviewProjectApproved() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_PENDING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.reviewProject(testUser, 1L, true, "Approved");

        assertNotNull(response);
        verify(projectRepository).save(any());
    }

    @Test
    @DisplayName("审核项目拒绝")
    void testReviewProjectRejected() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_PENDING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.reviewProject(testUser, 1L, false, "Rejected");

        assertNotNull(response);
    }

    @Test
    @DisplayName("审核项目失败-不在待审核状态")
    void testReviewProjectFailInvalidStatus() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        assertThrows(BusinessException.class, () -> service.reviewProject(testUser, 1L, true, "Approved"));
    }

    @Test
    @DisplayName("启动实施成功")
    void testStartImplementationSuccess() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_APPROVED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.startImplementation(testUser, 1L);

        assertNotNull(response);
    }

    @Test
    @DisplayName("提交核证申请成功")
    void testSubmitForVerificationSuccess() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        Reviewer reviewer = Reviewer.builder().userId(2L).build();
        reviewer.setId(9L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.of(reviewer));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.submitForVerification(testUser, 1L, 2L);

        assertNotNull(response);
        verify(projectRepository).save(argThat(project -> Long.valueOf(2L).equals(project.getVerifierId())));
    }

    @Test
    @DisplayName("核证项目成功")
    void testVerifyProjectSuccess() {
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_PENDING);
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        
        ProjectVerificationRequest request = new ProjectVerificationRequest();
        request.setProjectId(1L);
        request.setVerifiedReduction(BigDecimal.valueOf(800));
        request.setVerificationReport("Report");
        request.setMonitoringData("Data");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.verifyProject(testUser, request);

        assertNotNull(response);
    }

    @Test
    @DisplayName("核证项目失败-不在待核证状态")
    void testVerifyProjectFailInvalidStatus() {
        ProjectVerificationRequest request = new ProjectVerificationRequest();
        request.setProjectId(1L);
        request.setVerifiedReduction(BigDecimal.valueOf(800));

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        assertThrows(BusinessException.class, () -> service.verifyProject(testUser, request));
    }

    @Test
    @DisplayName("消耗碳信用成功")
    void testUseCreditsSuccess() {
        testProject.setIssuedCredits(BigDecimal.valueOf(1000));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.useCredits(testUser, 1L, BigDecimal.valueOf(100));

        assertNotNull(response);
    }

    @Test
    @DisplayName("消耗碳信用失败-余额不足")
    void testUseCreditsFailInsufficient() {
        testProject.setIssuedCredits(BigDecimal.valueOf(100));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.useCredits(testUser, 1L, BigDecimal.valueOf(200)));
    }

    @Test
    @DisplayName("更新监测数据成功")
    void testUpdateMonitoringSuccess() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.updateMonitoring(testUser, 1L, "New Data");

        assertNotNull(response);
    }

    @Test
    @DisplayName("申请认证成功")
    void testApplyForCertificationSuccess() {
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_VERIFIED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.applyForCertification(testUser, 1L, "CERT_ORG");

        assertNotNull(response);
    }

    @Test
    @DisplayName("完成认证成功")
    void testCompleteCertificationSuccess() {
        testProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_PENDING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.completeCertification(1L, 9L);

        assertNotNull(response);
    }

    @Test
    @DisplayName("核证通过-签发碳信用等于核定减排量")
    void verifyProject_issuedCreditsEqualVerifiedReduction() {
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_PENDING);
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        testProject.setIssuedCredits(BigDecimal.valueOf(10));

        ProjectVerificationRequest request = new ProjectVerificationRequest();
        request.setProjectId(1L);
        request.setVerifiedReduction(BigDecimal.valueOf(42.5));

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonNeutralProjectResponse response = service.verifyProject(testUser, request);

        assertEquals(0, BigDecimal.valueOf(52.5).compareTo(response.getIssuedCredits()));
        assertEquals(CarbonNeutralProjectService.VERIFY_STATUS_VERIFIED, response.getVerificationStatus());
    }

    @Test
    @DisplayName("完成认证-首次写入碳中和认证加分事件")
    void completeCertification_addsCarbonNeutralCreditEvent() {
        testProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_PENDING);
        testProject.setProjectNo("CNP202605050001");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditEventRepository.existsByEnterpriseIdAndEventTypeAndDeletedFalse(
                1L, CreditEventTypeEnum.CARBON_NEUTRAL_CERTIFIED.getCode())).thenReturn(false);

        CarbonNeutralProjectResponse response = service.completeCertification(1L, 2L);

        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CreditEventTypeEnum> eventTypeCaptor = ArgumentCaptor.forClass(CreditEventTypeEnum.class);
        verify(creditScoreService, times(1)).addBonusPoints(
                eq(1L),
                eq(CreditEventTypeEnum.CARBON_NEUTRAL_CERTIFIED.getDefaultPoints()),
                descriptionCaptor.capture(),
                eq(2L),
                eventTypeCaptor.capture());

        assertEquals(CreditEventTypeEnum.CARBON_NEUTRAL_CERTIFIED, eventTypeCaptor.getValue());
        assertTrue(descriptionCaptor.getValue().contains("CNP202605050001"));
        assertTrue(descriptionCaptor.getValue().contains(response.getCertNo()));
    }

    @Test
    @DisplayName("完成认证-同企业第二个项目不重复加分")
    void completeCertification_secondProjectNoDuplicateBonus() {
        testProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_PENDING);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditEventRepository.existsByEnterpriseIdAndEventTypeAndDeletedFalse(anyLong(), anyInt()))
                .thenReturn(true);

        CarbonNeutralProjectResponse response = service.completeCertification(1L, 2L);

        verify(creditScoreService, never()).addBonusPoints(
                anyLong(), anyInt(), anyString(), anyLong(), any());
        assertNotNull(response.getCertNo());
        assertEquals(CarbonNeutralProjectService.CERT_STATUS_CERTIFIED, response.getCertStatus());
    }

    @Test
    @DisplayName("完成认证-证书编号符合 CNE-{year}-{6 位} 格式")
    void completeCertification_generatesCertNoInExpectedFormat() {
        testProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_PENDING);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonNeutralProjectResponse response = service.completeCertification(1L, 2L);

        assertTrue(response.getCertNo().matches("^CNE-\\d{4}-\\d{6}$"));
        assertEquals(String.valueOf(LocalDate.now().getYear()), response.getCertNo().substring(4, 8));
    }

    @Test
    @DisplayName("终止项目成功")
    void testTerminateProjectSuccess() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.terminateProject(testUser, 1L, "Reason");

        assertNotNull(response);
    }

    @Test
    @DisplayName("获取项目详情成功")
    void testGetProjectSuccess() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

        CarbonNeutralProjectResponse response = service.getProject(1L);

        assertNotNull(response);
        assertEquals("Test Project", response.getProjectName());
    }

    @Test
    @DisplayName("获取项目详情失败-项目不存在")
    void testGetProjectFailNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.getProject(1L));
    }

    @Test
    @DisplayName("搜索项目成功")
    void testSearchProjectsSuccess() {
        List<CarbonNeutralProject> projects = List.of(testProject);
        Page<CarbonNeutralProject> page = new PageImpl<>(projects);
        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(1, 0, null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    @DisplayName("获取我的项目列表成功")
    void testGetMyProjectsSuccess() {
        List<CarbonNeutralProject> projects = List.of(testProject);
        Page<CarbonNeutralProject> page = new PageImpl<>(projects);
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.findByOwnerIdAndDeletedFalse(anyLong(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

        Page<CarbonNeutralProjectResponse> response = service.getMyProjects(testUser, null, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    @DisplayName("获取待核证项目列表成功")
    void testGetPendingVerificationProjectsSuccess() {
        List<CarbonNeutralProject> projects = List.of(testProject);
        Page<CarbonNeutralProject> page = new PageImpl<>(projects);
        Reviewer reviewer = Reviewer.builder().userId(1L).build();
        reviewer.setId(7L);
        when(reviewerRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(reviewer));
        when(reviewerRepository.findByUserIdInAndDeletedFalse(anyCollection())).thenReturn(List.of(reviewer));
        when(projectRepository.findVerificationWorkPool(anyCollection(), anyCollection(), anyInt(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));
        User verifierUser = new User();
        verifierUser.setId(1L);
        verifierUser.setRealName("Verifier");
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(verifierUser));

        Page<CarbonNeutralProjectResponse> response = service.getPendingVerificationProjects(1L, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        verify(projectRepository).findVerificationWorkPool(
                argThat(ids -> ids.contains(1L) && ids.contains(7L)),
                eq(List.of(CarbonNeutralProjectService.STATUS_IMPLEMENTING, CarbonNeutralProjectService.STATUS_COMPLETED)),
                eq(CarbonNeutralProjectService.VERIFY_STATUS_PENDING),
                any(Pageable.class));
    }

    @Test
    @DisplayName("搜索项目批量加载展示字段，避免逐条 N+1 查询")
    void testSearchProjectsBatchLoadsDisplayFields() {
        CarbonNeutralProject secondProject = new CarbonNeutralProject();
        secondProject.setProjectNo("CNP202401010002");
        secondProject.setProjectName("Project Two");
        secondProject.setProjectType(2);
        secondProject.setOwnerId(1L);
        secondProject.setReviewerId(10L);
        secondProject.setVerifierId(21L);
        secondProject.setStatus(CarbonNeutralProjectService.STATUS_PENDING);
        secondProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_NONE);
        secondProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_PENDING);
        secondProject.setIssuedCredits(BigDecimal.ZERO);
        secondProject.setUsedCredits(BigDecimal.ZERO);

        testProject.setReviewerId(10L);
        testProject.setVerifierId(20L);

        Page<CarbonNeutralProject> page = new PageImpl<>(List.of(testProject, secondProject));

        Reviewer verifierByUserId = Reviewer.builder().userId(20L).build();
        verifierByUserId.setId(30L);
        Reviewer verifierByReviewerId = Reviewer.builder().userId(22L).build();
        verifierByReviewerId.setId(21L);

        User reviewerUser = new User();
        reviewerUser.setId(10L);
        reviewerUser.setRealName("Reviewer One");

        User verifierUserDirect = new User();
        verifierUserDirect.setId(20L);
        verifierUserDirect.setRealName("Verifier Direct");

        User verifierUserFromReviewer = new User();
        verifierUserFromReviewer.setId(22L);
        verifierUserFromReviewer.setRealName("Verifier From Reviewer");

        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));
        when(reviewerRepository.findByUserIdInAndDeletedFalse(anyCollection())).thenReturn(List.of(verifierByUserId));
        when(reviewerRepository.findAllById(anyIterable())).thenReturn(List.of(verifierByReviewerId));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
                reviewerUser,
                verifierUserDirect,
                verifierUserFromReviewer
        ));

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);

        assertEquals(2, response.getTotalElements());
        assertEquals("Test Enterprise", response.getContent().get(0).getOwnerName());
        assertEquals("Reviewer One", response.getContent().get(0).getReviewerName());
        assertEquals("Verifier Direct", response.getContent().get(0).getVerifierName());
        assertEquals("Verifier From Reviewer", response.getContent().get(1).getVerifierName());

        verify(enterpriseRepository).findAllById(anyIterable());
        verify(userRepository).findAllById(anyIterable());
        verify(reviewerRepository).findByUserIdInAndDeletedFalse(anyCollection());
        verify(reviewerRepository).findAllById(anyIterable());
        verify(enterpriseRepository, never()).findById(anyLong());
        verify(userRepository, never()).findById(anyLong());
        verify(reviewerRepository, never()).findById(anyLong());
        verify(reviewerRepository, never()).findByUserIdAndDeletedFalse(anyLong());
    }

    @Test
    @DisplayName("提交审核失败-缺少项目名称")
    void testSubmitForReviewFailMissingName() {
        testProject.setProjectName(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForReview(testUser, 1L));
    }

    @Test
    @DisplayName("提交审核失败-缺少项目类型")
    void testSubmitForReviewFailMissingType() {
        testProject.setProjectType(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForReview(testUser, 1L));
    }

    @Test
    @DisplayName("提交审核失败-预计减排量无效")
    void testSubmitForReviewFailInvalidReduction() {
        testProject.setExpectedReduction(BigDecimal.ZERO);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForReview(testUser, 1L));
    }

    @Test
    @DisplayName("提交审核成功-从被拒绝状态重新提交")
    void testSubmitForReviewFromRejectedStatus() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_REJECTED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.submitForReview(testUser, 1L);

        assertNotNull(response);
        verify(projectRepository).save(any());
    }

    @Test
    @DisplayName("提交审核失败-状态不允许(已审核通过)")
    void testSubmitForReviewFailApprovedStatus() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_APPROVED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForReview(testUser, 1L));
    }

    @Test
    @DisplayName("更新项目成功-从被拒绝状态")
    void testUpdateProjectFromRejectedStatus() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_REJECTED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.updateProject(testUser, 1L, projectRequest);

        assertNotNull(response);
        verify(projectRepository).save(any());
    }

    @Test
    @DisplayName("启动实施失败-项目状态不是审核通过")
    void testStartImplementationFailNotApproved() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_DRAFT);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.startImplementation(testUser, 1L));
    }

    @Test
    @DisplayName("启动实施失败-项目不存在")
    void testStartImplementationFailProjectNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.startImplementation(testUser, 1L));
    }

    @Test
    @DisplayName("终止项目成功-审核通过的项目")
    void testTerminateProjectApproved() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_APPROVED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.terminateProject(testUser, 1L, "Budget cut");

        assertNotNull(response);
        verify(projectRepository).save(any());
    }

    @Test
    @DisplayName("终止项目失败-状态不允许(草稿)")
    void testTerminateProjectFailInvalidStatus() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_DRAFT);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.terminateProject(testUser, 1L, "reason"));
    }

    @Test
    @DisplayName("提交核证申请成功-已完成状态")
    void testSubmitForVerificationFromCompletedStatus() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_COMPLETED);
        Reviewer reviewer = Reviewer.builder().userId(2L).build();
        reviewer.setId(9L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.of(reviewer));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.submitForVerification(testUser, 1L, 2L);

        assertNotNull(response);
    }

    @Test
    @DisplayName("提交核证申请失败-状态不允许(草稿)")
    void testSubmitForVerificationFailInvalidStatus() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_DRAFT);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForVerification(testUser, 1L, 2L));
    }

    @Test
    @DisplayName("提交核证申请失败-项目已在核证中不可重复提交")
    void submitForVerification_pendingProjectRejected() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_PENDING);
        testProject.setVerifierId(7L);
        Reviewer reviewer = Reviewer.builder().userId(2L).build();
        reviewer.setId(9L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.of(reviewer));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitForVerification(testUser, 1L, 2L));

        assertEquals(3003, ex.getCode());
        verify(projectRepository, never()).save(any());
        assertEquals(7L, testProject.getVerifierId());
    }

    @Test
    @DisplayName("提交核证申请失败-已核证项目不可重复提交")
    void submitForVerification_verifiedProjectRejected() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_COMPLETED);
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_VERIFIED);
        testProject.setIssuedCredits(BigDecimal.valueOf(100));
        Reviewer reviewer = Reviewer.builder().userId(2L).build();
        reviewer.setId(9L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.of(reviewer));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitForVerification(testUser, 1L, 2L));

        assertEquals(3003, ex.getCode());
        verify(projectRepository, never()).save(any());
        assertEquals(CarbonNeutralProjectService.VERIFY_STATUS_VERIFIED, testProject.getVerificationStatus());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(testProject.getIssuedCredits()));
    }

    @Test
    @DisplayName("提交核证申请成功-核证失败的项目可重新提交")
    void submitForVerification_failedVerificationAllowed() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_FAILED);
        testProject.setIssuedCredits(BigDecimal.ZERO);
        Reviewer reviewer = Reviewer.builder().userId(2L).build();
        reviewer.setId(9L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.of(reviewer));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonNeutralProjectResponse response = service.submitForVerification(testUser, 1L, 2L);

        assertNotNull(response);
        assertEquals(CarbonNeutralProjectService.VERIFY_STATUS_PENDING, testProject.getVerificationStatus());
        assertEquals(Long.valueOf(2L), testProject.getVerifierId());
        verify(projectRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("提交核证申请失败-历史空核证状态按不可提交处理")
    void submitForVerification_nullVerificationStatusRejected() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        testProject.setVerificationStatus(null);
        Reviewer reviewer = Reviewer.builder().userId(2L).build();
        reviewer.setId(9L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.of(reviewer));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitForVerification(testUser, 1L, 2L));

        assertEquals(3003, ex.getCode());
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("核证项目成功-项目状态为已完成不改变状态")
    void testVerifyProjectWhenAlreadyCompleted() {
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_PENDING);
        testProject.setStatus(CarbonNeutralProjectService.STATUS_COMPLETED);

        ProjectVerificationRequest request = new ProjectVerificationRequest();
        request.setProjectId(1L);
        request.setVerifiedReduction(BigDecimal.valueOf(800));
        request.setVerificationReport("Report");
        request.setMonitoringData("Data");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonNeutralProjectResponse response = service.verifyProject(testUser, request);

        assertNotNull(response);
    }

    @Test
    @DisplayName("核证项目失败-项目不存在")
    void testVerifyProjectFailProjectNotFound() {
        ProjectVerificationRequest request = new ProjectVerificationRequest();
        request.setProjectId(99L);
        request.setVerifiedReduction(BigDecimal.valueOf(800));

        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.verifyProject(testUser, request));
    }

    @Test
    @DisplayName("申请认证失败-未核证")
    void testApplyForCertificationFailNotVerified() {
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_NONE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.applyForCertification(testUser, 1L, "CERT_ORG"));
    }

    @Test
    @DisplayName("完成认证失败-不在待认证状态")
    void testCompleteCertificationFailInvalidStatus() {
        testProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_NONE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        assertThrows(BusinessException.class, () -> service.completeCertification(1L, 9L));
    }

    @Test
    @DisplayName("消耗碳信用失败-项目不存在")
    void testUseCreditsFailProjectNotFound() {
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.useCredits(testUser, 1L, BigDecimal.TEN));
    }

    @Test
    @DisplayName("获取企业项目列表-带状态过滤")
    void testGetMyProjectsWithStatusFilter() {
        List<CarbonNeutralProject> projects = List.of(testProject);
        Page<CarbonNeutralProject> page = new PageImpl<>(projects);
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.findByOwnerIdAndStatusAndDeletedFalse(anyLong(), eq(0), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

        Page<CarbonNeutralProjectResponse> response = service.getMyProjects(testUser, 0, 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    @DisplayName("获取企业项目列表失败-企业不存在")
    void testGetMyProjectsFailEnterpriseNotFound() {
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.getMyProjects(testUser, null, 1, 10));
    }

    @Test
    @DisplayName("搜索项目-带关键字特殊字符转义")
    void testSearchProjectsWithSpecialKeyword() {
        List<CarbonNeutralProject> projects = List.of(testProject);
        Page<CarbonNeutralProject> page = new PageImpl<>(projects);
        when(projectRepository.search(eq(1), eq(0), eq("test\\%value\\_name"), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(1, 0, "test%value_name", 1, 10);

        assertNotNull(response);
    }

    @Test
    @DisplayName("删除项目权限验证-无企业信息")
    void testValidateOwnerFailNoEnterprise() {
        testProject.setOwnerId(2L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.updateProject(testUser, 1L, projectRequest));
    }

    @Test
    @DisplayName("搜索项目-空结果集")
    void testSearchProjectsEmptyResults() {
        Page<CarbonNeutralProject> emptyPage = new PageImpl<>(List.of());
        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(emptyPage);

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, "nonexistent", 1, 10);

        assertNotNull(response);
        assertEquals(0, response.getTotalElements());
    }

    @Test
    @DisplayName("提交审核失败-企业不存在")
    void testSubmitForReviewFailNoEnterprise() {
        testProject.setOwnerId(999L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForReview(testUser, 1L));
    }

    @Test
    @DisplayName("提交审核失败-项目名称空白")
    void testSubmitForReviewFailBlankName() {
        testProject.setProjectName("   ");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForReview(testUser, 1L));
    }

    @Test
    @DisplayName("消耗碳信用边界-恰好等于可用余额")
    void testUseCreditsExactAvailable() {
        testProject.setIssuedCredits(BigDecimal.valueOf(100));
        testProject.setUsedCredits(BigDecimal.ZERO);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.useCredits(testUser, 1L, BigDecimal.valueOf(100));

        assertNotNull(response);
        verify(projectRepository).save(any());
    }

    @Test
    @DisplayName("提交核证申请-验证者ID通过reviewerRepository.findById解析")
    void testSubmitForVerificationVerifierResolvedById() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.empty());
        Reviewer reviewer = Reviewer.builder().userId(2L).build();
        reviewer.setId(5L);
        when(reviewerRepository.findById(2L)).thenReturn(Optional.of(reviewer));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.submitForVerification(testUser, 1L, 2L);

        assertNotNull(response);
    }

    @Test
    @DisplayName("提交核证申请-验证者ID通过userRepository解析")
    void testSubmitForVerificationVerifierResolvedByUser() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(2L)).thenReturn(Optional.empty());
        when(reviewerRepository.findById(2L)).thenReturn(Optional.empty());
        User user = new User();
        user.setId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.submitForVerification(testUser, 1L, 2L);

        assertNotNull(response);
    }

    @Test
    @DisplayName("提交核证申请-验证者ID为null抛异常")
    void testSubmitForVerificationVerifierNullThrows() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForVerification(testUser, 1L, null));
    }

    @Test
    @DisplayName("提交核证申请-验证者找不到抛异常")
    void testSubmitForVerificationVerifierNotFound() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(999L)).thenReturn(Optional.empty());
        when(reviewerRepository.findById(999L)).thenReturn(Optional.empty());
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.submitForVerification(testUser, 1L, 999L));
    }

    // ==================== Additional branch coverage tests ====================

    @Test
    @DisplayName("提交审核失败-预计减排量为null")
    void testSubmitForReviewFailNullReduction() {
        testProject.setExpectedReduction(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForReview(testUser, 1L));
    }

    @Test
    @DisplayName("提交审核失败-预计减排量为负数")
    void testSubmitForReviewFailNegativeReduction() {
        testProject.setExpectedReduction(BigDecimal.valueOf(-100));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(BusinessException.class, () -> service.submitForReview(testUser, 1L));
    }

    @Test
    @DisplayName("项目类型名称-未知类型(99)")
    void testGetProjectTypeNameUnknownType() {
        testProject.setProjectType(99);
        Page<CarbonNeutralProject> page = new PageImpl<>(List.of(testProject));
        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);
        assertEquals("未知类型", response.getContent().get(0).getProjectTypeName());
    }

    @Test
    @DisplayName("项目类型名称-所有已知类型")
    void testGetProjectTypeNameAllTypes() {
        for (int type = 1; type <= 5; type++) {
            testProject.setProjectType(type);
            Page<CarbonNeutralProject> page = new PageImpl<>(List.of(testProject));
            when(projectRepository.search(eq(type), any(), any(), any(Pageable.class))).thenReturn(page);
            when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

            Page<CarbonNeutralProjectResponse> response = service.searchProjects(type, null, null, 1, 10);
            assertNotNull(response.getContent().get(0).getProjectTypeName());
        }
    }

    @Test
    @DisplayName("状态名称-所有状态")
    void testGetStatusTextAllStatuses() {
        int[] statuses = {0, 1, 2, 3, 4, 5, 6};
        for (int status : statuses) {
            testProject.setStatus(status);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

            CarbonNeutralProjectResponse response = service.getProject(1L);
            assertNotNull(response.getStatusText());
        }
    }

    @Test
    @DisplayName("认证状态名称-认证失败(3)")
    void testGetCertStatusTextFailed() {
        testProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_FAILED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertEquals("认证失败", response.getCertStatusText());
    }

    @Test
    @DisplayName("核证状态名称-核证失败(3)")
    void testGetVerificationStatusTextFailed() {
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_FAILED);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertEquals("核证失败", response.getVerificationStatusText());
    }

    @Test
    @DisplayName("核证状态名称-所有状态")
    void testGetVerificationStatusTextAll() {
        int[] statuses = {0, 1, 2, 3};
        for (int status : statuses) {
            testProject.setVerificationStatus(status);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

            CarbonNeutralProjectResponse response = service.getProject(1L);
            assertNotNull(response.getVerificationStatusText());
        }
    }

    @Test
    @DisplayName("认证状态名称-所有状态")
    void testGetCertStatusTextAll() {
        int[] statuses = {0, 1, 2, 3};
        for (int status : statuses) {
            testProject.setCertStatus(status);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

            CarbonNeutralProjectResponse response = service.getProject(1L);
            assertNotNull(response.getCertStatusText());
        }
    }

    @Test
    @DisplayName("状态名称-未知状态(99)")
    void testGetStatusTextUnknown() {
        testProject.setStatus(99);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertEquals("未知状态", response.getStatusText());
    }

    @Test
    @DisplayName("核证状态名称-未知状态(99)")
    void testGetVerificationStatusTextUnknown() {
        testProject.setVerificationStatus(99);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertEquals("未知状态", response.getVerificationStatusText());
    }

    @Test
    @DisplayName("认证状态名称-未知状态(99)")
    void testGetCertStatusTextUnknown() {
        testProject.setCertStatus(99);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertEquals("未知状态", response.getCertStatusText());
    }

    @Test
    @DisplayName("toResponse-usedCredits为null时使用ZERO")
    void testToResponseWithNullUsedCredits() {
        testProject.setUsedCredits(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertNotNull(response);
    }

    @Test
    @DisplayName("搜索项目-keyword为null时不做转义")
    void testSearchProjectsWithNullKeyword() {
        List<CarbonNeutralProject> projects = List.of(testProject);
        Page<CarbonNeutralProject> page = new PageImpl<>(projects);
        when(projectRepository.search(any(), any(), isNull(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);
        assertNotNull(response);
    }

    @Test
    @DisplayName("buildDisplayContext-空项目列表")
    void testBuildDisplayContextEmptyProjects() {
        Page<CarbonNeutralProject> emptyPage = new PageImpl<>(List.of());
        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(emptyPage);

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);
        assertNotNull(response);
        assertEquals(0, response.getTotalElements());
    }

    @Test
    @DisplayName("buildDisplayContext-企业名称为null")
    void testBuildDisplayContextEnterpriseNameNull() {
        Enterprise enterpriseWithNullName = new Enterprise();
        enterpriseWithNullName.setId(1L);
        enterpriseWithNullName.setEnterpriseName(null);

        testProject.setOwnerId(1L);
        Page<CarbonNeutralProject> page = new PageImpl<>(List.of(testProject));
        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(enterpriseWithNullName));

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);
        assertEquals("未知企业", response.getContent().get(0).getOwnerName());
    }

    @Test
    @DisplayName("resolveVerifierName-verifierId在reviewerUserIds中")
    void testResolveVerifierNameInReviewerUserIds() {
        testProject.setVerifierId(10L);
        testProject.setOwnerId(1L);

        CarbonNeutralProject secondProject = new CarbonNeutralProject();
        secondProject.setProjectNo("CNP202401010002");
        secondProject.setProjectName("Project Two");
        secondProject.setProjectType(1);
        secondProject.setOwnerId(1L);
        secondProject.setVerifierId(10L);
        secondProject.setStatus(CarbonNeutralProjectService.STATUS_DRAFT);
        secondProject.setCertStatus(CarbonNeutralProjectService.CERT_STATUS_NONE);
        secondProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_NONE);
        secondProject.setIssuedCredits(BigDecimal.ZERO);
        secondProject.setUsedCredits(BigDecimal.ZERO);

        Page<CarbonNeutralProject> page = new PageImpl<>(List.of(testProject, secondProject));

        User verifierUser = new User();
        verifierUser.setId(10L);
        verifierUser.setRealName("Verifier User");

        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(verifierUser));
        when(reviewerRepository.findByUserIdInAndDeletedFalse(anyCollection())).thenReturn(List.of());
        when(reviewerRepository.findAllById(anyCollection())).thenReturn(List.of());

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);
        assertEquals(2, response.getTotalElements());
        assertEquals("Verifier User", response.getContent().get(0).getVerifierName());
    }

    @Test
    @DisplayName("resolveVerifierName-verifierId通过reviewerUserIdsByReviewerId映射")
    void testResolveVerifierNameViaReviewerIdMapping() {
        testProject.setVerifierId(21L); // This is a reviewer ID, not a userId
        testProject.setOwnerId(1L);

        Page<CarbonNeutralProject> page = new PageImpl<>(List.of(testProject));

        Reviewer verifierAsReviewer = Reviewer.builder().userId(30L).build();
        verifierAsReviewer.setId(21L);

        User verifierUser = new User();
        verifierUser.setId(30L);
        verifierUser.setRealName("Verifier Via Reviewer");

        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));
        when(reviewerRepository.findByUserIdInAndDeletedFalse(anyCollection())).thenReturn(List.of());
        when(reviewerRepository.findAllById(anyCollection())).thenReturn(List.of(verifierAsReviewer));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(verifierUser));

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);
        assertEquals("Verifier Via Reviewer", response.getContent().get(0).getVerifierName());
    }

    @Test
    @DisplayName("resolveVerifierName-verifierId不在任何映射中(使用默认值)")
    void testResolveVerifierNameNotInAnyMapping() {
        testProject.setVerifierId(99L); // Unknown ID
        testProject.setOwnerId(1L);

        Page<CarbonNeutralProject> page = new PageImpl<>(List.of(testProject));

        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));
        when(reviewerRepository.findByUserIdInAndDeletedFalse(anyCollection())).thenReturn(List.of());
        when(reviewerRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);
        assertNull(response.getContent().get(0).getVerifierName());
    }

    @Test
    @DisplayName("resolveVerifierName-verifierId为null时返回null")
    void testResolveVerifierNameNull() {
        testProject.setVerifierId(null);
        testProject.setOwnerId(1L);

        Page<CarbonNeutralProject> page = new PageImpl<>(List.of(testProject));

        when(projectRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(reviewerRepository.findByUserIdInAndDeletedFalse(anyCollection())).thenReturn(List.of());
        when(reviewerRepository.findAllById(anyCollection())).thenReturn(List.of());

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(null, null, null, 1, 10);
        assertNull(response.getContent().get(0).getVerifierName());
    }

    @Test
    @DisplayName("toResponse-reviewerId为null时reviewerName为null")
    void testToResponseNullReviewerId() {
        testProject.setReviewerId(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertNull(response.getReviewerName());
    }

    @Test
    @DisplayName("verifyProject-状态为COMPLETED时验证后不改变状态")
    void testVerifyProjectCompletedStatusNoChange() {
        testProject.setVerificationStatus(CarbonNeutralProjectService.VERIFY_STATUS_PENDING);
        testProject.setStatus(CarbonNeutralProjectService.STATUS_COMPLETED);

        ProjectVerificationRequest request = new ProjectVerificationRequest();
        request.setProjectId(1L);
        request.setVerifiedReduction(BigDecimal.valueOf(500));
        request.setVerificationReport("Report");
        request.setMonitoringData("Data");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonNeutralProjectResponse response = service.verifyProject(testUser, request);
        assertEquals(CarbonNeutralProjectService.STATUS_COMPLETED, response.getStatus());
    }

    @Test
    @DisplayName("项目类型名称-所有类型switch覆盖")
    void testGetProjectTypeNameAllSwitchCases() {
        String[] expected = {"未知类型", "碳汇项目", "CCUS项目", "可再生能源项目", "节能改造项目", "其他减排项目", "未知类型"};
        for (int type = 0; type <= 6; type++) {
            testProject.setProjectType(type);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
            CarbonNeutralProjectResponse response = service.getProject(1L);
            assertEquals(expected[type], response.getProjectTypeName());
        }
    }

    @Test
    @DisplayName("searchProjects-keyword包含特殊字符应转义")
    void testSearchProjectsKeywordWithSpecialChars() {
        List<CarbonNeutralProject> projects = List.of(testProject);
        Page<CarbonNeutralProject> page = new PageImpl<>(projects);
        when(projectRepository.search(eq(1), eq(0), eq("test\\%value\\_name"), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findAllById(anyIterable())).thenReturn(List.of(testEnterprise));

        Page<CarbonNeutralProjectResponse> response = service.searchProjects(1, 0, "test%value_name", 1, 10);
        assertNotNull(response);
    }

    @Test
    @DisplayName("buildVerifierLookupIds-verifierId存在但reviewerRepository返回empty")
    void testBuildVerifierLookupIdsNoReviewer() {
        testProject.setStatus(CarbonNeutralProjectService.STATUS_IMPLEMENTING);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(reviewerRepository.findByUserIdAndDeletedFalse(5L)).thenReturn(Optional.empty());
        when(reviewerRepository.findById(5L)).thenReturn(Optional.empty());
        User fallbackUser = User.builder()
                .username("fallback")
                .password("encoded")
                .realName("Fallback User")
                .userType(1)
                .build();
        fallbackUser.setId(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(fallbackUser));
        when(projectRepository.save(any())).thenReturn(testProject);

        CarbonNeutralProjectResponse response = service.submitForVerification(testUser, 1L, 5L);
        assertNotNull(response);
    }

    @Test
    @DisplayName("toResponse-projectType为null时返回空字符串")
    void testGetProjectTypeNameNull() {
        testProject.setProjectType(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertNotNull(response);
        assertEquals("", response.getProjectTypeName());
    }

    @Test
    @DisplayName("toResponse-status为null时返回空字符串")
    void testGetStatusTextNull() {
        testProject.setStatus(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertNotNull(response);
        assertEquals("", response.getStatusText());
    }

    @Test
    @DisplayName("toResponse-certStatus为null时返回空字符串")
    void testGetCertStatusTextNull() {
        testProject.setCertStatus(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertNotNull(response);
        assertEquals("", response.getCertStatusText());
    }

    @Test
    @DisplayName("toResponse-verificationStatus为null时返回空字符串")
    void testGetVerificationStatusTextNull() {
        testProject.setVerificationStatus(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertNotNull(response);
        assertEquals("", response.getVerificationStatusText());
    }

    @Test
    @DisplayName("buildDisplayContext-ownerId为null时跳过企业查询(ownerIds为空)")
    void testBuildDisplayContextNullOwnerId() {
        testProject.setOwnerId(null);
        testProject.setReviewerId(null);
        testProject.setVerifierId(null);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        CarbonNeutralProjectResponse response = service.getProject(1L);
        assertNotNull(response);
        assertEquals("未知企业", response.getOwnerName());
        assertNull(response.getReviewerName());
        assertNull(response.getVerifierName());
        verify(enterpriseRepository, never()).findAllById(anyIterable());
    }

    @Test
    @DisplayName("buildVerifierLookupIds-verifierId为null时返回空集合")
    void testBuildVerifierLookupIdsNullVerifierId() {
        Page<CarbonNeutralProject> emptyPage = new PageImpl<>(List.of());
        when(projectRepository.findVerificationWorkPool(
                anyCollection(), anyCollection(), anyInt(), any(Pageable.class))).thenReturn(emptyPage);

        Page<CarbonNeutralProjectResponse> response = service.getPendingVerificationProjects(null, 1, 10);
        assertNotNull(response);
        assertEquals(0, response.getTotalElements());
        verify(projectRepository).findVerificationWorkPool(
                argThat(ids -> ids.isEmpty()),
                eq(List.of(CarbonNeutralProjectService.STATUS_IMPLEMENTING, CarbonNeutralProjectService.STATUS_COMPLETED)),
                eq(CarbonNeutralProjectService.VERIFY_STATUS_PENDING),
                any(Pageable.class));
    }

    @SuppressWarnings("unchecked")
    private static Iterable<Long> anyIterable() {
        return (Iterable<Long>) any(Iterable.class);
    }
}
