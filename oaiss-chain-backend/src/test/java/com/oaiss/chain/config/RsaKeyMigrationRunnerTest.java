package com.oaiss.chain.config;

import com.oaiss.chain.entity.RsaKeyPair;
import com.oaiss.chain.repository.RsaKeyPairRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * RsaKeyMigrationRunner 单元测试。
 *
 * <p>Runner 只负责编排：查询待迁移密钥并逐条委托给独立 Bean
 * {@link RsaKeyMigrationExecutor}。加密与幂等逻辑由执行器测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RsaKeyMigrationRunner unit tests")
class RsaKeyMigrationRunnerTest {

    @Mock
    private RsaKeyPairRepository rsaKeyPairRepository;

    @Mock
    private RsaKeyMigrationExecutor rsaKeyMigrationExecutor;

    @InjectMocks
    private RsaKeyMigrationRunner runner;

    @Test
    @DisplayName("没有待迁移密钥时不触碰执行器")
    void migratePlaintextKeys_shouldSkipWhenNoKeys() {
        when(rsaKeyPairRepository.findByEncryptedAndDeletedFalse(false)).thenReturn(List.of());

        runner.migratePlaintextKeys();

        verifyNoInteractions(rsaKeyMigrationExecutor);
    }

    @Test
    @DisplayName("单条失败不影响后续密钥，且必须经执行器 Bean 调用")
    void migratePlaintextKeys_shouldContinueAfterFailureAndDelegate() {
        RsaKeyPair first = RsaKeyPair.builder().userId(1L).privateKey("plain-1").encrypted(false).build();
        first.setId(11L);
        RsaKeyPair second = RsaKeyPair.builder().userId(2L).privateKey("plain-2").encrypted(false).build();
        second.setId(22L);

        when(rsaKeyPairRepository.findByEncryptedAndDeletedFalse(false)).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("encrypt failed")).when(rsaKeyMigrationExecutor).migrateSingleKey(first);
        doNothing().when(rsaKeyMigrationExecutor).migrateSingleKey(second);

        runner.migratePlaintextKeys();

        verify(rsaKeyMigrationExecutor).migrateSingleKey(first);
        verify(rsaKeyMigrationExecutor).migrateSingleKey(second);
    }
}
