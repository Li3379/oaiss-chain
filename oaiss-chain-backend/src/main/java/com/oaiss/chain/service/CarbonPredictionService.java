package com.oaiss.chain.service;

import com.oaiss.chain.dto.CarbonPredictionRequest;
import com.oaiss.chain.dto.CarbonPredictionResponse;
import com.oaiss.chain.dto.EmissionForecastRequest;
import com.oaiss.chain.dto.EmissionForecastResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.service.ml.MlServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI碳排放预测服务
 * 基于历史碳报告数据，调用ML服务（Prophet回归模型）进行排放趋势预测
 * <p>
 * 设计文档要求（doc03）：
 * - AI驱动的碳排放趋势预测
 * - 预测可信度评分
 * <p>
 * 实现通过MlServiceClient调用Python ML服务中的Prophet时间序列回归
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonPredictionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_HISTORY_REPORTS = 12;
    private static final int MIN_DATA_POINTS = 2;

    private final CarbonReportRepository carbonReportRepository;
    private final MlServiceClient mlServiceClient;

    /**
     * 预测企业未来碳排放趋势
     * 聚合历史碳报告数据，调用ML服务Prophet回归模型进行预测
     *
     * @param request 预测请求（enterpriseId, predictMonths）
     * @return CarbonPredictionResponse with predictions, confidence, trend
     * @throws BusinessException if insufficient data or ML service fails
     */
    public CarbonPredictionResponse predict(CarbonPredictionRequest request) {
        Long enterpriseId = request.getEnterpriseId();
        log.info("Starting emission prediction for enterpriseId={}, predictMonths={}",
                enterpriseId, request.getPredictMonths());

        // 获取企业历史报告（最近N期）
        List<CarbonReport> reports = carbonReportRepository
                .findByEnterpriseIdAndDeletedFalse(enterpriseId, PageRequest.of(0, MAX_HISTORY_REPORTS))
                .getContent();

        if (reports.size() < MIN_DATA_POINTS) {
            log.warn("Insufficient historical data for enterpriseId={}: {} reports (need at least {})",
                    enterpriseId, reports.size(), MIN_DATA_POINTS);
            return CarbonPredictionResponse.builder()
                    .enterpriseId(enterpriseId)
                    .confidence(0.1)
                    .message("历史数据不足，至少需要2期碳报告")
                    .predictions(List.of())
                    .generatedAt(LocalDateTime.now())
                    .build();
        }

        EmissionForecastRequest mlRequest = buildMlRequest(request, reports);

        // 调用ML服务Prophet预测
        EmissionForecastResponse mlResponse = mlServiceClient.predictEmission(mlRequest);

        CarbonPredictionResponse response = mapToResponse(enterpriseId, mlResponse);

        log.info("Emission prediction completed for enterpriseId={}: trend={}, confidence={}, modelVersion={}",
                enterpriseId, mlResponse.getTrend(), mlResponse.getConfidence(), mlResponse.getModelVersion());

        return response;
    }

    /**
     * 构建ML服务请求
     * 将历史碳报告数据转换为EmissionForecastRequest格式
     */
    private EmissionForecastRequest buildMlRequest(CarbonPredictionRequest request, List<CarbonReport> reports) {
        List<String> dates = reports.stream()
                .map(report -> report.getCreatedAt().format(DATE_FORMATTER))
                .collect(Collectors.toList());

        List<Double> emissions = reports.stream()
                .map(report -> report.getTotalEmission() != null
                        ? report.getTotalEmission().doubleValue()
                        : 0.0)
                .collect(Collectors.toList());

        int horizonDays = request.getPredictMonths() != null
                ? request.getPredictMonths() * 30
                : 180;

        return EmissionForecastRequest.builder()
                .enterpriseId(request.getEnterpriseId())
                .dates(dates)
                .emissions(emissions)
                .horizonDays(horizonDays)
                .build();
    }

    /**
     * 将ML服务响应转换为业务响应
     * Prophet日度预测 → 月度汇总
     */
    private CarbonPredictionResponse mapToResponse(Long enterpriseId, EmissionForecastResponse mlResponse) {
        List<CarbonPredictionResponse.PredictionPoint> predictions = new ArrayList<>();

        List<String> forecastDates = mlResponse.getForecastDates();
        List<Double> forecastEmissions = mlResponse.getForecastEmissions();

        if (forecastDates != null && forecastEmissions != null && !forecastDates.isEmpty()) {
            // 按月分组汇总日度预测数据
            predictions = groupByMonth(forecastDates, forecastEmissions);
        }

        return CarbonPredictionResponse.builder()
                .enterpriseId(enterpriseId)
                .confidence(mlResponse.getConfidence())
                .message("Prophet预测模型 - " + mlResponse.getTrend())
                .predictions(predictions)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 将日度预测数据按月分组汇总
     * 每月取该月所有日度预测值的平均值作为月度预测
     */
    private List<CarbonPredictionResponse.PredictionPoint> groupByMonth(
            List<String> forecastDates, List<Double> forecastEmissions) {
        // 按年月分组计算平均排放
        var monthlyData = new java.util.LinkedHashMap<String, List<Double>>();

        for (int i = 0; i < forecastDates.size() && i < forecastEmissions.size(); i++) {
            String dateStr = forecastDates.get(i);
            // Extract YYYY-MM from YYYY-MM-DD
            String yearMonth = dateStr.substring(0, 7);
            monthlyData.computeIfAbsent(yearMonth, k -> new ArrayList<>())
                    .add(forecastEmissions.get(i));
        }

        List<CarbonPredictionResponse.PredictionPoint> predictions = new ArrayList<>();
        int monthIndex = 1;
        for (var entry : monthlyData.entrySet()) {
            double avg = entry.getValue().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            predictions.add(CarbonPredictionResponse.PredictionPoint.builder()
                    .period("M+" + monthIndex)
                    .predictedEmission(BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP))
                    .build());
            monthIndex++;
        }

        return predictions;
    }
}
