package com.oaiss.chain.service.ml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.oaiss.chain.dto.EnterpriseInferenceRequest;
import com.oaiss.chain.dto.EnterpriseInferenceResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.CreditScore;
import com.oaiss.chain.entity.EmissionRating;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.CreditScoreRepository;
import com.oaiss.chain.repository.EmissionRatingRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnterpriseInferenceService Unit Tests")
class EnterpriseInferenceServiceTest {

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @Mock
    private CreditScoreRepository creditScoreRepository;

    @Mock
    private EmissionRatingRepository emissionRatingRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private EnterpriseInferenceService service;

    @BeforeEach
    void setUp() {
        service = new EnterpriseInferenceService(
                mlServiceClient, userRepository, carbonReportRepository,
                creditScoreRepository, emissionRatingRepository, transactionRepository);
    }

    @Test
    @DisplayName("Should throw when enterprise not found")
    void inferEnterprise_enterpriseNotFound_throws() {
        when(userRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.inferEnterprise(999L));
    }

    @Test
    @DisplayName("Should throw when no carbon reports exist")
    void inferEnterprise_noReports_throws() {
        User user = new User();
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(1L))
                .thenReturn(Collections.emptyList());

        assertThrows(BusinessException.class, () -> service.inferEnterprise(1L));
    }

    @Test
    @DisplayName("Should aggregate data and call ML service successfully")
    void inferEnterprise_validData_returnsResponse() {
        // Arrange
        Long enterpriseId = 1L;
        User user = new User();
        when(userRepository.findByIdAndDeletedFalse(enterpriseId)).thenReturn(Optional.of(user));

        CarbonReport report = new CarbonReport();
        report.setTotalEmission(new BigDecimal("500.0"));
        report.setStatus(3); // approved
        report.setCreatedAt(LocalDateTime.now().minusDays(10));
        when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(List.of(report));

        CreditScore creditScore = new CreditScore();
        creditScore.setScore(80);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(Optional.of(creditScore));

        when(emissionRatingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.countByUserIdRelated(enterpriseId)).thenReturn(5L);

        EnterpriseInferenceResponse mlResponse = EnterpriseInferenceResponse.builder()
                .enterpriseId(enterpriseId)
                .complianceStatus("compliant")
                .confidence(0.92)
                .anomalyScore(0.15)
                .isAnomaly(false)
                .riskFactors(Collections.emptyList())
                .modelVersion("1.0.0")
                .build();
        when(mlServiceClient.inferEnterprise(any(EnterpriseInferenceRequest.class))).thenReturn(mlResponse);

        // Act
        EnterpriseInferenceResponse response = service.inferEnterprise(enterpriseId);

        // Assert
        assertNotNull(response);
        assertEquals(enterpriseId, response.getEnterpriseId());
        assertEquals("compliant", response.getComplianceStatus());
        assertEquals(0.92, response.getConfidence());
        assertFalse(response.getIsAnomaly());
        verify(mlServiceClient).inferEnterprise(any(EnterpriseInferenceRequest.class));
    }

    @Test
    @DisplayName("Should count rejected reports as compliance flags")
    void inferEnterprise_rejectedReports_countedAsFlags() {
        Long enterpriseId = 2L;
        User user = new User();
        when(userRepository.findByIdAndDeletedFalse(enterpriseId)).thenReturn(Optional.of(user));

        CarbonReport rejected = new CarbonReport();
        rejected.setTotalEmission(new BigDecimal("200.0"));
        rejected.setStatus(4); // rejected
        rejected.setCreatedAt(LocalDateTime.now().minusDays(5));

        CarbonReport approved = new CarbonReport();
        approved.setTotalEmission(new BigDecimal("100.0"));
        approved.setStatus(3); // approved
        approved.setCreatedAt(LocalDateTime.now().minusDays(1));

        when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(List.of(rejected, approved));
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(Optional.empty());
        when(emissionRatingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.countByUserIdRelated(enterpriseId)).thenReturn(0L);

        EnterpriseInferenceResponse mlResponse = EnterpriseInferenceResponse.builder()
                .enterpriseId(enterpriseId)
                .complianceStatus("at_risk")
                .confidence(0.65)
                .anomalyScore(-0.3)
                .isAnomaly(true)
                .riskFactors(List.of("High compliance flag count"))
                .modelVersion("1.0.0")
                .build();
        when(mlServiceClient.inferEnterprise(any(EnterpriseInferenceRequest.class))).thenReturn(mlResponse);

        EnterpriseInferenceResponse response = service.inferEnterprise(enterpriseId);

        assertNotNull(response);
        assertTrue(response.getIsAnomaly());
        verify(mlServiceClient).inferEnterprise(any(EnterpriseInferenceRequest.class));
    }

    @Test
    @DisplayName("Should use defaults when credit score and emission rating not found")
    void inferEnterprise_missingCreditAndRating_usesDefaults() {
        Long enterpriseId = 3L;
        User user = new User();
        when(userRepository.findByIdAndDeletedFalse(enterpriseId)).thenReturn(Optional.of(user));

        CarbonReport report = new CarbonReport();
        report.setTotalEmission(new BigDecimal("300.0"));
        report.setStatus(1);
        report.setCreatedAt(LocalDateTime.now());
        when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(List.of(report));
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(Optional.empty());
        when(emissionRatingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.countByUserIdRelated(enterpriseId)).thenReturn(2L);

        EnterpriseInferenceResponse mlResponse = EnterpriseInferenceResponse.builder()
                .enterpriseId(enterpriseId)
                .complianceStatus("compliant")
                .confidence(0.80)
                .anomalyScore(0.05)
                .isAnomaly(false)
                .riskFactors(Collections.emptyList())
                .modelVersion("1.0.0")
                .build();
        when(mlServiceClient.inferEnterprise(any(EnterpriseInferenceRequest.class))).thenReturn(mlResponse);

        EnterpriseInferenceResponse response = service.inferEnterprise(enterpriseId);

        assertNotNull(response);
        assertEquals("compliant", response.getComplianceStatus());
    }

    @Test
    @DisplayName("Should handle reports with null totalEmission")
    void inferEnterprise_reportsWithNullEmission_handledGracefully() {
        Long enterpriseId = 4L;
        User user = new User();
        when(userRepository.findByIdAndDeletedFalse(enterpriseId)).thenReturn(Optional.of(user));

        CarbonReport reportWithNull = new CarbonReport();
        reportWithNull.setTotalEmission(null);
        reportWithNull.setStatus(1);
        reportWithNull.setCreatedAt(LocalDateTime.now());

        CarbonReport reportWithEmission = new CarbonReport();
        reportWithEmission.setTotalEmission(new BigDecimal("200.0"));
        reportWithEmission.setStatus(1);
        reportWithEmission.setCreatedAt(LocalDateTime.now());

        when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(List.of(reportWithNull, reportWithEmission));
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(Optional.empty());
        when(emissionRatingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.countByUserIdRelated(enterpriseId)).thenReturn(0L);

        EnterpriseInferenceResponse mlResponse = EnterpriseInferenceResponse.builder()
                .enterpriseId(enterpriseId)
                .complianceStatus("compliant")
                .confidence(0.85)
                .anomalyScore(0.1)
                .isAnomaly(false)
                .riskFactors(Collections.emptyList())
                .modelVersion("1.0.0")
                .build();
        when(mlServiceClient.inferEnterprise(any(EnterpriseInferenceRequest.class))).thenReturn(mlResponse);

        EnterpriseInferenceResponse response = service.inferEnterprise(enterpriseId);

        assertNotNull(response);
        assertEquals("compliant", response.getComplianceStatus());
    }

    @Test
    @DisplayName("Should handle reports with null createdAt")
    void inferEnterprise_reportsWithNullCreatedAt_handledGracefully() {
        Long enterpriseId = 5L;
        User user = new User();
        when(userRepository.findByIdAndDeletedFalse(enterpriseId)).thenReturn(Optional.of(user));

        CarbonReport reportWithNullDate = new CarbonReport();
        reportWithNullDate.setTotalEmission(new BigDecimal("100.0"));
        reportWithNullDate.setStatus(1);
        reportWithNullDate.setCreatedAt(null);

        CarbonReport reportWithDate = new CarbonReport();
        reportWithDate.setTotalEmission(new BigDecimal("200.0"));
        reportWithDate.setStatus(1);
        reportWithDate.setCreatedAt(LocalDateTime.now().minusDays(5));

        when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(List.of(reportWithNullDate, reportWithDate));
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(Optional.empty());
        when(emissionRatingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.countByUserIdRelated(enterpriseId)).thenReturn(0L);

        EnterpriseInferenceResponse mlResponse = EnterpriseInferenceResponse.builder()
                .enterpriseId(enterpriseId)
                .complianceStatus("compliant")
                .confidence(0.80)
                .anomalyScore(0.05)
                .isAnomaly(false)
                .riskFactors(Collections.emptyList())
                .modelVersion("1.0.0")
                .build();
        when(mlServiceClient.inferEnterprise(any(EnterpriseInferenceRequest.class))).thenReturn(mlResponse);

        EnterpriseInferenceResponse response = service.inferEnterprise(enterpriseId);

        assertNotNull(response);
        assertEquals("compliant", response.getComplianceStatus());
    }

    @Test
    @DisplayName("Should handle credit score with score value and emission rating with score")
    void inferEnterprise_withCreditScoreAndEmissionRating_usesValues() {
        Long enterpriseId = 6L;
        User user = new User();
        when(userRepository.findByIdAndDeletedFalse(enterpriseId)).thenReturn(Optional.of(user));

        CarbonReport report = new CarbonReport();
        report.setTotalEmission(new BigDecimal("500.0"));
        report.setStatus(1);
        report.setCreatedAt(LocalDateTime.now());
        when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(List.of(report));

        CreditScore creditScore = new CreditScore();
        creditScore.setScore(85);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(Optional.of(creditScore));

        EmissionRating emissionRating = new EmissionRating();
        emissionRating.setRatingScore(80);
        when(emissionRatingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId))
                .thenReturn(List.of(emissionRating));
        when(transactionRepository.countByUserIdRelated(enterpriseId)).thenReturn(10L);

        EnterpriseInferenceResponse mlResponse = EnterpriseInferenceResponse.builder()
                .enterpriseId(enterpriseId)
                .complianceStatus("compliant")
                .confidence(0.90)
                .anomalyScore(0.05)
                .isAnomaly(false)
                .riskFactors(Collections.emptyList())
                .modelVersion("1.0.0")
                .build();
        when(mlServiceClient.inferEnterprise(any(EnterpriseInferenceRequest.class))).thenReturn(mlResponse);

        EnterpriseInferenceResponse response = service.inferEnterprise(enterpriseId);

        assertNotNull(response);
        assertEquals("compliant", response.getComplianceStatus());
    }

    @Test
    @DisplayName("Should handle report with null status in compliance flag filter")
    void inferEnterprise_reportWithNullStatus_notCountedAsFlag() {
        Long enterpriseId = 7L;
        User user = new User();
        when(userRepository.findByIdAndDeletedFalse(enterpriseId)).thenReturn(Optional.of(user));

        CarbonReport reportWithNullStatus = new CarbonReport();
        reportWithNullStatus.setTotalEmission(new BigDecimal("100.0"));
        reportWithNullStatus.setStatus(null);
        reportWithNullStatus.setCreatedAt(LocalDateTime.now());

        CarbonReport approvedReport = new CarbonReport();
        approvedReport.setTotalEmission(new BigDecimal("200.0"));
        approvedReport.setStatus(3);
        approvedReport.setCreatedAt(LocalDateTime.now().minusDays(2));

        when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(List.of(reportWithNullStatus, approvedReport));
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId))
                .thenReturn(Optional.empty());
        when(emissionRatingRepository.findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.countByUserIdRelated(enterpriseId)).thenReturn(0L);

        EnterpriseInferenceResponse mlResponse = EnterpriseInferenceResponse.builder()
                .enterpriseId(enterpriseId)
                .complianceStatus("compliant")
                .confidence(0.85)
                .anomalyScore(0.1)
                .isAnomaly(false)
                .riskFactors(Collections.emptyList())
                .modelVersion("1.0.0")
                .build();
        when(mlServiceClient.inferEnterprise(any(EnterpriseInferenceRequest.class))).thenReturn(mlResponse);

        EnterpriseInferenceResponse response = service.inferEnterprise(enterpriseId);

        assertNotNull(response);
        assertEquals("compliant", response.getComplianceStatus());
    }
}