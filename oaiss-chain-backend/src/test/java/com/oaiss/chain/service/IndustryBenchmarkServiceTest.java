package com.oaiss.chain.service;

import com.oaiss.chain.entity.IndustryBenchmark;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.IndustryBenchmarkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 行业基准值维护服务测试。
 *
 * <p>重点验证三类约束：
 * <ol>
 *   <li><b>不跨年度降级</b>：查不到就返回空，绝不回退到别的年度——那会让配额悄悄算错</li>
 *   <li><b>写入校验</b>：值必须为正、机组类型必须在 I/II/III/IV 内、单位必须是 tCO2/MWh</li>
 *   <li><b>就绪检查</b>：能让审核界面提前知道某年度缺哪几类</li>
 * </ol>
 */
class IndustryBenchmarkServiceTest {

    private IndustryBenchmarkRepository repository;
    private IndustryBenchmarkService service;

    @BeforeEach
    void setUp() {
        repository = mock(IndustryBenchmarkRepository.class);
        service = new IndustryBenchmarkService(repository);
    }

    private IndustryBenchmark sample(String unitType, int year, String value) {
        return IndustryBenchmark.builder()
                .industryCode("POWER")
                .unitType(unitType)
                .benchmarkYear(year)
                .benchmarkKind("GENERATION_BENCHMARK")
                .benchmarkValue(new BigDecimal(value))
                .benchmarkUnit("tCO2/MWh")
                .build();
    }

    // ==================== 查询语义 ====================

    @Test
    @DisplayName("查不到返回空——不跨年度降级")
    void resolve_returnsEmptyWhenMissing_notFallback() {
        when(repository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.empty());

        Optional<IndustryBenchmark> r = service.resolve("POWER", "II", 2027, "GENERATION_BENCHMARK");

        assertTrue(r.isEmpty(), "2027 无基准值时应返回空，不能回退到 2026");
        // 关键：只查了一次，没有降级重试
        verify(repository, times(1))
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("查到则原样返回")
    void resolve_returnsRecord() {
        IndustryBenchmark b = sample("II", 2026, "0.7985");
        when(repository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        "POWER", "II", 2026, "GENERATION_BENCHMARK"))
                .thenReturn(Optional.of(b));

        Optional<IndustryBenchmark> r = service.resolve("POWER", "II", 2026, "GENERATION_BENCHMARK");

        assertTrue(r.isPresent());
        assertEquals(new BigDecimal("0.7985"), r.get().getBenchmarkValue());
    }

    // ==================== 就绪检查 ====================

    @Test
    @DisplayName("四类齐全 → ready=true，missing 为空")
    void readiness_allPresent() {
        when(repository.findByIndustryCodeAndBenchmarkYearAndDeletedFalse("POWER", 2026))
                .thenReturn(List.of(
                        sample("I", 2026, "0.7894"), sample("II", 2026, "0.7985"),
                        sample("III", 2026, "0.8080"), sample("IV", 2026, "0.3288")));

        IndustryBenchmarkService.Readiness r = service.readiness(2026);

        assertTrue(r.isReady());
        assertTrue(r.getMissingUnitTypes().isEmpty());
        assertEquals(List.of("I", "II", "III", "IV"), r.getPresentUnitTypes());
    }

    @Test
    @DisplayName("缺两类 → ready=false，missing 精确指出缺哪两类")
    void readiness_partiallyPresent() {
        when(repository.findByIndustryCodeAndBenchmarkYearAndDeletedFalse("POWER", 2027))
                .thenReturn(List.of(
                        sample("I", 2027, "0.78"), sample("II", 2027, "0.79")));

        IndustryBenchmarkService.Readiness r = service.readiness(2027);

        assertFalse(r.isReady());
        assertEquals(List.of("III", "IV"), r.getMissingUnitTypes(),
                "审核界面应能明确告诉管理员缺哪两类");
    }

    @Test
    @DisplayName("一条都没有 → ready=false，四类全缺")
    void readiness_nothingPresent() {
        when(repository.findByIndustryCodeAndBenchmarkYearAndDeletedFalse("POWER", 2030))
                .thenReturn(List.of());

        IndustryBenchmarkService.Readiness r = service.readiness(2030);

        assertFalse(r.isReady());
        assertEquals(4, r.getMissingUnitTypes().size());
    }

    // ==================== 写入校验 ====================

    @Test
    @DisplayName("值必须为正数")
    void create_rejectsNonPositiveValue() {
        IndustryBenchmark b = sample("II", 2026, "0");
        assertThrows(BusinessException.class, () -> service.create(b));

        IndustryBenchmark neg = sample("II", 2026, "-0.5");
        assertThrows(BusinessException.class, () -> service.create(neg));
    }

    @Test
    @DisplayName("机组类型必须是 I/II/III/IV")
    void create_rejectsInvalidUnitType() {
        IndustryBenchmark b = sample("V", 2026, "0.5");
        assertThrows(BusinessException.class, () -> service.create(b));
    }

    @Test
    @DisplayName("行业必须是 POWER（本轮范围）")
    void create_rejectsUnsupportedIndustry() {
        IndustryBenchmark b = sample("II", 2026, "0.5");
        b.setIndustryCode("STEEL");
        assertThrows(BusinessException.class, () -> service.create(b));
    }

    @Test
    @DisplayName("单位必须是 tCO2/MWh")
    void create_rejectsWrongUnit() {
        IndustryBenchmark b = sample("II", 2026, "0.5");
        b.setBenchmarkUnit("tCO2/GJ");
        assertThrows(BusinessException.class, () -> service.create(b));
    }

    @Test
    @DisplayName("单位为空时自动补默认值 tCO2/MWh")
    void create_defaultsUnit() {
        IndustryBenchmark b = sample("II", 2026, "0.7985");
        b.setBenchmarkUnit(null);
        when(repository.save(any(IndustryBenchmark.class))).thenAnswer(i -> i.getArgument(0));

        IndustryBenchmark saved = service.create(b);

        assertEquals("tCO2/MWh", saved.getBenchmarkUnit());
    }

    @Test
    @DisplayName("重复录入（同行业+机组+年度+类别）→ 拒绝，提示改用更新接口")
    void create_rejectsDuplicate() {
        IndustryBenchmark b = sample("II", 2026, "0.7985");
        when(repository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Optional.of(sample("II", 2026, "0.7985")));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(b));
        assertTrue(ex.getMessage().contains("已存在"));
        verify(repository, never()).save(any(IndustryBenchmark.class));
    }

    @Test
    @DisplayName("年度越界拒绝")
    void create_rejectsInvalidYear() {
        IndustryBenchmark b = sample("II", 1999, "0.5");
        assertThrows(BusinessException.class, () -> service.create(b));

        IndustryBenchmark future = sample("II", 2200, "0.5");
        assertThrows(BusinessException.class, () -> service.create(future));
    }

    // ==================== 更新 / 删除 ====================

    @Test
    @DisplayName("更新：只改数值，其余字段不动")
    void update_changesValueOnly() {
        IndustryBenchmark existing = sample("II", 2026, "0.7985");
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(IndustryBenchmark.class))).thenAnswer(i -> i.getArgument(0));

        IndustryBenchmark updated = service.update(1L, new BigDecimal("0.7900"));

        assertEquals(new BigDecimal("0.7900"), updated.getBenchmarkValue());
        assertEquals("II", updated.getUnitType());
        assertEquals(Integer.valueOf(2026), updated.getBenchmarkYear());
    }

    @Test
    @DisplayName("更新为非正数 → 拒绝")
    void update_rejectsNonPositive() {
        IndustryBenchmark existing = sample("II", 2026, "0.7985");
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class,
                () -> service.update(1L, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("删除是软删——已发放的记录需要能回溯依据")
    void delete_isSoftDelete() {
        IndustryBenchmark existing = sample("II", 2026, "0.7985");
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(IndustryBenchmark.class))).thenAnswer(i -> i.getArgument(0));

        service.delete(1L);

        assertTrue(existing.getDeleted(), "应为软删除而非物理删除");
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("删除不存在的记录 → 抛错")
    void delete_missingThrows() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.delete(999L));
    }
}