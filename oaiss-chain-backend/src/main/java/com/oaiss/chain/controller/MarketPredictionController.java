package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.MarketForecastResponse;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.service.ml.MarketPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/**
 * ==============================================================================
 * 【业务模块】：19. 碳市场量化AI预测与宏观行情推演 (Carbon Market AI Forecasting)
 * 【服务角色】：企业用户 (ENTERPRISE)、系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - AI市场预测页面: views/enterprise/MarketPrediction.vue -> 路由: /enterprise/market-prediction
 * 【调用前端 API 模块】：src/api/marketPrediction.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/ai/market")
@RequiredArgsConstructor
@Validated
@Tag(name = "19. AI市场预测", description = "碳市场价格走势预测、供需预测、市场趋势分析")
public class MarketPredictionController {

    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final int MAX_HORIZON_DAYS = 365;

    private final MarketPredictionService marketPredictionService;

    /**
     * 【业务功能】：基于 Prophet / 时间序列算法预测未来周期碳市场整体交易活跃度与大盘走势
     * 【对应前端页面】：views/enterprise/MarketPrediction.vue (/enterprise/market-prediction)
     * 【对应前端API】：api/marketPrediction.ts -> getMarketTrend()
     * 【可见UI操作】：在“市场行情趋势预测”面板选择预测周期（7/30/90/180天），图表自动更新
     */
    @AuditLog(module = "AI智能", action = "PREDICTTREND", description = "AI智能 PREDICTTREND")
    @PostMapping("/trend")
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    @RateLimit(key = "market_trend", limit = 10, period = 60)
    @Operation(
            summary = "Predict market trend",
            description = "Generate carbon market trend forecast using Prophet time-series model"
    )
    public ResponseEntity<ApiResponse<MarketForecastResponse>> predictTrend(
            @RequestParam(defaultValue = "30")
            @Min(1) @Max(MAX_HORIZON_DAYS) Integer horizonDays) {
        MarketForecastResponse result = executeForecast(
                () -> marketPredictionService.predictMarketTrend(horizonDays));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 【业务功能】：基于 XGBoost 回归模型预测未来碳配额成交均价走势与置信区间波动带
     * 【对应前端页面】：views/enterprise/MarketPrediction.vue (/enterprise/market-prediction)
     * 【对应前端API】：api/marketPrediction.ts -> getMarketPrice()
     * 【可见UI操作】：在“配额价格预测”面板查看未来 30 天预测价格曲线与高低价格区间
     */
    @AuditLog(module = "AI智能", action = "PREDICTPRICE", description = "AI智能 PREDICTPRICE")
    @PostMapping("/price")
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    @RateLimit(key = "market_price", limit = 10, period = 60)
    @Operation(
            summary = "Predict carbon price",
            description = "Generate carbon price forecast with confidence intervals"
    )
    public ResponseEntity<ApiResponse<MarketForecastResponse>> predictPrice(
            @RequestParam(defaultValue = "30")
            @Min(1) @Max(MAX_HORIZON_DAYS) Integer horizonDays) {
        MarketForecastResponse result = executeForecast(
                () -> marketPredictionService.predictCarbonPrice(horizonDays));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 【业务功能】：预测未来周期全市场碳配额买入需求与卖出供应的供需缺口平衡指数
     * 【对应前端页面】：views/enterprise/MarketPrediction.vue (/enterprise/market-prediction)
     * 【对应前端API】：api/marketPrediction.ts -> getSupplyDemand()
     * 【可见UI操作】：在“供需平衡分析”卡片查看配额缺口/盈余指数柱状图
     */
    @AuditLog(module = "AI智能", action = "PREDICTSUPPLYDEMAND", description = "AI智能 PREDICTSUPPLYDEMAND")
    @PostMapping("/supply-demand")
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    @RateLimit(key = "market_supply_demand", limit = 10, period = 60)
    @Operation(
            summary = "Predict supply and demand",
            description = "Generate supply/demand volume forecast using XGBoost"
    )
    public ResponseEntity<ApiResponse<MarketForecastResponse>> predictSupplyDemand(
            @RequestParam(defaultValue = "30")
            @Min(1) @Max(MAX_HORIZON_DAYS) Integer horizonDays) {
        MarketForecastResponse result = executeForecast(
                () -> marketPredictionService.predictSupplyDemand(horizonDays));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private MarketForecastResponse executeForecast(ForecastSupplier supplier) {
        try {
            return supplier.get();
        } catch (BusinessException ex) {
            if (Integer.valueOf(ErrorCode.INSUFFICIENT_DATA).equals(ex.getCode())) {
                return emptyForecast();
            }
            throw ex;
        }
    }

    private MarketForecastResponse emptyForecast() {
        return MarketForecastResponse.builder()
                .forecastDates(Collections.emptyList())
                .forecastPrices(Collections.emptyList())
                .lowerBound(Collections.emptyList())
                .upperBound(Collections.emptyList())
                .trend("insufficient-data")
                .modelVersion("N/A")
                .build();
    }

    @FunctionalInterface
    private interface ForecastSupplier {
        MarketForecastResponse get();
    }
}
