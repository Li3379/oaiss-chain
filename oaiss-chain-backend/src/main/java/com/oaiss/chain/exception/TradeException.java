package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 碳交易业务异常
 * Carbon Trade Exception
 * 
 * @author OAISS Team
 */
@Getter
public class TradeException extends BusinessException {

    public TradeException(Integer code, String message) {
        super(code, message);
    }

    public TradeException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 交易不存在
     */
    public static TradeException tradeNotFound(Long tradeId) {
        return new TradeException(ErrorCode.TRADE_NOT_FOUND, 
                "交易不存在: " + tradeId);
    }

    /**
     * 余额不足
     */
    public static TradeException insufficientBalance(Double balance, Double required) {
        return new TradeException(ErrorCode.INSUFFICIENT_BALANCE, 
                String.format("余额不足: 当前%.2f, 需要%.2f", balance, required));
    }

    /**
     * 余额不足 (BigDecimal)
     */
    public static TradeException insufficientBalance(BigDecimal balance, BigDecimal required) {
        return new TradeException(ErrorCode.INSUFFICIENT_BALANCE, 
                String.format("余额不足: 当前%.2f, 需要%.2f", balance, required));
    }

    /**
     * 碳配额不足
     */
    public static TradeException insufficientQuota(BigDecimal quota, BigDecimal required) {
        return new TradeException(ErrorCode.INSUFFICIENT_QUOTA, 
                String.format("碳配额不足: 当前%.2f, 需要%.2f", quota, required));
    }

    /**
     * 拍卖已结束
     */
    public static TradeException auctionEnded(Long auctionId) {
        return new TradeException(ErrorCode.AUCTION_ENDED, 
                "拍卖已结束: " + auctionId);
    }

    /**
     * 拍卖未开始
     */
    public static TradeException auctionNotStarted(Long auctionId) {
        return new TradeException(ErrorCode.AUCTION_NOT_STARTED, 
                "拍卖未开始: " + auctionId);
    }

    /**
     * 出价过低
     */
    public static TradeException bidTooLow(Double bidPrice, Double minPrice) {
        return new TradeException(ErrorCode.BID_TOO_LOW, 
                String.format("出价过低: 当前出价%.2f, 最低价%.2f", bidPrice, minPrice));
    }

    /**
     * 订单已取消
     */
    public static TradeException orderCancelled(Long orderId) {
        return new TradeException(ErrorCode.ORDER_CANCELLED, 
                "订单已取消: " + orderId);
    }

    /**
     * 订单已完成
     */
    public static TradeException orderCompleted(Long orderId) {
        return new TradeException(ErrorCode.ORDER_COMPLETED, 
                "订单已完成: " + orderId);
    }

    /**
     * 交易双方相同
     */
    public static TradeException samePartyError(Long userId) {
        return new TradeException(ErrorCode.SAME_PARTY_ERROR, 
                "交易双方不能为同一用户: " + userId);
    }

    /**
     * P2P对方不在线
     */
    public static TradeException peerOffline(Long peerId) {
        return new TradeException(ErrorCode.PEER_OFFLINE, 
                "P2P交易对方不在线: " + peerId);
    }

    /**
     * 交易金额超限
     */
    public static TradeException tradeAmountExceeded(Double amount, Double maxAmount) {
        return new TradeException(ErrorCode.TRADE_AMOUNT_EXCEEDED, 
                String.format("交易金额超出限制: %.2f > %.2f", amount, maxAmount));
    }

    /**
     * 挂单已存在
     */
    public static TradeException orderAlreadyExists(Long orderId) {
        return new TradeException(ErrorCode.ORDER_ALREADY_EXISTS,
                "挂单已存在: " + orderId);
    }

    /**
     * 信誉分过低/账户冻结导致交易受限
     */
    public static TradeException creditTradeRestricted(String message) {
        return new TradeException(ErrorCode.CREDIT_TRADE_RESTRICTED, message);
    }

    /**
     * 交易越权-当前用户不是交易参与方
     */
    public static TradeException unauthorizedTrade(Long currentUserId, Long expectedUserId) {
        return new TradeException(ErrorCode.PERMISSION_DENIED,
                String.format("无权操作: 当前用户%d不是指定的交易方%d", currentUserId, expectedUserId));
    }
}
