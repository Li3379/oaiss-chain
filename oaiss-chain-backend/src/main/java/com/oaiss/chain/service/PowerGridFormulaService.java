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

        BigDecimal gridEmissionFactor = request.getGridEmissionFactor();

        // ===== 国标净电量式（D4）=====
        // E_输配 = (EL上网 + EL输入 − EL输出 − EL售电) × EF电网
        //
        // 字段映射（v1 的命名与国标口径错位，此处显式对齐）：
        //   generationVolume     → EL上网（发电量，近似上网电量）
        //   importedElectricity  → EL输入
        //   exportedElectricity  → EL输出
        //   soldElectricity      → EL售电（v1 完全没有这个字段）
        //
        // 历史实现是「输配电量 × 线损率 × 因子」，属另一个模型，已弃用。
        BigDecimal elOnGrid = nz(request.getGenerationVolume());
        BigDecimal elImported = nz(request.getImportedElectricity());
        BigDecimal elExported = nz(request.getExportedElectricity());
        BigDecimal elSold = nz(request.getSoldElectricity());

        BigDecimal netElectricity = elOnGrid.add(elImported).subtract(elExported).subtract(elSold);

        // 净电量可能为负（售电大于供电）。国标语义下负排放无意义，
        // 钳制为 0 并置标志，交由前端提示用户核对电量口径。
        boolean netNegative = netElectricity.compareTo(ZERO) < 0;
        if (netNegative) {
            netElectricity = ZERO;
        }
        netElectricity = netElectricity.setScale(SCALE, ROUNDING);

        // 输配环节排放（沿用 transmissionLossEmission 字段承载，语义已变为「净电量排放」）
        BigDecimal transmissionLossEmission = netElectricity.multiply(gridEmissionFactor)
                .setScale(SCALE, ROUNDING);
        BigDecimal transmissionLoss = netElectricity;

        // 外购电力排放：国标式里 EL输入 已含在净电量中，不再重复计一次。
        // 保留字段用于向后兼容展示（旧报告可能已有该值），新逻辑恒为 0。
        BigDecimal importedEmission = ZERO;

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
                .netElectricityNegative(netNegative)
                .formulaReference("GB/T 32150-2015")
                .reportingYear(String.valueOf(request.getReportingYear()))
                .enterpriseName(request.getEnterpriseName())
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    /** null 安全的取零；国标式四项电量均为可选（未填即 0）。 */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private void validate(PowerGridCalculationRequest request) {
        // 国标净电量式使用的四项电量均为可选，未填按 0 处理。
        // 旧字段（输配电量/线损率）已弃用，可能为 null——不能再裸解引用（历史 NPE 隐患）。
        if (request.getLineLossRate() != null
                && request.getLineLossRate().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.DATA_OUT_OF_RANGE, "线损率不能超过1");
        }
        if (request.getTransmissionVolume() != null
                && request.getTransmissionVolume().compareTo(ZERO) < 0) {
            throw new BusinessException(ErrorCode.DATA_OUT_OF_RANGE, "输配电量不能为负数");
        }
        if (request.getGenerationVolume() != null
                && request.getGenerationVolume().compareTo(ZERO) < 0) {
            throw new BusinessException(ErrorCode.DATA_OUT_OF_RANGE, "上网电量不能为负数");
        }
    }
}
