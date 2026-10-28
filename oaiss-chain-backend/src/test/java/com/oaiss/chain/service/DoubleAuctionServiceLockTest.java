package com.oaiss.chain.service;

import com.oaiss.chain.annotation.DistributedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * DoubleAuctionService distributed lock tests (CON-01)
 * Verifies that executeMatching uses @DistributedLock instead of synchronized.
 */
class DoubleAuctionServiceLockTest {

    @Test
    @DisplayName("executeMatching方法应有@DistributedLock注解")
    void executeMatchingShouldHaveDistributedLockAnnotation() throws NoSuchMethodException {
        // When
        Method method = DoubleAuctionService.class.getMethod("executeMatching");
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);

        // Then
        assertThat(annotation)
                .as("executeMatching() must have @DistributedLock annotation for distributed concurrency control")
                .isNotNull();
    }

    @Test
    @DisplayName("@DistributedLock注解的key应为auction:matching")
    void distributedLockKeyShouldBeAuctionMatching() throws NoSuchMethodException {
        // When
        Method method = DoubleAuctionService.class.getMethod("executeMatching");
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);

        // Then
        assertThat(annotation).isNotNull();
        assertThat(annotation.key())
                .as("@DistributedLock key must be 'auction:matching' for auction-specific locking")
                .isEqualTo("'auction:matching'");
    }

    @Test
    @DisplayName("@DistributedLock注解的expireTime应为30秒")
    void distributedLockExpireTimeShouldBe30() throws NoSuchMethodException {
        // When
        Method method = DoubleAuctionService.class.getMethod("executeMatching");
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);

        // Then
        assertThat(annotation).isNotNull();
        assertThat(annotation.expireTime())
                .as("@DistributedLock expireTime must be 30 seconds")
                .isEqualTo(30);
    }

    @Test
    @DisplayName("@DistributedLock注解的waitTime应为0（非阻塞）")
    void distributedLockWaitTimeShouldBe0() throws NoSuchMethodException {
        // When
        Method method = DoubleAuctionService.class.getMethod("executeMatching");
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);

        // Then
        assertThat(annotation).isNotNull();
        assertThat(annotation.waitTime())
                .as("@DistributedLock waitTime must be 0 (non-blocking, fail-fast)")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("executeMatching方法不应有synchronized修饰符")
    void executeMatchingShouldNotBeSynchronized() throws NoSuchMethodException {
        // When
        Method method = DoubleAuctionService.class.getMethod("executeMatching");
        int modifiers = method.getModifiers();

        // Then
        assertFalse(java.lang.reflect.Modifier.isSynchronized(modifiers),
                "executeMatching() must NOT be synchronized — use @DistributedLock instead for multi-instance support");
    }
}
