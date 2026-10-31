package com.oaiss.chain.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.exception.CarbonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class EmissionDataValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("validateAndParse - 正常结构化数组核算载荷计算正确")
    void testStructuredArrayPayload() {
        String json = """
                {
                  "scope1": [
                    { "name": "燃煤锅炉", "activity_data": 100.0, "emission_factor": 2.5 }
                  ],
                  "scope2": [
                    { "name": "购入电网电力", "activity_data": 200.0, "emission_factor": 0.5 }
                  ],
                  "scope3": [
                    { "name": "商务差旅", "activity_data": 10.0, "emission_factor": 1.2 }
                  ]
                }
                """;

        BigDecimal[] totals = EmissionDataValidator.validateAndParse(json, objectMapper);

        assertNotNull(totals);
        assertEquals(4, totals.length);
        assertEquals(new BigDecimal("250.00"), totals[0].setScale(2)); // scope1: 100 * 2.5 = 250
        assertEquals(new BigDecimal("100.00"), totals[1].setScale(2)); // scope2: 200 * 0.5 = 100
        assertEquals(new BigDecimal("12.00"), totals[2].setScale(2));  // scope3: 10 * 1.2 = 12
        assertEquals(new BigDecimal("362.00"), totals[3].setScale(2)); // total: 362
    }

    @Test
    @DisplayName("validateAndParse - 标量简写核算载荷计算正确")
    void testScalarShorthandPayload() {
        String json = """
                {
                  "scope1": 150.5,
                  "scope2": 80.25,
                  "scope3": 10.0,
                  "totalEmission": 240.75
                }
                """;

        BigDecimal[] totals = EmissionDataValidator.validateAndParse(json, objectMapper);

        assertNotNull(totals);
        assertEquals(new BigDecimal("150.50"), totals[0].setScale(2));
        assertEquals(new BigDecimal("80.25"), totals[1].setScale(2));
        assertEquals(new BigDecimal("10.00"), totals[2].setScale(2));
        assertEquals(new BigDecimal("240.75"), totals[3].setScale(2));
    }

    @Test
    @DisplayName("validateAndParse - 拦截标量负数排放漏洞（漏洞报告Case 1: scope1=-100.5, scope2=-50.0）")
    void testRejectsNegativeScalarEmissions() {
        String json = "{\"scope1\": -100.5, \"scope2\": -50.0}";

        CarbonException ex = assertThrows(CarbonException.class, () ->
                EmissionDataValidator.validateAndParse(json, objectMapper));

        assertTrue(ex.getMessage().contains("不能为负数"));
    }

    @Test
    @DisplayName("validateAndParse - 拦截负数字符串绕过（漏洞报告Case 2: 负数字符串）")
    void testRejectsNegativeStringEmissions() {
        String json = "{\"scope1\": \"-100.5\", \"scope2\": 50.0}";

        CarbonException ex = assertThrows(CarbonException.class, () ->
                EmissionDataValidator.validateAndParse(json, objectMapper));

        assertTrue(ex.getMessage().contains("不能为负数"));
    }

    @Test
    @DisplayName("validateAndParse - 拦截嵌套数组中的负数活动水平数据")
    void testRejectsNegativeActivityDataInArray() {
        String json = """
                {
                  "scope1": [
                    { "name": "燃煤", "activity_data": -50.0, "emission_factor": 2.0 }
                  ]
                }
                """;

        CarbonException ex = assertThrows(CarbonException.class, () ->
                EmissionDataValidator.validateAndParse(json, objectMapper));

        assertTrue(ex.getMessage().contains("不能为负数"));
    }

    @Test
    @DisplayName("validateAndParse - 拦截嵌套数组中的负数排放因子")
    void testRejectsNegativeEmissionFactorInArray() {
        String json = """
                {
                  "scope1": [
                    { "name": "燃煤", "activity_data": 50.0, "emission_factor": -1.5 }
                  ]
                }
                """;

        CarbonException ex = assertThrows(CarbonException.class, () ->
                EmissionDataValidator.validateAndParse(json, objectMapper));

        assertTrue(ex.getMessage().contains("不能为负数"));
    }

    @Test
    @DisplayName("validateAndParse - 拦截负数总排放量字段")
    void testRejectsNegativeTotalEmission() {
        String json = "{\"scope1\": 100.0, \"totalEmission\": -200.0}";

        CarbonException ex = assertThrows(CarbonException.class, () ->
                EmissionDataValidator.validateAndParse(json, objectMapper));

        assertTrue(ex.getMessage().contains("不能为负数"));
    }

    @Test
    @DisplayName("validateAndParse - 空载荷或空对象返回零值")
    void testEmptyAndBlankPayload() {
        BigDecimal[] zero1 = EmissionDataValidator.validateAndParse(null, objectMapper);
        assertEquals(BigDecimal.ZERO, zero1[3]);

        BigDecimal[] zero2 = EmissionDataValidator.validateAndParse("   ", objectMapper);
        assertEquals(BigDecimal.ZERO, zero2[3]);

        BigDecimal[] zero3 = EmissionDataValidator.validateAndParse("{}", objectMapper);
        assertEquals(BigDecimal.ZERO, zero3[3]);
    }


    /**
     * C-06：非对象根 / 畸形 JSON 必须报错，不得静默返回全 0。
     *
     * <p>历史行为是静默归零，于是「报告创建成功、totalEmission=0」这种最危险的组合
     * 无人察觉——真实排放被记成 0，后续评级/配额/交易全部失真。</p>
     */
    @Test
    @DisplayName("C-06: 畸形载荷抛错而非静默归零")
    void testMalformedPayloadThrowsInsteadOfSilentZero() {
        // 纯文本
        assertThrows(CarbonException.class,
                () -> EmissionDataValidator.validateAndParse("not-a-json", objectMapper),
                "纯文本应抛错");
        // 标量根
        assertThrows(CarbonException.class,
                () -> EmissionDataValidator.validateAndParse("123", objectMapper),
                "标量根应抛错");
        // 非空数组根（结构错误，不是「无数据」的表达）
        assertThrows(CarbonException.class,
                () -> EmissionDataValidator.validateAndParse("[1,2,3]", objectMapper),
                "非空数组根应抛错");
    }

    @Test
    @DisplayName("C-06b: 空数组与 JSON null 仍视为合法的零排放")
    void testEmptyArrayAndNullStillZero() {
        BigDecimal[] emptyArr = EmissionDataValidator.validateAndParse("[]", objectMapper);
        assertEquals(BigDecimal.ZERO, emptyArr[3]);

        BigDecimal[] jsonNull = EmissionDataValidator.validateAndParse("null", objectMapper);
        assertEquals(BigDecimal.ZERO, jsonNull[3]);
    }
}
