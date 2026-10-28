package com.oaiss.chain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlockchainException 单元测试
 * BlockchainException Unit Tests
 */
class BlockchainExceptionTest {

    @Test
    @DisplayName("创建基本异常")
    void testBasicException() {
        BlockchainException ex = new BlockchainException(1001, "Test error");
        assertEquals(1001, ex.getCode());
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("创建带原因的异常")
    void testExceptionWithCause() {
        Throwable cause = new RuntimeException("Original cause");
        BlockchainException ex = new BlockchainException(1002, "Test error", cause);
        assertEquals(1002, ex.getCode());
        assertEquals("Test error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("connectionFailed工厂方法")
    void testConnectionFailed() {
        BlockchainException ex = BlockchainException.connectionFailed("Network timeout");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Network timeout"));
    }

    @Test
    @DisplayName("chaincodeInvokeFailed工厂方法")
    void testChaincodeInvokeFailed() {
        BlockchainException ex = BlockchainException.chaincodeInvokeFailed("myChaincode", "Invalid args");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("myChaincode"));
        assertTrue(ex.getMessage().contains("Invalid args"));
    }

    @Test
    @DisplayName("txCommitFailed工厂方法")
    void testTxCommitFailed() {
        BlockchainException ex = BlockchainException.txCommitFailed("tx123", "Endorsement failure");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("tx123"));
    }

    @Test
    @DisplayName("blockQueryFailed工厂方法")
    void testBlockQueryFailed() {
        BlockchainException ex = BlockchainException.blockQueryFailed(100L, "Block not found");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("100"));
    }

    @Test
    @DisplayName("txQueryFailed工厂方法")
    void testTxQueryFailed() {
        BlockchainException ex = BlockchainException.txQueryFailed("tx456", "Transaction not found");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("tx456"));
    }

    @Test
    @DisplayName("channelNotFound工厂方法")
    void testChannelNotFound() {
        BlockchainException ex = BlockchainException.channelNotFound("mychannel");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("mychannel"));
    }

    @Test
    @DisplayName("identityAuthFailed工厂方法")
    void testIdentityAuthFailed() {
        BlockchainException ex = BlockchainException.identityAuthFailed("user1");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("user1"));
    }

    @Test
    @DisplayName("smartContractError工厂方法")
    void testSmartContractError() {
        BlockchainException ex = BlockchainException.smartContractError("MyContract", "Execution reverted");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("MyContract"));
    }

    @Test
    @DisplayName("rsaKeyGenerationFailed工厂方法")
    void testRsaKeyGenerationFailed() {
        BlockchainException ex = BlockchainException.rsaKeyGenerationFailed("Key size too small");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Key size too small"));
    }

    @Test
    @DisplayName("rsaKeyPairNotFound工厂方法")
    void testRsaKeyPairNotFound() {
        BlockchainException ex = BlockchainException.rsaKeyPairNotFound(1L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("1"));
    }

    @Test
    @DisplayName("rsaSignFailed工厂方法")
    void testRsaSignFailed() {
        BlockchainException ex = BlockchainException.rsaSignFailed("Invalid private key");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Invalid private key"));
    }

    @Test
    @DisplayName("rsaVerifyFailed工厂方法")
    void testRsaVerifyFailed() {
        BlockchainException ex = BlockchainException.rsaVerifyFailed("Signature mismatch");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Signature mismatch"));
    }

    @Test
    @DisplayName("rsaEncryptFailed工厂方法")
    void testRsaEncryptFailed() {
        BlockchainException ex = BlockchainException.rsaEncryptFailed("Data too large");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Data too large"));
    }

    @Test
    @DisplayName("rsaDecryptFailed工厂方法")
    void testRsaDecryptFailed() {
        BlockchainException ex = BlockchainException.rsaDecryptFailed("Invalid ciphertext");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Invalid ciphertext"));
    }

    @Test
    @DisplayName("rsaKeyExpired工厂方法")
    void testRsaKeyExpired() {
        BlockchainException ex = BlockchainException.rsaKeyExpired(2L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("2"));
    }

    @Test
    @DisplayName("rsaKeyRevoked工厂方法")
    void testRsaKeyRevoked() {
        BlockchainException ex = BlockchainException.rsaKeyRevoked(3L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("3"));
    }
}
