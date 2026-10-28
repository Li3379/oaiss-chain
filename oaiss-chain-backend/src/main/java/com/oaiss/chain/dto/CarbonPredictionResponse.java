package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 碳排放预测响应
 *
 * @author OAISS Team
 */
@Data
@Builder
public class CarbonPredictionResponse {

    private Long enterpriseId;

    /**
     * 预测可信度（0-1）
     */
    private Double confidence;

    private String message;

    private List<PredictionPoint> predictions;

    private LocalDateTime generatedAt;

    @Data
    @Builder
    public static class PredictionPoint {
        /**
         * 预测期（M+1, M+2, ...）
         */
        private String period;

        /**
         * 预测碳排放量（吨CO2当量）
         */
        private BigDecimal predictedEmission;
    }
}
