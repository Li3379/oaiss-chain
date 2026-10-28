package com.oaiss.chain.service;

import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.aop.AuditLogWriter;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.AuctionOrderRequest;
import com.oaiss.chain.dto.AuctionOrderResponse;
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
    private final AuditLogWriter auditLogWriter;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private static final List<Integer> ACTIVE_ORDER_STATUSES = Arrays.asList(
            AuctionOrderStatusEnum.PENDING.getCode(),
            AuctionOrderStatusEnum.PARTIALLY_MATCHED.getCode()
    );

    private static final ThreadLocalRandom MATCH_RNG = ThreadLocalRandom.current();

    /**
     * 提交买入挂单
     */
    @DistributedLock(key = "'auction:order:' + #currentUser.userId", expireTime = 10)
    @Transactional
    public AuctionOrderResponse placeBuyOrder(JwtUserDetails currentUser, AuctionOrderRequest request) {
        validateOrderRequest(request);

        // 自成交与洗售交易防范：同一主体存在未完成的反向挂单时禁止提交
        if (auctionOrderRepository.existsByUserIdAndDirectionAndStatusInAndDeletedFalse(
                currentUser.getUserId(), 2, ACTIVE_ORDER_STATUSES)) {
            throw new TradeException(ErrorCode.PARAM_ERROR, "存在未完成的卖出挂单，同一主体禁止同时买卖以防自成交/洗售交易");
        }

        enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, request.getQuantity()));

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

        // 自成交与洗售交易防范：同一主体存在未完成的反向挂单时禁止提交
        if (auctionOrderRepository.existsByUserIdAndDirectionAndStatusInAndDeletedFalse(
                currentUser.getUserId(), 1, ACTIVE_ORDER_STATUSES)) {
            throw new TradeException(ErrorCode.PARAM_ERROR, "存在未完成的买入挂单，同一主体禁止同时买卖以防自成交/洗售交易");
        }

        Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, request.getQuantity()));

        BigDecimal remaining = enterprise.getCarbonTradable();
        if (remaining.compareTo(request.getQuantity()) < 0) {
            throw TradeException.insufficientQuota(remaining, request.getQuantity());
        }

        // 全额现货锁定卖方碳配额
        enterprise.setCarbonTradable(enterprise.getCarbonTradable().subtract(request.getQuantity()));
        BigDecimal currentFrozen = enterprise.getCarbonFrozen() != null ? enterprise.getCarbonFrozen() : BigDecimal.ZERO;
        enterprise.setCarbonFrozen(currentFrozen.add(request.getQuantity()));
        enterpriseRepository.save(enterprise);

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
    }

    @DistributedLock(key = "'auction:matching'", expireTime = 30, waitTime = 0)
    @Transactional
    public List<MatchingResultResponse> executeMatching() {
        List<Integer> activeStatuses = ACTIVE_ORDER_STATUSES;

        // 买方：价格降序（出价高的优先）
        List<AuctionOrder> buyOrders = auctionOrderRepository
                .findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(1, activeStatuses);

        // 卖方：价格升序（出价低的优先）
        List<AuctionOrder> sellOrders = auctionOrderRepository
                .findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(2, activeStatuses);

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

                match = matchingResultRepository.save(match);

                Transaction trade = Transaction.builder()
                        .tradeNo(CommonUtils.generateTradeId())
                        .tradeType(TradeTypeEnum.AUCTION.getCode())
                        .sellerId(sellOrder.getUserId())
                        .buyerId(buyOrder.getUserId())
                        .quantity(matchQty)
                        .unitPrice(settlementPrice)
                        .totalAmount(totalAmount)
                        .status(TradeStatusEnum.COMPLETED.getCode())
                        .completedAt(LocalDateTime.now())
                        .build();
                trade = transactionRepository.save(trade);

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

                // 通知买卖双方：撮合成交
                notificationService.sendBatchNotificationsAsync(
                        java.util.List.of(buyOrder.getUserId(), sellOrder.getUserId()),
                        "拍卖撮合成交",
                        "您的拍卖订单已成交 " + matchQty + " 吨，成交价 " + settlementPrice + " 元/吨",
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

        if (order.getStatus() != AuctionOrderStatusEnum.PENDING.getCode()
                && order.getStatus() != AuctionOrderStatusEnum.PARTIALLY_MATCHED.getCode()) {
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
                // 卖单：解冻剩余未成交的碳配额
                Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                        .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, unmatchedQty));
                BigDecimal currentFrozen = enterprise.getCarbonFrozen() != null ? enterprise.getCarbonFrozen() : BigDecimal.ZERO;
                if (currentFrozen.compareTo(unmatchedQty) < 0) {
                    enterprise.setCarbonFrozen(BigDecimal.ZERO);
                } else {
                    enterprise.setCarbonFrozen(currentFrozen.subtract(unmatchedQty));
                }
                enterprise.setCarbonTradable(enterprise.getCarbonTradable().add(unmatchedQty));
                enterpriseRepository.save(enterprise);
            }
        }

        order.setStatus(AuctionOrderStatusEnum.CANCELLED.getCode());
        order = auctionOrderRepository.save(order);
        log.info("Auction order cancelled: orderId={}, user={}, direction={}, unfreezeQty={}",
                orderId, currentUser.getUsername(), order.getDirection(), unmatchedQty);
        return toOrderResponse(order);
    }

    /**
     * 更新买卖双方企业配额
     */
    private void updateEnterpriseQuota(Long buyerUserId, Long sellerUserId, BigDecimal quantity) {
        Enterprise seller = enterpriseRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, quantity));
        Enterprise buyer = enterpriseRepository.findByUserId(buyerUserId)
                .orElseThrow(() -> TradeException.insufficientQuota(BigDecimal.ZERO, quantity));

        // 卖方从已冻结配额扣减并转入已履约
        BigDecimal currentFrozen = seller.getCarbonFrozen() != null ? seller.getCarbonFrozen() : BigDecimal.ZERO;
        if (currentFrozen.compareTo(quantity) < 0) {
            seller.setCarbonFrozen(BigDecimal.ZERO);
        } else {
            seller.setCarbonFrozen(currentFrozen.subtract(quantity));
        }
        seller.setCarbonUsed(seller.getCarbonUsed().add(quantity));

        // 买方增加总配额和可用配额
        buyer.setCarbonQuota(buyer.getCarbonQuota().add(quantity));
        buyer.setCarbonTradable(buyer.getCarbonTradable().add(quantity));

        enterpriseRepository.save(seller);
        enterpriseRepository.save(buyer);
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
