package com.oaiss.chain.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

    /**
     * 成交量预测：仅供需端点（/ai/market/supply-demand）填充。
     *
     * <p>历史实现里 ML 侧 XGBoost 算出的成交量被丢弃，
     * 三个端点全都返回价格曲线——"供需预测"名不副实。
     * 该字段让供需端点真正携带成交量；价格类端点保持空列表，
     * 前端据此区分两种语义，而不是拿价格冒充供需。</p>
     */
    @Builder.Default
    @SuppressFBWarnings(
            value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
            justification = "响应 DTO：内部列表由本对象独占构造，序列化后即丢弃，"
                    + "不做防御性拷贝是为避免每次预测都多复制一遍整条曲线。")
    private List<Double> forecastVolumes = List.of();

    /** 成交量单位，如 tCO2e；价格类端点为 null。 */
    private String forecastVolumeUnit;
}