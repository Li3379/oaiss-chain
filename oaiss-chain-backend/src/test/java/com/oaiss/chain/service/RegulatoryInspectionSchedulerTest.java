package com.oaiss.chain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 监管定期核查调度器测试。
 *
 * <p>只验证调度层的三个行为：开关生效、异常不逸出、成功时调用一次。
 * 扫描规则本身由 {@link RegulatoryFindingServiceTest} 覆盖。
 */
class RegulatoryInspectionSchedulerTest {

    private RegulatoryFindingService findingService;
    private RegulatoryInspectionScheduler scheduler;

    @BeforeEach
    void setUp() {
        findingService = mock(RegulatoryFindingService.class);
        scheduler = new RegulatoryInspectionScheduler(findingService);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "intervalMs", 21600000L);
    }

    @Test
    @DisplayName("开关开启时执行一轮扫描")
    void enabled_runsScan() {
        when(findingService.scanAll()).thenReturn(Map.of(10L, List.of()));

        scheduler.runScheduledInspection();

        verify(findingService).scanAll();
    }

    @Test
    @DisplayName("开关关闭时不执行（测试与降级场景）")
    void disabled_doesNotScan() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.runScheduledInspection();

        verify(findingService, never()).scanAll();
    }

    @Test
    @DisplayName("扫描抛异常时不逸出（巡检失败不能打断调度线程）")
    void scanThrows_doesNotPropagate() {
        when(findingService.scanAll()).thenThrow(new RuntimeException("db down"));

        // 若异常逸出，测试会直接失败——这里就是断言
        scheduler.runScheduledInspection();

        verify(findingService).scanAll();
    }

    @Test
    @DisplayName("配置可读，供监管工作台展示巡检节奏")
    void configAccessors() {
        assertTrue(scheduler.isInspectionEnabled());
        assertEquals(21600000L, scheduler.getIntervalMs());
    }
}