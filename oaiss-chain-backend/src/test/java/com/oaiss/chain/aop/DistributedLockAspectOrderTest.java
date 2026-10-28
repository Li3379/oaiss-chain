package com.oaiss.chain.aop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DistributedLockAspect execution order tests (CON-03)
 * Verifies that the distributed lock aspect runs BEFORE @Transactional,
 * ensuring the lock is acquired before the transaction starts.
 */
class DistributedLockAspectOrderTest {

    @Test
    @DisplayName("DistributedLockAspect应具有@Order注解且值为HIGHEST_PRECEDENCE")
    void aspectShouldHaveOrderAnnotationWithHighestPrecedence() {
        // When
        Order orderAnnotation = DistributedLockAspect.class.getAnnotation(Order.class);

        // Then
        assertThat(orderAnnotation)
                .as("DistributedLockAspect must have @Order annotation to ensure lock-before-transaction execution")
                .isNotNull();
        assertThat(orderAnnotation.value())
                .as("@Order value must be HIGHEST_PRECEDENCE so lock wraps the transaction proxy")
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("DistributedLockAspect的@Order值应小于@Transactional的默认顺序")
    void aspectOrderShouldBeLessThanTransactionalDefault() {
        // Given: @Transactional defaults to Ordered.LOWEST_PRECEDENCE when no @Order is specified
        int lockAspectOrder = DistributedLockAspect.class.getAnnotation(Order.class).value();
        int transactionalDefaultOrder = Ordered.LOWEST_PRECEDENCE;

        // Then: Lock aspect must execute first (lower value = higher priority)
        assertThat(lockAspectOrder)
                .as("Lock aspect order (%d) must be less than @Transactional default order (%d) " +
                        "so lock is acquired before transaction starts",
                        lockAspectOrder, transactionalDefaultOrder)
                .isLessThan(transactionalDefaultOrder);
    }
}
