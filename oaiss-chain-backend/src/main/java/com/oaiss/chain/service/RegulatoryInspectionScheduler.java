package com.oaiss.chain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * ==============================================================================
 * 【业务模块】：24. 第三方监管 — 定期核查驱动器 (Regulatory Periodic Inspection)
 * 【服务角色】：系统后台（无用户主体）
 * 【对应前端页面】：views/third-party/Monitor.vue (/third-party/monitor) — 线索清单
 * 【设计意图】：
 *   原始需求明确要求监管「作为特殊的参与者角色，进行数据的定期核查」，
 *   但此前系统里既没有核查任务、也没有核查记录——「定期核查」在文档里写了、
 *   在代码里是零。本调度器把 {@link RegulatoryFindingService#scanAll()} 挂上时钟，
 *   让监管的线索清单会自己长出来，而不是等监管手动翻两万条交易。
 *
 * 【与撮合的差别】：撮合是批量集合竞价、必须有外部时钟才能出清（否则挂单永久停住）；
 *   核查则是低频巡检，节奏以天/小时计，不需要高频。因此默认间隔刻意放宽，
 *   且 initialDelay 给足预热窗口，避免应用刚启动、Flyway 还没跑完就开扫。
 *
 * 【失败隔离】：单个企业扫描异常由 Service 内部捕获并跳过，
 *   不让一个数据异常的企业把整轮巡检打断。
 * ==============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryInspectionScheduler {

    private final RegulatoryFindingService regulatoryFindingService;

    /** 定期核查开关（测试环境关闭，避免后台线程干扰断言） */
    @Value("${app.regulatory.periodic-inspection.enabled:true}")
    private boolean enabled;

    /** 核查间隔（毫秒），默认 6 小时——监管巡检不需要撮合那样的秒级节奏 */
    @Value("${app.regulatory.periodic-inspection.interval-ms:21600000}")
    private long intervalMs;

    public boolean isInspectionEnabled() {
        return enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    /**
     * 周期性核查。
     *
     * <p>fixedDelay 从上一次执行结束计时，天然避免任务堆积；
     * initialDelay 给应用启动留出预热窗口（Flyway/Redis 就绪后再跑第一轮）。
     */
    @Scheduled(
            fixedDelayString = "${app.regulatory.periodic-inspection.interval-ms:21600000}",
            initialDelayString = "${app.regulatory.periodic-inspection.initial-delay-ms:300000}")
    public void runScheduledInspection() {
        if (!enabled) {
            return;
        }
        try {
            var produced = regulatoryFindingService.scanAll();
            log.info("定期监管核查完成: 本轮产出线索涉及 {} 家企业", produced.size());
        } catch (Exception e) {
            // 巡检失败不应影响系统其他部分；下一轮会自然重试
            log.warn("定期监管核查失败，将于下一周期重试: {}", e.getMessage());
        }
    }
}