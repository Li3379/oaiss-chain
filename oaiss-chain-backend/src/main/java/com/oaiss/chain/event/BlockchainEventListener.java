package com.oaiss.chain.event;

import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.service.BlockchainServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * Handles blockchain commit asynchronously after report certification transaction commits.
 * Uses @TransactionalEventListener(AFTER_COMMIT) to ensure the DB transaction is complete
 * before attempting the blockchain write, avoiding long-running operations inside
 * the request thread.
 *
 * @author OAISS Team
 */
@Component
public class BlockchainEventListener {

    private static final Logger log = LoggerFactory.getLogger(BlockchainEventListener.class);

    private final BlockchainServicePort blockchainService;
    private final CarbonReportRepository carbonReportRepository;
    private final TransactionRepository transactionRepository;
    private final com.oaiss.chain.service.OutboxService outboxService;

    public BlockchainEventListener(BlockchainServicePort blockchainService,
                                   CarbonReportRepository carbonReportRepository,
                                   TransactionRepository transactionRepository,
                                   com.oaiss.chain.service.OutboxService outboxService) {
        this.blockchainService = blockchainService;
        this.carbonReportRepository = carbonReportRepository;
        this.transactionRepository = transactionRepository;
        this.outboxService = outboxService;
    }

    @Async("blockchainExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReportCertified(ReportCertifiedEvent event) {
        Long reportId = event.getReportId();
        log.info("Async blockchain outbox dispatch started for report {}", reportId);

        try {
            var outboxMsg = outboxService.saveOutboxMessage(
                    com.oaiss.chain.service.OutboxService.AGGREGATE_REPORT,
                    reportId,
                    "CreateCarbonReport",
                    event.getReportData());
            outboxService.processMessage(outboxMsg.getId());
            log.info("Blockchain outbox dispatch processed for report {}", reportId);
        } catch (Exception e) {
            log.error("Blockchain outbox dispatch failed for report {}: {}", reportId, e.getMessage(), e);
        }
    }

    /**
     * R114: Handle trade completion event - commit trade to blockchain asynchronously via outbox.
     */
    @Async("blockchainExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTradeCompleted(TradeCompletedEvent event) {
        Long tradeId = event.getTradeId();
        log.info("Async blockchain outbox dispatch started for trade {}", tradeId);
        try {
            var outboxMsg = outboxService.saveOutboxMessage(
                    com.oaiss.chain.service.OutboxService.AGGREGATE_TRADE,
                    tradeId,
                    "CreateTradeRecord",
                    event.getTradeData());
            outboxService.processMessage(outboxMsg.getId());
            log.info("Blockchain outbox dispatch processed for trade {}", tradeId);
        } catch (Exception e) {
            log.error("Blockchain outbox dispatch failed for trade {}: {}", tradeId, e.getMessage(), e);
        }
    }
}