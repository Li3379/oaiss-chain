package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.CoalOxidationRateRequest;
import com.oaiss.chain.dto.PowerGenerationCalculationRequest;
import com.oaiss.chain.dto.PowerGenerationCalculationResponse;
import com.oaiss.chain.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 发电企业碳排放公式计算服务测试（GB/T 32150-2015）
 *
 * @author OAISS Team
 */
@ExtendWith(MockitoExtension.class)
class PowerGenerationFormulaServiceTest {

    /**
     * 显式构造而非 @InjectMocks：服务新增了 {@link UnitConversionService} 依赖，
     * Mockito 的 @InjectMocks 对**无 mock 声明的真实协作者**会注入 null，
     * 导致所有用例 NPE。这里直接 new 出真实换算服务（它是纯函数式，无外部依赖）。
     */
    private PowerGenerationFormulaService service;

    private PowerGenerationCalculationRequest baseRequest;

    @BeforeEach
    void setUp() {
        service = new PowerGenerationFormulaService(new UnitConversionService());
        baseRequest = new PowerGenerationCalculationRequest();
        baseRequest.setReportingYear(2024);
        baseRequest.setEnterpriseName("测试发电企业");
        // All fuel params left null (treated as zero)
    }

    @Test
    @DisplayName("所有燃料参数为零时，总排放量应为0")
    void calculate_allZeros_returnsZeroEmission() {
        // All fuel params are null → emission = 0

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(0, response.getTotalEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getCombustionEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getDesulfurizationEmission().compareTo(BigDecimal.ZERO));
        assertTrue(response.getFuelDetails().isEmpty());
        assertEquals("2024", response.getReportingYear());
        assertEquals("测试发电企业", response.getEnterpriseName());
        assertEquals("GB/T 32150-2015", response.getFormulaReference());
    }

    /**
     * 已知值计算（<b>国标口径</b>）。
     *
     * <p>历史版本此处用 {@code NCV=20}（GJ/t）、{@code CC=0.025}（tC/GJ）并断言
     * 179.6667——那个期望值把「后端按 GJ/t 直乘、UI 让用户按 kJ/kg 填」的口径分裂
     * 锁死成了「正确」，从而使 10^6 级缺陷无法被测试捕获（缺陷 C-01 / C-10）。</p>
     *
     * <p>现统一按 GB/T 32150 表 1 口径录入：NCV=20000 kJ/kg、CC=25.0 tC/TJ。</p>
     */
    @Test
    @DisplayName("已知值计算: 原煤 FC=100, NCV=20000 kJ/kg, CC=25.0 tC/TJ, OF=0.98")
    void calculate_knownValues_correctCombustionEmission() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        baseRequest.setRawCoalNcv(new BigDecimal("20000"));   // kJ/kg
        baseRequest.setRawCoalCc(new BigDecimal("25.0"));     // tC/TJ
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        // 国标式：FC(t) × NCV(kJ/kg) × 10^-6 × CC(tC/TJ) × OF × 44/12
        // = 100 × 20000 × 1e-6 × 25.0 × 0.98 × 44/12 = 179.6667
        BigDecimal expected = new BigDecimal("179.6667");

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(0, response.getCombustionEmission().compareTo(expected),
                "燃烧排放应为 179.6667，实际 " + response.getCombustionEmission());
        assertEquals(0, response.getTotalEmission().compareTo(expected));
        assertEquals(1, response.getFuelDetails().size());
        assertEquals("原煤", response.getFuelDetails().get(0).getFuelType());
    }

    @Test
    @DisplayName("含脱硫参数时，总排放 = 燃烧排放 + 脱硫排放")
    void calculate_withDesulfurization_totalIsSumOfBoth() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        baseRequest.setCarbonateConsumed(new BigDecimal("10"));
        baseRequest.setDesulfEmissionFactor(new BigDecimal("0.44"));
        baseRequest.setDesulfConversionRate(new BigDecimal("1.0"));

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        // Desulfurization: 10 × 0.44 × 1.0 = 4.4000
        BigDecimal expectedDesulf = new BigDecimal("10")
                .multiply(new BigDecimal("0.44"))
                .multiply(new BigDecimal("1.0"))
                .setScale(4, RoundingMode.HALF_UP);
        assertEquals(0, response.getDesulfurizationEmission().compareTo(expectedDesulf));
        assertEquals(0, response.getTotalEmission().compareTo(
                response.getCombustionEmission().add(response.getDesulfurizationEmission())));
    }

    @Test
    @DisplayName("负燃料消耗量应抛出BusinessException")
    void calculate_negativeFc_throwsBusinessException() {
        baseRequest.setRawCoalFc(new BigDecimal("-5"));
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
    }

    @Test
    @DisplayName("碳氧化率大于1应抛出BusinessException")
    void calculate_ofGreaterThanOne_throwsBusinessException() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(new BigDecimal("1.5"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
    }

    @Test
    @DisplayName("null燃料参数视为零，不报错")
    void calculate_nullFuelParams_treatedAsZero() {
        // rawCoalFc is null, but NCV/CC/OF are set — should treat as zero emission
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(0, response.getCombustionEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getTotalEmission().compareTo(BigDecimal.ZERO));
        assertTrue(response.getFuelDetails().isEmpty());
    }

    @Test
    @DisplayName("FC已填写但NCV/CC/OF为null时应抛出BusinessException")
    void calculate_fcSetButOtherParamsNull_throwsBusinessException() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        // NCV, CC, OF left null

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    @DisplayName("脱硫转化率大于1应抛出BusinessException")
    void calculate_desulfConversionRateGreaterThanOne_throwsBusinessException() {
        baseRequest.setDesulfConversionRate(new BigDecimal("1.5"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("脱硫转化率"));
    }

    @Test
    @DisplayName("脱硫参数部分为null时不计算脱硫排放")
    void calculate_partialDesulfParams_zeroDesulfEmission() {
        baseRequest.setCarbonateConsumed(new BigDecimal("10"));
        baseRequest.setDesulfEmissionFactor(null); // null → desulf = 0
        baseRequest.setDesulfConversionRate(new BigDecimal("0.5"));

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(0, response.getDesulfurizationEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("脱硫参数部分为零时不计算脱硫排放")
    void calculate_zeroDesulfParams_zeroDesulfEmission() {
        baseRequest.setCarbonateConsumed(BigDecimal.ZERO);
        baseRequest.setDesulfEmissionFactor(new BigDecimal("0.44"));
        baseRequest.setDesulfConversionRate(new BigDecimal("0.5"));

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(0, response.getDesulfurizationEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("多种燃料同时计算正确汇总")
    void calculate_multipleFuels_correctTotal() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        baseRequest.setCleanedCoalFc(new BigDecimal("50"));
        baseRequest.setCleanedCoalNcv(new BigDecimal("25"));
        baseRequest.setCleanedCoalCc(new BigDecimal("0.030"));
        baseRequest.setCleanedCoalOf(new BigDecimal("0.95"));

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(2, response.getFuelDetails().size());
        assertTrue(response.getTotalEmission().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("FC为零时不添加到燃料详情列表")
    void calculate_zeroFc_notInFuelDetails() {
        baseRequest.setRawCoalFc(BigDecimal.ZERO);
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertTrue(response.getFuelDetails().isEmpty());
    }

    @Test
    @DisplayName("null脱硫转化率不抛异常")
    void calculate_nullDesulfConversionRate_noException() {
        baseRequest.setDesulfConversionRate(null);

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertNotNull(response);
        assertEquals(0, response.getDesulfurizationEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("负数洗精煤消耗量应抛出BusinessException")
    void calculate_negativeCleanedCoalFc_throwsBusinessException() {
        baseRequest.setCleanedCoalFc(new BigDecimal("-5"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("洗精煤"));
    }

    @Test
    @DisplayName("洗精煤碳氧化率大于1应抛出BusinessException")
    void calculate_cleanedCoalOfGreaterThanOne_throwsBusinessException() {
        baseRequest.setCleanedCoalFc(new BigDecimal("100"));
        baseRequest.setCleanedCoalOf(new BigDecimal("2.0"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("洗精煤"));
    }

    @Test
    @DisplayName("其他洗煤负消耗量应抛出BusinessException")
    void calculate_negativeOtherWashedCoalFc_throwsBusinessException() {
        baseRequest.setOtherWashedCoalFc(new BigDecimal("-10"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("其他洗煤"));
    }

    @Test
    @DisplayName("型煤碳氧化率大于1应抛出BusinessException")
    void calculate_briquetteOfGreaterThanOne_throwsBusinessException() {
        baseRequest.setBriquetteFc(new BigDecimal("100"));
        baseRequest.setBriquetteOf(new BigDecimal("2.0"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("型煤"));
    }

    @Test
    @DisplayName("其他煤负消耗量应抛出BusinessException")
    void calculate_negativeOtherCoalFc_throwsBusinessException() {
        baseRequest.setOtherCoalFc(new BigDecimal("-5"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("其他煤"));
    }

    @Test
    @DisplayName("其他煤碳氧化率大于1应抛出BusinessException")
    void calculate_otherCoalOfGreaterThanOne_throwsBusinessException() {
        baseRequest.setOtherCoalFc(new BigDecimal("100"));
        baseRequest.setOtherCoalOf(new BigDecimal("2.0"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("其他煤"));
    }

    @Test
    @DisplayName("型煤负消耗量应抛出BusinessException")
    void calculate_negativeBriquetteFc_throwsBusinessException() {
        baseRequest.setBriquetteFc(new BigDecimal("-5"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("型煤"));
    }

    @Test
    @DisplayName("其他洗煤碳氧化率大于1应抛出BusinessException")
    void calculate_otherWashedCoalOfGreaterThanOne_throwsBusinessException() {
        baseRequest.setOtherWashedCoalFc(new BigDecimal("100"));
        baseRequest.setOtherWashedCoalOf(new BigDecimal("2.0"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
        assertTrue(exception.getMessage().contains("其他洗煤"));
    }

    @Test
    @DisplayName("FC已填写但NCV为null时应抛出BusinessException")
    void calculate_fcSetButNcvNull_throwsBusinessException() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        baseRequest.setRawCoalNcv(null);
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    @DisplayName("FC已填写但CC为null时应抛出BusinessException")
    void calculate_fcSetButCcNull_throwsBusinessException() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(null);
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    @DisplayName("FC已填写但OF为null时应抛出BusinessException")
    void calculate_fcSetButOfNull_throwsBusinessException() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.calculate(baseRequest));
        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    @DisplayName("脱硫排放因子为零时不计算脱硫排放")
    void calculate_desulfEmissionFactorZero_zeroDesulfEmission() {
        baseRequest.setCarbonateConsumed(new BigDecimal("10"));
        baseRequest.setDesulfEmissionFactor(BigDecimal.ZERO);
        baseRequest.setDesulfConversionRate(new BigDecimal("0.5"));

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(0, response.getDesulfurizationEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("脱硫转化率为零时不计算脱硫排放")
    void calculate_desulfConversionRateZero_zeroDesulfEmission() {
        baseRequest.setCarbonateConsumed(new BigDecimal("10"));
        baseRequest.setDesulfEmissionFactor(new BigDecimal("0.44"));
        baseRequest.setDesulfConversionRate(BigDecimal.ZERO);

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(0, response.getDesulfurizationEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("燃煤碳氧化率实测反推计算-正常工况")
    void testCalculateOxidationRateFromAsh_normal() {
        CoalOxidationRateRequest req = CoalOxidationRateRequest.builder()
                .slagWeight(new BigDecimal("100.0"))
                .slagCarbonContent(new BigDecimal("0.02"))
                .flyAshWeight(new BigDecimal("800.0"))
                .flyAshCarbonContent(new BigDecimal("0.03"))
                .dustRemovalEfficiency(new BigDecimal("0.99"))
                .coalConsumption(new BigDecimal("10000.0"))
                .netCalorificValue(new BigDecimal("20000.0"))
                .carbonContent(new BigDecimal("26.0"))
                .build();

        BigDecimal of = service.calculateOxidationRateFromAsh(req);
        assertNotNull(of);
        assertTrue(of.compareTo(new BigDecimal("0.90")) > 0);
        assertTrue(of.compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    @DisplayName("燃煤碳氧化率实测反推计算-除尘效率默认100%")
    void testCalculateOxidationRateFromAsh_defaultEfficiency() {
        CoalOxidationRateRequest req = CoalOxidationRateRequest.builder()
                .slagWeight(new BigDecimal("50.0"))
                .slagCarbonContent(new BigDecimal("0.01"))
                .flyAshWeight(new BigDecimal("400.0"))
                .flyAshCarbonContent(new BigDecimal("0.02"))
                .dustRemovalEfficiency(null)
                .coalConsumption(new BigDecimal("5000.0"))
                .netCalorificValue(new BigDecimal("22000.0"))
                .carbonContent(new BigDecimal("25.0"))
                .build();

        BigDecimal of = service.calculateOxidationRateFromAsh(req);
        assertNotNull(of);
        assertTrue(of.compareTo(new BigDecimal("0.95")) > 0);
    }

    // ==================== 单位口径与公式完整性（契约层回归） ====================

    /**
     * C-01：国标单位（kJ/kg + tC/TJ）输入必须得到正确量级的结果。
     *
     * 历史缺陷：后端无 1e-6 换算而 UI 提示按 kJ/kg 填，导致同输入两端差 10^6。
     * 现约定：请求入参一律国标口径，服务内部换算为 GJ/t + tC/GJ 参与计算。
     *
     * 期望值推导（国标式）：FC × NCV × 10^-6 × CC × OF × 44/12
     * = 10000 × 20908 × 1e-6 × 26.3 × 0.98 × 44/12 ≈ 19759.0360
     */
    @Test
    @DisplayName("C-01: 国标单位输入 → 燃烧排放 ≈ 19759.04 tCO2")
    void testStandardUnitInputProducesExpectedEmission() {
        PowerGenerationCalculationRequest req = new PowerGenerationCalculationRequest();
        req.setReportingYear(2024);
        req.setEnterpriseName("单位一致性测试");
        req.setRawCoalFc(new BigDecimal("10000"));
        req.setRawCoalNcv(new BigDecimal("20908"));   // kJ/kg（国标口径）
        req.setRawCoalCc(new BigDecimal("26.3"));     // tC/TJ（国标口径）
        req.setRawCoalOf(new BigDecimal("0.98"));

        PowerGenerationCalculationResponse resp = service.calculate(req);

        BigDecimal actual = resp.getCombustionEmission();
        BigDecimal expected = new BigDecimal("19759.0360");
        BigDecimal tolerance = expected.multiply(new BigDecimal("0.0001")); // ±0.01%
        BigDecimal diff = actual.subtract(expected).abs();
        assertTrue(diff.compareTo(tolerance) <= 0,
                "燃烧排放应约为 " + expected + "（±0.01%），实际 " + actual + "，差 " + diff);
    }

    /**
     * C-03：净购入电力项必须计入总量。
     *
     * 历史缺陷：总量只算「燃烧 + 脱硫」，国标式第三项 AD电 × EF电 整项缺失，
     * 而类注释与接口文档都宣称含该项。
     */
    @Test
    @DisplayName("C-03: 净购入电力计入总量，且分量可单独读到")
    void testPurchasedElectricityIncludedInTotal() {
        PowerGenerationCalculationRequest req = new PowerGenerationCalculationRequest();
        req.setReportingYear(2024);
        req.setEnterpriseName("净购电测试");
        req.setRawCoalFc(new BigDecimal("10000"));
        req.setRawCoalNcv(new BigDecimal("20908"));
        req.setRawCoalCc(new BigDecimal("26.3"));
        req.setRawCoalOf(new BigDecimal("0.98"));
        // 净购入 5000 MWh × 0.5810 tCO2/MWh = 2905.0000
        req.setPurchasedElectricity(new BigDecimal("5000"));
        req.setElectricityEmissionFactor(new BigDecimal("0.5810"));

        PowerGenerationCalculationResponse resp = service.calculate(req);

        BigDecimal purchased = resp.getPurchasedElectricityEmission();
        assertNotNull(purchased, "响应必须暴露净购入电力排放分量");
        assertEquals(0, purchased.compareTo(new BigDecimal("2905.0000")),
                "净购电排放应为 5000 × 0.5810 = 2905，实际 " + purchased);

        BigDecimal sum = resp.getCombustionEmission()
                .add(resp.getDesulfurizationEmission())
                .add(purchased);
        assertEquals(0, resp.getTotalEmission().compareTo(sum),
                "总量必须等于 燃烧 + 脱硫 + 净购电");
    }

    @Test
    @DisplayName("C-03b: 未填净购电时该项为 0，不影响既有结果")
    void testPurchasedElectricityAbsentIsZero() {
        PowerGenerationCalculationRequest req = new PowerGenerationCalculationRequest();
        req.setReportingYear(2024);
        req.setEnterpriseName("无净购电");
        req.setRawCoalFc(new BigDecimal("10000"));
        req.setRawCoalNcv(new BigDecimal("20908"));
        req.setRawCoalCc(new BigDecimal("26.3"));
        req.setRawCoalOf(new BigDecimal("0.98"));

        PowerGenerationCalculationResponse resp = service.calculate(req);

        assertEquals(0, resp.getPurchasedElectricityEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("C-01b: 燃料明细回显用户录入的国标口径原值，不做换算")
    void testFuelDetailEchoesRawStandardUnits() {
        PowerGenerationCalculationRequest req = new PowerGenerationCalculationRequest();
        req.setReportingYear(2024);
        req.setEnterpriseName("回显测试");
        req.setRawCoalFc(new BigDecimal("10000"));
        req.setRawCoalNcv(new BigDecimal("20908"));
        req.setRawCoalCc(new BigDecimal("26.3"));
        req.setRawCoalOf(new BigDecimal("0.98"));

        PowerGenerationCalculationResponse resp = service.calculate(req);

        var detail = resp.getFuelDetails().get(0);
        assertEquals(0, detail.getNetCalorificValue().compareTo(new BigDecimal("20908")),
                "回显应为用户填的 20908 kJ/kg，而不是换算后的 20.908 GJ/t");
        assertEquals(0, detail.getCarbonContent().compareTo(new BigDecimal("26.3")),
                "回显应为用户填的 26.3 tC/TJ");
    }
}
