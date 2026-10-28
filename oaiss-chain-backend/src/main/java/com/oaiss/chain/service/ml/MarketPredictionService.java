package com.oaiss.chain.service.ml;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.MarketForecastRequest;
import com.oaiss.chain.dto.MarketForecastResponse;
import com.oaiss.chain.entity.AuctionOrder;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.AuctionOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Market prediction service that aggregates historical trade data
 * and calls the ML service for carbon market trend forecasting.
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPredictionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MIN_DATA_POINTS = 10;

    private final MlServiceClient mlServiceClient;
    private final AuctionOrderRepository auctionOrderRepository;

    /**
     * Predict market trend by aggregating recent auction order data
     * and calling the ML service for forecasting.
     *
     * @param horizonDays number of days to forecast into the future
     * @return MarketForecastResponse with forecast prices, confidence intervals, and trend
     * @throws BusinessException if insufficient historical data is available
     */
    public MarketForecastResponse predictMarketTrend(int horizonDays) {
        List<AuctionOrder> recentOrders = auctionOrderRepository
                .findTop60ByDeletedFalseOrderByCreatedAtDesc();

        if (recentOrders.size() < MIN_DATA_POINTS) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_DATA,
                    "Need at least 10 historical trade records for market prediction");
        }

        MarketForecastRequest request = buildForecastRequest(recentOrders, horizonDays);

        MarketForecastResponse response = mlServiceClient.predictMarketTrend(request);

        log.info("Market trend prediction result: trend={}, horizon={}days, modelVersion={}",
                response.getTrend(), horizonDays, response.getModelVersion());

        return response;
    }

    /**
     * Predict carbon price with confidence intervals.
     *
     * @param horizonDays number of days to forecast
     * @return MarketForecastResponse with price forecasts and confidence intervals
     */
    public MarketForecastResponse predictCarbonPrice(int horizonDays) {
        List<AuctionOrder> recentOrders = auctionOrderRepository
                .findTop60ByDeletedFalseOrderByCreatedAtDesc();

        if (recentOrders.size() < MIN_DATA_POINTS) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_DATA,
                    "Need at least 10 historical trade records for market prediction");
        }

        MarketForecastRequest request = buildForecastRequest(recentOrders, horizonDays);

        MarketForecastResponse response = mlServiceClient.predictCarbonPrice(request);

        log.info("Carbon price prediction result: trend={}, horizon={}days",
                response.getTrend(), horizonDays);

        return response;
    }

    /**
     * Predict supply and demand volume trends.
     *
     * @param horizonDays number of days to forecast
     * @return MarketForecastResponse with volume trend forecasts
     */
    public MarketForecastResponse predictSupplyDemand(int horizonDays) {
        List<AuctionOrder> recentOrders = auctionOrderRepository
                .findTop60ByDeletedFalseOrderByCreatedAtDesc();

        if (recentOrders.size() < MIN_DATA_POINTS) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_DATA,
                    "Need at least 10 historical trade records for market prediction");
        }

        MarketForecastRequest request = buildForecastRequest(recentOrders, horizonDays);

        MarketForecastResponse response = mlServiceClient.predictSupplyDemand(request);

        log.info("Supply/demand prediction result: trend={}, horizon={}days",
                response.getTrend(), horizonDays);

        return response;
    }

    private MarketForecastRequest buildForecastRequest(
            List<AuctionOrder> orders, int horizonDays) {
        List<String> dates = orders.stream()
                .map(order -> order.getCreatedAt().format(DATE_FORMATTER))
                .collect(Collectors.toList());

        List<Double> prices = orders.stream()
                .map(order -> order.getPrice().doubleValue())
                .collect(Collectors.toList());

        List<Double> volumes = orders.stream()
                .map(order -> order.getQuantity().doubleValue())
                .collect(Collectors.toList());

        return MarketForecastRequest.builder()
                .dates(dates)
                .prices(prices)
                .volumes(volumes)
                .horizonDays(horizonDays)
                .build();
    }
}