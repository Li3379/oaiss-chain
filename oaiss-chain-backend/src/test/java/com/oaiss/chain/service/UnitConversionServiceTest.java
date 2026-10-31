package com.oaiss.chain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 单位换算服务测试。
 *
 * <p>存在的理由：本项目曾因单位口径分歧导致同一组数据在不同路径上相差 10^6 倍
 * （后端按 GJ/t 计算、UI 提示按 kJ/kg 录入）。本测试锁定换算系数与双向可逆性。</p>
 */
class UnitConversionServiceTest {

    private final UnitConversionService svc = new UnitConversionService();

    @Test
    @DisplayName("kJ/kg → GJ/t：除以 1000")
    void kjPerKgToGjPerT() {
        assertEquals(0, svc.kjPerKgToGjPerT(new BigDecimal("20908"))
                .compareTo(new BigDecimal("20.908")));
        assertEquals(0, svc.kjPerKgToGjPerT(new BigDecimal("38931"))
                .compareTo(new BigDecimal("38.931")));
    }

    @Test
    @DisplayName("tC/TJ → tC/GJ：除以 1000")
    void tcPerTjToTcPerGj() {
        assertEquals(0, svc.tcPerTjToTcPerGj(new BigDecimal("26.3"))
                .compareTo(new BigDecimal("0.0263")));
        assertEquals(0, svc.tcPerTjToTcPerGj(new BigDecimal("15.32"))
                .compareTo(new BigDecimal("0.01532")));
    }

    @Test
    @DisplayName("双向换算可逆（精度 scale=8）")
    void roundTripIsLossless() {
        BigDecimal originalNcv = new BigDecimal("20908");
        BigDecimal backNcv = svc.gjPerTToKjPerKg(svc.kjPerKgToGjPerT(originalNcv));
        assertEquals(0, originalNcv.compareTo(backNcv));

        BigDecimal originalCc = new BigDecimal("26.3");
        BigDecimal backCc = svc.tcPerGjToTcPerTj(svc.tcPerTjToTcPerGj(originalCc));
        assertEquals(0, originalCc.compareTo(backCc));
    }

    @Test
    @DisplayName("质量与电量换算")
    void massAndEnergy() {
        assertEquals(0, svc.kgToT(new BigDecimal("2500")).compareTo(new BigDecimal("2.5")));
        assertEquals(0, svc.kWhToMwh(new BigDecimal("1500")).compareTo(new BigDecimal("1.5")));
        assertEquals(0, svc.tToKg(new BigDecimal("2.5")).compareTo(new BigDecimal("2500")));
        assertEquals(0, svc.mwhToKwh(new BigDecimal("1.5")).compareTo(new BigDecimal("1500")));
    }

    @Test
    @DisplayName("null 透传为 null（不抛异常）")
    void nullSafe() {
        assertNull(svc.kjPerKgToGjPerT(null));
        assertNull(svc.tcPerTjToTcPerGj(null));
        assertNull(svc.kgToT(null));
        assertNull(svc.kWhToMwh(null));
    }
}