package com.oaiss.chain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Market forecast request DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MarketForecastRequest {

    @NotEmpty(message = "Historical dates must not be empty")
    private List<String> dates;

    @NotEmpty(message = "Historical prices must not be empty")
    private List<Double> prices;

    @NotEmpty(message = "Historical volumes must not be empty")
    private List<Double> volumes;

    @NotNull(message = "Horizon days must not be null")
    @Builder.Default
    private Integer horizonDays = 30;
}