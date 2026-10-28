package com.oaiss.chain.service;

import com.oaiss.chain.dto.RsaKeyPairResponse;
import com.oaiss.chain.dto.SignatureResult;
import com.oaiss.chain.entity.RsaKeyPair;
import com.oaiss.chain.exception.BlockchainException;
import com.oaiss.chain.repository.RsaKeyPairRepository;
import com.oaiss.chain.security.crypto.SignatureStrategy;
import com.oaiss.chain.security.crypto.SignatureStrategyFactory;
import com.oaiss.chain.util.AesGcmEncryptor;
import com.oaiss.chain.util.RsaKeyUtil;
import com.oaiss.chain.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;

/**
 * 数字签名服务
 * Digital Signature Service
 * 
 * 提供国密 SM2/SM3 与国际 RSA/AES 双轨自适应密钥对管理、碳报告签名、验签、加密解密功能
 * 用于碳核算审核流程中的数据安全保障与司法不可抵赖性
 * 
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalSignatureService {

    private final RsaKeyPairRepository rsaKeyPairRepository;
    private final AesGcmEncryptor aesGcmEncryptor;
    private final SignatureStrategyFactory signatureStrategyFactory;

    /**
     * 密钥有效期（年）
     */
    private static final int KEY_EXPIRY_YEARS = 2;

    /**
     * 密钥状态：有效
     */
    private static final int KEY_STATUS_ACTIVE = 1;

    /**
     * 密钥状态：已失效
     */
    private static final int KEY_STATUS_REVOKED = 0;

    /**
     * 密钥状态：已过期
     */
    private static final int KEY_STATUS_EXPIRED = 2;

    // ==================== 密钥管理 ====================

    /**
     * 为用户生成默认算法的密钥对
     */
    @DistributedLock(key = "'keypair:generate:' + #userId", expireTime = 10)
    @Transactional
    public RsaKeyPairResponse generateKeyPair(Long userId) {
        return generateKeyPair(userId, null);
    }

    /**
     * 为用户生成指定算法的密钥对 (RSA / SM2)
     */
    @DistributedLock(key = "'keypair:generate:' + #userId", expireTime = 10)
    @Transactional
    public RsaKeyPairResponse generateKeyPair(Long userId, String algorithmType) {
        log.info("Generating key pair for user: {}, algorithm: {}", userId, algorithmType);

        if (rsaKeyPairRepository.existsByUserIdAndDeletedFalse(userId)) {
            log.warn("User already has an active key pair: {}", userId);
            // 将旧密钥标记为失效
            revokeKeyPair(userId);
        }

        try {
            SignatureStrategy strategy = signatureStrategyFactory.getStrategy(algorithmType);
            KeyPair keyPair = strategy.generateKeyPair();
            String publicKeyBase64 = strategy.encodePublicKey(keyPair.getPublic());
            String privateKeyBase64 = strategy.encodePrivateKey(keyPair.getPrivate());

            // 计算密钥版本（获取最新版本号）
            Integer latestVersion = rsaKeyPairRepository.findLatestByUserId(userId)
                    .map(RsaKeyPair::getKeyVersion)
                    .orElse(0);
            Integer newVersion = latestVersion + 1;

            // 创建密钥对实体（私钥加密存储）
            RsaKeyPair rsaKeyPairEntity = RsaKeyPair.builder()
                    .userId(userId)
                    .publicKey(publicKeyBase64)
                    .privateKey(aesGcmEncryptor.encrypt(privateKeyBase64))
                    .encrypted(true)
                    .keyStatus(KEY_STATUS_ACTIVE)
                    .expiresAt(LocalDateTime.now().plusYears(KEY_EXPIRY_YEARS))
                    .keyVersion(newVersion)
                    .keyUsage(3) // 通用用途
                    .build();

            rsaKeyPairEntity = rsaKeyPairRepository.save(rsaKeyPairEntity);
            log.info("Key pair generated successfully for user: {}, version: {}, algo: {}",
                    userId, newVersion, strategy.getAlgorithmType());

            return toResponse(rsaKeyPairEntity);
        } catch (Exception e) {
            log.error("Key pair generation failed for user {}: {}", userId, e.getMessage());
            throw BlockchainException.rsaKeyGenerationFailed(e.getMessage());
        }
    }

    /**
     * 撤销用户密钥对
     * Revoke user's key pair
     * 
     * @param userId 用户ID
     */
    @Transactional
    public void revokeKeyPair(Long userId) {
        log.info("Revoking RSA key pair for user: {}", userId);
        
        rsaKeyPairRepository.findLatestByUserId(userId)
                .ifPresent(keyPair -> {
                    keyPair.setKeyStatus(KEY_STATUS_REVOKED);
                    rsaKeyPairRepository.save(keyPair);
                    log.info("RSA key pair revoked for user: {}", userId);
                });
    }

    /**
     * 获取用户密钥对信息
     * Get user's key pair information
     * 
     * @param userId 用户ID
     * @return 密钥对响应DTO
     * @throws BlockchainException 如果密钥对不存在
     */
    public RsaKeyPairResponse getKeyPair(Long userId) {
        RsaKeyPair keyPair = rsaKeyPairRepository.findLatestByUserId(userId)
                .orElse(null);

        if (keyPair == null) {
            return null;
        }

        // 先标记过期密钥（事务写入），再校验状态
        markExpiredKeys(userId);

        // 重新获取更新后的密钥对
        keyPair = rsaKeyPairRepository.findLatestByUserId(userId)
                .orElse(null);

        if (keyPair == null) {
            return null;
        }

        return toResponse(keyPair);
    }

    // ==================== 签名与验签 ====================

    /**
     * 对碳报告数据进行签名
     * Sign carbon report data
     * 
     * @param userId 用户ID（企业用户）
     * @param reportData 报告数据（JSON字符串）
     * @return 签名结果DTO
     * @throws BlockchainException 如果签名失败
     */
    public SignatureResult signReport(Long userId, String reportData) {
        log.info("Signing report data for user: {}", userId);

        RsaKeyPair keyPair = rsaKeyPairRepository.findLatestByUserId(userId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(userId));

        // 先标记过期密钥（事务写入），再校验状态
        markExpiredKeys(userId);
        keyPair = rsaKeyPairRepository.findLatestByUserId(userId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(userId));

        validateKeyStatus(keyPair);

        try {
            // 解密并解码私钥（策略自适应）
            SignatureStrategy strategy = resolveStrategy(keyPair.getPublicKey());
            PrivateKey privateKey = strategy.decodePrivateKey(aesGcmEncryptor.decrypt(keyPair.getPrivateKey()));

            String signature = strategy.sign(reportData, privateKey);

            log.info("Report data signed successfully for user: {}, algo: {}", userId, strategy.getSignatureAlgorithm());

            return SignatureResult.builder()
                    .signature(signature)
                    .algorithm(strategy.getSignatureAlgorithm())
                    .timestamp(LocalDateTime.now())
                    .signerId(userId)
                    .build();
        } catch (Exception e) {
            log.error("Report signing failed for user {}: {}", userId, e.getMessage());
            throw BlockchainException.rsaSignFailed(e.getMessage());
        }
    }

    /**
     * 验证碳报告签名
     * Verify carbon report signature
     * 
     * @param userId 用户ID（企业用户）
     * @param reportData 报告数据（JSON字符串）
     * @param signature 签名数据（Base64编码）
     * @return 验签是否成功
     * @throws BlockchainException 如果验签过程出错
     *
     * 纯只读方法，刻意不开启事务：若开启且验签抛 RuntimeException（篡改数据
     * 解码异常等），异常穿过事务拦截器时会把外层共享事务标记 rollback-only，
     * 即使调用方（审核/待审列表）catch 降级，提交阶段仍报
     * UnexpectedRollbackException → 500。无事务后异常按普通语义上抛，
     * 由调用方决定降级策略。
     */
    public boolean verifySignature(Long userId, String reportData, String signature) {
        log.info("Verifying signature for user: {}", userId);

        RsaKeyPair keyPair = rsaKeyPairRepository.findLatestByUserId(userId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(userId));

        // 验证密钥状态（验签时允许使用已过期密钥，但记录警告）
        if (keyPair.getKeyStatus() == KEY_STATUS_REVOKED) {
            throw BlockchainException.rsaKeyRevoked(userId);
        }
        if (keyPair.getKeyStatus() == KEY_STATUS_EXPIRED || 
            (keyPair.getExpiresAt() != null && keyPair.getExpiresAt().isBefore(LocalDateTime.now()))) {
            log.warn("Using expired key for verification: userId={}", userId);
        }

        try {
            // 解码公钥并按策略验签
            SignatureStrategy strategy = resolveStrategy(keyPair.getPublicKey());
            PublicKey publicKey = strategy.decodePublicKey(keyPair.getPublicKey());

            boolean isValid = strategy.verify(reportData, signature, publicKey);

            log.info("Signature verification result for user {}: {} (algo: {})", userId, isValid, strategy.getAlgorithmType());

            return isValid;
        } catch (Exception e) {
            log.error("Signature verification failed for user {}: {}", userId, e.getMessage());
            throw BlockchainException.rsaVerifyFailed(e.getMessage());
        }
    }

    /**
     * 直接使用公钥字符串与算法类型进行验签（客户端就地签名模式，私钥不经过服务端）
     */
    public boolean verifyDirectSignature(String reportData, String signature, String publicKeyBase64, String algorithmType) {
        log.info("Verifying direct client signature: algo={}", algorithmType);
        try {
            SignatureStrategy strategy = signatureStrategyFactory.getStrategy(algorithmType);
            PublicKey publicKey = strategy.decodePublicKey(publicKeyBase64);
            return strategy.verify(reportData, signature, publicKey);
        } catch (Exception e) {
            log.error("Direct signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 加密与解密 ====================

    /**
     * 为审核员加密数据
     * Encrypt data for reviewer
     * 
     * 使用审核员的公钥加密数据，确保数据传输安全
     * 
     * @param data 待加密的数据
     * @param reviewerId 审核员用户ID
     * @return 加密后的数据（Base64编码）
     * @throws BlockchainException 如果加密失败
     */
    public String encryptForReviewer(String data, Long reviewerId) {
        log.info("Encrypting data for reviewer: {}", reviewerId);

        RsaKeyPair keyPair = rsaKeyPairRepository.findLatestByUserId(reviewerId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(reviewerId));

        // 先标记过期密钥（事务写入），再校验状态
        markExpiredKeys(reviewerId);
        keyPair = rsaKeyPairRepository.findLatestByUserId(reviewerId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(reviewerId));

        validateKeyStatus(keyPair);

        try {
            // 解码公钥
            PublicKey publicKey = RsaKeyUtil.decodePublicKey(keyPair.getPublicKey());

            // 加密数据
            String encryptedData = RsaKeyUtil.encrypt(data, publicKey);

            log.info("Data encrypted successfully for reviewer: {}", reviewerId);

            return encryptedData;
        } catch (Exception e) {
            log.error("Data encryption failed for reviewer {}: {}", reviewerId, e.getMessage());
            throw BlockchainException.rsaEncryptFailed(e.getMessage());
        }
    }

    /**
     * 审核员解密来自企业的数据
     * Decrypt data from enterprise for reviewer
     * 
     * 使用审核员的私钥解密数据
     * 
     * @param encryptedData 加密的数据（Base64编码）
     * @param reviewerId 审核员用户ID
     * @return 解密后的原始数据
     * @throws BlockchainException 如果解密失败
     */
    public String decryptForReviewer(String encryptedData, Long reviewerId) {
        log.info("Decrypting data for reviewer: {}", reviewerId);

        RsaKeyPair keyPair = rsaKeyPairRepository.findLatestByUserId(reviewerId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(reviewerId));

        // 先标记过期密钥（事务写入），再校验状态
        markExpiredKeys(reviewerId);
        keyPair = rsaKeyPairRepository.findLatestByUserId(reviewerId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(reviewerId));

        validateKeyStatus(keyPair);

        try {
            // 解密并解码私钥
            PrivateKey privateKey = RsaKeyUtil.decodePrivateKey(aesGcmEncryptor.decrypt(keyPair.getPrivateKey()));

            // 解密数据
            String decryptedData = RsaKeyUtil.decrypt(encryptedData, privateKey);

            log.info("Data decrypted successfully for reviewer: {}", reviewerId);

            return decryptedData;
        } catch (Exception e) {
            log.error("Data decryption failed for reviewer {}: {}", reviewerId, e.getMessage());
            throw BlockchainException.rsaDecryptFailed(e.getMessage());
        }
    }

    /**
     * 企业解密来自审核员的数据
     * Decrypt data from reviewer for enterprise
     * 
     * 使用企业的私钥解密数据
     * 
     * @param encryptedData 加密的数据（Base64编码）
     * @param enterpriseUserId 企业用户ID
     * @return 解密后的原始数据
     * @throws BlockchainException 如果解密失败
     */
    public String decryptForEnterprise(String encryptedData, Long enterpriseUserId) {
        log.info("Decrypting data for enterprise user: {}", enterpriseUserId);

        RsaKeyPair keyPair = rsaKeyPairRepository.findLatestByUserId(enterpriseUserId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(enterpriseUserId));

        // 先标记过期密钥（事务写入），再校验状态
        markExpiredKeys(enterpriseUserId);
        keyPair = rsaKeyPairRepository.findLatestByUserId(enterpriseUserId)
                .orElseThrow(() -> BlockchainException.rsaKeyPairNotFound(enterpriseUserId));

        validateKeyStatus(keyPair);

        try {
            // 解密并解码私钥
            PrivateKey privateKey = RsaKeyUtil.decodePrivateKey(aesGcmEncryptor.decrypt(keyPair.getPrivateKey()));

            // 解密数据
            String decryptedData = RsaKeyUtil.decrypt(encryptedData, privateKey);

            log.info("Data decrypted successfully for enterprise user: {}", enterpriseUserId);

            return decryptedData;
        } catch (Exception e) {
            log.error("Data decryption failed for enterprise user {}: {}", enterpriseUserId, e.getMessage());
            throw BlockchainException.rsaDecryptFailed(e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 标记过期密钥（写入数据库，需要事务）
     * Mark expired keys for a user (DB write, requires transaction)
     *
     * @param userId 用户ID
     */
    @Transactional
    public void markExpiredKeys(Long userId) {
        rsaKeyPairRepository.findLatestByUserId(userId).ifPresent(kp -> {
            if (kp.getExpiresAt() != null && kp.getExpiresAt().isBefore(LocalDateTime.now())
                    && kp.getKeyStatus() != KEY_STATUS_EXPIRED) {
                kp.setKeyStatus(KEY_STATUS_EXPIRED);
                rsaKeyPairRepository.save(kp);
            }
        });
    }

    /**
     * 验证密钥状态（纯检查，不写入数据库）
     * Validate key status (read-only check)
     *
     * @param keyPair 密钥对
     * @throws BlockchainException 如果密钥无效
     */
    private void validateKeyStatus(RsaKeyPair keyPair) {
        // 检查密钥是否已失效
        if (keyPair.getKeyStatus() == KEY_STATUS_REVOKED) {
            throw BlockchainException.rsaKeyRevoked(keyPair.getUserId());
        }

        // 检查密钥是否已过期
        if (keyPair.getExpiresAt() != null && keyPair.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BlockchainException.rsaKeyExpired(keyPair.getUserId());
        }

        // 检查状态是否为已过期标记
        if (keyPair.getKeyStatus() == KEY_STATUS_EXPIRED) {
            throw BlockchainException.rsaKeyExpired(keyPair.getUserId());
        }
    }

    /**
     * 根据公钥特征解析适用的签名策略 (RSA 或 SM2)
     */
    private SignatureStrategy resolveStrategy(String publicKeyBase64) {
        if (publicKeyBase64 != null && publicKeyBase64.length() < 250) {
            return signatureStrategyFactory.getStrategy("SM2");
        }
        return signatureStrategyFactory.getStrategy("RSA");
    }

    /**
     * Entity转Response
     * Convert entity to response DTO
     * 
     * 注意：不包含私钥信息
     */
    private RsaKeyPairResponse toResponse(RsaKeyPair keyPair) {
        String statusText;
        switch (keyPair.getKeyStatus()) {
            case KEY_STATUS_ACTIVE:
                statusText = "有效";
                break;
            case KEY_STATUS_REVOKED:
                statusText = "已失效";
                break;
            case KEY_STATUS_EXPIRED:
                statusText = "已过期";
                break;
            default:
                statusText = "未知";
        }

        SignatureStrategy strategy = resolveStrategy(keyPair.getPublicKey());

        return RsaKeyPairResponse.builder()
                .id(keyPair.getId())
                .userId(keyPair.getUserId())
                .publicKey(keyPair.getPublicKey())
                .createdAt(keyPair.getCreatedAt())
                .expiresAt(keyPair.getExpiresAt())
                .keyStatus(keyPair.getKeyStatus())
                .keyStatusText(statusText)
                .keyVersion(keyPair.getKeyVersion())
                .keyUsage(keyPair.getKeyUsage())
                .algorithm(strategy != null ? strategy.getAlgorithmType() : "RSA")
                .build();
    }
}
