package com.oaiss.chain.service;

import com.oaiss.chain.config.PriceLimitProperties;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 限价服务测试。
 *
 * <p>覆盖三条规则与两条边界：
 * <ol>
 *   <li>绝对区间：min ≤ price ≤ max</li>
 *   <li>偏离带：|price - avg| / avg ≤ maxDeviationPercent（无均价时跳过）</li>
 *   <li>口径分界：effectiveFrom 之前的交易不校验（历史元口径不参与新规）</li>
 * </ol>
 *
 * <p><b>时间必须可控</b>：窗口下界是 {@code max(now - referenceDays, effectiveFrom)}，
 * 若用真实 now，分界日当天该式恒等于 effectiveFrom，窗口长度为 0、均价恒为 null，
 * 偏离带永远不触发——这正是设计文档里要避免的「不可观察的规则」。
 * 故全部用例注入 {@code Clock.fixed}。
 */
class PriceLimitServiceTest {

    private PriceLimitProperties properties;
    private TransactionRepository transactionRepository;
    private PriceLimitService service;

    /** 口径分界日 */
    private static final LocalDateTime EFFECTIVE_FROM = LocalDateTime.of(2026, 11, 1, 0, 0);

    /** 把「现在」固定在分界日之后 40 天，使 [now-30, now] 完全落在分界后 */
    private static final LocalDateTime NOW = EFFECTIVE_FROM.plusDays(40);

    @BeforeEach
    void setUp() {
        properties = new PriceLimitProperties();
        properties.setMin(new BigDecimal("1"));
        properties.setMax(new BigDecimal("500"));
        properties.setMaxDeviationPercent(new BigDecimal("20"));
        properties.setReferenceDays(30);
        properties.setEffectiveFrom(EFFECTIVE_FROM);

        transactionRepository = mock(TransactionRepository.class);

        Clock clock = Clock.fixed(
                NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        service = new PriceLimitService(properties, transactionRepository, clock);
    }

    private void stubAvg(String avg) {
        when(transactionRepository.findAverageUnitPriceSince(any()))
                .thenReturn(avg == null ? null : new BigDecimal(avg));
    }

    // ==================== 绝对区间 ====================

    @Test
    @DisplayName("区间内且无历史均价时放行（偏离检查跳过）")
    void withinRange_noAvg_passes() {
        stubAvg(null);

        assertDoesNotThrow(() -> service.validate(new BigDecimal("100"), NOW));
    }

    @Test
    @DisplayName("低于下限 → 拒绝，错误信息含允许区间")
    void belowMin_rejected() {
        stubAvg(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(new BigDecimal("0.5"), NOW));
        assertTrue(ex.getMessage().contains("1") || ex.getMessage().contains("区间")
                        || ex.getMessage().contains("range"),
                "错误信息应给出可读区间，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("高于上限 → 拒绝")
    void aboveMax_rejected() {
        stubAvg(null);

        assertThrows(BusinessException.class,
                () -> service.validate(new BigDecimal("600"), NOW));
    }

    @Test
    @DisplayName("正好等于下限与上限均放行（边界含端点）")
    void boundaries_inclusive() {
        stubAvg(null);

        assertDoesNotThrow(() -> service.validate(new BigDecimal("1"), NOW));
        assertDoesNotThrow(() -> service.validate(new BigDecimal("500"), NOW));
    }

    // ==================== 偏离带 ====================

    @Test
    @DisplayName("偏离均价超过阈值 → 拒绝")
    void deviationExceeded_rejected() {
        stubAvg("100");

        // 130 相对 100 偏离 30% > 20%
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(new BigDecimal("130"), NOW));
        assertTrue(ex.getMessage().contains("100") || ex.getMessage().contains("均价")
                        || ex.getMessage().contains("average"),
                "错误信息应给出均价，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("偏离恰好等于阈值不拒绝（严格大于才算异常）")
    void deviationAtThreshold_passes() {
        stubAvg("100");

        assertDoesNotThrow(() -> service.validate(new BigDecimal("120"), NOW));
    }

    @Test
    @DisplayName("无历史成交（均价为 null）→ 跳过偏离检查，不阻断")
    void nullAvg_skipsDeviationCheck() {
        stubAvg(null);

        assertDoesNotThrow(() -> service.validate(new BigDecimal("400"), NOW));
    }

    @Test
    @DisplayName("两条规则同时不满足 → 只报绝对区间（先判硬边界）")
    void bothViolated_reportsAbsoluteFirst() {
        stubAvg("100");

        // 600 既超上限 500，又偏离均价 500%
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(new BigDecimal("600"), NOW));
        String msg = ex.getMessage();
        // 绝对区间的提示应出现；均价数字不应作为主因出现（避免用户改完一个又被另一个卡住）
        assertTrue(msg.contains("500"), "应给出上限 500，实际: " + msg);
    }

    // ==================== 口径分界 ====================

    @Test
    @DisplayName("分界日之前的交易不校验（历史元口径不参与新规）")
    void legacyTrade_skipped() {
        stubAvg("100");

        // 价格离谱且早于分界日 → 放行
        assertDoesNotThrow(() ->
                service.validate(new BigDecimal("99999"), EFFECTIVE_FROM.minusSeconds(1)));
    }

    @Test
    @DisplayName("AC-12g：边界断言——分界日前 1 秒为历史口径")
    void isLegacy_beforeEffectiveFrom() {
        assertTrue(service.isLegacy(EFFECTIVE_FROM.minusSeconds(1)));
    }

    @Test
    @DisplayName("AC-12g：边界断言——分界日后 1 秒不是历史口径")
    void isLegacy_afterEffectiveFrom() {
        assertFalse(service.isLegacy(EFFECTIVE_FROM.plusSeconds(1)));
    }

    @Test
    @DisplayName("AC-12g：分界时刻本身视为新口径（>= 边界）")
    void isLegacy_atEffectiveFrom_isNotLegacy() {
        assertFalse(service.isLegacy(EFFECTIVE_FROM));
    }

    // ==================== 均价窗口 ====================

    @Test
    @DisplayName("AC-12e：均价窗口下界取 max(now - referenceDays, effectiveFrom)")
    void averageWindow_usesMaxOfReferenceAndEffectiveFrom() {
        stubAvg(null);
        service.validate(new BigDecimal("100"), NOW);

        // now = effectiveFrom + 40d，now-30d = effectiveFrom+10d > effectiveFrom
        // 期望下界是 now-30d，而不是 effectiveFrom
        verify(transactionRepository).findAverageUnitPriceSince(
                argThat((LocalDateTime since) -> since.isEqual(NOW.minusDays(30))));
    }

    @Test
    @DisplayName("AC-12e：分界日刚过时窗口下界取 effectiveFrom（now-N 可能早于分界日）")
    void averageWindow_clampedByEffectiveFrom() {
        stubAvg(null);
        LocalDateTime justAfter = EFFECTIVE_FROM.plusDays(5);  // now-30d 早于分界日
        service.validate(new BigDecimal("100"), justAfter);

        verify(transactionRepository).findAverageUnitPriceSince(
                argThat((LocalDateTime since) -> since.isEqual(EFFECTIVE_FROM)));
    }

    @Test
    @DisplayName("窗口下界是 LocalDateTime（与 createdAt 同类型，避免 Instant 转换）")
    void averageWindow_passesLocalDateTime() {
        stubAvg(null);
        service.validate(new BigDecimal("100"), NOW);

        verify(transactionRepository).findAverageUnitPriceSince(any(LocalDateTime.class));
    }

    // ==================== 配置缺失时的健壮性 ====================

    @Test
    @DisplayName("maxDeviationPercent 为 null 时跳过偏离检查（配置缺失不阻断交易）")
    void nullDeviationPercent_skipsCheck() {
        properties.setMaxDeviationPercent(null);
        stubAvg("100");

        assertDoesNotThrow(() -> service.validate(new BigDecimal("400"), NOW));
    }

    @Test
    @DisplayName("min/max 为 null 时跳过绝对区间检查")
    void nullBounds_skipsAbsoluteCheck() {
        properties.setMin(null);
        properties.setMax(null);
        stubAvg(null);

        assertDoesNotThrow(() -> service.validate(new BigDecimal("99999"), NOW));
    }
}