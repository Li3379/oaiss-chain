package com.oaiss.chain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核算周期的年度提取（AC-22）。
 *
 * <p><b>问题</b>：年度评级按「核算周期的前 4 个字符」取年份
 * （`accountingPeriod.substring(0, 4)`）。存量数据里出现过 `2024-01-15` 这类
 * 形态，也允许出现 `Q4-2026`。后者会被截成 `Q4-2`，静默写进年度评级表——
 * 不报错、不告警，只是那一年的评级从此再也算不进去。
 *
 * <p>静默算错比明确报错危险：报错会有人来修，算错没人发现。
 * 故改为正则校验，畸形值退回「当前年度」并留日志。
 */
class AccountingPeriodYearTest {

    @Test
    @DisplayName("标准形式：2026-Q1 → 2026")
    void extractsFromQuarterForm() {
        assertEquals("2026", CarbonService.extractYear("2026-Q1"));
    }

    @Test
    @DisplayName("年度形式：2026 → 2026")
    void extractsFromAnnualForm() {
        assertEquals("2026", CarbonService.extractYear("2026"));
    }

    @Test
    @DisplayName("存量形态：2024-01-15 → 2024（前四位是合法年份）")
    void extractsFromLegacyDateForm() {
        assertEquals("2024", CarbonService.extractYear("2024-01-15"));
    }

    @Test
    @DisplayName("倒置形态 Q4-2026 → 不截成 Q4-2，退回当年")
    void rejectsInvertedForm() {
        String year = CarbonService.extractYear("Q4-2026");
        assertTrue(year.matches("\\d{4}"),
                "必须返回四位年份，不能是 'Q4-2' 这种截断值；实际: " + year);
        assertEquals(String.valueOf(java.time.LocalDateTime.now().getYear()), year,
                "无法解析时退回当前年度");
    }

    @Test
    @DisplayName("空值 / 空白 / 纯文字 → 退回当年")
    void rejectsUnparsable() {
        int current = java.time.LocalDateTime.now().getYear();
        assertEquals(String.valueOf(current), CarbonService.extractYear(null));
        assertEquals(String.valueOf(current), CarbonService.extractYear(""));
        assertEquals(String.valueOf(current), CarbonService.extractYear("   "));
        assertEquals(String.valueOf(current), CarbonService.extractYear("年度报告"));
    }

    @Test
    @DisplayName("越界年份不采纳（1900 之前 / 2200 之后）")
    void rejectsOutOfRange() {
        int current = java.time.LocalDateTime.now().getYear();
        assertEquals(String.valueOf(current), CarbonService.extractYear("1000-Q1"));
        assertEquals(String.valueOf(current), CarbonService.extractYear("9999-Q1"));
    }
}