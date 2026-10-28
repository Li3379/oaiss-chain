package com.oaiss.chain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Emission forecast response DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EmissionForecastResponse {

    private Long enterpriseId;
    private List<String> forecastDates;
    private List<Double> forecastEmissions;
    private List<Double> lowerBound;
    private List<Double> upperBound;
    private String trend;
    private Double confidence;
    @Builder.Default
    private String modelVersion = "1.0.0";
}