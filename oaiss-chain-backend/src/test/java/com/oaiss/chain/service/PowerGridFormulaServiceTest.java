package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.PowerGridCalculationRequest;
import com.oaiss.chain.dto.PowerGridCalculationResponse;
import com.oaiss.chain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 电网碳排放公式计算服务测试
 */
@ExtendWith(MockitoExtension.class)
class PowerGridFormulaServiceTest {

    @InjectMocks
    private PowerGridFormulaService powerGridFormulaService;

    /**
     * 构造**国标净电量式**请求（D4）。
     *
     * 公式：E_输配 = (EL上网 + EL输入 − EL输出 − EL售电) × EF电网
     *
     * 旧字段 transmissionVolume / lineLossRate 已 @Deprecated，不再参与计算；
     * 这里仍显式置 null，顺带验证新逻辑对它们的缺失是安全的（历史 NPE 隐患）。
     */
    private PowerGridCalculationRequest buildNetRequest(
            BigDecimal onGrid,
            BigDecimal imported,
            BigDecimal exported,
            BigDecimal sold,
            BigDecimal gridEmissionFactor) {
        PowerGridCalculationRequest request = new PowerGridCalculationRequest();
        request.setGenerationVolume(onGrid);
        request.setImportedElectricity(imported);
        request.setExportedElectricity(exported);
        request.setSoldElectricity(sold);
        request.setGridEmissionFactor(gridEmissionFactor);
        request.setTransmissionVolume(null);
        request.setLineLossRate(null);
        request.setReportingYear(2024);
        request.setEnterpriseName("测试企业");
        return request;
    }

    @Test
    @DisplayName("全零输入 → 总排放为0")
    void calculate_allZeros_returnsZeroEmission() {
        PowerGridCalculationRequest request = buildNetRequest(null, null, null, null, BigDecimal.ZERO);

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getTotalEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getTransmissionLossEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getTransmissionLoss().compareTo(BigDecimal.ZERO));
        assertFalse(response.getNetElectricityNegative(), "全零不应被判为负净电量");
    }

    @Test
    @DisplayName("国标净电量: 上网12000-售电7000=5000 × EF0.6 → 3000")
    void calculate_basicTransmissionLoss_returnsCorrectEmission() {
        PowerGridCalculationRequest request = buildNetRequest(
                new BigDecimal("12000"), null, null, new BigDecimal("7000"),
                new BigDecimal("0.6"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, new BigDecimal("5000.0000").compareTo(response.getTransmissionLoss()),
                "净电量 = 上网12000 − 售电7000 = 5000");
        assertEquals(0, new BigDecimal("3000.0000").compareTo(response.getTransmissionLossEmission()));
        assertEquals(0, new BigDecimal("3000.0000").compareTo(response.getTotalEmission()));
    }

    @Test
    @DisplayName("国标净电量含 EL输入: (10000上网+2000输入−0输出−0售电) × 0.6 = 7200")
    void calculate_withImportedEmission_returnsCorrectTotal() {
        PowerGridCalculationRequest request = buildNetRequest(
                new BigDecimal("10000"), new BigDecimal("2000"), null, null,
                new BigDecimal("0.6"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, new BigDecimal("7200.0000").compareTo(response.getTransmissionLossEmission()),
                "EL输入已并入净电量，不再重复计一次");
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getImportedEmission()),
                "importedEmission 保留字段但恒为 0（避免与净电量重复）");
        assertEquals(0, new BigDecimal("7200.0000").compareTo(response.getTotalEmission()));
    }

    @Test
    @DisplayName("线损率超过1 → 抛出BusinessException")
    void calculate_lineLossRateExceedsOne_throwsBusinessException() {
        PowerGridCalculationRequest request = new PowerGridCalculationRequest();
        request.setTransmissionVolume(new BigDecimal("10000"));
        request.setLineLossRate(new BigDecimal("1.5"));
        request.setGridEmissionFactor(new BigDecimal("0.6"));
        request.setReportingYear(2024);
        request.setEnterpriseName("测试企业");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> powerGridFormulaService.calculate(request));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
    }

    @Test
    @DisplayName("负输配电量 → 抛出BusinessException")
    void calculate_negativeTransmissionVolume_throwsBusinessException() {
        // Bean Validation would normally reject this, but test service-layer defense
        PowerGridCalculationRequest request = new PowerGridCalculationRequest();
        request.setTransmissionVolume(new BigDecimal("-100"));
        request.setLineLossRate(new BigDecimal("0.05"));
        request.setGridEmissionFactor(new BigDecimal("0.6"));
        request.setReportingYear(2024);
        request.setEnterpriseName("测试企业");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> powerGridFormulaService.calculate(request));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
    }

    @Test
    @DisplayName("可选参数为null → 视为零，不报错")
    void calculate_nullOptionalParams_treatedAsZero() {
        // 旧字段（输配电量/线损率）已 @Deprecated 且不参与国标式；
        // 四项国标电量全缺 → 净电量为 0。此用例锁定「旧字段被忽略」这一新语义，
        // 同时验证四个可选电量缺失时不会 NPE。
        PowerGridCalculationRequest request = new PowerGridCalculationRequest();
        request.setTransmissionVolume(new BigDecimal("5000"));
        request.setLineLossRate(new BigDecimal("0.04"));
        request.setGridEmissionFactor(new BigDecimal("0.5"));
        request.setReportingYear(2024);
        request.setEnterpriseName("测试企业");

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTransmissionLoss()),
                "旧字段不再参与计算，净电量应为 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTotalEmission()),
                "无国标电量输入时总量为 0（旧线损模型已弃用）");
        assertFalse(response.getNetElectricityNegative());
        assertEquals("GB/T 32150-2015", response.getFormulaReference());
        assertEquals("2024", response.getReportingYear());
        assertEquals("测试企业", response.getEnterpriseName());
        assertNotNull(response.getCalculatedAt());
    }

    // ===== 国标净电量式下的可选参数健壮性（替代原「外购电因子组合」用例）=====

    @Test
    @DisplayName("四项电量全部缺失 + EF 非零 → 净电量为 0，总量 0")
    void calculate_allElectricityMissing_zeroTotal() {
        PowerGridCalculationRequest request = buildNetRequest(null, null, null, null, new BigDecimal("0.6"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTotalEmission()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTransmissionLoss()));
        assertFalse(response.getNetElectricityNegative());
    }

    @Test
    @DisplayName("只有上网电量 → 净电量等于上网电量")
    void calculate_onlyOnGrid_netEqualsOnGrid() {
        PowerGridCalculationRequest request = buildNetRequest(
                new BigDecimal("5000"), null, null, null, new BigDecimal("0.5"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, new BigDecimal("5000.0000").compareTo(response.getTransmissionLoss()));
        assertEquals(0, new BigDecimal("2500.0000").compareTo(response.getTotalEmission()));
    }

    @Test
    @DisplayName("输出大于上网 → 净电量为负，被钳制为 0 且置标志")
    void calculate_outputExceedsOnGrid_clampedToZeroWithFlag() {
        PowerGridCalculationRequest request = buildNetRequest(
                new BigDecimal("1000"), null, new BigDecimal("3000"), null,
                new BigDecimal("0.6"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTransmissionLoss()),
                "净电量为负应钳制为 0，不产出负排放");
        assertTrue(response.getNetElectricityNegative(), "必须置标志，让前端提示用户核对");
    }

    @Test
    @DisplayName("售电等于上网+输入 → 净电量恰好 0，不置负标志")
    void calculate_netExactlyZero_notFlagged() {
        PowerGridCalculationRequest request = buildNetRequest(
                new BigDecimal("4000"), new BigDecimal("1000"),
                null, new BigDecimal("5000"), new BigDecimal("0.6"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTransmissionLoss()));
        assertFalse(response.getNetElectricityNegative(), "恰好为 0 不应判为负");
    }

    @Test
    @DisplayName("负上网电量 → 抛 BusinessException")
    void calculate_negativeOnGrid_throwsBusinessException() {
        PowerGridCalculationRequest request = buildNetRequest(
                new BigDecimal("-100"), null, null, null, new BigDecimal("0.6"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> powerGridFormulaService.calculate(request));
        assertEquals(ErrorCode.DATA_OUT_OF_RANGE, exception.getCode());
    }

}
