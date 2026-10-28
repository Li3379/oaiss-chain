package com.oaiss.chain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Enterprise inference response DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
public class EnterpriseInferenceResponse {

    @JsonAlias("enterprise_id")
    private Long enterpriseId;

    @JsonAlias("compliance_status")
    private String complianceStatus;

    private Double confidence;

    @JsonAlias("anomaly_score")
    private Double anomalyScore;

    @JsonAlias("is_anomaly")
    private Boolean isAnomaly;

    @JsonAlias("risk_factors")
    @Builder.Default
    private List<String> riskFactors = List.of();

    @JsonAlias("model_version")
    @Builder.Default
    private String modelVersion = "1.0.0";
}
