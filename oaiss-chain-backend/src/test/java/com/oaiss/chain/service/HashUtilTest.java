package com.oaiss.chain.service;

import com.oaiss.chain.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SHA-256哈希工具测试
 * 
 * @author OAISS Team
 */
class HashUtilTest {

    @Test
    @DisplayName("SHA-256哈希 - 应返回64位十六进制字符串")
    void testSha256Hex() {
        String input = "Hello, OAISS Chain!";
        String hash = HashUtil.sha256Hex(input);
        
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("SHA-256哈希 - 相同输入应产生相同哈希")
    void testSha256HexConsistent() {
        String input = "test data";
        String hash1 = HashUtil.sha256Hex(input);
        String hash2 = HashUtil.sha256Hex(input);
        
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("SHA-256哈希 - 不同输入应产生不同哈希")
    void testSha256HexDifferent() {
        String hash1 = HashUtil.sha256Hex("data1");
        String hash2 = HashUtil.sha256Hex("data2");
        
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("SHA-256 Base64 - 应返回有效的Base64字符串")
    void testSha256Base64() {
        String input = "test";
        String hash = HashUtil.sha256Base64(input);
        
        assertNotNull(hash);
        assertTrue(hash.length() > 0);
    }

    @Test
    @DisplayName("验证完整性 - 正确的数据应验证通过")
    void testVerifyIntegrityCorrect() {
        String data = "carbon report data";
        String hash = HashUtil.sha256Hex(data);
        
        assertTrue(HashUtil.verifyIntegrity(data, hash));
    }

    @Test
    @DisplayName("验证完整性 - 篡改的数据应验证失败")
    void testVerifyIntegrityTampered() {
        String originalData = "original data";
        String tamperedData = "tampered data";
        String hash = HashUtil.sha256Hex(originalData);
        
        assertFalse(HashUtil.verifyIntegrity(tamperedData, hash));
    }

    @Test
    @DisplayName("验证完整性 - 空数据应正常处理")
    void testVerifyIntegrityEmpty() {
        String hash = HashUtil.sha256Hex("");
        assertTrue(HashUtil.verifyIntegrity("", hash));
    }
}
