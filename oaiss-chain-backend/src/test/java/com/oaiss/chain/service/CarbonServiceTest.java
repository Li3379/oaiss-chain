package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.BatchCertifyRequest;
import com.oaiss.chain.dto.BatchCertifyResponse;
import com.oaiss.chain.dto.CarbonReportRequest;
import com.oaiss.chain.dto.CarbonReportResponse;
import com.oaiss.chain.dto.ReviewRequest;
import com.oaiss.chain.dto.SignatureResult;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.ReportStatusEnum;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.exception.AuthorizationException;
import com.oaiss.chain.exception.BlockchainException;
import com.oaiss.chain.exception.CarbonException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarbonServiceTest {

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @Mock
    private EnterpriseRepository enterpriseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CreditScoreService creditScoreService;

    @Mock
    private EmissionRatingService emissionRatingService;

    @Mock
    private BlockchainServicePort blockchainService;

    @Mock
    private DigitalSignatureService digitalSignatureService;

    @InjectMocks
    private CarbonService carbonService;

    private JwtUserDetails currentUser;
    private Enterprise testEnterprise;
    private CarbonReport testReport;
    private CarbonReportRequest reportRequest;

    @BeforeEach
    void setUp() {
        currentUser = new JwtUserDetails();
        currentUser.setUserId(1L);
        currentUser.setUsername("testuser");

        testEnterprise = Enterprise.builder()
                .userId(1L)
                .enterpriseName("Test Enterprise")
                .build();
        testEnterprise.setId(1L);

        testReport = CarbonReport.builder()
                .reportNo("CR20240101001")
                .enterpriseId(1L)
                .submitterId(1L)
                .accountingPeriod("2024-Q1")
                .title("Test Carbon Report")
                .reportType(1)
                .emissionData("{\"scope1\":[]}")
                .status(ReportStatusEnum.DRAFT.getCode())
                .totalEmission(BigDecimal.ZERO)
                .build();
        testReport.setId(1L);
        testReport.setCreatedAt(LocalDateTime.now());
        testReport.setUpdatedAt(LocalDateTime.now());

        reportRequest = new CarbonReportRequest();
        reportRequest.setAccountingPeriod("2024-Q1");
        reportRequest.setTitle("Test Report");
        reportRequest.setReportType(1);
        reportRequest.setEmissionData("{\"scope1\":[]}");
    }

    @Test
    @DisplayName("create report success")
    void testCreateReportSuccess() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenReturn(testReport);

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);

        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("create report fails when enterprise missing")
    void testCreateReportFailEnterpriseNotFound() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class, () -> carbonService.createReport(currentUser, reportRequest));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit report success")
    void testSubmitReportSuccess() throws Exception {
        testReport.setStatus(ReportStatusEnum.DRAFT.getCode());
        testReport.setEmissionData("{\"scope1\":[],\"scope2\":[],\"scope3\":[]}");

        ObjectMapper realMapper = new ObjectMapper();
        var mockNode = realMapper.readTree("{\"scope1\":[],\"scope2\":[],\"scope3\":[]}");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(any(String.class))).thenReturn(mockNode);
        when(carbonReportRepository.save(any(CarbonReport.class))).thenReturn(testReport);

        CarbonReportResponse response = carbonService.submitReport(currentUser, 1L);

        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("submitReport-签名成功时报告携带签名")
    void testSubmitReport_signSuccess_setsSignature() throws Exception {
        testReport.setStatus(ReportStatusEnum.DRAFT.getCode());
        testReport.setEmissionData("{\"scope1\":[],\"scope2\":[],\"scope3\":[]}");

        ObjectMapper realMapper = new ObjectMapper();
        var mockNode = realMapper.readTree("{\"scope1\":[],\"scope2\":[],\"scope3\":[]}");

        SignatureResult signed = SignatureResult.builder()
                .signature("SIG-BASE64")
                .algorithm("SHA256withRSA")
                .signerId(1L)
                .build();

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(any(String.class))).thenReturn(mockNode);
        when(digitalSignatureService.signReport(1L, testReport.getEmissionData())).thenReturn(signed);
        when(carbonReportRepository.save(any(CarbonReport.class))).thenReturn(testReport);

        CarbonReportResponse response = carbonService.submitReport(currentUser, 1L);

        assertEquals("SIG-BASE64", testReport.getSignatureData());
        assertEquals(ReportStatusEnum.SUBMITTED.getCode(), testReport.getStatus());
        assertNotNull(response);
    }

    @Test
    @DisplayName("submitReport-签名失败跳过签名仍可提交（放行语义，不阻断）")
    void testSubmitReport_signFailure_skipsSignatureAndSubmits() throws Exception {
        testReport.setStatus(ReportStatusEnum.DRAFT.getCode());
        testReport.setEmissionData("{\"scope1\":[],\"scope2\":[],\"scope3\":[]}");

        ObjectMapper realMapper = new ObjectMapper();
        var mockNode = realMapper.readTree("{\"scope1\":[],\"scope2\":[],\"scope3\":[]}");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(any(String.class))).thenReturn(mockNode);
        when(digitalSignatureService.signReport(1L, testReport.getEmissionData()))
                .thenThrow(BlockchainException.rsaSignFailed("private key corrupted"));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenReturn(testReport);

        CarbonReportResponse response = carbonService.submitReport(currentUser, 1L);

        assertNull(testReport.getSignatureData());
        assertEquals(ReportStatusEnum.SUBMITTED.getCode(), testReport.getStatus());
        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("submit report fails when report missing")
    void testSubmitReportFailReportNotFound() {
        when(carbonReportRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 999L));
    }

    @Test
    @DisplayName("方案A: 审核通过自动触发上链，存证成功推进至ON_CHAIN并奖励信誉分")
    void testReviewReportSuccess() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "Approved");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-auto-123");

        CarbonReportResponse response = carbonService.reviewReport(currentUser, reviewRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.ON_CHAIN.getCode(), response.getStatus());
        assertEquals("tx-auto-123", response.getBlockchainTxHash());
        verify(blockchainService, times(1)).commitReportToChain(1L, testReport.getEmissionData());
        verify(creditScoreService, times(1)).addBonusPoints(eq(1L), eq(5), any(), eq(1L));
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("方案A容错: 审核通过自动上链抛异常时保持APPROVED状态，TxHash为null，可供后续certify补救")
    void testReviewReportAutoChainExceptionKeepsApprovedStatus() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "Approved");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData()))
                .thenThrow(BlockchainException.connectionFailed("Fabric network timeout"));

        CarbonReportResponse response = carbonService.reviewReport(currentUser, reviewRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.APPROVED.getCode(), response.getStatus());
        assertNull(response.getBlockchainTxHash());
        assertNull(testReport.getBlockchainTxHash());
        verify(blockchainService, times(1)).commitReportToChain(1L, testReport.getEmissionData());
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("方案A容错: 审核通过自动上链返回null时保持APPROVED状态，TxHash为null，可供后续certify补救")
    void testReviewReportAutoChainReturnsNullKeepsApprovedStatus() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "Approved");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn(null);

        CarbonReportResponse response = carbonService.reviewReport(currentUser, reviewRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.APPROVED.getCode(), response.getStatus());
        assertNull(response.getBlockchainTxHash());
        assertNull(testReport.getBlockchainTxHash());
        verify(blockchainService, times(1)).commitReportToChain(1L, testReport.getEmissionData());
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("审核失败-数字签名验签失败(篡改数据)时严禁审核通过")
    void testReviewReportFailTamperedSignatureCannotBeApproved() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        testReport.setSignatureData("tampered-signature-data");
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "Approved");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(digitalSignatureService.verifySignature(any(), any(), eq("tampered-signature-data"))).thenReturn(false);

        CarbonException ex = assertThrows(CarbonException.class,
                () -> carbonService.reviewReport(currentUser, reviewRequest));
        assertTrue(ex.getMessage().contains("数字签名校验失败"));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("admin certification pushes approved report on-chain")
    void testCertifyReportSuccess() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setTotalEmission(BigDecimal.TEN);
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-123");

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.ON_CHAIN.getCode(), response.getStatus());
        assertEquals("tx-123", response.getBlockchainTxHash());
        verify(blockchainService, times(1)).commitReportToChain(1L, testReport.getEmissionData());
        verify(creditScoreService, times(1)).addBonusPoints(eq(1L), eq(5), any(), eq(1L));
        verify(emissionRatingService, times(1)).rateEnterprise(eq(1L), eq("2024"), eq(BigDecimal.TEN), eq(null), eq(1L));
    }

    @Test
    @DisplayName("admin certification rejects non-approved report")
    void testCertifyReportFailsWhenStatusInvalid() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.certifyReport(currentUser, certificationRequest));
        verify(blockchainService, never()).commitReportToChain(any(), any());
    }

    @Test
    @DisplayName("batch certify reports success")
    void testBatchCertifyReportsSuccess() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any())).thenReturn(testReport);
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));
        when(blockchainService.commitReportToChain(any(), any())).thenReturn("tx-batch-1");

        BatchCertifyRequest req = BatchCertifyRequest.builder()
                .reportIds(Arrays.asList(1L))
                .reviewResult(ReportStatusEnum.ON_CHAIN.getCode())
                .reviewComment("batch ok")
                .build();

        BatchCertifyResponse resp = carbonService.batchCertifyReports(currentUser, req);

        assertNotNull(resp);
        assertEquals(1, resp.getTotalCount());
        assertEquals(1, resp.getSuccessCount());
        assertEquals(0, resp.getFailureCount());
        assertEquals(1L, resp.getSuccessReportIds().get(0));
    }

    @Test
    @DisplayName("get report success")
    void testGetReportSuccess() {
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));

        CarbonReportResponse response = carbonService.getReport(currentUser, 1L);

        assertNotNull(response);
        assertEquals("CR20240101001", response.getReportNo());
    }

    @Test
    @DisplayName("已软删除的报告不可再被读取（软删除不得绕过数据隔离）")
    void testGetReportFailWhenSoftDeleted() {
        // 软删除行的主键仍存在于表中，仓库按 deleted=false 过滤后必须返回空
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class, () -> carbonService.getReport(currentUser, 1L));
    }

    @Test
    @DisplayName("企业用户读取他企业报告必须被拒绝（水平越权防护）")
    void testGetReportFailWhenCrossEnterprise() {
        currentUser.setRoles(List.of("ENTERPRISE"));
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        // 报告属于企业 1，当前用户的企业是 2
        Enterprise otherEnterprise = Enterprise.builder().userId(2L).enterpriseName("Other").build();
        otherEnterprise.setId(2L);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(otherEnterprise));

        assertThrows(AuthorizationException.class, () -> carbonService.getReport(currentUser, 1L));
    }

    @Test
    @DisplayName("企业用户删除他企业草稿必须被拒绝（水平越权防护）")
    void testDeleteReportFailWhenCrossEnterprise() {
        currentUser.setRoles(List.of("ENTERPRISE"));
        testReport.setStatus(ReportStatusEnum.DRAFT.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        Enterprise otherEnterprise = Enterprise.builder().userId(2L).enterpriseName("Other").build();
        otherEnterprise.setId(2L);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(otherEnterprise));

        assertThrows(AuthorizationException.class, () -> carbonService.deleteReport(currentUser, 1L));
        verify(carbonReportRepository, never()).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("核查员可跨企业读取报告详情（审阅角色放行）")
    void testGetReportAllowedForReviewerRole() {
        currentUser.setRoles(List.of("REVIEWER"));
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));

        CarbonReportResponse response = carbonService.getReport(currentUser, 1L);

        assertNotNull(response);
        assertEquals("CR20240101001", response.getReportNo());
    }

    @Test
    @DisplayName("已软删除的报告不可再被提交")
    void testSubmitReportFailWhenSoftDeleted() {
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 1L));
    }

    @Test
    @DisplayName("get report fails when not found")
    void testGetReportFailNotFound() {
        when(carbonReportRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class, () -> carbonService.getReport(currentUser, 999L));
    }

    @Test
    @DisplayName("list reports")
    void testListReports() {
        Page<CarbonReport> page = new PageImpl<>(Arrays.asList(testReport));
        when(carbonReportRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));

        Page<CarbonReportResponse> result = carbonService.listReports(null, null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("list my reports")
    void testListMyReports() {
        Page<CarbonReport> page = new PageImpl<>(Arrays.asList(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.searchMyReports(eq(1L), eq(null), eq(null), eq(null), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));

        Page<CarbonReportResponse> result = carbonService.listMyReports(currentUser, null, null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("list my reports with title and accounting period filters")
    void testListMyReportsWithFilters() {
        Page<CarbonReport> page = new PageImpl<>(Arrays.asList(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.searchMyReports(eq(1L), eq(ReportStatusEnum.DRAFT.getCode()), eq("Test"), eq("2024-Q1"), any(Pageable.class)))
                .thenReturn(page);
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));

        Page<CarbonReportResponse> result = carbonService.listMyReports(
                currentUser,
                ReportStatusEnum.DRAFT.getCode(),
                "  Test  ",
                " 2024-Q1 ",
                1,
                10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(carbonReportRepository).searchMyReports(eq(1L), eq(ReportStatusEnum.DRAFT.getCode()), eq("Test"), eq("2024-Q1"), any(Pageable.class));
    }

    @Test
    @DisplayName("delete report success")
    void testDeleteReportSuccess() {
        testReport.setStatus(ReportStatusEnum.DRAFT.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenReturn(testReport);

        carbonService.deleteReport(currentUser, 1L);

        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("delete report fails when not draft")
    void testDeleteReportFailNotDraft() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.deleteReport(currentUser, 1L));
        verify(carbonReportRepository, never()).save(any());
    }

    // ==================== Additional coverage tests ====================

    @Test
    @DisplayName("submit report fails when enterprise not found")
    void testSubmitReportFailEnterpriseNotFound() {
        testReport.setStatus(ReportStatusEnum.DRAFT.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 1L));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit report fails when no permission (report belongs to different enterprise)")
    void testSubmitReportFailNoPermission() {
        testReport.setStatus(ReportStatusEnum.DRAFT.getCode());
        testReport.setEnterpriseId(999L); // different enterprise
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 1L));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit report fails when already submitted")
    void testSubmitReportFailAlreadySubmitted() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 1L));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("review report fails when report not reviewable (DRAFT status)")
    void testReviewReportFailNotReviewable() {
        testReport.setStatus(ReportStatusEnum.DRAFT.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "Approved");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.reviewReport(currentUser, reviewRequest));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("review report fails with invalid review result (null)")
    void testReviewReportFailInvalidResult() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, null, "Comment");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.reviewReport(currentUser, reviewRequest));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("review report fails with invalid review result (not APPROVED or REJECTED)")
    void testReviewReportFailInvalidDecision() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "Comment");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.reviewReport(currentUser, reviewRequest));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("certify report fails with invalid certification decision")
    void testCertifyReportFailInvalidDecision() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.certifyReport(currentUser, certificationRequest));
        verify(blockchainService, never()).commitReportToChain(any(), any());
    }

    @Test
    @DisplayName("certify report rejects with comment")
    void testCertifyReportRejectWithComment() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setReviewComment("Existing comment");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), "Certification issues found");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.REJECTED.getCode(), response.getStatus());
        verify(blockchainService, never()).commitReportToChain(any(), any());
    }

    @Test
    @DisplayName("certify report rejects without existing comment")
    void testCertifyReportRejectWithoutExistingComment() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setReviewComment(null);
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), "Issues");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.REJECTED.getCode(), response.getStatus());
    }

    @Test
    @DisplayName("certify report rejects with blank comment")
    void testCertifyReportRejectWithBlankComment() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setReviewComment("");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), "");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.REJECTED.getCode(), response.getStatus());
    }

    @Test
    @DisplayName("certify report with null review comment on rejection")
    void testCertifyReportRejectWithNullComment() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setReviewComment("previous");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), null);

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.REJECTED.getCode(), response.getStatus());
    }

    /**
     * 畸形 emissionData 必须**拒绝创建**，而不是静默落库 totalEmission=0。
     *
     * <p>旧断言曾要求本用例「创建成功并 save」，把「静默归零」这个最危险的旧行为
     * 固化成了契约——真实排放被记成 0 却显示创建成功，后续评级/配额/交易全部失真。
     * 语义变更后该断言必须同步：应抛数据格式异常且不落库。</p>
     */
    @Test
    @DisplayName("create report rejects malformed emission data (no silent zero)")
    void testCreateReportWithMalformedEmissionData() throws Exception {
        reportRequest.setEmissionData("not-json");
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree("not-json")).thenThrow(new RuntimeException("parse error"));

        assertThrows(CarbonException.class,
                () -> carbonService.createReport(currentUser, reportRequest),
                "畸形载荷应抛数据格式异常");
        verify(carbonReportRepository, never()).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("create report with null emission data")
    void testCreateReportWithNullEmissionData() {
        reportRequest.setEmissionData(null);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenReturn(testReport);

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);

        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("create report rejects negative activity_data (BUG-03)")
    void testCreateReportRejectsNegativeEmission() throws Exception {
        String emissionJson = "{\"scope1\":[{\"activity_data\":-100,\"emission_factor\":0.5}]}";
        reportRequest.setEmissionData(emissionJson);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(emissionJson))
                .thenReturn(new ObjectMapper().readTree(emissionJson));

        assertThrows(CarbonException.class,
                () -> carbonService.createReport(currentUser, reportRequest));
        verify(carbonReportRepository, never()).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("create report rejects negative scalar emission data (scope1=-100.5, scope2=-50.0)")
    void testCreateReportRejectsNegativeScalarEmissionData() throws Exception {
        String emissionJson = "{\"scope1\": -100.5, \"scope2\": -50.0}";
        reportRequest.setEmissionData(emissionJson);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(emissionJson))
                .thenReturn(new ObjectMapper().readTree(emissionJson));

        CarbonException ex = assertThrows(CarbonException.class,
                () -> carbonService.createReport(currentUser, reportRequest));
        assertTrue(ex.getMessage().contains("不能为负数"));
        verify(carbonReportRepository, never()).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("create report rejects negative string emission data (scope1='-100.5')")
    void testCreateReportRejectsNegativeStringEmissionData() throws Exception {
        String emissionJson = "{\"scope1\": \"-100.5\"}";
        reportRequest.setEmissionData(emissionJson);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(emissionJson))
                .thenReturn(new ObjectMapper().readTree(emissionJson));

        CarbonException ex = assertThrows(CarbonException.class,
                () -> carbonService.createReport(currentUser, reportRequest));
        assertTrue(ex.getMessage().contains("不能为负数"));
        verify(carbonReportRepository, never()).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("create report rejects negative totalEmission (totalEmission=-500.0)")
    void testCreateReportRejectsNegativeTotalEmission() throws Exception {
        String emissionJson = "{\"totalEmission\": -500.0}";
        reportRequest.setEmissionData(emissionJson);
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(emissionJson))
                .thenReturn(new ObjectMapper().readTree(emissionJson));

        CarbonException ex = assertThrows(CarbonException.class,
                () -> carbonService.createReport(currentUser, reportRequest));
        assertTrue(ex.getMessage().contains("不能为负数"));
        verify(carbonReportRepository, never()).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("list my reports fails when enterprise not found")
    void testListMyReportsFailEnterpriseNotFound() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class,
                () -> carbonService.listMyReports(currentUser, null, null, null, 1, 10));
    }

    @Test
    @DisplayName("get report with enterprise and reviewer names resolved")
    void testGetReportWithEnterpriseAndReviewer() {
        testReport.setReviewerId(2L);
        User reviewer = User.builder().realName("Reviewer A").build();
        reviewer.setId(2L);

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));
        when(userRepository.findById(2L)).thenReturn(Optional.of(reviewer));

        CarbonReportResponse response = carbonService.getReport(currentUser, 1L);

        assertNotNull(response);
        assertEquals("Test Enterprise", response.getEnterpriseName());
        assertEquals("Reviewer A", response.getReviewerName());
    }

    @Test
    @DisplayName("get report with missing enterprise and reviewer")
    void testGetReportWithMissingEnterpriseAndReviewer() {
        testReport.setReviewerId(999L);
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        CarbonReportResponse response = carbonService.getReport(currentUser, 1L);

        assertNotNull(response);
    }

    @Test
    @DisplayName("certify report with accounting period longer than 4 chars")
    void testCertifyReportWithLongAccountingPeriod() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setTotalEmission(BigDecimal.TEN);
        testReport.setAccountingPeriod("2024-Q1-detail");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-456");

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.ON_CHAIN.getCode(), response.getStatus());
        verify(emissionRatingService).rateEnterprise(eq(1L), eq("2024"), any(), eq(null), eq(1L));
    }

    @Test
    @DisplayName("certify report with null accounting period uses current year")
    void testCertifyReportWithNullAccountingPeriod() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setTotalEmission(BigDecimal.TEN);
        testReport.setAccountingPeriod(null);
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-789");

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);

        assertNotNull(response);
        verify(emissionRatingService).rateEnterprise(eq(1L), any(), any(), eq(null), eq(1L));
    }

    @Test
    @DisplayName("certify report with empty accounting period uses current year")
    void testCertifyReportWithEmptyAccountingPeriod() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setTotalEmission(BigDecimal.TEN);
        testReport.setAccountingPeriod("");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-101");

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);

        assertNotNull(response);
        verify(emissionRatingService).rateEnterprise(eq(1L), any(), any(), eq(null), eq(1L));
    }

    @Test
    @DisplayName("review report with REJECTED status")
    void testReviewReportRejected() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), "Needs revision");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.reviewReport(currentUser, reviewRequest);

        assertNotNull(response);
        assertEquals(ReportStatusEnum.REJECTED.getCode(), response.getStatus());
        verify(creditScoreService, times(1)).deductPoints(
                eq(testReport.getEnterpriseId()),
                any(Integer.class),
                any(String.class),
                eq(currentUser.getUserId()),
                eq(testReport.getId()));
    }

    // ==================== Additional branch coverage tests ====================

    @Test
    @DisplayName("isReviewerDecision-当reviewResult为null时返回false")
    void testIsReviewerDecisionNull() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, null, "Comment");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.reviewReport(currentUser, reviewRequest));
    }

    @Test
    @DisplayName("isCertificationDecision-当reviewResult为null时返回false")
    void testIsCertificationDecisionNull() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        ReviewRequest certificationRequest = new ReviewRequest(1L, null, "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.certifyReport(currentUser, certificationRequest));
    }

    @Test
    @DisplayName("certifyReport-拒绝时评论非空非blank且无已有评论")
    void testCertifyReportRejectWithNewCommentOnly() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setReviewComment(null);
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), "Certification issues");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);
        assertNotNull(response);
        assertEquals(ReportStatusEnum.REJECTED.getCode(), response.getStatus());
    }

    @Test
    @DisplayName("certifyReport-拒绝时评论非空非blank且已有评论")
    void testCertifyReportRejectWithExistingAndNewComment() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setReviewComment("Previous comment");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), "More issues");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);
        assertNotNull(response);
        assertEquals(ReportStatusEnum.REJECTED.getCode(), response.getStatus());
    }

    @Test
    @DisplayName("toResponse-enterpriseId为null时enterpriseName为null")
    void testToResponseNullEnterpriseId() {
        testReport.setEnterpriseId(null);
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        CarbonReportResponse response = carbonService.getReport(currentUser, 1L);
        assertNull(response.getEnterpriseName());
    }

    @Test
    @DisplayName("toResponse-reviewerId为null时reviewerName为null")
    void testToResponseNullReviewerId() {
        testReport.setReviewerId(null);
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));

        CarbonReportResponse response = carbonService.getReport(currentUser, 1L);
        assertNull(response.getReviewerName());
    }

    @Test
    @DisplayName("toResponse-status为null时statusText为null")
    void testToResponseNullStatus() {
        testReport.setStatus(null);
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        CarbonReportResponse response = carbonService.getReport(currentUser, 1L);
        assertNull(response.getStatusText());
    }

    @Test
    @DisplayName("parseEmissionTotals-带完整scope1/2/3数据")
    void testCreateReportWithFullEmissionData() {
        String emissionData = "{\"scope1\":[{\"activity_data\":\"100\",\"emission_factor\":\"2.5\"}],\"scope2\":[{\"activity_data\":\"200\",\"emission_factor\":\"1.5\"}],\"scope3\":[{\"activity_data\":\"50\",\"emission_factor\":\"3.0\"}]}";
        reportRequest.setEmissionData(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("parseEmissionTotals-仅scope1数据")
    void testCreateReportWithScope1Only() {
        String emissionData = "{\"scope1\":[{\"activity_data\":\"100\",\"emission_factor\":\"2.5\"}]}";
        reportRequest.setEmissionData(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
    }

    @Test
    @DisplayName("normalizeFilter-空字符串返回null")
    void testListMyReportsWithEmptyFilters() {
        Page<CarbonReport> page = new PageImpl<>(Arrays.asList(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.searchMyReports(eq(1L), eq(null), isNull(), isNull(), any(Pageable.class))).thenReturn(page);
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));

        Page<CarbonReportResponse> result = carbonService.listMyReports(currentUser, null, "", "  ", 1, 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("certifyReport-ON_CHAIN时带空accountingPeriod使用当前年")
    void testCertifyReportOnChainWithEmptyAccountingPeriod() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setTotalEmission(BigDecimal.TEN);
        testReport.setAccountingPeriod("");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-202");

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);
        assertNotNull(response);
        assertEquals(ReportStatusEnum.ON_CHAIN.getCode(), response.getStatus());
    }

    @Test
    @DisplayName("deleteReport-报告不存在时抛出异常")
    void testDeleteReportNotFound() {
        when(carbonReportRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class, () -> carbonService.deleteReport(currentUser, 999L));
    }

    @Test
    @DisplayName("deleteReport-非草稿状态不能删除")
    void testDeleteReportNotDraftStatus() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.deleteReport(currentUser, 1L));
    }

    @Test
    @DisplayName("submitReport-报告不存在时抛出异常")
    void testSubmitReportNotFound() {
        when(carbonReportRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 999L));
    }

    @Test
    @DisplayName("listMyReports-企业不存在时抛出异常")
    void testListMyReportsEnterpriseNotFound() {
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(CarbonException.class,
                () -> carbonService.listMyReports(currentUser, null, null, null, 1, 10));
    }

    @Test
    @DisplayName("certifyReport-ON_CHAIN时带4字符accountingPeriod")
    void testCertifyReportOnChainWithFourCharAccountingPeriod() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setTotalEmission(BigDecimal.TEN);
        testReport.setAccountingPeriod("2024");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-303");

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);
        assertNotNull(response);
        verify(emissionRatingService).rateEnterprise(eq(1L), eq("2024"), any(), eq(null), eq(1L));
    }

    // ==================== Additional branch coverage tests (batch 2) ====================

    @Test
    @DisplayName("submitReport-UNDER_REVIEW状态不可提交")
    void testSubmitReportFailUnderReviewStatus() {
        testReport.setStatus(ReportStatusEnum.UNDER_REVIEW.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 1L));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitReport-APPROVED状态不可提交")
    void testSubmitReportFailApprovedStatus() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 1L));
    }

    @Test
    @DisplayName("submitReport-ON_CHAIN状态不可提交")
    void testSubmitReportFailOnChainStatus() {
        testReport.setStatus(ReportStatusEnum.ON_CHAIN.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));

        assertThrows(CarbonException.class, () -> carbonService.submitReport(currentUser, 1L));
    }

    @Test
    @DisplayName("submitReport-REJECTED状态可以重新提交")
    void testSubmitReportSuccessRejectedStatus() {
        testReport.setStatus(ReportStatusEnum.REJECTED.getCode());
        testReport.setEmissionData("{\"scope1\":[],\"scope2\":[],\"scope3\":[]}");
        ObjectMapper realMapper = new ObjectMapper();
        try {
            var mockNode = realMapper.readTree("{\"scope1\":[],\"scope2\":[],\"scope3\":[]}");
            when(objectMapper.readTree(any(String.class))).thenReturn(mockNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenReturn(testReport);

        CarbonReportResponse response = carbonService.submitReport(currentUser, 1L);
        assertNotNull(response);
        verify(carbonReportRepository).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("reviewReport-UNDER_REVIEW状态可审核并自动上链")
    void testReviewReportSuccessUnderReviewStatus() {
        testReport.setStatus(ReportStatusEnum.UNDER_REVIEW.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "Good");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-under-review");

        CarbonReportResponse response = carbonService.reviewReport(currentUser, reviewRequest);
        assertNotNull(response);
        assertEquals(ReportStatusEnum.ON_CHAIN.getCode(), response.getStatus());
        assertEquals("tx-under-review", response.getBlockchainTxHash());
    }

    @Test
    @DisplayName("reviewReport-APPROVED状态不可审核")
    void testReviewReportFailApprovedStatus() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "Good");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.reviewReport(currentUser, reviewRequest));
    }

    @Test
    @DisplayName("reviewReport-ON_CHAIN状态不可审核")
    void testReviewReportFailOnChainStatus() {
        testReport.setStatus(ReportStatusEnum.ON_CHAIN.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "Good");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.reviewReport(currentUser, reviewRequest));
    }

    @Test
    @DisplayName("deleteReport-REJECTED状态不可删除")
    void testDeleteReportFailRejectedStatus() {
        testReport.setStatus(ReportStatusEnum.REJECTED.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.deleteReport(currentUser, 1L));
        verify(carbonReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteReport-UNDER_REVIEW状态不可删除")
    void testDeleteReportFailUnderReviewStatus() {
        testReport.setStatus(ReportStatusEnum.UNDER_REVIEW.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.deleteReport(currentUser, 1L));
    }

    @Test
    @DisplayName("createReport-仅scope2数据")
    void testCreateReportWithScope2Only() {
        String emissionData = "{\"scope2\":[{\"activity_data\":\"200\",\"emission_factor\":\"1.5\"}]}";
        reportRequest.setEmissionData(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
    }

    @Test
    @DisplayName("createReport-仅scope3数据")
    void testCreateReportWithScope3Only() {
        String emissionData = "{\"scope3\":[{\"activity_data\":\"50\",\"emission_factor\":\"3.0\"}]}";
        reportRequest.setEmissionData(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
    }

    @Test
    @DisplayName("createReport-scope2+scope3数据")
    void testCreateReportWithScope2And3() {
        String emissionData = "{\"scope2\":[{\"activity_data\":\"200\",\"emission_factor\":\"1.5\"}],\"scope3\":[{\"activity_data\":\"50\",\"emission_factor\":\"3.0\"}]}";
        reportRequest.setEmissionData(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
    }

    @Test
    @DisplayName("createReport-空scope数组数据")
    void testCreateReportWithEmptyScopeArrays() {
        String emissionData = "{\"scope1\":[],\"scope2\":[],\"scope3\":[]}";
        reportRequest.setEmissionData(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
    }

    @Test
    @DisplayName("listReports-带企业ID和状态筛选")
    void testListReportsWithFilters() {
        Page<CarbonReport> page = new PageImpl<>(Arrays.asList(testReport));
        when(carbonReportRepository.search(eq(1L), eq(0), eq("test"), any(Pageable.class))).thenReturn(page);

        Page<CarbonReportResponse> result = carbonService.listReports(1L, 0, "test", 1, 10);
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("listReports-普通企业用户越权调用抛出权限拒绝异常")
    void testListReportsThrowsForEnterpriseUser() {
        JwtUserDetails entUser = JwtUserDetails.builder()
                .userId(1L)
                .username("enterprise001")
                .roles(List.of("ENTERPRISE"))
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> carbonService.listReports(entUser, null, null, null, 1, 10));
        assertEquals(ErrorCode.PERMISSION_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("reviewReport-报告不存在时抛出异常")
    void testReviewReportNotFound() {
        when(carbonReportRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());
        ReviewRequest reviewRequest = new ReviewRequest(999L, ReportStatusEnum.APPROVED.getCode(), "Good");

        assertThrows(CarbonException.class, () -> carbonService.reviewReport(currentUser, reviewRequest));
    }

    @Test
    @DisplayName("certifyReport-报告不存在时抛出异常")
    void testCertifyReportNotFound() {
        when(carbonReportRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());
        ReviewRequest certificationRequest = new ReviewRequest(999L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        assertThrows(CarbonException.class, () -> carbonService.certifyReport(currentUser, certificationRequest));
    }

    @Test
    @DisplayName("certifyReport-ON_CHAIN但accountingPeriod为null")
    void testCertifyReportOnChainWithNullAccountingPeriod() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setTotalEmission(BigDecimal.TEN);
        testReport.setAccountingPeriod(null);
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-null-period");

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);
        assertNotNull(response);
        assertEquals(ReportStatusEnum.ON_CHAIN.getCode(), response.getStatus());
        // null accountingPeriod → uses current year
        verify(emissionRatingService).rateEnterprise(eq(1L), any(), any(), eq(null), eq(1L));
    }

    @Test
    @DisplayName("certifyReport-ON_CHAIN但accountingPeriod为空字符串")
    void testCertifyReportOnChainWithEmptyAccountingPeriodV2() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setTotalEmission(BigDecimal.TEN);
        testReport.setAccountingPeriod("");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "certified");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(blockchainService.commitReportToChain(1L, testReport.getEmissionData())).thenReturn("tx-empty-period");

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);
        assertNotNull(response);
        verify(emissionRatingService).rateEnterprise(eq(1L), any(), any(), eq(null), eq(1L));
    }

    @Test
    @DisplayName("certifyReport-拒绝时不修改blockchainTxHash")
    void testCertifyReportRejectClearsBlockchainHash() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setBlockchainTxHash("old-hash");
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), "Issues");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);
        assertNotNull(response);
        assertNull(response.getBlockchainTxHash());
        assertNull(response.getOnChainAt());
    }

    @Test
    @DisplayName("getReport-状态为ON_CHAIN时statusText正确")
    void testGetReportOnChainStatus() {
        testReport.setStatus(ReportStatusEnum.ON_CHAIN.getCode());
        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.of(testEnterprise));

        CarbonReportResponse response = carbonService.getReport(currentUser, 1L);
        assertNotNull(response);
        assertEquals("已上链", response.getStatusText());
    }

    @Test
    @DisplayName("isReviewerDecision-ON_CHAIN不是有效审核决定")
    void testReviewReportFailOnChainAsDecision() {
        testReport.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        ReviewRequest reviewRequest = new ReviewRequest(1L, ReportStatusEnum.ON_CHAIN.getCode(), "on chain");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.reviewReport(currentUser, reviewRequest));
    }

    @Test
    @DisplayName("isCertificationDecision-APPROVED不是有效认证决定")
    void testCertifyReportFailApprovedAsDecision() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.APPROVED.getCode(), "approved");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));

        assertThrows(CarbonException.class, () -> carbonService.certifyReport(currentUser, certificationRequest));
    }

    // ==================== Branch coverage: parseEmissionTotals with mocked objectMapper ====================

    @Test
    @DisplayName("parseEmissionTotals-仅scope1有数据(mock objectMapper),覆盖scope2/scope3缺失分支")
    void testCreateReportScope1OnlyWithParsedData() throws Exception {
        String emissionData = "{\"scope1\":[{\"activity_data\":\"100\",\"emission_factor\":\"2.5\"}]}";
        reportRequest.setEmissionData(emissionData);

        ObjectMapper realMapper = new ObjectMapper();
        var mockNode = realMapper.readTree(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(emissionData)).thenReturn(mockNode);
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("parseEmissionTotals-仅scope2有数据(mock objectMapper),覆盖scope1/scope3缺失分支和scope2循环体")
    void testCreateReportScope2OnlyWithParsedData() throws Exception {
        String emissionData = "{\"scope2\":[{\"activity_data\":\"200\",\"emission_factor\":\"1.5\"}]}";
        reportRequest.setEmissionData(emissionData);

        ObjectMapper realMapper = new ObjectMapper();
        var mockNode = realMapper.readTree(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(emissionData)).thenReturn(mockNode);
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("parseEmissionTotals-仅scope3有数据(mock objectMapper),覆盖scope3循环体执行")
    void testCreateReportScope3OnlyWithParsedData() throws Exception {
        String emissionData = "{\"scope3\":[{\"activity_data\":\"50\",\"emission_factor\":\"3.0\"}]}";
        reportRequest.setEmissionData(emissionData);

        ObjectMapper realMapper = new ObjectMapper();
        var mockNode = realMapper.readTree(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(emissionData)).thenReturn(mockNode);
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    @Test
    @DisplayName("parseEmissionTotals-全部scope有非空数据(mock objectMapper),覆盖所有scope循环体执行")
    void testCreateReportAllScopesWithParsedData() throws Exception {
        String emissionData = "{\"scope1\":[{\"activity_data\":\"100\",\"emission_factor\":\"2.5\"}],"
                + "\"scope2\":[{\"activity_data\":\"200\",\"emission_factor\":\"1.5\"}],"
                + "\"scope3\":[{\"activity_data\":\"50\",\"emission_factor\":\"3.0\"}]}";
        reportRequest.setEmissionData(emissionData);

        ObjectMapper realMapper = new ObjectMapper();
        var mockNode = realMapper.readTree(emissionData);

        when(enterpriseRepository.findByUserId(1L)).thenReturn(Optional.of(testEnterprise));
        when(objectMapper.readTree(emissionData)).thenReturn(mockNode);
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.createReport(currentUser, reportRequest);
        assertNotNull(response);
        verify(carbonReportRepository, times(1)).save(any(CarbonReport.class));
    }

    // ==================== Branch coverage: mergeCertificationComment isBlank true branch ====================

    @Test
    @DisplayName("certifyReport-拒绝时已有评论为空白字符串(非null),覆盖mergeCertificationComment的isBlank分支")
    void testCertifyReportRejectWithBlankExistingComment() {
        testReport.setStatus(ReportStatusEnum.APPROVED.getCode());
        testReport.setReviewComment("   "); // blank but not null
        ReviewRequest certificationRequest = new ReviewRequest(1L, ReportStatusEnum.REJECTED.getCode(), "Certification issues");

        when(carbonReportRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testReport));
        when(carbonReportRepository.save(any(CarbonReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarbonReportResponse response = carbonService.certifyReport(currentUser, certificationRequest);
        assertNotNull(response);
        assertEquals(ReportStatusEnum.REJECTED.getCode(), response.getStatus());
        verify(blockchainService, never()).commitReportToChain(any(), any());
    }
}
