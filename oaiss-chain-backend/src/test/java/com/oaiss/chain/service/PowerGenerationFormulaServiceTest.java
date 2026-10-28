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

    @InjectMocks
    private PowerGenerationFormulaService service;

    private PowerGenerationCalculationRequest baseRequest;

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("已知值计算: 原煤 FC=100, NCV=20, CC=0.025, OF=0.98 → 燃烧排放≈179.6667")
    void calculate_knownValues_correctCombustionEmission() {
        baseRequest.setRawCoalFc(new BigDecimal("100"));
        baseRequest.setRawCoalNcv(new BigDecimal("20"));
        baseRequest.setRawCoalCc(new BigDecimal("0.025"));
        baseRequest.setRawCoalOf(new BigDecimal("0.98"));

        // Expected: 100 × 20 × 0.025 × 0.98 × 44/12
        BigDecimal expected = new BigDecimal("100")
                .multiply(new BigDecimal("20"))
                .multiply(new BigDecimal("0.025"))
                .multiply(new BigDecimal("0.98"))
                .multiply(new BigDecimal("44").divide(new BigDecimal("12"), 4, RoundingMode.HALF_UP))
                .setScale(4, RoundingMode.HALF_UP);

        PowerGenerationCalculationResponse response = service.calculate(baseRequest);

        assertEquals(0, response.getCombustionEmission().compareTo(expected));
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
}
