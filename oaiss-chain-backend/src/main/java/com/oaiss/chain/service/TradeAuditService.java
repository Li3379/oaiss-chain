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
 *   <li><b>驳回必须解冻而非冲正</b>——待审期资产从未转移（卖方额度一直在挂单冻结里、
 *       买方碳币一直在冻结里），所以驳回是纯解冻。调用假定转移已发生的
 *       settleSell/rollback 会造成无中生有，测试用 never() 显式禁止</li>
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
    private final CreditScoreService creditScoreService;
    private final CarbonCoinService carbonCoinService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询审核工单。
     *
     * @param status            审核态；null 表示全部
     * @param page              页码（1-based）
     * @param size              每页大小
     * @param enterpriseKeyword 企业名称/信用代码关键词（可选，供监管与审核员按企业检索）
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TradeAudit> listByStatus(
            Integer status, Integer page, Integer size, String enterpriseKeyword) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 200);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(safePage - 1, safeSize,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.ASC, "createdAt"));

        // 关键词检索暂未接入（需要 join 企业表），先按状态返回，避免假装支持
        if (status == null) {
            return tradeAuditRepository.findAll(pageable);
        }
        return tradeAuditRepository.findByAuditStatusAndDeletedFalseOrderByCreatedAtAsc(status, pageable);
    }

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
     * 审核通过：**此时才真正结算**，然后上链。
     *
     * <p>与方案 B（先结算再冲正）的关键差别：待审期间卖方的额度一直保持挂单冻结态、
     * 买方的碳币一直保持挂单冻结态，所以通过时才发生第一次资产移动。这样
     * 「待审」才真正意味着「资产被锁定」，而驳回路径退化为纯解冻（更简单、更难出错）。
     */
    @Transactional
    public void approve(Long tradeId, Long auditorId, String remark) {
        Transaction trade = loadTrade(tradeId);
        requirePending(trade, "通过");
        requireRemark(remark);

        BigDecimal quantity = trade.getQuantity();

        // 2026-11-03 模型变更：配额退出资产层，结算对象改为碳币。
        //
        // 原实现四步：卖方配额冻结转已用 + 买方配额入账 + 买方扣碳币 + 卖方收碳币。
        // 现在配额只算不发（企业账上无配额），P2P/拍卖都是「碳币换碳币」：
        // 卖方挂单时冻结的碳币，审核通过后直接转给买方——一进一出，
        // 卖方不再收到任何对价。四步合并为一步。
        carbonCoinService.transferFrozen(trade.getSellerId(), trade.getBuyerId(),
                quantity, tradeId);

        // 3) 状态落定
        trade.setAuditStatus(TradeAudit.AUDIT_APPROVED);
        trade.setStatus(TradeStatusEnum.COMPLETED.getCode());
        trade.setCompletedAt(LocalDateTime.now());
        trade.setAuditRemark(remark);
        transactionRepository.save(trade);

        writeAuditRecord(tradeId, TradeAudit.AUDIT_APPROVED, auditorId, remark, null);

        // 4) 结算完成后才上链：链上存证不可修改，未审先上链会让链上数据成为半成品
        String tradeDataJson = buildTradeDataJson(trade, auditorId);
        eventPublisher.publishEvent(
                new com.oaiss.chain.event.TradeCompletedEvent(this, trade.getId(), tradeDataJson));

        notifyBothParties(trade, "成交审核通过",
                "您的成交已通过审核，额度与资金已结算完成。");

        log.info("成交审核通过并结算: tradeId={}, auditorId={}", tradeId, auditorId);
    }

    /**
     * 审核驳回：**纯解冻**，不做冲正。
     *
     * <p>为什么不是 rollback：待审期间资产从未转移（卖方额度一直在冻结里、买方碳币
     * 一直在冻结里），所以"回滚"没有对象。若此处调用 rollback/settleSell 等
     * 假定转移已发生的操作，会造成无中生有——卖方可交易凭空增加、买方凭空多一份额度。
     */
    @Transactional
    public void reject(Long tradeId, Long auditorId, String remark, String reason) {
        Transaction trade = loadTrade(tradeId);
        requirePending(trade, "驳回");
        requireRemark(remark);

        BigDecimal quantity = trade.getQuantity();

        // 信誉扣分按 enterpriseId 记账，需从 userId 换算（仅驳回路径使用）
        Long sellerEnterpriseId = resolveEnterpriseId(trade.getSellerId());
        Long buyerEnterpriseId = resolveEnterpriseId(trade.getBuyerId());

        // 驳回：解冻**卖方**挂单时冻结的碳币，退回可用余额。
        //
        // 2026-11-03 变更：原实现解冻的是 buyerId（买家冻结的货款），
        // 但现在 P2P/拍卖都不再冻结买方——冻结的是卖方的碳币。
        // 若继续对买家 unfreezeBalance，会让买家**凭空多出一笔币**
        // （unfreeze 会按冻结额截断并把差额计入可用余额，见 CarbonCoinService.unfreezeBalance）。
        //
        // 卖方在待审期也未入账（transferFrozen 尚未执行），故无需扣回。
        // 配额四列在待审期同样未被修改，也无需回滚。
        carbonCoinService.unfreezeBalance(trade.getSellerId(), quantity);

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

    /** 交易里存的是 userId，信誉账本按 enterpriseId 记账，需换算（驳回路径专用） */
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