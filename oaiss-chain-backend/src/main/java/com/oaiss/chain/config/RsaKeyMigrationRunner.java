package com.oaiss.chain.config;

import com.oaiss.chain.entity.RsaKeyPair;
import com.oaiss.chain.repository.RsaKeyPairRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migrates existing plaintext RSA private keys to AES-256-GCM encrypted storage.
 *
 * <p>Runs once on application startup. Reads all {@code rsa_key_pair} rows where
 * {@code encrypted = 0}, encrypts each private key with {@link AesGcmEncryptor},
 * and updates the row to {@code encrypted = 1}.</p>
 *
 * @author OAISS Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RsaKeyMigrationRunner {

    private final RsaKeyPairRepository rsaKeyPairRepository;
    private final RsaKeyMigrationExecutor rsaKeyMigrationExecutor;

    @EventListener(ApplicationReadyEvent.class)
    public void migratePlaintextKeys() {
        List<RsaKeyPair> unencryptedKeys = rsaKeyPairRepository.findByEncryptedAndDeletedFalse(false);

        if (unencryptedKeys.isEmpty()) {
            log.info("No unencrypted RSA private keys found -- migration skipped");
            return;
        }

        log.info("Found {} unencrypted RSA private key(s) -- starting migration", unencryptedKeys.size());

        int success = 0;
        for (RsaKeyPair keyPair : unencryptedKeys) {
            try {
                // 必须经独立 Bean 调用，否则 this. 自调用会绕过 @Transactional 代理
                rsaKeyMigrationExecutor.migrateSingleKey(keyPair);
                success++;
            } catch (Exception e) {
                log.error("Failed to migrate key id={}, userId={}: {}",
                        keyPair.getId(), keyPair.getUserId(), e.getMessage());
                // Continue with next key instead of aborting entire batch
            }
        }

        log.info("RSA private key migration complete -- {}/{} key(s) encrypted", success, unencryptedKeys.size());
    }
}
