package com.oaiss.chain.service.ml;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enterprise inference service that aggregates enterprise data from the
 * database and delegates compliance risk assessment to the ML service.
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseInferenceService {

    private final MlServiceClient mlServiceClient;
    private final UserRepository userRepository;
    private final CarbonReportRepository carbonReportRepository;
    private final CreditScoreRepository creditScoreRepository;
    private final EmissionRatingRepository emissionRatingRepository;
    private final TransactionRepository transactionRepository;

    /** CarbonReport status: 4 = rejected (审核拒绝) */
    private static final int REPORT_STATUS_REJECTED = 4;

    /**
     * Run enterprise compliance inference by aggregating enterprise data
     * from the database and calling the ML service.
     *
     * @param enterpriseId the enterprise user ID
     * @return inference response from the ML service
     */
    public EnterpriseInferenceResponse inferEnterprise(Long enterpriseId) {
        // Validate enterprise exists
        User enterprise = userRepository.findByIdAndDeletedFalse(enterpriseId)
                .orElseThrow(() -> new BusinessException(
                        4001, "Enterprise not found: " + enterpriseId));

        // Query carbon reports
        List<CarbonReport> reports = carbonReportRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId);

        if (reports.isEmpty()) {
            throw new BusinessException(
                    3004, "No carbon reports found for enterprise: " + enterpriseId);
        }

        // Compute features from reports
        int reportCount = reports.size();
        double totalEmissions = reports.stream()
                .map(CarbonReport::getTotalEmission)
                .filter(e -> e != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
        double avgEmissionPerReport = totalEmissions / reportCount;
        long daysSinceLastReport = reports.stream()
                .map(CarbonReport::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(last -> ChronoUnit.DAYS.between(last, LocalDateTime.now()))
                .orElse(365L);

        // Credit score from CreditScore entity (default 50 if not found)
        double creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                .map(CreditScore::getScore)
                .map(Integer::doubleValue)
                .orElse(50.0);

        // Emission rating: use latest EmissionRating ratingScore / 20 as proxy
        double emissionRating = emissionRatingRepository
                .findByEnterpriseIdAndDeletedFalseOrderByRatingYearDesc(enterpriseId)
                .stream()
                .findFirst()
                .map(EmissionRating::getRatingScore)
                .map(Integer::doubleValue)
                .map(score -> score / 20.0)
                .orElse(3.0);

        // Transaction volume: count transactions where enterprise is buyer or seller
        long transactionVolume = transactionRepository.countByUserIdRelated(enterpriseId);

        // Compliance flags: count reports with REJECTED status
        int complianceFlags = (int) reports.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == REPORT_STATUS_REJECTED)
                .count();

        // Build ML request
        EnterpriseInferenceRequest mlRequest = EnterpriseInferenceRequest.builder()
                .enterpriseId(enterpriseId)
                .reportCount(reportCount)
                .totalEmissions(totalEmissions)
                .creditScore(creditScore)
                .emissionRating(emissionRating)
                .transactionVolume((double) transactionVolume)
                .complianceFlags(complianceFlags)
                .avgEmissionPerReport(avgEmissionPerReport)
                .daysSinceLastReport((int) daysSinceLastReport)
                .build();

        log.info("Enterprise inference request built for id={}, reportCount={}, totalEmissions={}",
                enterpriseId, reportCount, totalEmissions);

        EnterpriseInferenceResponse response = mlServiceClient.inferEnterprise(mlRequest);

        log.info("Enterprise inference result: id={}, status={}, confidence={}, anomaly={}",
                response.getEnterpriseId(), response.getComplianceStatus(),
                response.getConfidence(), response.getIsAnomaly());

        return response;
    }
}