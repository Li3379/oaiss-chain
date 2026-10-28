package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.PowerGridCalculationRequest;
import com.oaiss.chain.dto.PowerGridCalculationResponse;
import com.oaiss.chain.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 电网碳排放计算服务
 * Power Grid Carbon Emission Formula Service
 *
 * <p>基于 GB/T 32150-2015 标准，9参数电网碳排放计算</p>
 *
 * @author OAISS Team
 */
@Service
@RequiredArgsConstructor
public class PowerGridFormulaService {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 计算电网碳排放
     *
     * @param request 计算请求参数
     * @return 计算结果
     */
    public PowerGridCalculationResponse calculate(PowerGridCalculationRequest request) {
        validate(request);

        BigDecimal transmissionVolume = request.getTransmissionVolume();
        BigDecimal lineLossRate = request.getLineLossRate();
        BigDecimal gridEmissionFactor = request.getGridEmissionFactor();

        // 输配损耗电量 = 输配电量 × 线损率
        BigDecimal transmissionLoss = transmissionVolume.multiply(lineLossRate)
                .setScale(SCALE, ROUNDING);

        // 输配损耗排放 = 输配损耗电量 × 电网排放因子
        BigDecimal transmissionLossEmission = transmissionLoss.multiply(gridEmissionFactor)
                .setScale(SCALE, ROUNDING);

        // 外购电力排放 = 外购电量 × 外购电力排放因子（两者均非空且非零时计算）
        BigDecimal importedElectricity = request.getImportedElectricity();
        BigDecimal importEmissionFactor = request.getImportEmissionFactor();
        BigDecimal importedEmission = ZERO;
        if (importedElectricity != null && importEmissionFactor != null
                && importedElectricity.compareTo(ZERO) > 0
                && importEmissionFactor.compareTo(ZERO) > 0) {
            importedEmission = importedElectricity.multiply(importEmissionFactor)
                    .setScale(SCALE, ROUNDING);
        }

        // 六氟化硫 (SF6) 绝缘设备泄漏排放 (tCO2e)
        // 依据原生规范 04-碳核算模型介绍文档 式(153):
        // E_sf6 = [(REC_容量 - REC_回收) + (REP_容量 - REP_回收)] × GWP_SF6 × 10^-3, GWP=23900
        BigDecimal sf6Emission = ZERO;
        BigDecimal recCap = request.getRecCapacity() != null ? request.getRecCapacity() : ZERO;
        BigDecimal recRec = request.getRecRecovery() != null ? request.getRecRecovery() : ZERO;
        BigDecimal repCap = request.getRepCapacity() != null ? request.getRepCapacity() : ZERO;
        BigDecimal repRec = request.getRepRecovery() != null ? request.getRepRecovery() : ZERO;
        BigDecimal recLeak = recCap.subtract(recRec).max(ZERO);
        BigDecimal repLeak = repCap.subtract(repRec).max(ZERO);
        BigDecimal totalSf6LeakKg = recLeak.add(repLeak);
        if (totalSf6LeakKg.compareTo(ZERO) > 0) {
            sf6Emission = totalSf6LeakKg.multiply(new BigDecimal("23.900"))
                    .setScale(SCALE, ROUNDING);
        }

        // 总排放 = 输配损耗排放 + 外购电力排放 + SF6设备泄漏排放
        BigDecimal totalEmission = transmissionLossEmission.add(importedEmission).add(sf6Emission)
                .setScale(SCALE, ROUNDING);

        return PowerGridCalculationResponse.builder()
                .totalEmission(totalEmission)
                .transmissionLossEmission(transmissionLossEmission)
                .importedEmission(importedEmission)
                .sf6Emission(sf6Emission)
                .transmissionLoss(transmissionLoss)
                .formulaReference("GB/T 32150-2015")
                .reportingYear(String.valueOf(request.getReportingYear()))
                .enterpriseName(request.getEnterpriseName())
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private void validate(PowerGridCalculationRequest request) {
        // Bean Validation 已覆盖 lineLossRate <= 1 和非空/非负校验
        // 此处补充业务层二次校验，确保防御性编程
        if (request.getLineLossRate().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.DATA_OUT_OF_RANGE, "线损率不能超过1");
        }
        if (request.getTransmissionVolume().compareTo(ZERO) < 0) {
            throw new BusinessException(ErrorCode.DATA_OUT_OF_RANGE, "输配电量不能为负数");
        }
    }
}
