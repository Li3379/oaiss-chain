package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.TradeAudit;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.enums.CreditEventTypeEnum;
import com.oaiss.chain.enums.TradeStatusEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.TradeAuditRepository;
import com.oaiss.chain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成交审核服务：把「成交」与「结算」拆开的那一半。
 *
 * <p>核心不变量（由 TradeAuditServiceTest 逐条守住）：
 * <ol>
 *   <li><b>只有待审可裁决</b>——已通过/已驳回再裁决必须失败。这不是幂等，
 *       而是明确拒绝：重复裁决意味着重复结算，钱会凭空多出来</li>
 *   <li><b>驳回必须全额回滚</b>——额度走 QuotaLedgerService、碳币走
 *       CarbonCoinService.refundRejectedTrade，两条路径都留痕</li>
 *   <li><b>驳回必须扣分并归因</b>——CreditEvent 要能指回这笔交易</li>
 *   <li><b>裁决必须留痕</b>——审核人、时间、意见、命中的风险理由全部落库</li>
 * </ol>
 *
 * <p>审核通过才发 {@code TradeCompletedEvent}：链上存证一旦写入不可改，
 * 「未审先上链」会让链上数据成为半成品。这与报告域「审核通过即自动上链」
 * 的既有语义一致。
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeAuditService {

    private final TransactionRepository transactionRepository;
    private final TradeAuditRepository tradeAuditRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final QuotaLedgerService quotaLedgerService;
    private final CreditScoreService creditScoreService;
    private final CarbonCoinService carbonCoinService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建待审记录。由撮合路径在分流器判定需要人工审核时调用。
     *
     * @param tradeId     交易ID
     * @param riskReasons 分流器给出的风险理由列表
     */
    @Transactional
    public TradeAudit createPendingAudit(Long tradeId, List<String> riskReasons) {
        Transaction trade = loadTrade(tradeId);

        trade.setAuditStatus(TradeAudit.AUDIT_PENDING);
        transactionRepository.save(trade);

        TradeAudit record = TradeAudit.builder()
                .tradeId(tradeId)
                .auditStatus(TradeAudit.AUDIT_PENDING)
                .riskReasons(toJsonArray(riskReasons))
                .build();
        return tradeAuditRepository.save(record);
    }

    /**
     * 审核通过：完成结算并上链。
     */
    @Transactional
    public void approve(Long tradeId, Long auditorId, String remark) {
        Transaction trade = loadTrade(tradeId);
        requirePending(trade, "通过");
        requireRemark(remark);

        trade.setAuditStatus(TradeAudit.AUDIT_APPROVED);
        trade.setStatus(TradeStatusEnum.COMPLETED.getCode());
        trade.setCompletedAt(LocalDateTime.now());
        trade.setAuditRemark(remark);
        transactionRepository.save(trade);

        writeAuditRecord(tradeId, TradeAudit.AUDIT_APPROVED, auditorId, remark, null);

        // 审核通过后才允许上链：链上存证不可修改，未审先上链会让链上数据成为半成品
        String tradeDataJson = buildTradeDataJson(trade, auditorId);
        eventPublisher.publishEvent(
                new com.oaiss.chain.event.TradeCompletedEvent(this, trade.getId(), tradeDataJson));

        notifyBothParties(trade, "成交审核通过",
                "您的成交已通过审核，额度与资金已结算完成。");

        log.info("成交审核通过: tradeId={}, auditorId={}", tradeId, auditorId);
    }

    /**
     * 审核驳回：全额回滚额度与碳币，并对买卖双方扣信誉分。
     *
     * @param reason 驳回的主理由（取 {@code TradeAudit.REASON_*} 常量）
     */
    @Transactional
    public void reject(Long tradeId, Long auditorId, String remark, String reason) {
        Transaction trade = loadTrade(tradeId);
        requirePending(trade, "驳回");
        requireRemark(remark);

        BigDecimal quantity = trade.getQuantity();
        BigDecimal amount = trade.getTotalAmount();

        // 1) 额度回滚：卖方被扣减的额度、买方买入的额度都原路退回
        Long sellerEnterpriseId = resolveEnterpriseId(trade.getSellerId());
        Long buyerEnterpriseId = resolveEnterpriseId(trade.getBuyerId());

        quotaLedgerService.rollback(sellerEnterpriseId, quantity,
                "成交审核驳回，卖方额度退回", auditorId, "AUDIT", tradeId);
        quotaLedgerService.rollback(buyerEnterpriseId, quantity,
                "成交审核驳回，买方额度退回", auditorId, "AUDIT", tradeId);

        // 2) 碳币回滚：买方货款退回、卖方货款扣回
        carbonCoinService.refundRejectedTrade(tradeId, trade.getBuyerId(),
                trade.getSellerId(), amount);

        // 3) 信誉扣分并归因到该笔交易（双方都参与价格形成，只罚一方会留下规避口子）
        creditScoreService.deductPointsForTrade(sellerEnterpriseId,
                CreditEventTypeEnum.TRADE_AUDIT_REJECTED.getCode(),
                "成交被驳回: " + remark, auditorId, tradeId);
        creditScoreService.deductPointsForTrade(buyerEnterpriseId,
                CreditEventTypeEnum.TRADE_AUDIT_REJECTED.getCode(),
                "成交被驳回: " + remark, auditorId, tradeId);

        // 4) 交易置已取消、审核态置驳回
        trade.setAuditStatus(TradeAudit.AUDIT_REJECTED);
        trade.setStatus(TradeStatusEnum.CANCELLED.getCode());
        trade.setAuditRemark(remark);
        transactionRepository.save(trade);

        writeAuditRecord(tradeId, TradeAudit.AUDIT_REJECTED, auditorId, remark, reason);

        notifyBothParties(trade, "成交审核驳回",
                "您的成交未通过审核：" + remark + "。额度与资金已原路退回。");

        log.info("成交审核驳回: tradeId={}, auditorId={}, reason={}", tradeId, auditorId, reason);
    }

    // ==================== 内部方法 ====================

    private Transaction loadTrade(Long tradeId) {
        return transactionRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND,
                        "交易不存在: " + tradeId));
    }

    private void requirePending(Transaction trade, String action) {
        Integer current = trade.getAuditStatus();
        if (current == null || !current.equals(TradeAudit.AUDIT_PENDING)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该交易当前审核态为 " + current + "，不可" + action
                            + "（仅待审核状态可裁决，防止重复结算）");
        }
    }

    private void requireRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "审核意见不能为空——裁决必须可解释、可追责");
        }
    }

    /** 交易里存的是 userId，额度账本按 enterpriseId 记账，需换算 */
    private Long resolveEnterpriseId(Long userId) {
        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "交易方未关联企业: userId=" + userId));
        return enterprise.getId();
    }

    private void writeAuditRecord(Long tradeId, int status, Long auditorId,
                                   String remark, String decisionReason) {
        TradeAudit record = TradeAudit.builder()
                .tradeId(tradeId)
                .auditStatus(status)
                .auditRemark(remark)
                .decisionReason(decisionReason)
                .auditorId(auditorId)
                .auditedAt(LocalDateTime.now())
                .build();
        tradeAuditRepository.save(record);
    }

    private String toJsonArray(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(reasons.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    private String buildTradeDataJson(Transaction trade, Long auditorId) {
        return String.format(
                "{\"tradeNo\":\"%s\",\"tradeType\":\"AUCTION\",\"sellerId\":%d,\"buyerId\":%d,"
                        + "\"quantity\":%s,\"unitPrice\":%s,\"totalAmount\":%s,"
                        + "\"completedAt\":\"%s\",\"auditMode\":\"MANUAL\",\"auditorId\":%d}",
                trade.getTradeNo(), trade.getSellerId(), trade.getBuyerId(),
                trade.getQuantity(), trade.getUnitPrice(), trade.getTotalAmount(),
                trade.getCompletedAt(), auditorId);
    }

    private void notifyBothParties(Transaction trade, String title, String content) {
        notificationService.sendBatchNotificationsAsync(
                List.of(trade.getSellerId(), trade.getBuyerId()),
                title, content, "TRADE");
    }
}