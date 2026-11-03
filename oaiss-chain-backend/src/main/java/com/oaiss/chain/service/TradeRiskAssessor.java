package com.oaiss.chain.service;

import com.oaiss.chain.config.TradeAuditProperties;
import com.oaiss.chain.entity.Transaction;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 成交风险分流器。
 *
 * <p>决定「这笔成交必须人工复核，还是可以自动放行」。撮合引擎每 30 秒出清一轮，
 * 全量送审会让工单以市场出清速度积压；而不设闸门则等于放弃风控。分流是两者之间
 * 唯一可执行的折中。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>理由全部收集</b>而非命中即返回：审核员需要看到全部风险维度才能判断，
 *       且多条理由同时出现本身就是更强信号（例如大额 + 价格异常 + 洗售）。</li>
 *   <li><b>缺失值一律从严</b>：无信誉记录按最低分处理、无历史均价则不判价格偏离。
 *       前者是「未知不等于安全」，后者是「冷启动不该让所有成交进人工」——
 *       两种缺失的方向相反，不能一刀切。</li>
 *   <li><b>纯函数</b>：不读库、不写库、无副作用。均价与信誉由调用方查好后传入，
 *       使分流规则可以脱离交易链路独立测试与调参。</li>
 * </ul>
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeRiskAssessor {

    public static final String REASON_LARGE_AMOUNT = "LARGE_AMOUNT";
    public static final String REASON_PRICE_DEVIATION = "PRICE_DEVIATION";
    public static final String REASON_LOW_CREDIT = "LOW_CREDIT";
    public static final String REASON_WASH_TRADING = "WASH_TRADING";

    private final TradeAuditProperties properties;

    /**
     * 暴露配置，供调用方读取参考价窗口等参数。
     *
     * <p>P2P 分流需要与拍卖撮合取同一窗口的均价作为偏离基准；
     * 把窗口天数写死在调用方会与配置漂移。</p>
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "properties 是 Spring 绑定的不可变配置代理，注入后只读；"
                    + "调用方需要与撮合取同一参考价窗口，拷贝反而会造成口径漂移。")
    public TradeAuditProperties getProperties() {
        return properties;
    }

    /**
     * 评估结果。
     *
     * @param requiresAudit 是否需要人工审核
     * @param reasons       命中的风险理由（空表示无风险）
     */
    public record Decision(boolean requiresAudit, List<String> reasons) {
    }

    /**
     * 评估一笔成交。
     *
     * @param trade              成交记录（取 quantity 与 unitPrice）
     * @param referencePrice     近 N 日均价；null 表示无历史参考，跳过价格偏离检查
     * @param buyerCreditScore   买方信誉分；null 表示无信誉记录，按最低分处理
     * @param sellerCreditScore  卖方信誉分
     * @param washTradingSuspect 该笔撮合此前是否触发过自成交/洗售拦截
     */
    public Decision assess(Transaction trade,
                           BigDecimal referencePrice,
                           Integer buyerCreditScore,
                           Integer sellerCreditScore,
                           boolean washTradingSuspect) {

        // 总开关关闭：紧急降级，一律放行
        if (!properties.isEnabled()) {
            return new Decision(false, List.of());
        }

        List<String> reasons = new ArrayList<>();

        // 1) 大额
        if (properties.isLargeAmountCheckEnabled()) {
            BigDecimal threshold = properties.getLargeAmountThreshold();
            BigDecimal quantity = trade.getQuantity();
            if (threshold != null && quantity != null && quantity.compareTo(threshold) >= 0) {
                reasons.add(REASON_LARGE_AMOUNT + "(" + quantity.toPlainString()
                        + ">=" + threshold.toPlainString() + ")");
            }
        }

        // 2) 价格偏离（严格大于阈值；无参考价则跳过）
        if (properties.isPriceDeviationCheckEnabled() && referencePrice != null
                && referencePrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal deviation = trade.getUnitPrice()
                    .subtract(referencePrice)
                    .abs()
                    .divide(referencePrice, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal limit = properties.getPriceDeviationPercent();
            if (limit != null && deviation.compareTo(limit) > 0) {
                reasons.add(REASON_PRICE_DEVIATION + "(" + deviation.toPlainString()
                        + "%>" + limit.toPlainString() + "%)");
            }
        }

        // 3) 低信誉（缺失视为最低分——未知不等于安全）
        if (properties.isLowCreditCheckEnabled()) {
            int min = properties.getMinCreditScore();
            Integer buyerScore = buyerCreditScore;
            Integer sellerScore = sellerCreditScore;

            if (buyerScore == null) {
                reasons.add(REASON_LOW_CREDIT + "(BUYER_UNSCORED)");
            } else if (buyerScore < min) {
                reasons.add(REASON_LOW_CREDIT + "(BUYER=" + buyerScore + "<" + min + ")");
            }

            if (sellerScore == null) {
                reasons.add(REASON_LOW_CREDIT + "(SELLER_UNSCORED)");
            } else if (sellerScore < min) {
                reasons.add(REASON_LOW_CREDIT + "(SELLER=" + sellerScore + "<" + min + ")");
            }
        }

        // 4) 洗售嫌疑
        if (properties.isWashTradingCheckEnabled() && washTradingSuspect) {
            reasons.add(REASON_WASH_TRADING);
        }

        boolean requiresAudit = !reasons.isEmpty();
        if (requiresAudit) {
            log.info("成交转人工审核: tradeId={}, reasons={}", trade.getId(), reasons);
        }
        return new Decision(requiresAudit, List.copyOf(reasons));
    }
}