package com.oaiss.chain.service;

import com.oaiss.chain.aop.AuditLogWriter;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.MatchingResultResponse;
import com.oaiss.chain.entity.OperationLog;
import com.oaiss.chain.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * ==============================================================================
 * 【业务模块】：12. 碳配额双向连续竞价拍卖 (Double Auction) — 自动撮合驱动器
 * 【服务角色】：系统后台（无用户主体）
 * 【对应前端页面】：
 *   - 企业端交易大厅: views/enterprise/TradingMarket.vue (/enterprise/trading/market) — 被动获得成交通知
 *   - 管理员端撮合控制台: views/admin/AuctionMatching.vue (/admin/trading/matching) — 手动立即出清
 * 【设计意图】：
 *   撮合引擎本身是「批量集合竞价」——每轮把全市场买盘按价格降序、卖盘按价格升序双指针扫一遍。
 *   这类算法需要一个外部时钟驱动，否则挂单会永久停在「待撮合」。本调度器即为该时钟：
 *   按固定间隔调用 {@link DoubleAuctionService#executeMatching()}，让买卖盘自动出清。
 *
 * 【并发安全】：真正的串行化由 Service 层 @DistributedLock(key='auction:matching') 保证；
 *   管理员手动触发与定时触发同时到达时，后到者拿不到锁直接失败（本类捕获后忽略，
 *   因为下一轮定时任务会自然补上）。
 *
 * 【审计】：定时触发没有 HTTP 请求，@AuditLog 切面不生效，因此在本类内手工落库一条
 *   系统身份的 OperationLog（仅在真正产生成交时写入，避免每 30 秒刷屏）。
 * ==============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionMatchingScheduler {

    private final DoubleAuctionService doubleAuctionService;
    private final AuditLogWriter auditLogWriter;

    /** 自动撮合开关（测试环境关闭，避免后台线程干扰断言） */
    @Value("${app.auction.auto-matching.enabled:true}")
    private boolean enabled;

    /** 自动撮合间隔（毫秒），供撮合控制台展示 */
    @Value("${app.auction.auto-matching.interval-ms:30000}")
    private long intervalMs;

    public boolean isAutoMatchingEnabled() {
        return enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    /**
     * 周期性集中出清。
     * <p>
     * fixedDelay 从上一次执行结束开始计时，天然避免任务堆积；
     * initialDelay 给应用启动留出预热窗口（Flyway/Redis 连接就绪后再跑第一轮）。
     */
    @Scheduled(
            fixedDelayString = "${app.auction.auto-matching.interval-ms:30000}",
            initialDelayString = "${app.auction.auto-matching.initial-delay-ms:60000}")
    public void runScheduledMatching() {
        if (!enabled) {
            return;
        }
        runMatching("SCHEDULED");
    }

    /**
     * 执行一轮撮合并记录结果。
     *
     * @param trigger 触发来源（SCHEDULED / MANUAL），用于审计描述
     * @return 本轮撮合结果；失败或无成交时返回空列表
     */
    public List<MatchingResultResponse> runMatching(String trigger) {
        try {
            List<MatchingResultResponse> results = doubleAuctionService.executeMatching();
            if (results.isEmpty()) {
                log.debug("[AUCTION-MATCH] trigger={} completed with no match opportunity", trigger);
                return results;
            }
            log.info("[AUCTION-MATCH] trigger={} settled {} match(es)", trigger, results.size());
            recordAudit(trigger, results);
            return results;
        } catch (BusinessException e) {
            // 锁竞争（管理员刚手动触发过）属正常现象，降级为 debug，其余记录 error
            if (ErrorCode.OPERATION_IN_PROGRESS == e.getCode()) {
                log.debug("[AUCTION-MATCH] trigger={} skipped: another matching round is running", trigger);
            } else {
                log.error("[AUCTION-MATCH] trigger={} failed: {}", trigger, e.getMessage(), e);
            }
            return List.of();
        } catch (Exception e) {
            log.error("[AUCTION-MATCH] trigger={} failed: {}", trigger, e.getMessage(), e);
            return List.of();
        }
    }

    private void recordAudit(String trigger, List<MatchingResultResponse> results) {
        if (auditLogWriter == null) {
            return;
        }
        try {
            BigDecimal totalQuantity = results.stream()
                    .map(MatchingResultResponse::getMatchedQuantity)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            OperationLog opLog = OperationLog.builder()
                    .userId(0L)
                    .username("SYSTEM-AUCTION-MATCHER")
                    .userType(0)
                    .module("双向拍卖撮合")
                    .action(trigger)
                    .description(String.format("自动撮合出清 %d 笔，累计成交量 %s 吨", results.size(), totalQuantity))
                    .httpMethod("INTERNAL")
                    .requestUrl("/api/v1/auction/match")
                    .requestParams("{\"trigger\":\"" + trigger + "\"}")
                    .status(1)
                    .build();
            auditLogWriter.saveAsync(opLog);
        } catch (Exception e) {
            log.warn("[AUCTION-MATCH] failed to record audit log: {}", e.getMessage());
        }
    }
}
