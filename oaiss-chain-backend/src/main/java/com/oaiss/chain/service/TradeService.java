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

        // 平台限价：必须在任何资源冻结之前——否则注定失败的请求会先冻结配额再被拒
        priceLimitService.validate(request.getUnitPrice(), LocalDateTime.now());

        if (sellerEnterprise.getCarbonTradable().compareTo(request.getQuantity()) < 0) {
            throw TradeException.insufficientQuota(sellerEnterprise.getCarbonTradable(), request.getQuantity());
        }

        // 挂单时冻结卖方可交易配额，防止超额挂单与双花透支
        sellerEnterprise.setCarbonTradable(sellerEnterprise.getCarbonTradable().subtract(request.getQuantity()));
        BigDecimal currentFrozen = sellerEnterprise.getCarbonFrozen() != null ? sellerEnterprise.getCarbonFrozen() : BigDecimal.ZERO;
        sellerEnterprise.setCarbonFrozen(currentFrozen.add(request.getQuantity()));
        enterpriseRepository.save(sellerEnterprise);

        BigDecimal totalAmount = request.getQuantity().multiply(request.getUnitPrice());

        Transaction trade = Transaction.builder()
                .tradeNo(CommonUtils.generateTradeId())
                .tradeType(request.getTradeType())
                .sellerId(sellerId)
                .buyerId(buyerId)
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice())
                .totalAmount(totalAmount)
                .reportId(request.getReportId())
                .status(TradeStatusEnum.PENDING.getCode())
                .remark(request.getRemark())
                .build();

        trade = transactionRepository.save(trade);

        log.info("P2P trade created: {} ({} -> {}, qty: {}, price: {})", 
                trade.getTradeNo(), sellerId, buyerId, request.getQuantity(), request.getUnitPrice());

        return toResponse(trade);
    }

    /**
     * 创建拍卖挂单
     */
    @Transactional
    public TradeResponse createAuctionOrder(JwtUserDetails currentUser, TradeRequest request) {
        Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, request.getQuantity()));

        // 信誉分风控闸门：被限制交易或账户冻结的发起方不得创建拍卖挂单
        assertTradeAllowed(enterprise.getId());

        // 平台限价：与 createP2PTrade 保持一致的报错优先级（价格先于配额）
        priceLimitService.validate(request.getUnitPrice(), LocalDateTime.now());

        if (enterprise.getCarbonTradable().compareTo(request.getQuantity()) < 0) {
            throw TradeException.insufficientQuota(enterprise.getCarbonTradable(), request.getQuantity());
        }

        // 拍卖挂单时冻结配额
        enterprise.setCarbonTradable(enterprise.getCarbonTradable().subtract(request.getQuantity()));
        BigDecimal currentFrozen = enterprise.getCarbonFrozen() != null ? enterprise.getCarbonFrozen() : BigDecimal.ZERO;
        enterprise.setCarbonFrozen(currentFrozen.add(request.getQuantity()));
        enterpriseRepository.save(enterprise);

        BigDecimal totalAmount = request.getQuantity().multiply(request.getUnitPrice());

        Transaction trade = Transaction.builder()
                .tradeNo(CommonUtils.generateTradeId())
                .tradeType(TradeTypeEnum.AUCTION.getCode())
                .sellerId(currentUser.getUserId())
                .buyerId(0L) // 拍卖挂单，买方待定
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice())
                .totalAmount(totalAmount)
                .status(TradeStatusEnum.PENDING.getCode())
                .remark(request.getRemark())
                .build();

        trade = transactionRepository.save(trade);

        log.info("Auction order created: {} by user {}", trade.getTradeNo(), currentUser.getUsername());
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

        // 立即将状态设置为PROCESSING并保存，防止并发请求重复操作
        trade.setStatus(TradeStatusEnum.PROCESSING.getCode());
        transactionRepository.save(trade);

        // 卖方核减冻结配额并计入已售出
        BigDecimal currentFrozen = sellerEnterprise.getCarbonFrozen() != null ? sellerEnterprise.getCarbonFrozen() : BigDecimal.ZERO;
        if (currentFrozen.compareTo(tradeQuantity) < 0) {
            // 兼容性保护：若此前挂单未走冻结，则从可用余额扣除并严格校验余额，杜绝负数穿透
            BigDecimal available = sellerEnterprise.getCarbonTradable().add(currentFrozen);
            if (available.compareTo(tradeQuantity) < 0) {
                throw TradeException.insufficientQuota(available, tradeQuantity);
            }
            BigDecimal remainingDeduct = tradeQuantity.subtract(currentFrozen);
            sellerEnterprise.setCarbonFrozen(BigDecimal.ZERO);
            sellerEnterprise.setCarbonTradable(sellerEnterprise.getCarbonTradable().subtract(remainingDeduct));
        } else {
            sellerEnterprise.setCarbonFrozen(currentFrozen.subtract(tradeQuantity));
        }
        sellerEnterprise.setCarbonUsed(sellerEnterprise.getCarbonUsed().add(tradeQuantity));

        // 买方增加配额
        buyerEnterprise.setCarbonQuota(buyerEnterprise.getCarbonQuota().add(tradeQuantity));
        buyerEnterprise.setCarbonTradable(buyerEnterprise.getCarbonTradable().add(tradeQuantity));

        enterpriseRepository.save(sellerEnterprise);
        enterpriseRepository.save(buyerEnterprise);

        trade.setStatus(TradeStatusEnum.COMPLETED.getCode());
        trade.setCompletedAt(LocalDateTime.now());
        trade = transactionRepository.save(trade);

        // 碳币货款两讫结算 (DVP): 买方扣除碳币，卖方增加碳币
        carbonCoinService.buyQuota(trade.getBuyerId(), trade.getTotalAmount(), trade.getQuantity(), trade.getId());
        carbonCoinService.sellQuota(trade.getSellerId(), trade.getTotalAmount(), trade.getQuantity(), trade.getId());

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
            throw new com.oaiss.chain.exception.BusinessException(com.oaiss.chain.constant.ErrorCode.PERMISSION_DENIED, "无权取消非本人参与的交易");
        }

        TradeStatusEnum status = TradeStatusEnum.fromCode(trade.getStatus());
        if (!status.isCancellable()) {
            throw TradeException.orderCancelled(tradeId);
        }

        trade.setStatus(TradeStatusEnum.CANCELLED.getCode());
        trade = transactionRepository.save(trade);

        // 撤单解冻卖方配额
        final BigDecimal cancelQty = trade.getQuantity();
        final Long sellerUserId = trade.getSellerId();
        enterpriseRepository.findByUserId(sellerUserId).ifPresent(seller -> {
            BigDecimal currentFrozen = seller.getCarbonFrozen() != null ? seller.getCarbonFrozen() : BigDecimal.ZERO;
            if (currentFrozen.compareTo(cancelQty) < 0) {
                seller.setCarbonFrozen(BigDecimal.ZERO);
            } else {
                seller.setCarbonFrozen(currentFrozen.subtract(cancelQty));
            }
            seller.setCarbonTradable(seller.getCarbonTradable().add(cancelQty));
            enterpriseRepository.save(seller);
        });

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
            java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
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
                .build();
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
