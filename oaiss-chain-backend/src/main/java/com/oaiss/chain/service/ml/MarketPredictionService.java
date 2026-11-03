package com.oaiss.chain.service.ml;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.MarketForecastRequest;
import com.oaiss.chain.dto.MarketForecastResponse;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 市场预测服务：把历史**成交**数据聚合成日频序列后调用 ML 服务。
 *
 * <p><b>数据源为什么是 transaction 而不是 auction_order</b>：
 * 历史实现读 {@code auction_order} 的 60 条挂单，逐条当作一个数据点。这有两处错误——</p>
 * <ol>
 *   <li><b>挂单不是成交</b>：挂单价是「报价」而非「成交价」，用它预测行情等于拿意愿当事实。</li>
 *   <li><b>逐条不是日频</b>：同一天有 8 条挂单就产生 8 个「交易日」，时间轴被拉伸，
 *       Prophet 的季节性/趋势都失去意义。</li>
 * </ol>
 *
 * <p>更致命的是 {@code auction_order} 实测 **0 行**（挂了也走旧路径），
 * 于是三个预测端点全部落到 {@code INSUFFICIENT_DATA}——页面上永远是空态。
 * 换成已成交交易并按日聚合后，只要有真实成交就有真实曲线。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPredictionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 最少需要的**聚合后日数**。低于此值无法形成有意义的时间序列。 */
    private static final int MIN_DATA_POINTS = 2;

    /** 取最近多少笔成交参与聚合。 */
    private static final int RECENT_TRADE_LIMIT = 200;

    private final MlServiceClient mlServiceClient;
    private final TransactionRepository transactionRepository;

    /**
     * 预测市场趋势（价格维度）。
     */
    public MarketForecastResponse predictMarketTrend(int horizonDays) {
        MarketForecastRequest request = buildDailyForecastRequest(horizonDays, "market trend");

        MarketForecastResponse response = mlServiceClient.predictMarketTrend(request);

        log.info("Market trend prediction result: trend={}, horizon={}days, modelVersion={}",
                response.getTrend(), horizonDays, response.getModelVersion());

        return response;
    }

    /**
     * 预测碳价（含置信区间）。
     */
    public MarketForecastResponse predictCarbonPrice(int horizonDays) {
        MarketForecastRequest request = buildDailyForecastRequest(horizonDays, "carbon price");

        MarketForecastResponse response = mlServiceClient.predictCarbonPrice(request);

        log.info("Carbon price prediction result: trend={}, horizon={}days",
                response.getTrend(), horizonDays);

        return response;
    }

    /**
     * 预测供需（成交量维度）。
     *
     * <p>此前三个端点共用同一个 {@code market_service.predict()}，
     * 「供需预测」实际返回的是价格预测——名字与结果不符。
     * 现在成交量列取自真实成交量的日汇总，ML 侧也会走成交量的预测分支。</p>
     */
    public MarketForecastResponse predictSupplyDemand(int horizonDays) {
        MarketForecastRequest request = buildDailyForecastRequest(horizonDays, "supply/demand");

        MarketForecastResponse response = mlServiceClient.predictSupplyDemand(request);

        log.info("Supply/demand prediction result: trend={}, horizon={}days",
                response.getTrend(), horizonDays);

        return response;
    }

    /**
     * 取最近成交，按**自然日**聚合成等间隔时间序列。
     *
     * <p>聚合规则：</p>
     * <ul>
     *   <li>价格 → 当日成交量加权均价（无成交量的日子不产生数据点）</li>
     *   <li>成交量 → 当日成交总量</li>
     *   <li>日期 → 升序（Prophet 要求时间单调递增；倒序会让趋势方向被反转）</li>
     * </ul>
     *
     * @param purpose 仅用于日志，便于定位是哪个端点在报数据不足
     * @throws BusinessException 聚合后的日数不足 {@link #MIN_DATA_POINTS}
     */
    private MarketForecastRequest buildDailyForecastRequest(int horizonDays, String purpose) {
        List<Transaction> trades = transactionRepository
                .findRecentCompletedForForecast(PageRequest.of(0, RECENT_TRADE_LIMIT));

        if (trades.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_DATA,
                    "No completed trades available for " + purpose
                            + " forecast: at least " + MIN_DATA_POINTS + " distinct trading days required");
        }

        // TreeMap 保证按日期升序输出，且天然把同一天的成交聚到同一桶
        Map<LocalDate, List<Transaction>> byDay = new TreeMap<>();
        for (Transaction t : trades) {
            if (t.getCompletedAt() == null) {
                continue;
            }
            byDay.computeIfAbsent(t.getCompletedAt().toLocalDate(), d -> new ArrayList<>()).add(t);
        }

        if (byDay.size() < MIN_DATA_POINTS) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_DATA,
                    String.format("Insufficient trading days for %s forecast: got %d, need %d",
                            purpose, byDay.size(), MIN_DATA_POINTS));
        }

        List<String> dates = new ArrayList<>(byDay.size());
        List<Double> prices = new ArrayList<>(byDay.size());
        List<Double> volumes = new ArrayList<>(byDay.size());

        for (Map.Entry<LocalDate, List<Transaction>> entry : byDay.entrySet()) {
            dates.add(entry.getKey().format(DATE_FORMATTER));

            BigDecimal dayVolume = BigDecimal.ZERO;
            BigDecimal dayAmount = BigDecimal.ZERO;
            for (Transaction t : entry.getValue()) {
                BigDecimal qty = t.getQuantity() == null ? BigDecimal.ZERO : t.getQuantity();
                BigDecimal amount = t.getTotalAmount();
                if (amount == null && t.getUnitPrice() != null) {
                    amount = t.getUnitPrice().multiply(qty);
                }
                dayVolume = dayVolume.add(qty);
                dayAmount = dayAmount.add(amount == null ? BigDecimal.ZERO : amount);
            }

            // 成交量加权均价；若当日总量为 0（异常数据），退化为 0 而非抛错，
            // 让 ML 侧按「零成交量日」处理，不要因为一天脏数据让整条曲线不可用。
            BigDecimal avgPrice = dayVolume.compareTo(BigDecimal.ZERO) > 0
                    ? dayAmount.divide(dayVolume, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            prices.add(avgPrice.doubleValue());
            volumes.add(dayVolume.doubleValue());
        }

        if (log.isInfoEnabled()) {
            log.info("Forecast request built for {}: {} trades over {} trading days ({} .. {})",
                    purpose, trades.size(), byDay.size(), dates.get(0), dates.get(dates.size() - 1));
        }

        return MarketForecastRequest.builder()
                .dates(dates)
                .prices(prices)
                .volumes(volumes)
                .horizonDays(horizonDays)
                .build();
    }
}