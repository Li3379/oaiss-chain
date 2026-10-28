package com.oaiss.chain.config;

import com.oaiss.chain.entity.RsaKeyPair;
import com.oaiss.chain.repository.RsaKeyPairRepository;
import com.oaiss.chain.util.AesGcmEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RSA 密钥迁移执行器（独立 bean，确保 @Transactional 代理生效）。
 *
 * <p>从 {@link RsaKeyMigrationRunner} 抽离的单密钥迁移逻辑。必须在独立 bean 中，
 * 否则切面内部 {@code this.} 调用会绕过 Spring 事务代理 ——
 * 曾导致加密成功但保存失败时部分行仍标记 encrypted=0，下次启动重复加密（毁钥）。</p>
 *
 * @author OAISS Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RsaKeyMigrationExecutor {

    private final RsaKeyPairRepository rsaKeyPairRepository;
    private final AesGcmEncryptor aesGcmEncryptor;

    /**
     * 单条密钥迁移：加密明文私钥并原子保存（加密+标记在同一事务内）。
     * 通过独立 bean 触发 @Transactional 代理，保证事务边界。
     *
     * <p>幂等保护：若当前值已能被 KEK 成功解开，说明它本身就是密文，
     * 此时只补正 {@code encrypted} 标记，绝不再次加密 —— 重复加密会永久毁钥，
     * 导致该用户此后所有签名静默失败。</p>
     */
    @Transactional
    public void migrateSingleKey(RsaKeyPair keyPair) {
        String storedValue = keyPair.getPrivateKey();
        if (storedValue == null || storedValue.isBlank()) {
            log.warn("Skipping RSA key pair id={} with null/blank private key", keyPair.getId());
            return;
        }

        if (isAlreadyEncrypted(storedValue)) {
            keyPair.setEncrypted(true);
            rsaKeyPairRepository.save(keyPair);
            log.info("RSA key id={} is already encrypted -- corrected flag only, no re-encryption",
                    keyPair.getId());
            return;
        }

        keyPair.setPrivateKey(aesGcmEncryptor.encrypt(storedValue));
        keyPair.setEncrypted(true);
        rsaKeyPairRepository.save(keyPair);
    }

    /**
     * 判定当前存储值是否已是 KEK 密文。
     * AES-GCM 带认证标签，明文被误判为密文的概率可忽略。
     */
    private boolean isAlreadyEncrypted(String storedValue) {
        try {
            aesGcmEncryptor.decrypt(storedValue);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
