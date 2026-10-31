package com.oaiss.chain.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 成交审核配置。
 *
 * <p>所有阈值走配置而非硬编码，原因有三：
 * <ol>
 *   <li>分流规则天然是业务口径，不同市场阶段阈值不同，写死在代码里必须改代码才能调</li>
 *   <li>演示与验收需要能现场调参触发/关闭某条规则</li>
 *   <li>阈值不落地就没有「为什么这笔被拦」的可解释依据</li>
 * </ol>
 *
 * <p>配置前缀 {@code oaiss.trade.audit}，示例见 application.yml。
 *
 * @author OAISS Team
 */
@Component
@ConfigurationProperties(prefix = "oaiss.trade.audit")
@Getter
@Setter
public class TradeAuditProperties {

    /** 成交审核总开关。关闭后所有成交自动放行（紧急降级用） */
    private boolean enabled = true;

    /** 大额检查开关 */
    private boolean largeAmountCheckEnabled = true;

    /** 单笔成交数量达到该值（吨）即转人工 */
    private BigDecimal largeAmountThreshold = new BigDecimal("1000");

    /** 价格偏离检查开关 */
    private boolean priceDeviationCheckEnabled = true;

    /** 成交价偏离近 30 日均价超过该百分比（严格大于）即转人工 */
    private BigDecimal priceDeviationPercent = new BigDecimal("20");

    /** 低信誉检查开关 */
    private boolean lowCreditCheckEnabled = true;

    /** 任一交易方信誉分低于该值即转人工（与信用闸门阈值保持一致） */
    private int minCreditScore = 40;

    /** 洗售嫌疑检查开关 */
    private boolean washTradingCheckEnabled = true;

    /** 单笔额度上限（吨）。0 表示不限 */
    private BigDecimal singleTradeLimit = BigDecimal.ZERO;

    /** 自然月累计成交上限（吨）。0 表示不限 */
    private BigDecimal monthlyLimit = BigDecimal.ZERO;

    /** 价格参考窗口天数（计算 30 日均价） */
    private int priceReferenceDays = 30;
}