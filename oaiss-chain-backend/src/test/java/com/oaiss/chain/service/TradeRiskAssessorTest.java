package com.oaiss.chain.service;

import com.oaiss.chain.config.TradeAuditProperties;
import com.oaiss.chain.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S2 接缝：成交风险分流器。
 *
 * <p>存在理由：撮合引擎每 30 秒自动出清一轮（{@code AuctionMatchingScheduler}），
 * 若把每笔成交都送人工审核，工单会以市场出清速度积压。分流器决定
 * 「哪些成交必须人工复核、哪些可以自动放行」——它是整个审核功能能否落地的闸门。
 *
 * <p>四条触发规则（任一命中即转人工，全部走配置可调）：
 * <ol>
 *   <li>大额：成交数量 ≥ 单笔阈值</li>
 *   <li>价格异常：成交价偏离近 30 日均价超过设定百分比</li>
 *   <li>低信誉方：任一交易方信誉分低于阈值</li>
 *   <li>洗售嫌疑：该笔撮合此前触发过自成交拦截</li>
 * </ol>
 *
 * <p>会漏掉的：规则之间长期叠加后的边界漂移、以及规则本身的业务合理性——
 * 后者需靠配置调参与监管线索侧的共同验证。
 */
class TradeRiskAssessorTest {

    private TradeAuditProperties properties;
    private TradeRiskAssessor assessor;

    @BeforeEach
    void setUp() {
        properties = new TradeAuditProperties();
        properties.setEnabled(true);
        properties.setLargeAmountThreshold(new BigDecimal("1000"));   // 吨
        properties.setPriceDeviationPercent(new BigDecimal("20"));    // %
        properties.setMinCreditScore(40);                              // 与信用闸门阈值一致
        assessor = new TradeRiskAssessor(properties);
    }

    private Transaction trade(String quantity, String price) {
        Transaction t = new Transaction();
        t.setId(1L);
        t.setQuantity(new BigDecimal(quantity));
        t.setUnitPrice(new BigDecimal(price));
        return t;
    }

    @Test
    @DisplayName("四项指标均在阈值内时自动放行")
    void normalTrade_isAutoApproved() {
        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("100", "60"), new BigDecimal("58"), 80, 85, false);

        assertFalse(decision.requiresAudit(), "正常小额、价格贴合、信誉良好应自动放行");
        assertTrue(decision.reasons().isEmpty(), "放行时不应产生风险理由");
    }

    @Test
    @DisplayName("大额成交命中数量阈值 → 转人工")
    void largeAmount_requiresAudit() {
        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("1500", "60"), new BigDecimal("58"), 80, 85, false);

        assertTrue(decision.requiresAudit());
        assertTrue(decision.reasons().stream().anyMatch(r -> r.startsWith("LARGE_AMOUNT")),
                "应标记 LARGE_AMOUNT，实际: " + decision.reasons());
    }

    @Test
    @DisplayName("数量阈值边界：等于阈值要转人工，略低于则不")
    void largeAmount_boundary() {
        assertTrue(assessor.assess(trade("1000", "60"), new BigDecimal("58"), 80, 85, false)
                .requiresAudit(), "等于阈值应触发（避免边界擦线）");

        assertFalse(assessor.assess(trade("999.99", "60"), new BigDecimal("58"), 80, 85, false)
                .requiresAudit(), "低于阈值不应触发");
    }

    @Test
    @DisplayName("成交价偏离 30 日均价超过阈值 → 转人工")
    void priceDeviation_requiresAudit() {
        // 均价 100，成交价 130 → 偏离 30% > 20%
        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("100", "130"), new BigDecimal("100"), 80, 85, false);

        assertTrue(decision.requiresAudit());
        assertTrue(decision.reasons().stream().anyMatch(r -> r.startsWith("PRICE_DEVIATION")));
    }

    @Test
    @DisplayName("价格偏离恰好等于阈值不触发（严格大于才算异常）")
    void priceDeviation_boundary() {
        // 均价 100，成交价 120 → 偏离 20%，等于阈值，不触发
        assertFalse(assessor.assess(trade("100", "120"), new BigDecimal("100"), 80, 85, false)
                .requiresAudit());
    }

    @Test
    @DisplayName("无历史均价时不判价格异常（避免冷启动把所有成交都转人工）")
    void noReferencePrice_skipsDeviationCheck() {
        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("100", "999"), null, 80, 85, false);

        assertFalse(decision.requiresAudit());
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    @DisplayName("任一方信誉低于阈值 → 转人工")
    void lowCredit_requiresAudit() {
        TradeRiskAssessor.Decision buyerLow = assessor.assess(
                trade("100", "60"), new BigDecimal("58"), 39, 90, false);
        assertTrue(buyerLow.requiresAudit());
        assertTrue(buyerLow.reasons().stream().anyMatch(r -> r.startsWith("LOW_CREDIT")));

        TradeRiskAssessor.Decision sellerLow = assessor.assess(
                trade("100", "60"), new BigDecimal("58"), 90, 39, false);
        assertTrue(sellerLow.requiresAudit(), "卖方低信誉同样应触发");
    }

    @Test
    @DisplayName("信誉缺失（null）按最低分处理，不静默放行")
    void missingCredit_isTreatedAsUnscored() {
        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("100", "60"), new BigDecimal("58"), null, 90, false);

        assertTrue(decision.requiresAudit(), "无信誉记录不应等同于高信誉");
    }

    @Test
    @DisplayName("洗售嫌疑 → 无条件转人工")
    void washTradingSuspicion_requiresAudit() {
        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("100", "60"), new BigDecimal("58"), 90, 90, true);

        assertTrue(decision.requiresAudit());
        assertTrue(decision.reasons().stream().anyMatch(r -> r.startsWith("WASH_TRADING")));
    }

    @Test
    @DisplayName("多规则同时命中时，理由全部收集（不被第一条短路）")
    void multipleRules_allReasonsCollected() {
        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("5000", "200"), new BigDecimal("100"), 10, 10, true);

        assertTrue(decision.requiresAudit());

        // 四条规则维度全部命中；LOW_CREDIT 因买卖双方各低信誉而记两条
        // （审核员需要知道是谁出的问题，合并成一条会丢失方向信息）
        List<String> reasons = decision.reasons();
        assertEquals(5, reasons.size(), "理由条数不符，实际: " + reasons);
        assertTrue(reasons.stream().anyMatch(r -> r.startsWith("LARGE_AMOUNT")));
        assertTrue(reasons.stream().anyMatch(r -> r.startsWith("PRICE_DEVIATION")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("BUYER=10")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("SELLER=10")));
        assertTrue(reasons.stream().anyMatch(r -> r.startsWith("WASH_TRADING")));
    }

    @Test
    @DisplayName("总开关关闭时一律放行（便于紧急降级）")
    void disabledGate_performsNoAudit() {
        properties.setEnabled(false);

        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("99999", "9999"), new BigDecimal("1"), 0, 0, true);

        assertFalse(decision.requiresAudit(), "总开关关闭时不应拦截任何成交");
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    @DisplayName("四条规则可单独关闭，关闭后不产生该理由")
    void individualRuleSwitch() {
        properties.setLargeAmountCheckEnabled(false);

        TradeRiskAssessor.Decision decision = assessor.assess(
                trade("9999", "60"), new BigDecimal("58"), 90, 90, false);

        assertFalse(decision.requiresAudit(), "大额检查关闭后不应因数量转人工");
    }
}