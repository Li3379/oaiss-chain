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

    private PowerGridCalculationRequest buildRequest(
            BigDecimal transmissionVolume,
            BigDecimal lineLossRate,
            BigDecimal gridEmissionFactor,
            BigDecimal importedElectricity,
            BigDecimal importEmissionFactor) {
        PowerGridCalculationRequest request = new PowerGridCalculationRequest();
        request.setTransmissionVolume(transmissionVolume);
        request.setLineLossRate(lineLossRate);
        request.setGridEmissionFactor(gridEmissionFactor);
        request.setImportedElectricity(importedElectricity);
        request.setImportEmissionFactor(importEmissionFactor);
        request.setReportingYear(2024);
        request.setEnterpriseName("测试企业");
        return request;
    }

    @Test
    @DisplayName("全零输入 → 总排放为0")
    void calculate_allZeros_returnsZeroEmission() {
        PowerGridCalculationRequest request = buildRequest(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getTotalEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getTransmissionLossEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.getTransmissionLoss().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("基本输配损耗计算: V=10000, L_rate=0.05, EF=0.6 → T_loss=500, E_loss=300")
    void calculate_basicTransmissionLoss_returnsCorrectEmission() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), null, null);

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, new BigDecimal("500.0000").compareTo(response.getTransmissionLoss()));
        assertEquals(0, new BigDecimal("300.0000").compareTo(response.getTransmissionLossEmission()));
        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("300.0000").compareTo(response.getTotalEmission()));
    }

    @Test
    @DisplayName("含外购电力: imported=2000, importEF=0.8 → importedEmission=1600, total=1900")
    void calculate_withImportedEmission_returnsCorrectTotal() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), new BigDecimal("2000"), new BigDecimal("0.8"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, new BigDecimal("300.0000").compareTo(response.getTransmissionLossEmission()));
        assertEquals(0, new BigDecimal("1600.0000").compareTo(response.getImportedEmission()));
        assertEquals(0, new BigDecimal("1900.0000").compareTo(response.getTotalEmission()));
    }

    @Test
    @DisplayName("线损率超过1 → 抛出BusinessException")
    void calculate_lineLossRateExceedsOne_throwsBusinessException() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("1.5"),
                new BigDecimal("0.6"), null, null);

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
        PowerGridCalculationRequest request = new PowerGridCalculationRequest();
        request.setTransmissionVolume(new BigDecimal("5000"));
        request.setLineLossRate(new BigDecimal("0.04"));
        request.setGridEmissionFactor(new BigDecimal("0.5"));
        request.setReportingYear(2024);
        request.setEnterpriseName("测试企业");
        // importedElectricity, importEmissionFactor, generationVolume, exportedElectricity all null

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("200.0000").compareTo(response.getTransmissionLoss()));
        assertEquals(0, new BigDecimal("100.0000").compareTo(response.getTransmissionLossEmission()));
        assertEquals(0, new BigDecimal("100.0000").compareTo(response.getTotalEmission()));
        assertEquals("GB/T 32150-2015", response.getFormulaReference());
        assertEquals("2024", response.getReportingYear());
        assertEquals("测试企业", response.getEnterpriseName());
        assertNotNull(response.getCalculatedAt());
    }

    @Test
    @DisplayName("外购电量非零但排放因子为null时，外购电力排放为0")
    void calculate_importedElectricityPresentButFactorNull_zeroImportedEmission() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), new BigDecimal("2000"), null);

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("300.0000").compareTo(response.getTotalEmission()));
    }

    @Test
    @DisplayName("外购电量为零时，外购电力排放为0")
    void calculate_importedElectricityZero_zeroImportedEmission() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), BigDecimal.ZERO, new BigDecimal("0.8"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("300.0000").compareTo(response.getTotalEmission()));
    }

    @Test
    @DisplayName("外购电力排放因子为零时，外购电力排放为0")
    void calculate_importEmissionFactorZero_zeroImportedEmission() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), new BigDecimal("2000"), BigDecimal.ZERO);

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
        assertEquals(0, new BigDecimal("300.0000").compareTo(response.getTotalEmission()));
    }

    @Test
    @DisplayName("外购电量为null但排放因子非null时，外购电力排放为0")
    void calculate_importedElectricityNullButFactorPresent_zeroImportedEmission() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), null, new BigDecimal("0.8"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("外购电量为null且排放因子为零时，外购电力排放为0")
    void calculate_importedElectricityNullAndFactorZero_zeroImportedEmission() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), null, BigDecimal.ZERO);

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("外购电量为零且排放因子为null时，外购电力排放为0")
    void calculate_importedElectricityZeroAndFactorNull_zeroImportedEmission() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), BigDecimal.ZERO, null);

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertEquals(0, response.getImportedEmission().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("线损率恰好等于1时不抛异常")
    void calculate_lineLossRateExactlyOne_noException() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), BigDecimal.ONE,
                new BigDecimal("0.6"), null, null);

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertNotNull(response);
        assertEquals(0, new BigDecimal("10000.0000").compareTo(response.getTransmissionLoss()));
    }

    @Test
    @DisplayName("原生文档04国标SF6设备泄漏核算: REC(100-80)+REP(50-40)=30kg泄漏, GWP=23900 → 717 tCO2e")
    void calculate_withSf6Equipment_returnsCorrectSf6Emission() {
        PowerGridCalculationRequest request = buildRequest(
                new BigDecimal("10000"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), null, null);
        request.setRecCapacity(new BigDecimal("100"));
        request.setRecRecovery(new BigDecimal("80"));
        request.setRepCapacity(new BigDecimal("50"));
        request.setRepRecovery(new BigDecimal("40"));

        PowerGridCalculationResponse response = powerGridFormulaService.calculate(request);

        assertNotNull(response);
        // T_loss_emission = 10000 * 0.05 * 0.6 = 300.0000
        // SF6_emission = (20 + 10) * 23.900 = 717.0000
        // Total = 300.0000 + 717.0000 = 1017.0000
        assertEquals(0, new BigDecimal("717.0000").compareTo(response.getSf6Emission()));
        assertEquals(0, new BigDecimal("1017.0000").compareTo(response.getTotalEmission()));
    }
}
