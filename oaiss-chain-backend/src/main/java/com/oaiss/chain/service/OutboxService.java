package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.dto.CarbonReportSnapshot;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.OutboxMessage;
import com.oaiss.chain.enums.ReportStatusEnum;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.OutboxMessageRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.util.EmissionDataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 区块链事务发件箱服务 (Transactional Outbox Service)
 * 保证业务数据库提交与区块链联盟链存证之间的 100% 最终一致性
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final String AGGREGATE_REPORT = "REPORT";
    public static final String AGGREGATE_TRADE = "TRADE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD = "DEAD";

    private static final int MAX_RETRIES = 5;

    private final OutboxMessageRepository outboxMessageRepository;
    private final BlockchainServicePort blockchainService;
    private final CarbonReportRepository carbonReportRepository;
    private final TransactionRepository transactionRepository;
    /** v2 快照需要从 emissionData 原文明细里取出过程排放三项 */
    private final ObjectMapper objectMapper;

    /**
     * 在业务本地事务中保存待上链发件箱消息
     */
    @Transactional
    public OutboxMessage saveOutboxMessage(String aggregateType, Long aggregateId, String topic, String payload) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        OutboxMessage message = OutboxMessage.builder()
                .messageId(messageId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .topic(topic)
                .payload(payload)
                .status(STATUS_PENDING)
                .retryCount(0)
                .build();

        message = outboxMessageRepository.save(message);
        log.info("[OUTBOX] Stored pending outbox message: id={}, aggregate={}:{}",
                message.getId(), aggregateType, aggregateId);
        return message;
    }

    /**
     * 处理单条发件箱消息并向区块链提交存证
     */
    @Transactional
    public boolean processMessage(Long outboxMessageId) {
        OutboxMessage message = outboxMessageRepository.findById(outboxMessageId).orElse(null);
        if (message == null || STATUS_SENT.equals(message.getStatus()) || STATUS_DEAD.equals(message.getStatus())) {
            return false;
        }

        log.info("[OUTBOX] Processing outbox message id={}, type={}, aggId={}, retry={}",
                message.getId(), message.getAggregateType(), message.getAggregateId(), message.getRetryCount());

        try {
            String txHash;
            if (AGGREGATE_REPORT.equalsIgnoreCase(message.getAggregateType())) {
                // 报告分支必须与主链路走同一个 v2 快照入口。
                // 原先这里调 v1 `commitReportToChain(payload)`：它只把 payload 原文上链，
                // 链上拿不到企业归属、周期、总量与签名——补救写进来的存证和正常上链的存证
                // 是两种不同结构，事后无法统一核验。同时它不校验报告状态，
                // 任何人触发就能把草稿「存证」上链。
                txHash = commitReportViaSnapshot(message.getAggregateId());
            } else if (AGGREGATE_TRADE.equalsIgnoreCase(message.getAggregateType())) {
                txHash = blockchainService.commitTradeToChain(message.getAggregateId(), message.getPayload());
                transactionRepository.findById(message.getAggregateId()).ifPresent(trade -> {
                    trade.setBlockchainTxHash(txHash);
                    transactionRepository.save(trade);
                });
            } else {
                throw new IllegalArgumentException("Unknown aggregate type: " + message.getAggregateType());
            }

            message.setStatus(STATUS_SENT);
            message.setErrorMessage(null);
            outboxMessageRepository.save(message);
            log.info("[OUTBOX] Outbox message committed successfully: id={}, txHash={}", message.getId(), txHash);
            return true;

        } catch (Exception e) {
            int retries = message.getRetryCount() + 1;
            message.setRetryCount(retries);
            message.setErrorMessage(e.getMessage());

            if (retries >= MAX_RETRIES) {
                message.setStatus(STATUS_DEAD);
                message.setNextRetryAt(null);
                log.error("[OUTBOX-DEAD] Outbox message exceeded max retries and moved to DEAD-LETTER: id={}, aggId={}",
                        message.getId(), message.getAggregateId(), e);
            } else {
                message.setStatus(STATUS_FAILED);
                // 指数退避: 2^retries 秒
                long backoffSeconds = (long) Math.pow(2, retries);
                message.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                log.warn("[OUTBOX-RETRY] Outbox commit failed, scheduled retry #{} in {}s: id={}",
                        retries, backoffSeconds, message.getId());
            }

            outboxMessageRepository.save(message);
            return false;
        }
    }

    /**
     * 周期性后台补偿调度器：扫描未上链或达到重试时隙的发件箱记录
     */
    @Scheduled(fixedDelay = 10000)
    @DistributedLock(key = "'outbox:retry:scanner'", expireTime = 10, waitTime = 0)
    public void scheduleRetryPendingMessages() {
        List<OutboxMessage> retryableMessages = outboxMessageRepository.findPendingOrRetryableMessages(
                LocalDateTime.now(), PageRequest.of(0, 50));

        if (retryableMessages.isEmpty()) {
            return;
        }

        log.info("[OUTBOX-SCHEDULER] Found {} pending/retryable outbox messages to process", retryableMessages.size());
        for (OutboxMessage msg : retryableMessages) {
            try {
                processMessage(msg.getId());
            } catch (Exception e) {
                log.error("[OUTBOX-SCHEDULER] Error processing message id={}: {}", msg.getId(), e.getMessage());
            }
        }
    }

    /**
     * 按 v2 快照口径上链报告，并回写哈希。
     *
     * <p>与 {@code CarbonService#applyApprovedReportSideEffects} 保持同一存证结构；
     * 报告不存在或不是权威状态（已通过 / 已上链）时拒绝，避免把草稿或已驳回内容写到链上。</p>
     */
    private String commitReportViaSnapshot(Long reportId) {
        CarbonReport report = carbonReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在: " + reportId));

        Integer status = report.getStatus();
        if (!ReportStatusEnum.authoritativeCodes().contains(status)) {
            throw new IllegalStateException(
                    "报告状态不允许上链: " + status + "（仅接受审核通过/已上链）");
        }
        if (report.getBlockchainTxHash() != null && !report.getBlockchainTxHash().isBlank()) {
            // 已有 txHash：说明此前已成功上链，不能重复提交（链码本身也会因 state 已存在而报错）。
            log.info("[OUTBOX] Report {} already on chain, skip duplicate commit", reportId);
            return report.getBlockchainTxHash();
        }

        BigDecimal[] process = EmissionDataValidator.parseProcessEmissions(
                report.getEmissionData(), objectMapper);
        CarbonReportSnapshot snapshot = CarbonReportSnapshot.builder()
                .reportId(report.getId())
                .reportNo(report.getReportNo())
                .enterpriseId(report.getEnterpriseId())
                .accountingPeriod(report.getAccountingPeriod())
                .totalEmission(stringify(report.getTotalEmission()))
                .scope1Emission(stringify(report.getScope1Emission()))
                .scope2Emission(stringify(report.getScope2Emission()))
                .scope3Emission(stringify(report.getScope3Emission()))
                .desulfEmission(stringify(process[0]))
                .transmissionLossEmission(stringify(process[1]))
                .sf6Emission(stringify(process[2]))
                .signature(report.getSignatureData())
                .data(report.getEmissionData())
                .build();

        String txHash = blockchainService.commitReportSnapshot(snapshot);
        report.setBlockchainTxHash(txHash);
        report.setOnChainAt(LocalDateTime.now());
        if (Objects.equals(report.getStatus(), ReportStatusEnum.APPROVED.getCode())) {
            report.setStatus(ReportStatusEnum.ON_CHAIN.getCode());
        }
        carbonReportRepository.save(report);
        return txHash;
    }

    private static String stringify(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
