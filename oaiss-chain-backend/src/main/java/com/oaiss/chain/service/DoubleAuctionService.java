package com.oaiss.chain.service;

import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.aop.AuditLogWriter;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.AuctionOrderRequest;
import com.oaiss.chain.dto.AuctionOrderResponse;
import com.oaiss.chain.dto.AuctionMatchingStatusResponse;
import com.oaiss.chain.dto.MatchingResultResponse;
import com.oaiss.chain.entity.AuctionOrder;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.MatchingResult;
import com.oaiss.chain.entity.OperationLog;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.AuctionOrderStatusEnum;
import com.oaiss.chain.enums.MatchingStatusEnum;
import com.oaiss.chain.enums.TradeStatusEnum;
import com.oaiss.chain.enums.TradeTypeEnum;
import com.oaiss.chain.exception.TradeException;
import com.oaiss.chain.repository.AuctionOrderRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.MatchingResultRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * [DEFENSE-TRACE-AUCTION-DOUBLE-01]
 * 【核心算法】：双向连续竞价拍卖（Continuous Double Auction / CDA）撮合出清引擎
 * 【对应前端页面】：views/enterprise/TradingMarket.vue (/enterprise/trading/market)
 * 【对应前端API】：api/auction.ts -> placeBuyOrder() / placeSellOrder() / triggerMatching()
 * 【可见UI操作】：在“集中竞价/交易大厅”查看买卖盘深度口，输入价格数量提交买单/卖单并执行实时出清
 * 
 * 核心撮合出清算法与状态机流转：
 * 1. 优先法则：买方按最高出价优先（Price-Descending）、卖方按最低要价优先（Price-Ascending），同价按时间戳顺序优先（FIFO）。
 * 2. 出清价格：成交价格 P_clear = (P_buy + P_sell) / 2，买方若有折价撮合差额，系统自动执行差价原路解冻退回。
 * 3. 并发安全：基于 Redis 分布式锁 `@DistributedLock(key = "'auction:matching'")` 保证全局单实例串行撮合出清，杜绝资金配额超卖超买。
 * 4. 履约结算：货款两讫（DVP），原子划转买卖双方碳币与碳配额，并异步触发区块链落块存证事件。
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoubleAuctionService {

    private final AuctionOrderRepository auctionOrderRepository;
    private final MatchingResultRepository matchingResultRepository;
    private final TransactionRepository transactionRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CarbonCoinService carbonCoinService;
    private final CreditScoreService creditScoreService;
    private final AuditLogWriter auditLogWriter;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final QuotaLedgerService quotaLedgerService;
    private final TradeRiskAssessor tradeRiskAssessor;
    private final TradeAuditService tradeAuditService;
    private final PriceLimitService priceLimitService;
    /** 准入证书门禁：未签发 / 已吊销 / 已过期都不得挂单与结算 */
    private final CertificateGateService certificateGateService;

    private static final List<Integer> ACTIVE_ORDER_STATUSES = Arrays.asList(
            AuctionOrderStatusEnum.PENDING.getCode(),
            AuctionOrderStatusEnum.PARTIALLY_MATCHED.getCode()
    );

    private static final ThreadLocalRandom MATCH_RNG = ThreadLocalRandom.current();

    /**
     * 查不到信誉分时采用的中性分（0-100 区间的中间值）。
     * 采用中性分而非最低分，保证数据缺失的挂单不会被动排到最后。
     */
    private static final int NEUTRAL_CREDIT_SCORE = 50;

    /**
     * 提交买入挂单
     */
    @DistributedLock(key = "'auction:order:' + #currentUser.userId", expireTime = 10)
    @Transactional
    public AuctionOrderResponse placeBuyOrder(JwtUserDetails currentUser, AuctionOrderRequest request) {
        validateOrderRequest(request);

        Enterprise buyerEnterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, request.getQuantity()));

        // 信誉分风控闸门：被限制交易或账户冻结的发起方不得挂买单。
        // 必须排在其他业务校验之前——冻结账户无论挂单参数如何都不该参与市场，
        // 否则会被自成交/余额等次要提示掩盖，用户看不到真正原因。
        assertTradeAllowed(buyerEnterprise.getId());

        // 自成交与洗售交易防范：同一主体存在未完成的反向挂单时禁止提交
        if (auctionOrderRepository.existsByUserIdAndDirectionAndStatusInAndDeletedFalse(
                currentUser.getUserId(), 2, ACTIVE_ORDER_STATUSES)) {
            throw new TradeException(ErrorCode.PARAM_ERROR, "存在未完成的卖出挂单，同一主体禁止同时买卖以防自成交/洗售交易");
        }

        // 验证买方碳币余额是否足以支付买入意向金并执行全额现货锁定
        BigDecimal requiredAmount = request.getQuantity().multiply(request.getPrice()).setScale(2, RoundingMode.HALF_UP);
        var coinAccount = carbonCoinService.getOrCreateAccount(currentUser.getUserId());
        if (coinAccount.getBalance().compareTo(requiredAmount) < 0) {
            throw TradeException.insufficientBalance(coinAccount.getBalance(), requiredAmount);
        }
        carbonCoinService.freezeBalance(currentUser.getUserId(), requiredAmount);

        AuctionOrder order = AuctionOrder.builder()
                .orderNo(generateOrderNo("B"))
                .userId(currentUser.getUserId())
                .direction(1)
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .matchedQuantity(BigDecimal.ZERO)
                .frozenAmount(requiredAmount)
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();

        order = auctionOrderRepository.save(order);
        log.info("Buy order placed: {} by user {}, qty={}, price={}, frozen={}",
                order.getOrderNo(), currentUser.getUsername(), request.getQuantity(), request.getPrice(), requiredAmount);

        return toOrderResponse(order);
    }

    /**
     * 提交卖出挂单
     */
    @DistributedLock(key = "'auction:order:' + #currentUser.userId", expireTime = 10)
    @Transactional
    public AuctionOrderResponse placeSellOrder(JwtUserDetails currentUser, AuctionOrderRequest request) {
        validateOrderRequest(request);

        Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, request.getQuantity()));

        // 信誉分风控闸门：被限制交易或账户冻结的发起方不得挂卖单。
        // 必须排在其他业务校验之前，理由同 placeBuyOrder。
        assertTradeAllowed(enterprise.getId());

        // 自成交与洗售交易防范：同一主体存在未完成的反向挂单时禁止提交
        if (auctionOrderRepository.existsByUserIdAndDirectionAndStatusInAndDeletedFalse(
                currentUser.getUserId(), 1, ACTIVE_ORDER_STATUSES)) {
            throw new TradeException(ErrorCode.PARAM_ERROR, "存在未完成的买入挂单，同一主体禁止同时买卖以防自成交/洗售交易");
        }

        // 卖单冻结碳币（2026-11-03：配额退出资产层后，可交易标的是碳币）
        //
        // 原来这里校验并冻结 carbonTradable（配额）。配额现在只算不发，
        // 企业账上不再有配额余额，故改为冻结碳币——与 P2P 保持同一模型。
        // freezeBalance 内部会校验可用余额，不足时抛 4005。
        carbonCoinService.freezeBalance(currentUser.getUserId(), request.getQuantity());

        AuctionOrder order = AuctionOrder.builder()
                .orderNo(generateOrderNo("S"))
                .userId(currentUser.getUserId())
                .direction(2)
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .matchedQuantity(BigDecimal.ZERO)
                .frozenAmount(request.getQuantity())
                .status(AuctionOrderStatusEnum.PENDING.getCode())
                .build();

        order = auctionOrderRepository.save(order);
        log.info("Sell order placed: {} by user {}, qty={}, price={}, frozenQuota={}",
                order.getOrderNo(), currentUser.getUsername(), request.getQuantity(), request.getPrice(), request.getQuantity());

        return toOrderResponse(order);
    }

    /**
     * 执行双向拍卖撮合
     * <p>
     * 核心算法：
     * 1. 取出所有待匹配/部分匹配的买单（按价格降序）
     * 2. 取出所有待匹配/部分匹配的卖单（按价格升序）
     * 3. 双指针匹配：买价 >= 卖价时撮合
     * 4. 成交价 = (买价 + 卖价) / 2
     * 5. 创建撮合记录 + 交易记录
     * 6. 更新双方企业配额
     *
     * @return 撮合结果列表
     */
    /**
     * 挂单参数校验——买/卖两个入口的公共前置。
     *
     * <p>平台限价放在这里而不是两个入口各写一遍：4 个交易写入口收敛为 2 处
     * （TradeService 两处 + 此处一处覆盖双向），减少遗漏新入口的风险。
     *
     * <p>顺序也重要：限价必须在任何资源冻结之前——{@code placeBuyOrder} 会
     * freezeBalance、{@code placeSellOrder} 会冻结配额，若先冻结再因价格被拒，
     * 用户会看到一次无意义的资产锁定。
     */
    private void validateOrderRequest(AuctionOrderRequest request) {
        if (request == null) {
            throw new TradeException(ErrorCode.PARAM_ERROR, "拍卖挂单请求不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeException(ErrorCode.PARAM_ERROR, "挂单数量必须为正数");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeException(ErrorCode.PARAM_ERROR, "挂单价格必须为正数");
        }
        // 平台限价（与 TradeService 同源校验）
        priceLimitService.validate(request.getPrice(), LocalDateTime.now());
    }

    @DistributedLock(key = "'auction:matching'", expireTime = 30, waitTime = 0)
    @Transactional
    public List<MatchingResultResponse> executeMatching() {
        List<Integer> activeStatuses = ACTIVE_ORDER_STATUSES;

        // 买方：价格降序（出价高的优先）
        List<AuctionOrder> buyOrders = sortByPriceThenCredit(
                auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(1, activeStatuses),
                true);

        // 卖方：价格升序（出价低的优先）
        List<AuctionOrder> sellOrders = sortByPriceThenCredit(
                auctionOrderRepository.findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(2, activeStatuses),
                false);

        List<MatchingResultResponse> results = new ArrayList<>();

        if (buyOrders.isEmpty() || sellOrders.isEmpty()) {
            log.info("No matching opportunity: buy={}, sell={}", buyOrders.size(), sellOrders.size());
            return results;
        }

        Set<AuctionOrder> modifiedSellOrders = new HashSet<>();

        for (AuctionOrder buyOrder : buyOrders) {
            BigDecimal buyRemaining = buyOrder.getQuantity().subtract(buyOrder.getMatchedQuantity());

            for (AuctionOrder sellOrder : sellOrders) {
                if (buyRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal sellRemaining = sellOrder.getQuantity().subtract(sellOrder.getMatchedQuantity());
                if (sellRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // 检查价格是否匹配：买方价格 >= 卖方价格
                if (buyOrder.getPrice().compareTo(sellOrder.getPrice()) < 0) {
                    break; // 后续卖方价格更高，无法匹配
                }

                // 自成交 / 洗售交易拦截 (Wash Trading Prevention)
                if (isSameParty(buyOrder, sellOrder)) {
                    log.warn("[WASH_TRADING] 检测到自成交风险，已跳过撮合配对: buyOrder={}, sellOrder={}, userId={}",
                            buyOrder.getOrderNo(), sellOrder.getOrderNo(), buyOrder.getUserId());
                    recordWashTradingAudit(buyOrder, sellOrder);
                    continue;
                }

                // 证书门禁：挂单时合规、冻结期内却被吊销的对手方不得再成交。
                // 与自成交拦截同属「跳过该配对」而非整批中断——一笔不合规订单
                // 不应阻塞市场上其他人的正常出清。
                if (!isPairAdmitted(buyOrder, sellOrder)) {
                    log.warn("[CERT-GATE] 撮合跳过证书不合规的挂单: buyOrder={}, sellOrder={}",
                            buyOrder.getOrderNo(), sellOrder.getOrderNo());
                    continue;
                }

                BigDecimal matchQty = buyRemaining.min(sellRemaining);

                // 计算成交价格：(买方价格 + 卖方价格) / 2
                BigDecimal settlementPrice = buyOrder.getPrice()
                        .add(sellOrder.getPrice())
                        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

                BigDecimal totalAmount = matchQty.multiply(settlementPrice)
                        .setScale(2, RoundingMode.HALF_UP);

                MatchingResult match = MatchingResult.builder()
                        .matchNo(generateMatchNo())
                        .buyOrderId(buyOrder.getId())
                        .sellOrderId(sellOrder.getId())
                        .buyerId(buyOrder.getUserId())
                        .sellerId(sellOrder.getUserId())
                        .matchedQuantity(matchQty)
                        .settlementPrice(settlementPrice)
                        .totalAmount(totalAmount)
                        .status(MatchingStatusEnum.PENDING_SETTLEMENT.getCode())
                        .build();

                // ---- 结算价复核（限价）----
                // 必须早于 MatchingResult 落库：否则会先产生一条孤儿撮合记录再拒绝配对。
                // 注：绝对区间那半实际恒真（两侧挂单在挂单时都已校验，平均价必在区间内），
                // 真正有判别力的是偏离带——成交价可能因买卖报价跨度大而显著偏离均价。
                try {
                    priceLimitService.validate(settlementPrice, LocalDateTime.now());
                } catch (com.oaiss.chain.exception.BusinessException ex) {
                    log.warn("[PRICE-LIMIT] 撮合结算价复核不通过，跳过该配对: buy={}, sell={}, "
                                    + "settlePrice={}, reason={}",
                            buyOrder.getOrderNo(), sellOrder.getOrderNo(), settlementPrice,
                            ex.getMessage());
                    // 记录一条审计，便于事后区分「撮合未成交」与「成交但被限价拦下」
                    recordPriceLimitBlockedAudit(buyOrder, sellOrder, settlementPrice, ex.getMessage());
                    continue;
                }

                match = matchingResultRepository.save(match);

                // ---- 成交风险分流（S4）：决定这笔成交是当场结算还是转人工审核 ----
                // 待审期间资产始终保持挂单时的冻结态，绝不在此处结算——
                // 否则「待审」就成了账面谎言：看起来在审，钱和额度已经走了。
                Transaction pendingTrade = Transaction.builder()
                        .tradeNo(CommonUtils.generateTradeId())
                        .tradeType(TradeTypeEnum.AUCTION.getCode())
                        .sellerId(sellOrder.getUserId())
                        .buyerId(buyOrder.getUserId())
                        .quantity(matchQty)
                        .unitPrice(settlementPrice)
                        .totalAmount(totalAmount)
                        .status(TradeStatusEnum.PROCESSING.getCode())
                        .auditStatus(com.oaiss.chain.entity.TradeAudit.AUDIT_PENDING)
                        .build();

                BigDecimal referencePrice = null;
                Integer buyerCredit = null;
                Integer sellerCredit = null;
                try {
                    referencePrice = transactionRepository.findAverageUnitPriceSince(
                            LocalDateTime.now().minusDays(30));
                    java.util.Map<Long, Integer> scores =
                            creditScoreService.getScoresByUserIds(
                                    java.util.Set.of(buyOrder.getUserId(), sellOrder.getUserId()));
                    if (scores != null) {
                        buyerCredit = scores.get(buyOrder.getUserId());
                        sellerCredit = scores.get(sellOrder.getUserId());
                    }
                } catch (Exception e) {
                    // 分流数据取数失败不应阻断撮合：降级为「无参考价/无信誉」，
                    // 由分流器的缺失值规则决定（无均价跳过价格检查、无信誉按最低分处理）
                    log.warn("[AUDIT] 风险分流取数失败，降级评估: {}", e.getMessage());
                }

                TradeRiskAssessor.Decision riskDecision = tradeRiskAssessor.assess(
                        pendingTrade, referencePrice, buyerCredit, sellerCredit, false);

                Transaction trade;
                if (riskDecision.requiresAudit()) {
                    // 待审分支：建交易（处理中/待审）、建工单、更新挂单匹配量，然后 continue——
                    // 不转额度、不结算碳币、不发上链事件。结算整体推迟到审核通过。
                    trade = transactionRepository.save(pendingTrade);
                } else {
                    trade = pendingTrade;
                    trade.setStatus(TradeStatusEnum.COMPLETED.getCode());
                    trade.setAuditStatus(com.oaiss.chain.entity.TradeAudit.AUDIT_NOT_REQUIRED);
                    trade.setCompletedAt(LocalDateTime.now());
                    trade = transactionRepository.save(trade);
                }

                match.setTransactionId(trade.getId());
                match.setStatus(MatchingStatusEnum.SETTLED.getCode());
                match.setSettledAt(LocalDateTime.now());
                matchingResultRepository.save(match);

                buyOrder.setMatchedQuantity(buyOrder.getMatchedQuantity().add(matchQty));
                buyOrder.setSettlementPrice(settlementPrice);
                updateOrderStatus(buyOrder);

                sellOrder.setMatchedQuantity(sellOrder.getMatchedQuantity().add(matchQty));
                sellOrder.setSettlementPrice(settlementPrice);
                updateOrderStatus(sellOrder);
                modifiedSellOrders.add(sellOrder);

                if (riskDecision.requiresAudit()) {
                    tradeAuditService.createPendingAudit(trade.getId(), riskDecision.reasons());

                    notificationService.sendBatchNotificationsAsync(
                            java.util.List.of(buyOrder.getUserId(), sellOrder.getUserId()),
                            "拍卖撮合成交待审核",
                            "您的拍卖订单已撮合 " + matchQty + " 吨，成交价 " + settlementPrice
                                    + " 碳币/吨，正在审核中。审核通过后资金与配额才会结算。",
                            "TRADE");

                    buyRemaining = buyRemaining.subtract(matchQty);
                    results.add(toMatchResponse(match));

                    log.info("Matched (pending audit): buy={}, sell={}, qty={}, price={}, reasons={}",
                            buyOrder.getOrderNo(), sellOrder.getOrderNo(), matchQty,
                            settlementPrice, riskDecision.reasons());
                    continue;
                }

                updateEnterpriseQuota(buyOrder.getUserId(), sellOrder.getUserId(), matchQty);

                // 碳币货款两讫结算 (DVP): 买方扣除已冻结的碳币资金，卖方增加可用碳币资金
                carbonCoinService.deductFrozenBalance(buyOrder.getUserId(), totalAmount, matchQty, trade.getId());
                carbonCoinService.sellQuota(sellOrder.getUserId(), totalAmount, matchQty, trade.getId());

                // 买方折价成交解冻退差价：如果实际撮合单价低于买方挂单价，解冻多锁定的资金退回买方可用余额
                if (settlementPrice.compareTo(buyOrder.getPrice()) < 0) {
                    BigDecimal priceDiff = buyOrder.getPrice().subtract(settlementPrice);
                    BigDecimal refund = priceDiff.multiply(matchQty).setScale(2, RoundingMode.HALF_UP);
                    if (refund.compareTo(BigDecimal.ZERO) > 0) {
                        carbonCoinService.unfreezeBalance(buyOrder.getUserId(), refund);
                    }
                }

                // 发布交易完成事件，驱动异步区块链存证
                String tradeDataJson = String.format(
                        "{\"tradeNo\":\"%s\",\"tradeType\":\"AUCTION\",\"sellerId\":%d,\"buyerId\":%d,\"quantity\":%s,\"unitPrice\":%s,\"totalAmount\":%s,\"completedAt\":\"%s\"}",
                        trade.getTradeNo(), trade.getSellerId(), trade.getBuyerId(), matchQty, settlementPrice, totalAmount, trade.getCompletedAt()
                );
                eventPublisher.publishEvent(new com.oaiss.chain.event.TradeCompletedEvent(this, trade.getId(), tradeDataJson));

                // 通知买卖双方：撮合成交（成交由后台自动撮合周期产生，文案需让用户明白无需手动操作）
                notificationService.sendBatchNotificationsAsync(
                        java.util.List.of(buyOrder.getUserId(), sellOrder.getUserId()),
                        "拍卖撮合成交",
                        "您的拍卖订单已成交 " + matchQty + " 吨，成交价 " + settlementPrice
                                + " 碳币/吨（系统自动撮合），资金与配额已同步结算。",
                        "TRADE");

                buyRemaining = buyRemaining.subtract(matchQty);

                results.add(toMatchResponse(match));

                log.info("Matched: buy={}, sell={}, qty={}, price={}, total={}",
                        buyOrder.getOrderNo(), sellOrder.getOrderNo(), matchQty, settlementPrice, totalAmount);
            }

            auctionOrderRepository.save(buyOrder);
        }

        if (!modifiedSellOrders.isEmpty()) {
            auctionOrderRepository.saveAll(modifiedSellOrders);
        }

        log.info("Matching round completed: {} matches", results.size());
        return results;
    }

    /**
     * 交易准入校验（证书 + 信誉分）。
     * <p>
     * 先判证书再判信誉分：证书是「有没有资格进场」，信誉分是「进场后守不守规矩」。
     *
     * @param enterpriseId 挂单方企业ID
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
     * 撮合候选排序：价格优先不变，仅在同价位时以发起方企业信誉分从高到低作为次级键。
     * <p>
     * 信誉分缺失（无企业、无评分记录或评分服务异常）的挂单按 {@link #NEUTRAL_CREDIT_SCORE} 中性分处理，
     * 既不抛错也不被动排到最后。使用稳定排序，同价同分时保持仓储返回的原始顺序。
     *
     * @param orders         仓储按价格排好序的候选挂单
     * @param priceDescending true=买方（价格降序），false=卖方（价格升序）
     * @return 价格优先、同价信誉优先的挂单列表
     */
    private List<AuctionOrder> sortByPriceThenCredit(List<AuctionOrder> orders, boolean priceDescending) {
        if (orders == null || orders.size() < 2) {
            return orders;
        }

        Map<Long, Integer> scoresByUserId = resolveCreditScores(orders);

        Comparator<AuctionOrder> comparator = Comparator.comparing(
                (AuctionOrder order) -> order.getPrice() != null ? order.getPrice() : BigDecimal.ZERO);
        comparator = priceDescending ? comparator.reversed() : comparator;
        comparator = comparator.thenComparing(
                (AuctionOrder order) -> scoresByUserId.getOrDefault(order.getUserId(), NEUTRAL_CREDIT_SCORE),
                Comparator.reverseOrder());

        List<AuctionOrder> sorted = new ArrayList<>(orders);
        sorted.sort(comparator);
        return sorted;
    }

    /**
     * 批量解析挂单发起方的企业信誉分；评分不可用时降级为空 Map（全部按中性分处理），绝不阻断撮合。
     */
    private Map<Long, Integer> resolveCreditScores(List<AuctionOrder> orders) {
        Set<Long> userIds = orders.stream()
                .map(AuctionOrder::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        try {
            return creditScoreService.getScoresByUserIds(userIds);
        } catch (Exception e) {
            log.warn("[AUCTION-CREDIT] 信誉分批量查询失败，本轮按中性分排序: {}", e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }

    /**
     * 判断买卖双方是否都属于「持有效准入证书」的企业。
     *
     * <p>挂单只记录 userId，企业归属需要二次解析；解析不到企业时按不合规处理
     * （宁可跳过一笔也不放行身份不明的结算）。</p>
     */
    private boolean isPairAdmitted(AuctionOrder buyOrder, AuctionOrder sellOrder) {
        return isOrderAdmitted(buyOrder) && isOrderAdmitted(sellOrder);
    }

    private boolean isOrderAdmitted(AuctionOrder order) {
        if (order.getUserId() == null) {
            return false;
        }
        return enterpriseRepository.findByUserId(order.getUserId())
                .map(e -> certificateGateService.isEnterpriseAdmitted(e.getId()))
                .orElse(false);
    }

    /**
     * 判断买卖双方是否属于同一主体（自成交防范）
     */
    private boolean isSameParty(AuctionOrder buyOrder, AuctionOrder sellOrder) {
        if (buyOrder.getUserId() != null && buyOrder.getUserId().equals(sellOrder.getUserId())) {
            return true;
        }
        if (buyOrder.getUserId() != null && sellOrder.getUserId() != null) {
            Enterprise buyerEnterprise = enterpriseRepository.findByUserId(buyOrder.getUserId()).orElse(null);
            Enterprise sellerEnterprise = enterpriseRepository.findByUserId(sellOrder.getUserId()).orElse(null);
            if (buyerEnterprise != null && sellerEnterprise != null
                    && buyerEnterprise.getId() != null && buyerEnterprise.getId().equals(sellerEnterprise.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录自成交/洗售交易拦截审计日志
     */
    private void recordWashTradingAudit(AuctionOrder buyOrder, AuctionOrder sellOrder) {
        if (auditLogWriter == null) {
            return;
        }
        try {
            OperationLog opLog = OperationLog.builder()
                    .userId(buyOrder.getUserId())
                    .username("SYSTEM-MATCH-ENGINE")
                    .userType(1)
                    .module("双向拍卖撮合")
                    .action("WASH_TRADING_BLOCKED")
                    .description(String.format("拦截自成交/洗售交易风险: 买单 %s 与卖单 %s 属于同一主体 (用户ID: %d)",
                            buyOrder.getOrderNo(), sellOrder.getOrderNo(), buyOrder.getUserId()))
                    .httpMethod("INTERNAL")
                    .requestUrl("/api/v1/auction/match")
                    .requestParams(String.format("{\"buyOrder\":\"%s\",\"sellOrder\":\"%s\",\"userId\":%d}",
                            buyOrder.getOrderNo(), sellOrder.getOrderNo(), buyOrder.getUserId()))
                    .status(2)
                    .errorMsg("同一主体自买自卖被撮合引擎强力阻断")
                    .build();
            auditLogWriter.saveAsync(opLog);
        } catch (Exception e) {
            log.warn("Failed to record wash trading audit log: {}", e.getMessage());
        }
    }

    /**
     * 记录「撮合出清被限价拦下」的审计。
     *
     * <p>与自成交拦截不同：那笔配对根本没成立，这一笔是**价格算出来了但被规则拒绝**。
     * 若不记审计，事后无法区分「市场没撮合到」与「撮合到了但被限价挡回」，
     * 前者是常态，后者是风控事件。
     */
    private void recordPriceLimitBlockedAudit(AuctionOrder buyOrder, AuctionOrder sellOrder,
                                              BigDecimal settlementPrice, String reason) {
        if (auditLogWriter == null) {
            return;
        }
        try {
            OperationLog opLog = OperationLog.builder()
                    .userId(buyOrder.getUserId())
                    .username("SYSTEM-MATCH-ENGINE")
                    .userType(1)
                    .module("双向拍卖撮合")
                    .action("PRICE_LIMIT_BLOCKED")
                    .description(String.format("撮合结算价超出平台限价被拦下: 买单 %s / 卖单 %s，结算价 %s",
                            buyOrder.getOrderNo(), sellOrder.getOrderNo(), settlementPrice.toPlainString()))
                    .httpMethod("INTERNAL")
                    .requestUrl("/api/v1/auction/match")
                    .requestParams(String.format("{\"buyOrder\":\"%s\",\"sellOrder\":\"%s\",\"settlementPrice\":%s}",
                            buyOrder.getOrderNo(), sellOrder.getOrderNo(), settlementPrice.toPlainString()))
                    .status(2)
                    .errorMsg(reason)
                    .build();
            auditLogWriter.saveAsync(opLog);
        } catch (Exception e) {
            log.warn("Failed to record price limit audit log: {}", e.getMessage());
        }
    }

    /**
     * 分页查询挂单
     */
    public Page<AuctionOrderResponse> listOrders(Integer direction, Integer status,
                                                   Integer page, Integer size) {
        size = CommonUtils.sanitizePageSize(size);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuctionOrder> orders;
        if (direction != null && status != null) {
            orders = auctionOrderRepository.findByDirectionAndStatusAndDeletedFalse(direction, status, pageable);
        } else {
            orders = auctionOrderRepository.findByDeletedFalse(pageable);
        }
        return orders.map(this::toOrderResponse);
    }

    /**
     * 查询我的挂单
     */
    public Page<AuctionOrderResponse> listMyOrders(JwtUserDetails currentUser,
                                                     Integer direction, Integer status,
                                                     Integer page, Integer size) {
        size = CommonUtils.sanitizePageSize(size);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuctionOrder> orders;
        if (direction != null && status != null) {
            orders = auctionOrderRepository.findByUserIdAndDirectionAndStatusAndDeletedFalse(
                    currentUser.getUserId(), direction, status, pageable);
        } else if (direction != null) {
            orders = auctionOrderRepository.findByUserIdAndDirectionAndDeletedFalse(
                    currentUser.getUserId(), direction, pageable);
        } else {
            orders = auctionOrderRepository.findByUserIdAndDeletedFalse(currentUser.getUserId(), pageable);
        }
        return orders.map(this::toOrderResponse);
    }

    /**
     * 查询撮合结果
     */
    public Page<MatchingResultResponse> listMatchingResults(JwtUserDetails currentUser,
                                                              Integer page, Integer size) {
        size = CommonUtils.sanitizePageSize(size);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MatchingResult> results = matchingResultRepository.findByUserIdRelated(currentUser.getUserId(), pageable);
        Map<Long, String> userNames = resolveUserNames(results.getContent());
        return results.map(m -> toMatchResponse(m, userNames));
    }

    /**
     * 查询全平台撮合成交明细（管理员撮合控制台）
     */
    @Transactional(readOnly = true)
    public Page<MatchingResultResponse> listAllMatchingResults(Integer page, Integer size) {
        size = CommonUtils.sanitizePageSize(size);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MatchingResult> results = matchingResultRepository.findByDeletedFalse(pageable);
        Map<Long, String> userNames = resolveUserNames(results.getContent());
        return results.map(m -> toMatchResponse(m, userNames));
    }

    /**
     * 查询撮合控制台运行态：自动撮合开关、间隔、盘口待撮合规模、上次出清时间
     *
     * @param autoMatchingEnabled 自动撮合是否已启用（由调度器持有的配置决定）
     * @param intervalMs          自动撮合间隔（毫秒）
     */
    @Transactional(readOnly = true)
    public AuctionMatchingStatusResponse getMatchingStatus(
            boolean autoMatchingEnabled, long intervalMs) {
        long pendingBuys = auctionOrderRepository
                .countByDirectionAndStatusInAndDeletedFalse(1, ACTIVE_ORDER_STATUSES);
        long pendingSells = auctionOrderRepository
                .countByDirectionAndStatusInAndDeletedFalse(2, ACTIVE_ORDER_STATUSES);

        java.time.LocalDateTime lastMatchAt = matchingResultRepository
                .findFirstByDeletedFalseOrderByCreatedAtDesc()
                .map(MatchingResult::getCreatedAt)
                .orElse(null);

        return AuctionMatchingStatusResponse.builder()
                .autoMatchingEnabled(autoMatchingEnabled)
                .intervalSeconds(Math.max(1L, intervalMs / 1000L))
                .pendingBuyOrders(pendingBuys)
                .pendingSellOrders(pendingSells)
                .lastMatchAt(lastMatchAt)
                .build();
    }

    // ==================== 私有方法 ====================

    /**
     * 更新挂单状态（根据匹配数量判断）
     */
    private void updateOrderStatus(AuctionOrder order) {
        BigDecimal remaining = order.getQuantity().subtract(order.getMatchedQuantity());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            order.setStatus(AuctionOrderStatusEnum.FULLY_MATCHED.getCode());
            order.setMatchedAt(LocalDateTime.now());
        } else {
            order.setStatus(AuctionOrderStatusEnum.PARTIALLY_MATCHED.getCode());
        }
    }

    /**
     * 撤销挂单（将未成交的冻结资产 100% 解冻原路退回）
     */
    @DistributedLock(key = "'auction:order:' + #currentUser.userId", expireTime = 10)
    @Transactional
    public AuctionOrderResponse cancelOrder(JwtUserDetails currentUser, Long orderId) {
        AuctionOrder order = auctionOrderRepository.findById(orderId)
                .orElseThrow(() -> new TradeException(ErrorCode.TRADE_NOT_FOUND, "拍卖挂单不存在: " + orderId));

        if (!order.getUserId().equals(currentUser.getUserId())) {
            throw new TradeException(ErrorCode.PERMISSION_DENIED, "无权撤销他人的挂单");
        }

        // 注意：getStatus() 返回 Integer，必须用 equals 比较。
        // 用 == 比的是引用；Integer 只在 -128..127 有缓存，本枚举当前取值落在此区间
        // 所以现状碰巧正确——但只要将来新增一个大于 127 的状态码，
        // 这里会静默变成「永远不相等」，撤单闸门失效。
        if (!AuctionOrderStatusEnum.PENDING.getCode().equals(order.getStatus())
                && !AuctionOrderStatusEnum.PARTIALLY_MATCHED.getCode().equals(order.getStatus())) {
            throw TradeException.orderCancelled(orderId);
        }

        BigDecimal unmatchedQty = order.getQuantity().subtract(
                order.getMatchedQuantity() != null ? order.getMatchedQuantity() : BigDecimal.ZERO);

        if (unmatchedQty.compareTo(BigDecimal.ZERO) > 0) {
            if (order.getDirection() == 1) {
                // 买单：解冻剩余未成交金额对应的碳币
                BigDecimal unfreezeCash = unmatchedQty.multiply(order.getPrice()).setScale(2, RoundingMode.HALF_UP);
                carbonCoinService.unfreezeBalance(currentUser.getUserId(), unfreezeCash);
            } else if (order.getDirection() == 2) {
                // 卖单：解冻剩余未成交的碳币（2026-11-03：挂单冻的是碳币）
                carbonCoinService.unfreezeBalance(currentUser.getUserId(), unmatchedQty);
            }
        }

        order.setStatus(AuctionOrderStatusEnum.CANCELLED.getCode());
        order = auctionOrderRepository.save(order);
        log.info("Auction order cancelled: orderId={}, user={}, direction={}, unfreezeQty={}",
                orderId, currentUser.getUsername(), order.getDirection(), unmatchedQty);
        return toOrderResponse(order);
    }

    /**
     * 撮合成交时转让碳币。
     *
     * <p><b>2026-11-03 变更</b>：原实现是「卖方配额冻结转出、买方配额入账」，
     * 现在配额退出资产层，改由碳币承接——卖方挂单时冻结的碳币直接转给买方。
     *
     * <p>与 P2P 的 {@code TradeService.confirmTrade} 保持同一语义：
     * 一进一出，卖方不再收到任何对价（碳币换碳币，无计价关系）。
     *
     * @param buyerUserId  买方
     * @param sellerUserId 卖方
     * @param quantity     撮合数量（碳币枚数）
     */
    private void updateEnterpriseQuota(Long buyerUserId, Long sellerUserId, BigDecimal quantity) {
        carbonCoinService.transferFrozen(sellerUserId, buyerUserId, quantity, null);
    }

    /**
     * 生成挂单编号
     */
    private String generateOrderNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", MATCH_RNG.nextInt(10000));
    }

    /**
     * 生成撮合编号
     */
    private String generateMatchNo() {
        return "MT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", MATCH_RNG.nextInt(10000));
    }

    /**
     * AuctionOrder → AuctionOrderResponse
     */
    private AuctionOrderResponse toOrderResponse(AuctionOrder order) {
        BigDecimal remaining = order.getQuantity().subtract(
                order.getMatchedQuantity() != null ? order.getMatchedQuantity() : BigDecimal.ZERO);

        String directionText = order.getDirection() == 1 ? "买入" : "卖出";
        String statusText = safeAuctionStatusText(order.getStatus());

        return AuctionOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .direction(order.getDirection())
                .directionText(directionText)
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .matchedQuantity(order.getMatchedQuantity())
                .remainingQuantity(remaining)
                .frozenAmount(order.getFrozenAmount())
                .status(order.getStatus())
                .statusText(statusText)
                .settlementPrice(order.getSettlementPrice())
                .matchedAt(order.getMatchedAt())
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * 批量解析撮合结果涉及的用户名称（解决 N+1 查询问题）
     */
    private Map<Long, String> resolveUserNames(List<MatchingResult> matches) {
        Set<Long> userIds = new HashSet<>();
        for (MatchingResult m : matches) {
            userIds.add(m.getBuyerId());
            userIds.add(m.getSellerId());
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
    }

    /**
     * MatchingResult → MatchingResultResponse
     */
    private MatchingResultResponse toMatchResponse(MatchingResult match) {
        String buyerName = userRepository.findById(match.getBuyerId())
                .map(User::getRealName).orElse("未知");
        String sellerName = userRepository.findById(match.getSellerId())
                .map(User::getRealName).orElse("未知");
        String statusText = safeMatchingStatusText(match.getStatus());

        return buildMatchResponse(match, buyerName, sellerName, statusText);
    }

    private MatchingResultResponse toMatchResponse(MatchingResult match, Map<Long, String> userNames) {
        String buyerName = userNames.getOrDefault(match.getBuyerId(), "未知");
        String sellerName = userNames.getOrDefault(match.getSellerId(), "未知");
        String statusText = safeMatchingStatusText(match.getStatus());

        return buildMatchResponse(match, buyerName, sellerName, statusText);
    }

    private MatchingResultResponse buildMatchResponse(MatchingResult match, String buyerName,
                                                       String sellerName, String statusText) {

        return MatchingResultResponse.builder()
                .id(match.getId())
                .matchNo(match.getMatchNo())
                .buyOrderId(match.getBuyOrderId())
                .sellOrderId(match.getSellOrderId())
                .buyerId(match.getBuyerId())
                .sellerId(match.getSellerId())
                .buyerName(buyerName)
                .sellerName(sellerName)
                .matchedQuantity(match.getMatchedQuantity())
                .settlementPrice(match.getSettlementPrice())
                .totalAmount(match.getTotalAmount())
                .status(match.getStatus())
                .statusText(statusText)
                .transactionId(match.getTransactionId())
                .settledAt(match.getSettledAt())
                .createdAt(match.getCreatedAt())
                .build();
    }

    private String safeAuctionStatusText(Integer code) {
        AuctionOrderStatusEnum e = AuctionOrderStatusEnum.fromCode(code);
        return e != null ? e.getDescription() : "";
    }

    private String safeMatchingStatusText(Integer code) {
        MatchingStatusEnum e = MatchingStatusEnum.fromCode(code);
        return e != null ? e.getDescription() : "";
    }
}
