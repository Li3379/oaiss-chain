package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Reviewer;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.ReportStatusEnum;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.ReviewerRepository;
import com.oaiss.chain.repository.UserRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewerServiceTest {

    @Mock
    private ReviewerRepository reviewerRepository;

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @Mock
    private EnterpriseRepository enterpriseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DigitalSignatureService digitalSignatureService;

    @InjectMocks
    private ReviewerService reviewerService;

    private Reviewer reviewer;
    private User user;

    @BeforeEach
    void setUp() {
        reviewer = Reviewer.builder()
                .userId(11L)
                .qualificationNo("REV-001")
                .completedReviews(3)
                .level(2)
                .organization("OAISS Review")
                .build();
        reviewer.setDeleted(false);

        user = User.builder()
                .username("reviewer")
                .password("secret")
                .realName("Alice Reviewer")
                .userType(2)
                .build();
    }

    @Test
    @DisplayName("getReviewerInfo enriches reviewer name from user profile")
    void getReviewerInfo_enrichesReviewerName() {
        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));

        Reviewer result = reviewerService.getReviewerInfo(11L);

        assertEquals("Alice Reviewer", result.getName());
    }

    @Test
    @DisplayName("getReviewerInfo throws when reviewer is missing")
    void getReviewerInfo_whenMissing_throws() {
        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> reviewerService.getReviewerInfo(11L));
    }

    @Test
    @DisplayName("getPendingReports enriches enterprise and status text")
    void getPendingReports_enrichesDisplayFields() {
        CarbonReport report = CarbonReport.builder()
                .enterpriseId(9L)
                .status(ReportStatusEnum.SUBMITTED.getCode())
                .reportNo("CR-001")
                .submitterId(1L)
                .accountingPeriod("2026-Q1")
                .title("Q1 report")
                .reportType(1)
                .emissionData("{}")
                .build();
        Enterprise enterprise = Enterprise.builder()
                .userId(99L)
                .enterpriseName("Green Corp")
                .creditCode("123456789012345678")
                .build();
        Page<CarbonReport> page = new PageImpl<>(List.of(report));

        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.findByStatusAndDeletedFalse(eq(ReportStatusEnum.SUBMITTED.getCode()), any(Pageable.class)))
                .thenReturn(page);
        when(enterpriseRepository.findById(9L)).thenReturn(Optional.of(enterprise));

        Page<CarbonReport> result = reviewerService.getPendingReports(11L, 1, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals("Green Corp", result.getContent().get(0).getEnterpriseName());
        assertEquals(ReportStatusEnum.SUBMITTED.getDescription(), result.getContent().get(0).getStatusText());
    }

    @Test
    @DisplayName("getPendingReports verifies signature of signed report as valid")
    void getPendingReports_signedValid_reportSignatureValidTrue() {
        CarbonReport report = reportWithSignature("SIG-VALID");
        Page<CarbonReport> page = new PageImpl<>(List.of(report));

        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.findByStatusAndDeletedFalse(eq(ReportStatusEnum.SUBMITTED.getCode()), any(Pageable.class)))
                .thenReturn(page);
        when(digitalSignatureService.verifySignature(1L, "{}", "SIG-VALID")).thenReturn(true);

        Page<CarbonReport> result = reviewerService.getPendingReports(11L, 1, 10);

        assertTrue(result.getContent().get(0).getSignatureValid());
        verify(digitalSignatureService, times(1)).verifySignature(1L, "{}", "SIG-VALID");    }

    @Test
    @DisplayName("getPendingReports flags tampered signature as invalid before review")
    void getPendingReports_signedInvalid_signatureValidFalse() {
        CarbonReport report = reportWithSignature("SIG-TAMPERED");
        Page<CarbonReport> page = new PageImpl<>(List.of(report));

        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.findByStatusAndDeletedFalse(eq(ReportStatusEnum.SUBMITTED.getCode()), any(Pageable.class)))
                .thenReturn(page);
        when(digitalSignatureService.verifySignature(1L, "{}", "SIG-TAMPERED")).thenReturn(false);

        Page<CarbonReport> result = reviewerService.getPendingReports(11L, 1, 10);

        assertFalse(result.getContent().get(0).getSignatureValid());
    }

    @Test
    @DisplayName("getPendingReports leaves unsigned report signatureValid null without verifying")
    void getPendingReports_unsigned_signatureValidNull() {
        CarbonReport report = reportWithSignature(null);
        Page<CarbonReport> page = new PageImpl<>(List.of(report));

        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.findByStatusAndDeletedFalse(eq(ReportStatusEnum.SUBMITTED.getCode()), any(Pageable.class)))
                .thenReturn(page);

        Page<CarbonReport> result = reviewerService.getPendingReports(11L, 1, 10);

        assertNull(result.getContent().get(0).getSignatureValid());
        verify(digitalSignatureService, never()).verifySignature(anyLong(), anyString(), anyString());
    }

    private CarbonReport reportWithSignature(String signatureData) {
        return CarbonReport.builder()
                .enterpriseId(9L)
                .status(ReportStatusEnum.SUBMITTED.getCode())
                .reportNo("CR-SIG-001")
                .submitterId(1L)
                .accountingPeriod("2026-Q1")
                .title("Q1 report")
                .reportType(1)
                .emissionData("{}")
                .signatureData(signatureData)
                .build();
    }

    @Test
    @DisplayName("getReviewHistory enriches records and uses reviewer user id filter")
    void getReviewHistory_enrichesRecords() {
        CarbonReport report = CarbonReport.builder()
                .enterpriseId(7L)
                .status(ReportStatusEnum.APPROVED.getCode())
                .reviewerId(11L)
                .reportNo("CR-002")
                .submitterId(1L)
                .accountingPeriod("2026-Q2")
                .title("Q2 report")
                .reportType(2)
                .emissionData("{}")
                .build();
        Enterprise enterprise = Enterprise.builder()
                .userId(77L)
                .enterpriseName("Blue Sky")
                .creditCode("876543210987654321")
                .build();
        Page<CarbonReport> page = new PageImpl<>(List.of(report));

        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.findByReviewerIdAndStatusInAndDeletedFalse(eq(11L), any(), any(Pageable.class)))
                .thenReturn(page);
        when(enterpriseRepository.findById(7L)).thenReturn(Optional.of(enterprise));

        Page<CarbonReport> result = reviewerService.getReviewHistory(11L, 1, 5);

        assertEquals("Blue Sky", result.getContent().get(0).getEnterpriseName());
        assertEquals(ReportStatusEnum.APPROVED.getDescription(), result.getContent().get(0).getStatusText());
    }

    @Test
    @DisplayName("getStatistics 按审核员实审口径统计：通过+驳回=总审核数")
    void getStatistics_aggregatesCounts() {
        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.countByStatusAndDeletedFalse(ReportStatusEnum.SUBMITTED.getCode())).thenReturn(4L);
        when(carbonReportRepository.countByReviewerIdAndStatusInAndDeletedFalse(
                eq(11L), argThat(list -> list.contains(ReportStatusEnum.APPROVED.getCode())
                        && list.contains(ReportStatusEnum.ON_CHAIN.getCode())))).thenReturn(11L);
        when(carbonReportRepository.countByReviewerIdAndStatusInAndDeletedFalse(
                eq(11L), argThat(list -> list.size() == 1
                        && list.contains(ReportStatusEnum.REJECTED.getCode())))).thenReturn(2L);

        Map<String, Object> stats = reviewerService.getStatistics(11L);

        // completedReviews 由通过+驳回派生（同审核历史口径），不再取生涯累加计数器(3)
        assertEquals(13L, stats.get("completedReviews"));
        assertEquals(2, stats.get("level"));
        assertEquals("OAISS Review", stats.get("organization"));
        assertEquals(4L, stats.get("pendingCount"));
        assertEquals(11L, stats.get("passedCount"));
        assertEquals(2L, stats.get("rejectedCount"));
    }

    @Test
    @DisplayName("incrementCompletedReviews persists incremented counter")
    void incrementCompletedReviews_persistsUpdatedReviewer() {
        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));

        reviewerService.incrementCompletedReviews(11L);

        assertEquals(4, reviewer.getCompletedReviews());
        verify(reviewerRepository).save(reviewer);
    }

    @Test
    @DisplayName("getReviewerInfo skips name enrichment when user not found")
    void getReviewerInfo_whenUserNotFound_skipsEnrichment() {
        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.empty());

        Reviewer result = reviewerService.getReviewerInfo(11L);

        assertEquals(11L, result.getUserId());
    }

    @Test
    @DisplayName("getReviewerInfo skips name enrichment when realName is blank")
    void getReviewerInfo_whenRealNameBlank_skipsEnrichment() {
        user.setRealName("   ");
        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));

        Reviewer result = reviewerService.getReviewerInfo(11L);

        assertEquals(11L, result.getUserId());
    }

    @Test
    @DisplayName("enrichReportDisplayFields handles missing enterprise")
    void enrichReportDisplayFields_whenEnterpriseMissing_handlesGracefully() {
        CarbonReport report = CarbonReport.builder()
                .enterpriseId(99L)
                .status(ReportStatusEnum.SUBMITTED.getCode())
                .reportNo("CR-003")
                .submitterId(1L)
                .accountingPeriod("2026-Q3")
                .title("Q3 report")
                .reportType(1)
                .emissionData("{}")
                .build();
        Page<CarbonReport> page = new PageImpl<>(List.of(report));

        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.findByStatusAndDeletedFalse(eq(ReportStatusEnum.SUBMITTED.getCode()), any(Pageable.class)))
                .thenReturn(page);
        when(enterpriseRepository.findById(99L)).thenReturn(Optional.empty());

        Page<CarbonReport> result = reviewerService.getPendingReports(11L, 1, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals(ReportStatusEnum.SUBMITTED.getDescription(), result.getContent().get(0).getStatusText());
    }

    @Test
    @DisplayName("enrichReportDisplayFields handles null status")
    void enrichReportDisplayFields_whenNullStatus_handlesGracefully() {
        CarbonReport report = CarbonReport.builder()
                .enterpriseId(9L)
                .status(null)
                .reportNo("CR-004")
                .submitterId(1L)
                .accountingPeriod("2026-Q4")
                .title("Q4 report")
                .reportType(1)
                .emissionData("{}")
                .build();
        Page<CarbonReport> page = new PageImpl<>(List.of(report));

        when(reviewerRepository.findByUserIdAndDeletedFalse(11L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.findByStatusAndDeletedFalse(eq(ReportStatusEnum.SUBMITTED.getCode()), any(Pageable.class)))
                .thenReturn(page);

        Page<CarbonReport> result = reviewerService.getPendingReports(11L, 1, 10);

        assertEquals(1, result.getTotalElements());
    }
}
