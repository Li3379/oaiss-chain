package com.oaiss.chain.repository;

import com.oaiss.chain.entity.RsaKeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RsaKeyPairRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RsaKeyPairRepository repository;

    private RsaKeyPair keyPair1;
    private RsaKeyPair keyPair2;
    private RsaKeyPair deletedKeyPair;

    @BeforeEach
    void setUp() {
        keyPair1 = createKeyPair(1L, "pub1", "priv1", 1, 1, false);
        keyPair2 = createKeyPair(3L, "pub2", "priv2", 1, 2, true);
        deletedKeyPair = createKeyPair(2L, "pub3", "priv3", 0, 1, false);
        deletedKeyPair.setDeleted(true);

        entityManager.persist(keyPair1);
        entityManager.persist(keyPair2);
        entityManager.persist(deletedKeyPair);
        entityManager.flush();
    }

    private RsaKeyPair createKeyPair(Long userId, String publicKey, String privateKey,
                                      Integer keyStatus, Integer keyVersion, Boolean encrypted) {
        RsaKeyPair kp = RsaKeyPair.builder()
                .userId(userId)
                .publicKey(publicKey)
                .privateKey(privateKey)
                .keyStatus(keyStatus)
                .keyVersion(keyVersion)
                .keyUsage(3)
                .encrypted(encrypted)
                .expiresAt(LocalDateTime.now().plusYears(1))
                .build();
        kp.setCreatedAt(LocalDateTime.now());
        kp.setUpdatedAt(LocalDateTime.now());
        return kp;
    }

    @Test
    @DisplayName("findByUserIdAndDeletedFalse should return key pair for user")
    void findByUserId_shouldReturnKeyPair() {
        Optional<RsaKeyPair> result = repository.findByUserIdAndDeletedFalse(1L);
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findByUserIdAndDeletedFalse should return empty for deleted")
    void findByUserId_shouldReturnEmptyForDeleted() {
        Optional<RsaKeyPair> result = repository.findByUserIdAndDeletedFalse(2L);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByUserIdAndKeyStatusAndDeletedFalse should filter by status")
    void findByUserIdAndKeyStatus_shouldFilterByStatus() {
        Optional<RsaKeyPair> result = repository.findByUserIdAndKeyStatusAndDeletedFalse(1L, 1);
        assertThat(result).isPresent();
        assertThat(result.get().getKeyStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("findLatestByUserId should return highest version key")
    void findLatestByUserId_shouldReturnHighestVersion() {
        Optional<RsaKeyPair> result = repository.findLatestByUserId(3L);
        assertThat(result).isPresent();
        assertThat(result.get().getKeyVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("existsByUserIdAndDeletedFalse should return true for existing")
    void existsByUserId_shouldReturnTrueForExisting() {
        assertThat(repository.existsByUserIdAndDeletedFalse(1L)).isTrue();
    }

    @Test
    @DisplayName("existsByUserIdAndDeletedFalse should return false for deleted")
    void existsByUserId_shouldReturnFalseForDeleted() {
        assertThat(repository.existsByUserIdAndDeletedFalse(2L)).isFalse();
    }

    @Test
    @DisplayName("findByUserIdAndKeyVersionAndDeletedFalse should return specific version")
    void findByUserIdAndKeyVersion_shouldReturnSpecificVersion() {
        Optional<RsaKeyPair> result = repository.findByUserIdAndKeyVersionAndDeletedFalse(1L, 1);
        assertThat(result).isPresent();
        assertThat(result.get().getKeyVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByEncryptedAndDeletedFalse should return unencrypted keys")
    void findByEncrypted_shouldReturnUnencryptedKeys() {
        List<RsaKeyPair> result = repository.findByEncryptedAndDeletedFalse(false);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEncrypted()).isFalse();
    }

    @Test
    @DisplayName("findByEncryptedAndDeletedFalse should return encrypted keys")
    void findByEncrypted_shouldReturnEncryptedKeys() {
        List<RsaKeyPair> result = repository.findByEncryptedAndDeletedFalse(true);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEncrypted()).isTrue();
    }
}
