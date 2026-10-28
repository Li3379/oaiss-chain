package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.RsaKeyPairResponse;
import com.oaiss.chain.dto.SignatureResult;
import com.oaiss.chain.entity.RsaKeyPair;
import com.oaiss.chain.exception.BlockchainException;
import com.oaiss.chain.repository.RsaKeyPairRepository;
import com.oaiss.chain.util.AesGcmEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DigitalSignatureService 单元测试
 * DigitalSignatureService Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class DigitalSignatureServiceTest {

    @Mock
    private RsaKeyPairRepository rsaKeyPairRepository;

    @Mock
    private AesGcmEncryptor aesGcmEncryptor;

    @Mock
    private com.oaiss.chain.security.crypto.SignatureStrategyFactory signatureStrategyFactory;

    @InjectMocks
    private DigitalSignatureService digitalSignatureService;

    private RsaKeyPair testKeyPair;

    @BeforeEach
    void setUp() {
        // Identity mock: encrypt/decrypt pass through unchanged
        // (tests set plaintext keys directly; real encryption is tested in AesGcmEncryptorTest)
        lenient().when(aesGcmEncryptor.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(aesGcmEncryptor.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));

        var rsaStrategy = new com.oaiss.chain.security.crypto.RsaSignatureStrategy();
        var sm2Strategy = new com.oaiss.chain.security.crypto.Sm2SignatureStrategy();
        var factory = new com.oaiss.chain.security.crypto.SignatureStrategyFactory(java.util.List.of(rsaStrategy, sm2Strategy));
        lenient().when(signatureStrategyFactory.getStrategy(any())).thenAnswer(inv -> factory.getStrategy(inv.getArgument(0)));
        lenient().when(signatureStrategyFactory.getDefaultStrategy()).thenReturn(rsaStrategy);

        testKeyPair = RsaKeyPair.builder()
                .userId(1L)
                .publicKey("test-public-key-base64")
                .privateKey("test-private-key-base64")
                .keyStatus(1) // Active
                .keyVersion(1)
                .keyUsage(3)
                .expiresAt(LocalDateTime.now().plusYears(2))
                .build();
        testKeyPair.setId(1L);
        testKeyPair.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("获取密钥对成功")
    void testGetKeyPairSuccess() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals(1, response.getKeyStatus());
        verify(rsaKeyPairRepository, atLeast(1)).findLatestByUserId(1L);
    }

    @Test
    @DisplayName("获取密钥对-不存在时返回null")
    void testGetKeyPairFailNotFound() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        // When
        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);

        // Then
        assertNull(response);
    }

    @Test
    @DisplayName("获取密钥对失败-已撤销")
    void testGetKeyPairFailRevoked() {
        // Given
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);

        // Then
        assertNotNull(response);
        assertEquals(0, response.getKeyStatus());
        assertEquals("已失效", response.getKeyStatusText());
        verify(rsaKeyPairRepository, never()).save(any(RsaKeyPair.class));
    }

    @Test
    @DisplayName("获取密钥对失败-已过期")
    void testGetKeyPairFailExpired() {
        // Given
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenReturn(testKeyPair);

        // When
        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);

        // Then
        assertNotNull(response);
        assertEquals(2, response.getKeyStatus());
        assertEquals("已过期", response.getKeyStatusText());
        verify(rsaKeyPairRepository, times(1)).save(testKeyPair);
    }

    @Test
    @DisplayName("撤销密钥对成功")
    void testRevokeKeyPairSuccess() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenReturn(testKeyPair);

        // When
        digitalSignatureService.revokeKeyPair(1L);

        // Then
        verify(rsaKeyPairRepository, times(1)).save(any(RsaKeyPair.class));
    }

    @Test
    @DisplayName("撤销密钥对-不存在时无操作")
    void testRevokeKeyPairNotFound() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        // When
        digitalSignatureService.revokeKeyPair(1L);

        // Then
        verify(rsaKeyPairRepository, never()).save(any());
    }

    @Test
    @DisplayName("生成密钥对-已有密钥时撤销旧密钥")
    void testGenerateKeyPairWithExistingKey() {
        // Given
        when(rsaKeyPairRepository.existsByUserIdAndDeletedFalse(1L)).thenReturn(true);
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenAnswer(invocation -> {
            RsaKeyPair keyPair = invocation.getArgument(0);
            keyPair.setId(2L);
            return keyPair;
        });

        // When
        RsaKeyPairResponse response = digitalSignatureService.generateKeyPair(1L);

        // Then
        assertNotNull(response);
        // Should have called save for both revoke and new key
        verify(rsaKeyPairRepository, atLeastOnce()).save(any(RsaKeyPair.class));
    }

    @Test
    @DisplayName("签名失败-密钥不存在")
    void testSignReportFailKeyNotFound() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BlockchainException.class, () -> digitalSignatureService.signReport(1L, "test-data"));
    }

    @Test
    @DisplayName("验签失败-密钥不存在")
    void testVerifySignatureFailKeyNotFound() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BlockchainException.class, () ->
            digitalSignatureService.verifySignature(1L, "test-data", "test-signature"));
    }

    @Test
    @DisplayName("验签失败-密钥已撤销")
    void testVerifySignatureFailKeyRevoked() {
        // Given
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () ->
            digitalSignatureService.verifySignature(1L, "test-data", "test-signature"));
    }

    @Test
    @DisplayName("加密失败-密钥不存在")
    void testEncryptForReviewerFailKeyNotFound() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BlockchainException.class, () ->
            digitalSignatureService.encryptForReviewer("test-data", 1L));
    }

    @Test
    @DisplayName("解密失败-密钥不存在")
    void testDecryptForReviewerFailKeyNotFound() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BlockchainException.class, () ->
            digitalSignatureService.decryptForReviewer("encrypted-data", 1L));
    }

    @Test
    @DisplayName("生成密钥对成功-无现有密钥")
    void testGenerateKeyPairSuccessNoExistingKey() {
        // Given
        when(rsaKeyPairRepository.existsByUserIdAndDeletedFalse(1L)).thenReturn(false);
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenAnswer(invocation -> {
            RsaKeyPair keyPair = invocation.getArgument(0);
            keyPair.setId(1L);
            return keyPair;
        });

        // When
        RsaKeyPairResponse response = digitalSignatureService.generateKeyPair(1L);

        // Then
        assertNotNull(response);
        assertNotNull(response.getPublicKey());
        assertEquals(1L, response.getUserId());
        assertEquals(1, response.getKeyStatus());
    }

    @Test
    @DisplayName("生成密钥对必须同时标记 encrypted=true —— 防止启动迁移二次加密毁钥")
    void testGenerateKeyPairMustMarkEncryptedTrue() {
        // Given
        when(rsaKeyPairRepository.existsByUserIdAndDeletedFalse(9L)).thenReturn(false);
        when(rsaKeyPairRepository.findLatestByUserId(9L)).thenReturn(Optional.empty());
        java.util.concurrent.atomic.AtomicReference<RsaKeyPair> persisted =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenAnswer(invocation -> {
            RsaKeyPair keyPair = invocation.getArgument(0);
            keyPair.setId(99L);
            persisted.set(keyPair);
            return keyPair;
        });

        // When
        digitalSignatureService.generateKeyPair(9L);

        // Then
        RsaKeyPair saved = persisted.get();
        assertNotNull(saved, "密钥对必须被持久化");
        assertEquals(Boolean.TRUE, saved.getEncrypted(),
                "私钥加密存储时必须同标记 encrypted=true，否则启动迁移会再次加密（毁钥）");
        verify(aesGcmEncryptor, times(1)).encrypt(anyString());
    }

    @Test
    @DisplayName("签名成功-使用真实密钥")
    void testSignReportSuccess() throws Exception {
        // Given - Generate a real key pair for testing
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());
        
        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        SignatureResult result = digitalSignatureService.signReport(1L, "test-report-data");

        // Then
        assertNotNull(result);
        assertNotNull(result.getSignature());
        assertEquals(1L, result.getSignerId());
        assertEquals("SHA256withRSA", result.getAlgorithm());
    }

    @Test
    @DisplayName("验签成功-使用真实密钥")
    void testVerifySignatureSuccess() throws Exception {
        // Given - Generate a real key pair and sign data
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());
        
        String testData = "test-report-data";
        String signature = com.oaiss.chain.util.RsaKeyUtil.sign(testData, realKeyPair.getPrivate());
        
        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        boolean isValid = digitalSignatureService.verifySignature(1L, testData, signature);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("验签失败-数据被篡改")
    void testVerifySignatureFailTamperedData() throws Exception {
        // Given - Generate a real key pair and sign data
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());
        
        String testData = "test-report-data";
        String signature = com.oaiss.chain.util.RsaKeyUtil.sign(testData, realKeyPair.getPrivate());
        
        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When - Verify with tampered data
        boolean isValid = digitalSignatureService.verifySignature(1L, "tampered-data", signature);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("验签成功-密钥已过期但允许验签")
    void testVerifySignatureWithExpiredKey() throws Exception {
        // Given - Generate a real key pair and sign data
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());
        
        String testData = "test-report-data";
        String signature = com.oaiss.chain.util.RsaKeyUtil.sign(testData, realKeyPair.getPrivate());
        
        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        testKeyPair.setKeyStatus(2); // Expired status
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1));
        
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        boolean isValid = digitalSignatureService.verifySignature(1L, testData, signature);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("加密成功-使用真实密钥")
    void testEncryptForReviewerSuccess() throws Exception {
        // Given - Generate a real key pair
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());
        
        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        String encryptedData = digitalSignatureService.encryptForReviewer("sensitive-data", 1L);

        // Then
        assertNotNull(encryptedData);
        assertFalse(encryptedData.isEmpty());
    }

    @Test
    @DisplayName("解密成功-使用真实密钥")
    void testDecryptForReviewerSuccess() throws Exception {
        // Given - Generate a real key pair and encrypt data
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());
        
        String testData = "sensitive-data";
        String encryptedData = com.oaiss.chain.util.RsaKeyUtil.encrypt(testData, realKeyPair.getPublic());
        
        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        String decryptedData = digitalSignatureService.decryptForReviewer(encryptedData, 1L);

        // Then
        assertEquals(testData, decryptedData);
    }

    @Test
    @DisplayName("企业解密成功-使用真实密钥")
    void testDecryptForEnterpriseSuccess() throws Exception {
        // Given - Generate a real key pair and encrypt data
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());
        
        String testData = "enterprise-data";
        String encryptedData = com.oaiss.chain.util.RsaKeyUtil.encrypt(testData, realKeyPair.getPublic());
        
        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        String decryptedData = digitalSignatureService.decryptForEnterprise(encryptedData, 1L);

        // Then
        assertEquals(testData, decryptedData);
    }

    @Test
    @DisplayName("企业解密失败-密钥不存在")
    void testDecryptForEnterpriseFailKeyNotFound() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(BlockchainException.class, () -> 
            digitalSignatureService.decryptForEnterprise("encrypted-data", 1L));
    }

    @Test
    @DisplayName("企业解密失败-密钥已撤销")
    void testDecryptForEnterpriseFailKeyRevoked() {
        // Given
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () -> 
            digitalSignatureService.decryptForEnterprise("encrypted-data", 1L));
    }

    @Test
    @DisplayName("签名失败-密钥已撤销")
    void testSignReportFailKeyRevoked() {
        // Given
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () -> digitalSignatureService.signReport(1L, "test-data"));
    }

    @Test
    @DisplayName("加密失败-密钥已撤销")
    void testEncryptForReviewerFailKeyRevoked() {
        // Given
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () -> 
            digitalSignatureService.encryptForReviewer("test-data", 1L));
    }

    @Test
    @DisplayName("解密失败-密钥已撤销")
    void testDecryptForReviewerFailKeyRevoked() {
        // Given
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () -> 
            digitalSignatureService.decryptForReviewer("encrypted-data", 1L));
    }

    @Test
    @DisplayName("获取密钥对-状态文本已失效")
    void testGetKeyPairStatusRevoked() {
        // Given
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);

        // Then
        assertNotNull(response);
        assertEquals(0, response.getKeyStatus());
        assertEquals("已失效", response.getKeyStatusText());
    }

    @Test
    @DisplayName("获取密钥对-状态已过期标记")
    void testGetKeyPairStatusExpiredMarked() {
        // Given
        testKeyPair.setKeyStatus(2); // Expired status
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);

        // Then
        assertNotNull(response);
        assertEquals(2, response.getKeyStatus());
        assertEquals("已过期", response.getKeyStatusText());
        verify(rsaKeyPairRepository, never()).save(any(RsaKeyPair.class));
    }

    // ==================== Additional coverage tests ====================

    @Test
    @DisplayName("签名失败-密钥已过期(状态标记)")
    void testSignReportFailKeyExpiredByStatus() {
        // Given
        testKeyPair.setKeyStatus(2); // Expired
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () -> digitalSignatureService.signReport(1L, "test-data"));
    }

    @Test
    @DisplayName("签名失败-密钥已过期(日期过期)")
    void testSignReportFailKeyExpiredByDate() {
        // Given
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenReturn(testKeyPair);

        // When & Then - after markExpiredKeys, key status becomes 2
        assertThrows(BlockchainException.class, () -> digitalSignatureService.signReport(1L, "test-data"));
    }

    @Test
    @DisplayName("加密失败-密钥已过期(状态标记)")
    void testEncryptForReviewerFailKeyExpiredByStatus() {
        // Given
        testKeyPair.setKeyStatus(2); // Expired
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () ->
            digitalSignatureService.encryptForReviewer("test-data", 1L));
    }

    @Test
    @DisplayName("解密失败-审核员密钥已过期(状态标记)")
    void testDecryptForReviewerFailKeyExpiredByStatus() {
        // Given
        testKeyPair.setKeyStatus(2); // Expired
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () ->
            digitalSignatureService.decryptForReviewer("encrypted-data", 1L));
    }

    @Test
    @DisplayName("企业解密失败-密钥已过期(状态标记)")
    void testDecryptForEnterpriseFailKeyExpiredByStatus() {
        // Given
        testKeyPair.setKeyStatus(2); // Expired
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When & Then
        assertThrows(BlockchainException.class, () ->
            digitalSignatureService.decryptForEnterprise("encrypted-data", 1L));
    }

    @Test
    @DisplayName("标记过期密钥-密钥日期已过期且状态非过期")
    void testMarkExpiredKeysWhenExpiredByDate() {
        // Given
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1));
        testKeyPair.setKeyStatus(1); // Active but expired by date
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenReturn(testKeyPair);

        // When
        digitalSignatureService.markExpiredKeys(1L);

        // Then
        assertEquals(2, testKeyPair.getKeyStatus()); // Should be marked as expired
        verify(rsaKeyPairRepository).save(testKeyPair);
    }

    @Test
    @DisplayName("标记过期密钥-密钥未过期时不修改状态")
    void testMarkExpiredKeysWhenNotExpired() {
        // Given
        testKeyPair.setExpiresAt(LocalDateTime.now().plusYears(1));
        testKeyPair.setKeyStatus(1); // Active
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        digitalSignatureService.markExpiredKeys(1L);

        // Then
        assertEquals(1, testKeyPair.getKeyStatus()); // Should remain active
        verify(rsaKeyPairRepository, never()).save(any());
    }

    @Test
    @DisplayName("标记过期密钥-密钥不存在时无操作")
    void testMarkExpiredKeysWhenNoKeyPair() {
        // Given
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        // When
        digitalSignatureService.markExpiredKeys(1L);

        // Then
        verify(rsaKeyPairRepository, never()).save(any());
    }

    @Test
    @DisplayName("获取密钥对-状态文本未知值")
    void testGetKeyPairStatusUnknown() {
        // Given
        testKeyPair.setKeyStatus(99); // Unknown status
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When
        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);

        // Then
        assertNotNull(response);
        assertEquals(99, response.getKeyStatus());
        assertEquals("未知", response.getKeyStatusText());
    }

    @Test
    @DisplayName("验签-密钥已过期状态但验签允许")
    void testVerifySignatureWithExpiredStatusKey() throws Exception {
        // Given
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());

        String testData = "verify-expired-status-test";
        String signature = com.oaiss.chain.util.RsaKeyUtil.sign(testData, realKeyPair.getPrivate());

        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        testKeyPair.setKeyStatus(2); // Expired status
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // When - expired keys are allowed for verification (with warning)
        boolean isValid = digitalSignatureService.verifySignature(1L, testData, signature);

        // Then
        assertTrue(isValid);
    }

    // ==================== Additional branch coverage tests ====================

    @Test
    @DisplayName("getKeyPair-首次lookup返回null后第二次也返回null")
    void testGetKeyPairBothLookupsReturnNull() {
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);
        assertNull(response);
    }

    @Test
    @DisplayName("getKeyPair-首次lookup有key但第二次lookup返回null")
    void testGetKeyPairSecondLookupReturnsNull() {
        when(rsaKeyPairRepository.findLatestByUserId(1L))
                .thenReturn(Optional.of(testKeyPair))
                .thenReturn(Optional.empty());

        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);
        assertNull(response);
    }

    @Test
    @DisplayName("generateKeyPair-生成异常时抛出BlockchainException")
    void testGenerateKeyPairException() {
        when(rsaKeyPairRepository.existsByUserIdAndDeletedFalse(2L)).thenReturn(false);
        when(rsaKeyPairRepository.findLatestByUserId(2L)).thenReturn(Optional.empty());
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenThrow(new RuntimeException("DB error"));

        assertThrows(BlockchainException.class, () -> digitalSignatureService.generateKeyPair(2L));
    }

    @Test
    @DisplayName("signReport-签名时密钥被撤销")
    void testSignReportKeyRevokedDuringMarkExpired() {
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        assertThrows(BlockchainException.class, () -> digitalSignatureService.signReport(1L, "data"));
    }

    @Test
    @DisplayName("verifySignature-密钥被撤销时抛出异常")
    void testVerifySignatureRevokedKey() {
        testKeyPair.setKeyStatus(0); // Revoked
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        assertThrows(BlockchainException.class,
                () -> digitalSignatureService.verifySignature(1L, "data", "sig"));
    }

    @Test
    @DisplayName("verifySignature-密钥过期日期时仍允许验签(带警告)")
    void testVerifySignatureExpiredByDate() throws Exception {
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());

        String testData = "test-data";
        String signature = com.oaiss.chain.util.RsaKeyUtil.sign(testData, realKeyPair.getPrivate());

        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        testKeyPair.setKeyStatus(1); // Active status but expired by date
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        boolean isValid = digitalSignatureService.verifySignature(1L, testData, signature);
        assertTrue(isValid);
    }

    @Test
    @DisplayName("validateKeyStatus-密钥日期过期时抛出异常")
    void testValidateKeyStatusExpiredByDate() {
        testKeyPair.setKeyStatus(1); // Active
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        assertThrows(BlockchainException.class,
                () -> digitalSignatureService.signReport(1L, "data"));
    }

    @Test
    @DisplayName("validateKeyStatus-密钥状态已过期(2)时抛出异常")
    void testValidateKeyStatusExpiredByStatusCode() {
        testKeyPair.setKeyStatus(2); // Expired
        testKeyPair.setExpiresAt(LocalDateTime.now().plusYears(1)); // Not expired by date
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        assertThrows(BlockchainException.class,
                () -> digitalSignatureService.signReport(1L, "data"));
    }

    @Test
    @DisplayName("markExpiredKeys-密钥未过期时不修改")
    void testMarkExpiredKeysNotExpired() {
        testKeyPair.setExpiresAt(LocalDateTime.now().plusYears(1));
        testKeyPair.setKeyStatus(1);
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        digitalSignatureService.markExpiredKeys(1L);

        verify(rsaKeyPairRepository, never()).save(any());
    }

    @Test
    @DisplayName("markExpiredKeys-密钥不存在时不操作")
    void testMarkExpiredKeysNotFound() {
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        digitalSignatureService.markExpiredKeys(1L);

        verify(rsaKeyPairRepository, never()).save(any());
    }

    @Test
    @DisplayName("markExpiredKeys-密钥已过期且状态已是EXPIRED不重复保存")
    void testMarkExpiredKeysAlreadyExpiredStatus() {
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1));
        testKeyPair.setKeyStatus(2); // Already expired
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        digitalSignatureService.markExpiredKeys(1L);

        verify(rsaKeyPairRepository, never()).save(any());
    }

    @Test
    @DisplayName("toResponse-有效密钥状态返回有效文本")
    void testToResponseActiveKey() {
        testKeyPair.setKeyStatus(1);
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);
        assertEquals("有效", response.getKeyStatusText());
    }

    @Test
    @DisplayName("toResponse-已失效密钥状态返回已失效文本")
    void testToResponseRevokedKey() {
        testKeyPair.setKeyStatus(0);
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);
        assertEquals("已失效", response.getKeyStatusText());
    }

    @Test
    @DisplayName("toResponse-已过期密钥状态返回已过期文本")
    void testToResponseExpiredKey() {
        testKeyPair.setKeyStatus(2);
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);
        assertEquals("已过期", response.getKeyStatusText());
    }

    @Test
    @DisplayName("toResponse-未知密钥状态返回未知文本")
    void testToResponseUnknownKey() {
        testKeyPair.setKeyStatus(99);
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);
        assertEquals("未知", response.getKeyStatusText());
    }

    // ==================== Additional branch coverage tests (batch 2) ====================

    @Test
    @DisplayName("verifySignature-密钥expiresAt为null时跳过过期检查")
    void testVerifySignatureNullExpiresAt() throws Exception {
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());

        String testData = "test-data-null-expiry";
        String sig = com.oaiss.chain.util.RsaKeyUtil.sign(testData, realKeyPair.getPrivate());

        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        testKeyPair.setKeyStatus(1); // Active
        testKeyPair.setExpiresAt(null); // null expiry

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        boolean isValid = digitalSignatureService.verifySignature(1L, testData, sig);
        assertTrue(isValid);
    }

    @Test
    @DisplayName("signReport-签名过程异常时抛出BlockchainException")
    void testSignReportExceptionDuringSigning() throws Exception {
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = "invalid-base64-private-key";

        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        assertThrows(BlockchainException.class, () -> digitalSignatureService.signReport(1L, "data"));
    }

    @Test
    @DisplayName("encryptForReviewer-加密过程异常时抛出BlockchainException")
    void testEncryptForReviewerException() throws Exception {
        testKeyPair.setPublicKey("invalid-base64-public-key");

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        assertThrows(BlockchainException.class, () ->
                digitalSignatureService.encryptForReviewer("data", 1L));
    }

    @Test
    @DisplayName("decryptForReviewer-解密过程异常时抛出BlockchainException")
    void testDecryptForReviewerException() throws Exception {
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = "invalid-private-key";

        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        assertThrows(BlockchainException.class, () ->
                digitalSignatureService.decryptForReviewer("invalid-encrypted-data", 1L));
    }

    @Test
    @DisplayName("decryptForEnterprise-解密过程异常时抛出BlockchainException")
    void testDecryptForEnterpriseException() throws Exception {
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = "invalid-private-key";

        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        assertThrows(BlockchainException.class, () ->
                digitalSignatureService.decryptForEnterprise("invalid-encrypted-data", 1L));
    }

    @Test
    @DisplayName("validateKeyStatus-密钥过期日期检查中expiresAt为null")
    void testValidateKeyStatusNullExpiresAt() throws Exception {
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());

        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);
        testKeyPair.setKeyStatus(1); // Active
        testKeyPair.setExpiresAt(null); // null expiresAt

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        // Should not throw because keyStatus is ACTIVE and expiresAt is null
        RsaKeyPairResponse response = digitalSignatureService.getKeyPair(1L);
        assertNotNull(response);
    }

    @Test
    @DisplayName("markExpiredKeys-密钥已过期且状态为REVOKED时应更新为EXPIRED")
    void testMarkExpiredKeysRevokedNotExpiredStatus() {
        testKeyPair.setExpiresAt(LocalDateTime.now().minusDays(1));
        testKeyPair.setKeyStatus(0); // REVOKED
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        digitalSignatureService.markExpiredKeys(1L);

        // REVOKED key that is expired should be changed to EXPIRED (2) since condition only skips EXPIRED keys
        assertEquals(2, testKeyPair.getKeyStatus());
        verify(rsaKeyPairRepository, times(1)).save(testKeyPair);
    }

    @Test
    @DisplayName("generateKeyPair-已有密钥时撤销旧密钥并生成新密钥")
    void testGenerateKeyPairRevokeOldAndCreateNew() {
        when(rsaKeyPairRepository.existsByUserIdAndDeletedFalse(1L)).thenReturn(true);
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenAnswer(invocation -> {
            RsaKeyPair kp = invocation.getArgument(0);
            kp.setId(3L);
            return kp;
        });

        RsaKeyPairResponse response = digitalSignatureService.generateKeyPair(1L);
        assertNotNull(response);
        // save is called at least twice: once for revoke, once for new key
        verify(rsaKeyPairRepository, atLeast(2)).save(any(RsaKeyPair.class));
    }

    @Test
    @DisplayName("signReport-签名成功后返回正确结果")
    void testSignReportReturnsCorrectFields() throws Exception {
        java.security.KeyPair realKeyPair = com.oaiss.chain.util.RsaKeyUtil.generateKeyPair();
        String publicKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPublic());
        String privateKeyBase64 = com.oaiss.chain.util.RsaKeyUtil.encodeKey(realKeyPair.getPrivate());

        testKeyPair.setPublicKey(publicKeyBase64);
        testKeyPair.setPrivateKey(privateKeyBase64);

        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.of(testKeyPair));

        SignatureResult result = digitalSignatureService.signReport(1L, "report-data");
        assertNotNull(result);
        assertNotNull(result.getSignature());
        assertNotNull(result.getAlgorithm());
        assertNotNull(result.getTimestamp());
        assertEquals(1L, result.getSignerId());
    }

    @Test
    @DisplayName("decryptForEnterprise-密钥不存在时抛出异常")
    void testDecryptForEnterpriseKeyNotFound() {
        when(rsaKeyPairRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(BlockchainException.class, () ->
                digitalSignatureService.decryptForEnterprise("encrypted", 1L));
    }

    @Test
    @DisplayName("generateKeyPair-支持显式指定SM2算法生成国密密钥对")
    void testGenerateKeyPairSm2Success() {
        when(rsaKeyPairRepository.existsByUserIdAndDeletedFalse(2L)).thenReturn(false);
        when(rsaKeyPairRepository.findLatestByUserId(2L)).thenReturn(Optional.empty());
        when(rsaKeyPairRepository.save(any(RsaKeyPair.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RsaKeyPairResponse response = digitalSignatureService.generateKeyPair(2L, "SM2");

        assertNotNull(response);
        assertEquals("SM2", response.getAlgorithm());
        assertNotNull(response.getPublicKey());
    }

    @Test
    @DisplayName("verifyDirectSignature-支持客户端直签数据验签")
    void testVerifyDirectSignatureSuccess() {
        var sm2 = new com.oaiss.chain.security.crypto.Sm2SignatureStrategy();
        var kp = sm2.generateKeyPair();
        String data = "{\"scope1\":100}";
        String sig = sm2.sign(data, kp.getPrivate());
        String pub = sm2.encodePublicKey(kp.getPublic());

        boolean valid = digitalSignatureService.verifyDirectSignature(data, sig, pub, "SM2");
        assertTrue(valid, "客户端直签数据验签必须通过");
    }
}
