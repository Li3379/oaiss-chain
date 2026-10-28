package com.oaiss.chain.service;

import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.entity.OutboxMessage;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.OutboxMessageRepository;
import com.oaiss.chain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
                txHash = blockchainService.commitReportToChain(message.getAggregateId(), message.getPayload());
                carbonReportRepository.findById(message.getAggregateId()).ifPresent(report -> {
                    report.setBlockchainTxHash(txHash);
                    report.setOnChainAt(LocalDateTime.now());
                    carbonReportRepository.save(report);
                });
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
}
