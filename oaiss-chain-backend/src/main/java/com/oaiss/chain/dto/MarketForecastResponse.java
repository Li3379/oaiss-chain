package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Market forecast response DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
public class MarketForecastResponse {

    private List<String> forecastDates;
    private List<Double> forecastPrices;
    private List<Double> lowerBound;
    private List<Double> upperBound;
    private String trend;
    @Builder.Default
    private String modelVersion = "1.0.0";
}