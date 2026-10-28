package com.oaiss.chain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TradeException 单元测试
 * TradeException Unit Tests
 */
class TradeExceptionTest {

    @Test
    @DisplayName("创建基本异常")
    void testBasicException() {
        TradeException ex = new TradeException(1001, "Test error");
        assertEquals(1001, ex.getCode());
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("创建带原因的异常")
    void testExceptionWithCause() {
        Throwable cause = new RuntimeException("Original cause");
        TradeException ex = new TradeException(1002, "Test error", cause);
        assertEquals(1002, ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("tradeNotFound工厂方法")
    void testTradeNotFound() {
        TradeException ex = TradeException.tradeNotFound(1L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("1"));
    }

    @Test
    @DisplayName("insufficientBalance工厂方法")
    void testInsufficientBalance() {
        TradeException ex = TradeException.insufficientBalance(100.0, 200.0);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("100"));
        assertTrue(ex.getMessage().contains("200"));
    }

    @Test
    @DisplayName("insufficientQuota工厂方法")
    void testInsufficientQuota() {
        TradeException ex = TradeException.insufficientQuota(new BigDecimal("50"), new BigDecimal("100"));
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("50"));
    }

    @Test
    @DisplayName("auctionEnded工厂方法")
    void testAuctionEnded() {
        TradeException ex = TradeException.auctionEnded(2L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("2"));
    }

    @Test
    @DisplayName("auctionNotStarted工厂方法")
    void testAuctionNotStarted() {
        TradeException ex = TradeException.auctionNotStarted(3L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("3"));
    }

    @Test
    @DisplayName("bidTooLow工厂方法")
    void testBidTooLow() {
        TradeException ex = TradeException.bidTooLow(50.0, 100.0);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("50"));
        assertTrue(ex.getMessage().contains("100"));
    }

    @Test
    @DisplayName("orderCancelled工厂方法")
    void testOrderCancelled() {
        TradeException ex = TradeException.orderCancelled(4L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("4"));
    }

    @Test
    @DisplayName("orderCompleted工厂方法")
    void testOrderCompleted() {
        TradeException ex = TradeException.orderCompleted(5L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("5"));
    }

    @Test
    @DisplayName("samePartyError工厂方法")
    void testSamePartyError() {
        TradeException ex = TradeException.samePartyError(6L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("6"));
    }

    @Test
    @DisplayName("peerOffline工厂方法")
    void testPeerOffline() {
        TradeException ex = TradeException.peerOffline(7L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("7"));
    }

    @Test
    @DisplayName("tradeAmountExceeded工厂方法")
    void testTradeAmountExceeded() {
        TradeException ex = TradeException.tradeAmountExceeded(10000.0, 5000.0);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("10000"));
    }

    @Test
    @DisplayName("orderAlreadyExists工厂方法")
    void testOrderAlreadyExists() {
        TradeException ex = TradeException.orderAlreadyExists(8L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("8"));
    }
}
