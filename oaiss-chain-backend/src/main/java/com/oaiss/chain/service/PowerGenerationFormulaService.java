package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.CoalOxidationRateRequest;
import com.oaiss.chain.dto.PowerGenerationCalculationRequest;
import com.oaiss.chain.dto.PowerGenerationCalculationResponse;
import com.oaiss.chain.dto.PowerGenerationCalculationResponse.FuelEmissionDetail;
import com.oaiss.chain.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [DEFENSE-TRACE-FORMULA-POWER-01]
 * 发电企业碳排放公式计算服务（GB/T 32150-2015）
 * 核心公式：E_total = E_combust + E_desulf + E_net_power
 * 其中化石燃料燃烧排放: E_combust = Σ(FC_i × NCV_i × CC_i × OF_i × 44/12)
 * 脱硫过程过程排放: E_desulf = carbonateConsumed × desulfEmissionFactor × desulfConversionRate
 *
 * 算法与常数推导：
 * 1. CO2_TO_C_RATIO = 44 / 12 ≈ 3.6667 (CO2 与 C 的摩尔质量比，分子量 C:12, O:16, CO2:44)
 * 2. 燃料消耗量(FC)、低位发热量(NCV)、单位热值含碳量(CC)、碳氧化率(OF) 4项核心因子加权计算
 * 3. 计算精度：中间过程高精度运算，最终核算值采用 RoundingMode.HALF_UP 规范保留4位小数
 *
 * 业务映射：
 * 【对应前端页面】：views/enterprise/CarbonFormulaCalculator.vue
 * 【对应控制器端点】：POST /api/v1/carbon/calculate/power-generation
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PowerGenerationFormulaService {

    /**
     * CO2/C 摩尔质量比 44/12。
     *
     * <p>历史实现按 4 位小数截断（3.6667），相对偏差约 9×10⁻⁶，在万吨级排放上会带来
     * 0.1 t 级别的误差。改为保留 10 位，最终结果仍按 {@link #RESULT_SCALE} 收口。</p>
     */
    private static final BigDecimal CO2_TO_C_RATIO = new BigDecimal("44").divide(
            new BigDecimal("12"), 10, RoundingMode.HALF_UP);
    private static final int RESULT_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * 单位换算。
     *
     * <p><b>口径契约（务必遵守）</b>：本类对外接收的是 <b>GB/T 32150 表 1 口径</b>——
     * 低位发热量 kJ/kg、单位热值含碳量 tC/TJ（企业化验单与 UI 提示都用这套）。
     * 内部计算统一换算为 GJ/t 与 tC/GJ，因为国标式
     * {@code FC(t) × NCV × CC × OF × 44/12} 在这套单位下直接相乘即得 tCO₂。</p>
     *
     * <p>换算只在 {@link #buildFuelParamsList} 入口做一次；私有方法只接受计算口径。</p>
     */
    private final UnitConversionService unitConversionService;

    /**
     * 燃料参数定义。
     *
     * <p>{@code ncv} 与 {@code cc} 已是<b>计算口径</b>（GJ/t、tC/GJ），
     * 而 {@code rawNcv} / {@code rawCc} 保留用户录入的国标口径原值，
     * 用于响应回显——避免「用户填 20908，结果里显示 20.908」的静默错位。</p>
     */
    private record FuelParams(String name,
                              BigDecimal fc, BigDecimal ncv, BigDecimal cc, BigDecimal of,
                              BigDecimal rawNcv, BigDecimal rawCc) {}

    /**
     * 计算发电企业碳排放
     *
     * @param request 25参数计算请求
     * @return 计算结果
     * @throws BusinessException 参数校验失败
     */
    public PowerGenerationCalculationResponse calculate(PowerGenerationCalculationRequest request) {
        log.info("Starting power generation emission calculation for enterprise={}, year={}",
                request.getEnterpriseName(), request.getReportingYear());

        List<FuelParams> fuelParamsList = buildFuelParamsList(request);

        validateFuelParams(fuelParamsList);

        // 校验脱硫转化率（与DTO @DecimalMax保持一致）
        if (request.getDesulfConversionRate() != null
                && request.getDesulfConversionRate().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.DATA_OUT_OF_RANGE,
                    "脱硫转化率不能大于1");
        }

        List<FuelEmissionDetail> fuelDetails = new ArrayList<>();
        BigDecimal combustionEmission = BigDecimal.ZERO;

        for (FuelParams fp : fuelParamsList) {
            BigDecimal emission = calculateFuelEmission(fp);
            combustionEmission = combustionEmission.add(emission);

            if (fp.fc() != null && fp.fc().compareTo(BigDecimal.ZERO) > 0) {
                // 回显用**用户录入的国标口径原值**（kJ/kg、tC/TJ），不是内部换算值。
                // 否则用户填 20908、结果里显示 20.908，会产生 1000 倍的静默错位。
                fuelDetails.add(FuelEmissionDetail.builder()
                        .fuelType(fp.name())
                        .fuelConsumption(fp.fc())
                        .netCalorificValue(fp.rawNcv())
                        .carbonContent(fp.rawCc())
                        .oxidationRate(fp.of())
                        .emission(emission)
                        .build());
            }
        }

        BigDecimal desulfurizationEmission = calculateDesulfurizationEmission(request);

        // 国标式第三项：净购入电力间接排放 E_net = AD电(MWh) × EF电(tCO2/MWh)。
        // 历史实现只算「燃烧 + 脱硫」，与类注释、Controller 注释、前端 API 注释三处
        // 都宣称「含净购入电力」相矛盾——属于整项缺失，不是小偏差。
        BigDecimal purchasedElectricityEmission = calculatePurchasedElectricityEmission(request);

        // 总排放
        BigDecimal totalEmission = combustionEmission
                .add(desulfurizationEmission)
                .add(purchasedElectricityEmission);

        log.info("Calculation completed: combustion={}, desulfurization={}, purchasedElectricity={}, total={}",
                combustionEmission, desulfurizationEmission, purchasedElectricityEmission, totalEmission);

        return PowerGenerationCalculationResponse.builder()
                .totalEmission(totalEmission)
                .combustionEmission(combustionEmission)
                .desulfurizationEmission(desulfurizationEmission)
                .purchasedElectricityEmission(purchasedElectricityEmission)
                .fuelDetails(fuelDetails)
                .reportingYear(String.valueOf(request.getReportingYear()))
                .enterpriseName(request.getEnterpriseName())
                .formulaReference("GB/T 32150-2015")
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 净购入电力间接排放：E = AD电 × EF电。
     *
     * <p>任一分量为空或非正时返回 0（不视为错误——纯自备电厂可以不购电）。</p>
     */
    private BigDecimal calculatePurchasedElectricityEmission(PowerGenerationCalculationRequest request) {
        BigDecimal amount = request.getPurchasedElectricity();
        BigDecimal factor = request.getElectricityEmissionFactor();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || factor == null || factor.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(factor).setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    /**
     * 把请求里的 5 种燃料参数组装为内部计算结果结构。
     *
     * <p><b>单位换算的唯一入口</b>：请求携带的是国标口径（kJ/kg、tC/TJ），
     * 生成为计算口径（GJ/t、tC/GJ）。{@code rawNcv}/{@code rawCc} 保留原值供回显。</p>
     */
    private List<FuelParams> buildFuelParamsList(PowerGenerationCalculationRequest request) {
        return List.of(
                fuel("原煤", request.getRawCoalFc(), request.getRawCoalNcv(),
                        request.getRawCoalCc(), request.getRawCoalOf()),
                fuel("洗精煤", request.getCleanedCoalFc(), request.getCleanedCoalNcv(),
                        request.getCleanedCoalCc(), request.getCleanedCoalOf()),
                fuel("其他洗煤", request.getOtherWashedCoalFc(), request.getOtherWashedCoalNcv(),
                        request.getOtherWashedCoalCc(), request.getOtherWashedCoalOf()),
                fuel("型煤", request.getBriquetteFc(), request.getBriquetteNcv(),
                        request.getBriquetteCc(), request.getBriquetteOf()),
                fuel("其他煤", request.getOtherCoalFc(), request.getOtherCoalNcv(),
                        request.getOtherCoalCc(), request.getOtherCoalOf())
        );
    }

    /** 组装单个燃料参数：入参国标口径 → 内部计算口径。 */
    private FuelParams fuel(String name, BigDecimal fc, BigDecimal rawNcv, BigDecimal rawCc, BigDecimal of) {
        return new FuelParams(
                name,
                fc,
                unitConversionService.kjPerKgToGjPerT(rawNcv),
                unitConversionService.tcPerTjToTcPerGj(rawCc),
                of,
                rawNcv,
                rawCc);
    }

    private void validateFuelParams(List<FuelParams> fuelParamsList) {
        for (FuelParams fp : fuelParamsList) {
            if (fp.fc() != null && fp.fc().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCode.DATA_OUT_OF_RANGE,
                        fp.name() + "消耗量不能为负");
            }
            if (fp.of() != null && fp.of().compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException(ErrorCode.DATA_OUT_OF_RANGE,
                        fp.name() + "碳氧化率不能大于1");
            }
        }
    }

    /**
     * 单种燃料燃烧排放: E_i = FC × NCV × CC × OF × 44/12
     * FC为null或0时，排放量为0
     */
    private BigDecimal calculateFuelEmission(FuelParams fp) {
        if (fp.fc() == null || fp.fc().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (fp.ncv() == null || fp.cc() == null || fp.of() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    fp.name() + "低位发热量、含碳量、碳氧化率不能为空（消耗量已填写）");
        }
        return fp.fc().multiply(fp.ncv())
                .multiply(fp.cc())
                .multiply(fp.of())
                .multiply(CO2_TO_C_RATIO)
                .setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    /**
     * 脱硫排放: E_desulf = carbonateConsumed × desulfEmissionFactor × desulfConversionRate
     * 任意参数为null或0时，排放量为0
     */
    private BigDecimal calculateDesulfurizationEmission(PowerGenerationCalculationRequest request) {
        BigDecimal cc = request.getCarbonateConsumed();
        BigDecimal ef = request.getDesulfEmissionFactor();
        BigDecimal cr = request.getDesulfConversionRate();

        if (cc == null || cc.compareTo(BigDecimal.ZERO) == 0
                || ef == null || ef.compareTo(BigDecimal.ZERO) == 0
                || cr == null || cr.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return cc.multiply(ef).multiply(cr)
                .setScale(RESULT_SCALE, ROUNDING_MODE);
    }

    /**
     * 根据炉渣与飞灰含碳量实测反推燃煤碳氧化率 (GB/T 32150-2015):
     * OF = 1 - [ (G_slag * C_slag + G_ash * (C_ash / eta)) * 10^6 ] / (FC * NCV * CC)
     */
    public BigDecimal calculateOxidationRateFromAsh(CoalOxidationRateRequest req) {
        BigDecimal eta = req.getDustRemovalEfficiency() != null && req.getDustRemovalEfficiency().compareTo(BigDecimal.ZERO) > 0
                ? req.getDustRemovalEfficiency()
                : BigDecimal.ONE;

        // G_ash * (C_ash / eta)
        BigDecimal ashCarbon = req.getFlyAshWeight().multiply(
                req.getFlyAshCarbonContent().divide(eta, 8, RoundingMode.HALF_UP));

        // G_slag * C_slag
        BigDecimal slagCarbon = req.getSlagWeight().multiply(req.getSlagCarbonContent());

        // (slagCarbon + ashCarbon) * 10^6
        BigDecimal unoxidizedCarbon = slagCarbon.add(ashCarbon).multiply(new BigDecimal("1000000"));

        // FC * NCV * CC
        BigDecimal totalCarbonDenominator = req.getCoalConsumption()
                .multiply(req.getNetCalorificValue())
                .multiply(req.getCarbonContent());

        if (totalCarbonDenominator.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "燃煤总碳量分母必须大于0");
        }

        BigDecimal unoxidizedRatio = unoxidizedCarbon.divide(totalCarbonDenominator, 6, RoundingMode.HALF_UP);
        BigDecimal oxidationRate = BigDecimal.ONE.subtract(unoxidizedRatio).setScale(4, RoundingMode.HALF_UP);

        if (oxidationRate.compareTo(BigDecimal.ZERO) < 0) {
            oxidationRate = BigDecimal.ZERO;
        } else if (oxidationRate.compareTo(BigDecimal.ONE) > 0) {
            oxidationRate = BigDecimal.ONE;
        }

        return oxidationRate;
    }
}
