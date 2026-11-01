package com.oaiss.chain.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易限价配置。
 *
 * <p>实现原始需求明确要求、但此前从未落地的约束：
 * 「碳币定价有系统限价约束」（raw 段落 1346）、
 * 「交易的碳币数量是否符合平台限价规定」（raw 段落 1054）。
 *
 * <p><b>与 {@link TradeAuditProperties} 的分工</b>（两者都涉及价格偏离，不可混淆）：
 * <ul>
 *   <li>{@code audit.price-deviation-percent}：偏离即**转人工审核**，交易仍会成交</li>
 *   <li>{@code price-limit.max-deviation-percent}：偏离即**硬拒绝**，交易根本建不了</li>
 * </ul>
 * 硬拒绝阈值必须 **≤** 转人工阈值，否则会出现「能成交但要人工审」与
 * 「压根不让下单」并存的自相矛盾。生产默认值因此取 20（与审核侧一致）而非更宽的 30。
 *
 * <p><b>口径分界</b>：{@code effectiveFrom} 之前创建的成交是按「元/吨」口径录入的，
 * 不参与本限价。类型刻意用 {@link LocalDateTime} 与 {@code BaseEntity.createdAt} 保持一致，
 * 避免 {@code LocalDateTime.isBefore(Instant)} 这类类型不兼容。
 *
 * @author OAISS Team
 */
@Component
@ConfigurationProperties(prefix = "oaiss.trade.price-limit")
@Getter
@Setter
public class PriceLimitProperties {

    /** 单价下限（碳币/吨）。null 表示不检查下限 */
    private BigDecimal min = new BigDecimal("1");

    /** 单价上限（碳币/吨）。null 表示不检查上限 */
    private BigDecimal max = new BigDecimal("500");

    /**
     * 允许偏离近 N 日均价的最大百分比。null 表示不检查偏离。
     *
     * <p>必须 ≤ {@code oaiss.trade.audit.price-deviation-percent}，
     * 否则硬限价与转人工两条线会打架。
     */
    private BigDecimal maxDeviationPercent = new BigDecimal("20");

    /** 均价回看窗口天数 */
    private int referenceDays = 30;

    /**
     * 口径分界时刻。早于此时间创建的成交按历史口径（元/吨）处理，不做限价。
     *
     * <p><b>正式上线前必须改为真实切换时刻</b>；当前值仅为开发期占位。
     */
    private LocalDateTime effectiveFrom = LocalDateTime.of(2026, 11, 1, 0, 0);
}