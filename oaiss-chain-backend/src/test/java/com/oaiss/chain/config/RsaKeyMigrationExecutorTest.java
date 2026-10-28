package com.oaiss.chain.config;

import com.oaiss.chain.entity.RsaKeyPair;
import com.oaiss.chain.repository.RsaKeyPairRepository;
import com.oaiss.chain.util.AesGcmEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RsaKeyMigrationExecutor 单元测试。
 *
 * <p>重点回归「二次加密毁钥」：私钥一旦已被 KEK 加密，迁移只能补正
 * {@code encrypted} 标记，绝不能再次加密 —— 否则该用户此后所有签名
 * 都会因 {@code invalid key format} 静默失败。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RsaKeyMigrationExecutor unit tests")
class RsaKeyMigrationExecutorTest {

    @Mock
    private RsaKeyPairRepository rsaKeyPairRepository;

    @Mock
    private AesGcmEncryptor aesGcmEncryptor;

    @InjectMocks
    private RsaKeyMigrationExecutor executor;

    @Test
    @DisplayName("明文密钥：加密落库并标记 encrypted=true")
    void migrateSingleKey_shouldEncryptPlaintextKey() {
        RsaKeyPair keyPair = RsaKeyPair.builder().userId(1L).privateKey("plain-base64").encrypted(false).build();
        when(aesGcmEncryptor.decrypt("plain-base64")).thenThrow(new RuntimeException("not ciphertext"));
        when(aesGcmEncryptor.encrypt("plain-base64")).thenReturn("cipher");

        executor.migrateSingleKey(keyPair);

        assertEquals("cipher", keyPair.getPrivateKey());
        assertEquals(Boolean.TRUE, keyPair.getEncrypted());
        verify(rsaKeyPairRepository).save(keyPair);
    }

    @Test
    @DisplayName("已是密文：只补正标记，绝不二次加密（毁钥防护）")
    void migrateSingleKey_shouldNotReEncryptAlreadyEncryptedKey() {
        RsaKeyPair keyPair = RsaKeyPair.builder()
                .userId(2L).privateKey("ALREADY-CIPHERTEXT").encrypted(false).build();
        when(aesGcmEncryptor.decrypt("ALREADY-CIPHERTEXT")).thenReturn("plain-base64");

        executor.migrateSingleKey(keyPair);

        assertEquals("ALREADY-CIPHERTEXT", keyPair.getPrivateKey(), "密文不得被改写");
        assertEquals(Boolean.TRUE, keyPair.getEncrypted());
        verify(aesGcmEncryptor, never()).encrypt(anyString());
        verify(rsaKeyPairRepository).save(keyPair);
    }

    @Test
    @DisplayName("空私钥：跳过且不落库")
    void migrateSingleKey_shouldSkipBlankKey() {
        RsaKeyPair keyPair = RsaKeyPair.builder().userId(3L).privateKey("  ").encrypted(false).build();

        executor.migrateSingleKey(keyPair);

        verifyNoInteractions(aesGcmEncryptor);
        verify(rsaKeyPairRepository, never()).save(any());
    }

    @Test
    @DisplayName("null 私钥：跳过且不落库")
    void migrateSingleKey_shouldSkipNullKey() {
        RsaKeyPair keyPair = RsaKeyPair.builder().userId(4L).privateKey(null).encrypted(false).build();

        executor.migrateSingleKey(keyPair);

        verifyNoInteractions(aesGcmEncryptor);
        verify(rsaKeyPairRepository, never()).save(any());
    }
}
