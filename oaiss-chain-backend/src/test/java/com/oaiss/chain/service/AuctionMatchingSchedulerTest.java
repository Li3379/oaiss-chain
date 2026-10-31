package com.oaiss.chain.service;

import com.oaiss.chain.aop.AuditLogWriter;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.MatchingResultResponse;
import com.oaiss.chain.entity.OperationLog;
import com.oaiss.chain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动撮合调度器行为测试。
 *
 * <p>核心要证明的是「自动撮合真的存在」这一历史缺口已被闭合：
 * 定时任务会调用撮合引擎、有成交时落审计、锁竞争时不刷 error 日志。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuctionMatchingSchedulerTest {

    @Mock
    private DoubleAuctionService doubleAuctionService;

    @Mock
    private AuditLogWriter auditLogWriter;

    @InjectMocks
    private AuctionMatchingScheduler scheduler;

    private static MatchingResultResponse match(String quantity) {
        return MatchingResultResponse.builder()
                .matchedQuantity(new BigDecimal(quantity))
                .build();
    }

    @Test
    @DisplayName("定时任务调用撮合引擎并返回成交结果（自动撮合主路径）")
    void scheduledMatchingInvokesEngine() {
        when(doubleAuctionService.executeMatching()).thenReturn(List.of(match("10.5")));

        var results = scheduler.runMatching("SCHEDULED");

        assertEquals(1, results.size());
        verify(doubleAuctionService).executeMatching();
    }

    @Test
    @DisplayName("有成交时落一条系统身份审计日志，累计成交量正确")
    void recordsAuditWithAggregatedQuantity() {
        when(doubleAuctionService.executeMatching())
                .thenReturn(List.of(match("10.5"), match("4.5")));

        scheduler.runMatching("SCHEDULED");

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(auditLogWriter).saveAsync(captor.capture());
        OperationLog log = captor.getValue();

        assertEquals("SYSTEM-AUCTION-MATCHER", log.getUsername());
        assertEquals("SCHEDULED", log.getAction());
        assertEquals(1, log.getStatus());
        assertTrue(log.getDescription().contains("2 笔"), "描述应包含成交笔数");
        assertTrue(log.getDescription().contains("15"), "描述应包含累计成交量 15");
    }

    @Test
    @DisplayName("无成交时不写审计日志，避免每 30 秒刷屏")
    void skipsAuditWhenNoMatch() {
        when(doubleAuctionService.executeMatching()).thenReturn(List.of());

        var results = scheduler.runMatching("SCHEDULED");

        assertTrue(results.isEmpty());
        verify(auditLogWriter, never()).saveAsync(any());
    }

    @Test
    @DisplayName("锁竞争（另一轮撮合进行中）不向调用方抛错，返回空结果")
    void swallowsLockContention() {
        when(doubleAuctionService.executeMatching())
                .thenThrow(new BusinessException(ErrorCode.OPERATION_IN_PROGRESS, "操作正在处理中，请勿重复提交"));

        var results = scheduler.runMatching("SCHEDULED");

        assertTrue(results.isEmpty());
        verify(auditLogWriter, never()).saveAsync(any());
    }

    @Test
    @DisplayName("引擎异常被兜底，调度线程不因单轮失败而中断")
    void swallowsUnexpectedException() {
        when(doubleAuctionService.executeMatching())
                .thenThrow(new IllegalStateException("db down"));

        var results = scheduler.runMatching("SCHEDULED");

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("定时方法标注 @Scheduled（自动撮合必须由时钟驱动，而非纯人工触发）")
    void scheduledAnnotationPresent() throws NoSuchMethodException {
        var method = AuctionMatchingScheduler.class.getMethod("runScheduledMatching");
        var scheduled = method.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);

        assertTrue(scheduled != null, "runScheduledMatching 必须标注 @Scheduled，否则挂单永不自动出清");
        assertFalse(scheduled.fixedDelayString().isBlank(), "自动撮合必须配置轮询间隔");
    }

    @Test
    @DisplayName("开关关闭时定时任务直接跳过，不调用撮合引擎")
    void disabledSchedulerSkipsEngine() {
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.runScheduledMatching();

        verify(doubleAuctionService, never()).executeMatching();
    }

    @Test
    @DisplayName("开关开启时定时任务触发撮合（配置生效链路）")
    void enabledSchedulerRunsEngine() {
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "enabled", true);
        when(doubleAuctionService.executeMatching()).thenReturn(List.of());

        scheduler.runScheduledMatching();

        verify(doubleAuctionService).executeMatching();
    }
}
