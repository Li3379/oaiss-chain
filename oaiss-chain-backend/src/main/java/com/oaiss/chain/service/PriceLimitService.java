package com.oaiss.chain.service;

import com.oaiss.chain.config.PriceLimitProperties;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 交易限价校验服务。
 *
 * <p>把原始需求里「平台限价」这条从未实现的约束集中到一处，供 4 个写入口共用：
 * {@code POST /trade/p2p}、{@code POST /trade/auction}、
 * {@code POST /auction/buy}、{@code POST /auction/sell}。
 *
 * <p><b>校验时点语义</b>：限价是**下单时刻**的约束，不是市场熔断。
 * 配置改严不会追溯已挂出的订单，但撮合出清时会再做一次复核（见
 * {@code DoubleAuctionService.executeMatching}）。
 *
 * <p><b>为什么不用 AOP 注解</b>：两个请求 DTO 的字段名不同
 * （{@code TradeRequest.unitPrice} vs {@code AuctionOrderRequest.price}），
 * 注解必须携带 SpEL 字符串，写错不编译报错；且项目已有的
 * {@code @RateLimit}/{@code @AuditLog} 都不涉及字段路径解析。
 * 显式调用 + 源码门禁比再加一个"按配置读字段"的注解更可控。
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceLimitService {

    private final PriceLimitProperties properties;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    /**
     * 判断某时刻创建的成交是否属于「历史口径」（口径分界之前）。
     *
     * <p>历史成交按元/吨录入，不参与碳币/吨的限价规则。
     */
    public boolean isLegacy(LocalDateTime tradeCreatedAt) {
        LocalDateTime effectiveFrom = properties.getEffectiveFrom();
        if (effectiveFrom == null || tradeCreatedAt == null) {
            return false;
        }
        return tradeCreatedAt.isBefore(effectiveFrom);
    }

    /**
     * 校验单价是否合规。不合规即抛 {@link BusinessException}，阻断交易创建。
     *
     * <p>两条规则按「先硬边界、后偏离带」的顺序判定：绝对区间更刚性、更好解释，
     * 同时不满足时只报绝对区间，并在提示里附带两个区间供参考——
     * 避免用户改完第一个又被第二个卡住。
     *
     * @param unitPrice      单价（碳币/吨）
     * @param tradeCreatedAt 该交易的创建时刻；早于口径分界则直接放行
     */
    public void validate(BigDecimal unitPrice, LocalDateTime tradeCreatedAt) {
        if (unitPrice == null) {
            return;
        }
        // 历史口径不参与新规
        if (isLegacy(tradeCreatedAt)) {
            return;
        }

        BigDecimal min = properties.getMin();
        BigDecimal max = properties.getMax();
        boolean hasAbsoluteBound = (min != null && unitPrice.compareTo(min) < 0)
                || (max != null && unitPrice.compareTo(max) > 0);

        if (hasAbsoluteBound) {
            // 同时附上偏离带信息，但不把它当作拒绝主因
            String deviationHint = buildDeviationHint(unitPrice, tradeCreatedAt);
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    String.format("单价 %s 碳币/吨 超出平台限价区间 [%s, %s]%s",
                            unitPrice.toPlainString(),
                            min == null ? "不限" : min.toPlainString(),
                            max == null ? "不限" : max.toPlainString(),
                            deviationHint));
        }

        // 绝对区间通过后，再判偏离带
        BigDecimal deviationLimit = properties.getMaxDeviationPercent();
        if (deviationLimit == null) {
            return;
        }
        BigDecimal avg = queryAverage(tradeCreatedAt);
        if (avg == null || avg.compareTo(BigDecimal.ZERO) <= 0) {
            // 无历史成交：跳过偏离检查。冷启动阶段不该让所有交易都建不了
            return;
        }

        BigDecimal deviationPercent = unitPrice.subtract(avg)
                .abs()
                .divide(avg, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        if (deviationPercent.compareTo(deviationLimit) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    String.format("单价 %s 碳币/吨 偏离近 %d 日均价 %s 达 %s%%，超过允许的 ±%s%%",
                            unitPrice.toPlainString(),
                            properties.getReferenceDays(),
                            avg.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                            deviationPercent.toPlainString(),
                            deviationLimit.toPlainString()));
        }
    }

    /**
     * 均价查询窗口下界：{@code max(now - referenceDays, effectiveFrom)}。
     *
     * <p>下界取 effectiveFrom 是为了**杜绝跨口径污染**：分界日之前的成交价是元/吨，
     * 若混入均价，会让新口径的价格基准失真。
     */
    LocalDateTime averageWindowStart(LocalDateTime tradeCreatedAt) {
        LocalDateTime base = tradeCreatedAt != null ? tradeCreatedAt : LocalDateTime.now(clock);
        LocalDateTime byReferenceDays = base.minusDays(properties.getReferenceDays());
        LocalDateTime effectiveFrom = properties.getEffectiveFrom();
        if (effectiveFrom != null && byReferenceDays.isBefore(effectiveFrom)) {
            return effectiveFrom;
        }
        return byReferenceDays;
    }

    // ==================== 内部方法 ====================

    private BigDecimal queryAverage(LocalDateTime tradeCreatedAt) {
        return transactionRepository.findAverageUnitPriceSince(
                averageWindowStart(tradeCreatedAt));
    }

    /** 绝对区间拒绝时，顺带给出偏离带信息，便于用户一次改到位 */
    private String buildDeviationHint(BigDecimal unitPrice, LocalDateTime tradeCreatedAt) {
        BigDecimal deviationLimit = properties.getMaxDeviationPercent();
        if (deviationLimit == null) {
            return "";
        }
        BigDecimal avg = queryAverage(tradeCreatedAt);
        if (avg == null || avg.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return String.format("（参考：近 %d 日均价 %s，允许偏离 ±%s%%）",
                properties.getReferenceDays(),
                avg.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                deviationLimit.toPlainString());
    }
}