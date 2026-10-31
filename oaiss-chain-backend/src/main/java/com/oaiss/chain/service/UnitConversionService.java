package com.oaiss.chain.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 碳核算单位换算服务（GB/T 32150-2015 国标口径）。
 *
 * <p>存在的理由：本项目历史上存在两套隐式单位制，同一组数字在不同路径上相差 10^6 倍：</p>
 * <ul>
 *   <li>GB/T 32150 表 1 的原始口径：低位发热量 <b>kJ/kg</b>、单位热值含碳量 <b>tC/TJ</b>
 *       —— 这是企业化验单上的口径，也是 UI 提示用户录入的口径；</li>
 *   <li>本服务内部计算口径：低位发热量 <b>GJ/t</b>、单位热值含碳量 <b>tC/GJ</b>
 *       —— 与国标式 {@code FC(t) × NCV × CC × OF × 44/12} 直接相乘即得 tCO₂。</li>
 * </ul>
 *
 * <p>两者换算系数恒为 1000（1 GJ/t = 1000 kJ/kg，1 tC/GJ = 1000 tC/TJ）。
 * 统一在此处做一次，避免各分支各写一套（历史缺陷根因）。</p>
 */
@Service
public class UnitConversionService {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** 低位发热量：kJ/kg → GJ/t（÷1000）。 */
    public BigDecimal kjPerKgToGjPerT(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.divide(THOUSAND, SCALE, ROUNDING);
    }

    /** 低位发热量：GJ/t → kJ/kg（×1000）。 */
    public BigDecimal gjPerTToKjPerKg(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.multiply(THOUSAND);
    }

    /** 单位热值含碳量：tC/TJ → tC/GJ（÷1000）。 */
    public BigDecimal tcPerTjToTcPerGj(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.divide(THOUSAND, SCALE, ROUNDING);
    }

    /** 单位热值含碳量：tC/GJ → tC/TJ（×1000）。 */
    public BigDecimal tcPerGjToTcPerTj(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.multiply(THOUSAND);
    }

    /** 质量：kg → t（÷1000）。 */
    public BigDecimal kgToT(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.divide(THOUSAND, SCALE, ROUNDING);
    }

    /** 质量：t → kg（×1000）。 */
    public BigDecimal tToKg(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.multiply(THOUSAND);
    }

    /** 电量：kWh → MWh（÷1000）。 */
    public BigDecimal kWhToMwh(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.divide(THOUSAND, SCALE, ROUNDING);
    }

    /** 电量：MWh → kWh（×1000）。 */
    public BigDecimal mwhToKwh(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.multiply(THOUSAND);
    }
}
