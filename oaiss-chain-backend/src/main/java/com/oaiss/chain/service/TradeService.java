package com.oaiss.chain.service;

import com.oaiss.chain.dto.TradeRequest;
import com.oaiss.chain.dto.TradeResponse;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.TradeStatusEnum;
import com.oaiss.chain.enums.TradeTypeEnum;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.exception.TradeException;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.util.CommonUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 碳交易服务
 * 
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "构造器注入的是 Spring 单例协作者（无状态 Bean），"
                + "Lombok 生成构造器直接持有引用是框架约定；返回副本会破坏代理与事务语义。")
public class TradeService {

    private final TransactionRepository transactionRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final CarbonCoinService carbonCoinService;
    private final CreditScoreService creditScoreService;
    private final PriceLimitService priceLimitService;
    /** 准入证书门禁：未签发 / 已吊销 / 已过期都不得参与交易 */
    private final CertificateGateService certificateGateService;
    /** 成交风控分流：与拍卖撮合同一套规则（大额 / 偏离均价 / 低信誉 / 洗售） */
    private final TradeRiskAssessor tradeRiskAssessor;
    /** 命中风控时建待审工单；审核通过才结算，驳回纯解冻 */
    private final TradeAuditService tradeAuditService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * 创建交易（P2P）
     */
    @DistributedLock(key = "'trade:seller:' + #currentUser.userId", expireTime = 30)
    @Transactional
    public TradeResponse createP2PTrade(JwtUserDetails currentUser, TradeRequest request) {
        if (!TradeTypeEnum.P2P.getCode().equals(request.getTradeType())) {
            throw TradeException.tradeNotFound(0L); // 不匹配类型
        }

        // 如果请求未携带 sellerId，则默认使用当前登录用户作为卖方
        Long sellerId = request.getSellerId() != null ? request.getSellerId() : currentUser.getUserId();
        Long buyerId = request.getBuyerId();

        if (buyerId == null) {
            throw new TradeException(ErrorCode.PARAM_ERROR, "买方ID不能为空");
        }

        if (sellerId.equals(buyerId)) {
            throw TradeException.samePartyError(sellerId);
        }

        // 验证当前用户必须是卖方（防止越权）
        if (!currentUser.getUserId().equals(sellerId)) {
            throw TradeException.unauthorizedTrade(currentUser.getUserId(), sellerId);
        }

        Enterprise sellerEnterprise = enterpriseRepository.findByUserId(sellerId)
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, request.getQuantity()));

        // 信誉分风控闸门：被限制交易或账户冻结的发起方不得创建 P2P 交易
        assertTradeAllowed(sellerEnterprise.getId());

        // P2P 语义（2026-11-03）：卖方转让的是**碳币**，不是配额。
        //
        // 配额已退出资产层（只算不发），碳币成为唯一可交易资产。
        // P2P 因此退化为「碳币换碳币」——1 枚换 1 枚，无价格维度。
        // 单价字段仍保留在 DTO 里（拍卖要用），但 P2P 路径按 1 处理并忽略入参。
        BigDecimal transferAmount = request.getQuantity();
        BigDecimal unitPrice = BigDecimal.ONE;

        // 冻结卖方的碳币（不是配额），防止超额挂单与双花透支
        carbonCoinService.freezeBalance(sellerId, transferAmount);

        BigDecimal totalAmount = transferAmount.multiply(unitPrice);

        Transaction trade = Transaction.builder()
                .tradeNo(CommonUtils.generateTradeId())
                .tradeType(request.getTradeType())
                .sellerId(sellerId)
                .buyerId(buyerId)
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .reportId(request.getReportId())
                .status(TradeStatusEnum.PENDING.getCode())
                .remark(request.getRemark())
                .build();

        trade = transactionRepository.save(trade);

        log.info("P2P trade created: {} ({} -> {}, qty: {}, price: {})", 
                trade.getTradeNo(), sellerId, buyerId, request.getQuantity(), unitPrice);

        return toResponse(trade);
    }

    /**
     * [DEFENSE-TRACE-TRADE-CONFIRM-01]
     * 【业务功能】：P2P 交易确认与双向资产/配额货款两讫结算 (DVP)
     * 【对应前端页面】：views/enterprise/TradingP2P.vue (/enterprise/trading/p2p)
     * 【对应前端API】：api/trade.ts -> confirmTrade()
     * 【可见UI操作】：在“我的挂单/待确认交易”列表点击【确认成交】
     * 【底层核心服务】：TradeService.confirmTrade -> CarbonCoinService.buyQuota/sellQuota
     * 安全措施：
     * 1. 仅允许PENDING状态的交易确认（防止并发重复确认）
     * 2. 立即转移为PROCESSING状态，防止竞态条件
     */
    @Transactional
    public TradeResponse confirmTrade(Long tradeId, Long currentUserId) {
        Transaction trade = transactionRepository.findById(tradeId)
                .orElseThrow(() -> TradeException.tradeNotFound(tradeId));

        if (!trade.getSellerId().equals(currentUserId) && !trade.getBuyerId().equals(currentUserId)) {
            throw TradeException.tradeNotFound(tradeId);
        }

        TradeStatusEnum status = TradeStatusEnum.fromCode(trade.getStatus());
        // 严格检查：仅PENDING状态允许确认（防止并发确认和重复确认）
        if (status != TradeStatusEnum.PENDING) {
            throw TradeException.orderCompleted(tradeId);
        }

        final BigDecimal tradeQuantity = trade.getQuantity();
        Enterprise sellerEnterprise = enterpriseRepository.findByUserId(trade.getSellerId())
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, tradeQuantity));
        Enterprise buyerEnterprise = enterpriseRepository.findByUserId(trade.getBuyerId())
                .orElseThrow(() -> TradeException.tradeNotFound(tradeId));

        // 结算门禁：挂单时的校验覆盖不了「冻结期内被吊销」的情况——此刻才真正发生
        // 配额与碳币的交割，必须以双方当时的证书状态为准，任一方不合规即整笔拒绝。
        // 放在状态改写之前，避免拒绝时把交易留在 PROCESSING 的半途状态。
        certificateGateService.assertEnterpriseAdmitted(sellerEnterprise.getId());
        certificateGateService.assertEnterpriseAdmitted(buyerEnterprise.getId());

        // ===== 风控分流（与拍卖撮合同口径）=====
        //
        // 历史缺口：拍卖撮合走 TradeRiskAssessor 分流，命中则生成待审工单、
        // 资产留在冻结态等审核；而 P2P 确认**确认即结算**，完全没有风控。
        // 同一个「成交审核」业务，两条通道口径不一致——P2P 成了绕开审核的后门。
        //
        // 现在把分流前置到任何资产移动之前：命中则建工单、保持 PROCESSING、
        // 资产一分不动。审核通过才发生首次移动（TradeAuditService.approve），
        // 驳回则纯解冻（reject）。这与拍卖侧完全对称。
        TradeRiskAssessor.Decision riskDecision = assessP2pRisk(trade);
        if (riskDecision.requiresAudit()) {
            trade.setAuditStatus(com.oaiss.chain.entity.TradeAudit.AUDIT_PENDING);
            trade = transactionRepository.save(trade);
            tradeAuditService.createPendingAudit(trade.getId(), riskDecision.reasons());

            notificationService.sendBatchNotificationsAsync(
                    java.util.List.of(trade.getSellerId(), trade.getBuyerId()),
                    "P2P 成交待审核",
                    "您的 P2P 交易 " + trade.getTradeNo() + " 已达成 " + tradeQuantity
                            + " 吨，成交价 " + trade.getUnitPrice()
                            + " 碳币/吨，正在审核中。审核通过后资金与配额才会结算。",
                    "TRADE");

            log.info("P2P confirm routed to audit: tradeId={}, qty={}, price={}, reasons={}",
                    tradeId, tradeQuantity, trade.getUnitPrice(), riskDecision.reasons());
            return toResponse(trade);
        }

        // 立即将状态设置为PROCESSING并保存，防止并发请求重复操作
        trade.setStatus(TradeStatusEnum.PROCESSING.getCode());
        transactionRepository.save(trade);

        // P2P 成交结算（2026-11-03）：转让碳币，不动配额。
        //
        // 原实现是 DVP 双向：买方 buyQuota（扣碳币）+ 卖方 sellQuota（收碳币），
        // 同时卖方 carbonFrozen 核减、买方 carbonTradable 增加——即「用币买配额」。
        //
        // 现在配额已退出资产层（只算不发），P2P 退化为「碳币换碳币」：
        // 卖方挂单时已冻结 N 枚，成交时这 N 枚转给买方，卖方不再收到任何币。
        // 因此这里只需一进一出，不能再用 buyQuota/sellQuota（那是双向计价语义）。
        BigDecimal transferAmount = trade.getQuantity();
        carbonCoinService.transferFrozen(trade.getSellerId(), trade.getBuyerId(),
                transferAmount, trade.getId());

        // 配额侧完全不动：carbonTradable / carbonFrozen / carbonUsed 均不修改。
        // 但这三列的历史语义仍需保留在库中供审计，故不做清零。

        trade.setStatus(TradeStatusEnum.COMPLETED.getCode());
        trade.setCompletedAt(LocalDateTime.now());
        trade = transactionRepository.save(trade);

        // 发布交易完成事件，驱动异步区块链存证
        String tradeDataJson = String.format(
                "{\"tradeNo\":\"%s\",\"tradeType\":\"P2P\",\"sellerId\":%d,\"buyerId\":%d,\"quantity\":%s,\"unitPrice\":%s,\"totalAmount\":%s,\"completedAt\":\"%s\"}",
                trade.getTradeNo(), trade.getSellerId(), trade.getBuyerId(), trade.getQuantity(), trade.getUnitPrice(), trade.getTotalAmount(), trade.getCompletedAt()
        );
        eventPublisher.publishEvent(new com.oaiss.chain.event.TradeCompletedEvent(this, trade.getId(), tradeDataJson));

        // 通知对手方（非确认方）：交易已确认完成
        Long counterpartyId = currentUserId.equals(trade.getSellerId()) ? trade.getBuyerId() : trade.getSellerId();
        notificationService.sendNotificationAsync(counterpartyId,
                "P2P交易已完成", "您的 P2P 交易 " + trade.getTradeNo() + " 已被对方确认，交易完成",
                "TRADE", trade.getId(), "TRADE");

        log.info("Trade completed: {}", trade.getTradeNo());
        return toResponse(trade);
    }

    /**
     * 取消交易
     */
    @Transactional
    public TradeResponse cancelTrade(Long tradeId, JwtUserDetails currentUser) {
        Transaction trade = transactionRepository.findById(tradeId)
                .orElseThrow(() -> TradeException.tradeNotFound(tradeId));

        // 权限校验：仅卖方、买方或管理员允许取消
        boolean isParticipant = currentUser != null && (currentUser.isAdmin()
                || (trade.getSellerId() != null && trade.getSellerId().equals(currentUser.getUserId()))
                || (trade.getBuyerId() != null && trade.getBuyerId().equals(currentUser.getUserId())));
        if (!isParticipant) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权取消非本人参与的交易");
        }

        TradeStatusEnum status = TradeStatusEnum.fromCode(trade.getStatus());
        if (!status.isCancellable()) {
            throw TradeException.orderCancelled(tradeId);
        }

        trade.setStatus(TradeStatusEnum.CANCELLED.getCode());
        trade = transactionRepository.save(trade);

        // 撤单解冻卖方的碳币（2026-11-03：挂单冻的是碳币，不是配额）
        final BigDecimal cancelQty = trade.getQuantity();
        final Long sellerUserId = trade.getSellerId();
        carbonCoinService.unfreezeBalance(sellerUserId, cancelQty);

        // 通知对手方：订单已取消
        Long counterpartyId = currentUser.getUserId().equals(trade.getSellerId()) ? trade.getBuyerId() : trade.getSellerId();
        notificationService.sendNotificationAsync(counterpartyId,
                "P2P交易已取消", "您参与的 P2P 交易 " + trade.getTradeNo() + " 已被取消",
                "TRADE", trade.getId(), "TRADE");

        log.info("Trade cancelled: {} by user {}", trade.getTradeNo(), currentUser.getUsername());
        return toResponse(trade);
    }

    /**
     * 获取交易详情（含权限校验）
     */
    @Transactional(readOnly = true)
    public TradeResponse getTrade(Long tradeId, JwtUserDetails currentUser) {
        Transaction trade = transactionRepository.findById(tradeId)
                .orElseThrow(() -> TradeException.tradeNotFound(tradeId));

        // 权限校验：管理员/审核员/第三方可查看所有，企业用户只能查看自己参与的交易
        boolean isParticipant = trade.getSellerId().equals(currentUser.getUserId())
                || trade.getBuyerId().equals(currentUser.getUserId());
        if (!currentUser.isAdmin() && !currentUser.isReviewer() && !currentUser.hasRole("THIRD_PARTY") && !isParticipant) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权查看此交易详情");
        }

        return toResponse(trade);
    }

    /**
     * 分页查询交易
     */
    public Page<TradeResponse> listTrades(Long sellerId, Long buyerId,
            Integer tradeType, Integer status, Integer page, Integer size) {
        size = CommonUtils.sanitizePageSize(size);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> trades = transactionRepository.search(sellerId, buyerId, tradeType, status, pageable);
        Map<Long, String> userNames = resolveUserNames(trades.getContent());
        return trades.map(t -> toResponse(t, userNames));
    }

    /**
     * 查询我的交易（作为买方或卖方）
     */
    public Page<TradeResponse> listMyTrades(JwtUserDetails currentUser,
            Integer tradeType, Integer status, String tradeNo, String keyword, String identity,
            LocalDateTime startTime, LocalDateTime endTime,
            Integer page, Integer size) {
        size = CommonUtils.sanitizePageSize(size);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Long userId = currentUser.getUserId();
        Page<Transaction> trades = transactionRepository.findByUserIdRelated(
                userId,
                tradeType,
                status,
                normalizeFilterValue(tradeNo),
                normalizeFilterValue(keyword),
                normalizeIdentity(identity),
                startTime,
                endTime,
                pageable);
        Map<Long, String> userNames = resolveUserNames(trades.getContent());
        return trades.map(t -> toResponse(t, userNames));
    }

    // ==================== 私有方法 ====================

    /**
     * 交易准入校验（证书 + 信誉分）。
     *
     * <p>顺序有意为之：先判证书再判信誉分。证书是「有没有资格进场」，
     * 信誉分是「进场后守不守规矩」——先回答前者，错误提示才不会误导。</p>
     *
     * @param enterpriseId 发起方企业ID
     */
    private void assertTradeAllowed(Long enterpriseId) {
        if (enterpriseId == null) {
            return;
        }
        certificateGateService.assertEnterpriseAdmitted(enterpriseId);
        if (!creditScoreService.checkTradePermission(enterpriseId)) {
            throw TradeException.creditTradeRestricted(
                    "您的信誉分过低，暂不可参与碳交易（账户可能已被限制交易或冻结），请联系平台管理员");
        }
    }

    /**
     * 批量解析交易涉及的用户名称（解决 N+1 查询问题）
     */
    private Map<Long, String> resolveUserNames(java.util.List<Transaction> trades) {
        Set<Long> userIds = new HashSet<>();
        for (Transaction t : trades) {
            userIds.add(t.getSellerId());
            if (t.getBuyerId() > 0) {
                userIds.add(t.getBuyerId());
            }
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
    }

    private TradeResponse toResponse(Transaction trade) {
        String sellerName = userRepository.findById(trade.getSellerId())
                .map(User::getRealName).orElse("未知");
        String buyerName = trade.getBuyerId() > 0
                ? userRepository.findById(trade.getBuyerId()).map(User::getRealName).orElse("未知")
                : "待定";

        return buildResponse(trade, sellerName, buyerName);
    }

    private TradeResponse toResponse(Transaction trade, Map<Long, String> userNames) {
        String sellerName = userNames.getOrDefault(trade.getSellerId(), "未知");
        String buyerName = trade.getBuyerId() > 0
                ? userNames.getOrDefault(trade.getBuyerId(), "未知")
                : "待定";

        return buildResponse(trade, sellerName, buyerName);
    }

    private TradeResponse buildResponse(Transaction trade, String sellerName, String buyerName) {
        return TradeResponse.builder()
                .id(trade.getId())
                .tradeNo(trade.getTradeNo())
                .tradeType(trade.getTradeType())
                .tradeTypeText(safeTradeTypeText(trade.getTradeType()))
                .sellerId(trade.getSellerId())
                .sellerName(sellerName)
                .buyerId(trade.getBuyerId())
                .buyerName(buyerName)
                .quantity(trade.getQuantity())
                .unitPrice(trade.getUnitPrice())
                .totalAmount(trade.getTotalAmount())
                .reportId(trade.getReportId())
                .status(trade.getStatus())
                .statusText(safeTradeStatusText(trade.getStatus()))
                .remark(trade.getRemark())
                .blockchainTxHash(trade.getBlockchainTxHash())
                .completedAt(trade.getCompletedAt())
                .createdAt(trade.getCreatedAt())
                .auditStatus(trade.getAuditStatus())
                .build();
    }

    /**
     * P2P 成交的风控分流评估——与拍卖撮合 {@code DoubleAuctionService} 同一套规则。
     *
     * <p><b>为什么取数失败不阻断成交</b>：参考价与信誉分来自历史数据，
     * 冷启动时可能查不到。此时降级为「无参考价 / 无信誉」，
     * 由 {@link TradeRiskAssessor} 的缺失值规则处理（无均价跳过价格检查、
     * 无信誉按最低分）。若在这里抛异常，等于让「数据不足」变成「禁止交易」，
     * 而拍卖侧的处理是继续撮合——两侧口径必须一致。</p>
     */
    private TradeRiskAssessor.Decision assessP2pRisk(Transaction trade) {
        BigDecimal referencePrice = null;
        Integer buyerCredit = null;
        Integer sellerCredit = null;
        try {
            referencePrice = transactionRepository.findAverageUnitPriceSince(
                    LocalDateTime.now().minusDays(
                            tradeRiskAssessor.getProperties().getPriceReferenceDays()));
            Map<Long, Integer> scores =
                    creditScoreService.getScoresByUserIds(
                            Set.of(trade.getSellerId(), trade.getBuyerId()));
            if (scores != null) {
                sellerCredit = scores.get(trade.getSellerId());
                buyerCredit = scores.get(trade.getBuyerId());
            }
        } catch (Exception e) {
            // 参数含 e.getMessage()，显式守卫既满足 PMD GuardLogStatement，也避免异常路径上的无谓求值
            if (log.isWarnEnabled()) {
                log.warn("[AUDIT] P2P 风险分流取数失败，降级评估: {}", e.getMessage());
            }
        }
        // P2P 没有撮合环节，不存在自成交/洗售嫌疑，该维度传 false。
        return tradeRiskAssessor.assess(trade, referencePrice, buyerCredit, sellerCredit, false);
    }

    private String normalizeFilterValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeIdentity(String identity) {
        String normalized = normalizeFilterValue(identity);
        if (normalized == null) {
            return null;
        }

        String lowercase = normalized.toLowerCase(Locale.ROOT);
        if ("buyer".equals(lowercase) || "seller".equals(lowercase)) {
            return lowercase;
        }
        return null;
    }

    private String safeTradeTypeText(Integer code) {
        try {
            TradeTypeEnum e = TradeTypeEnum.fromCode(code);
            return e.getDescription();
        } catch (IllegalArgumentException ex) {
            return "未知";
        }
    }

    private String safeTradeStatusText(Integer code) {
        try {
            TradeStatusEnum e = TradeStatusEnum.fromCode(code);
            return e.getDescription();
        } catch (IllegalArgumentException ex) {
            return "未知";
        }
    }
}
