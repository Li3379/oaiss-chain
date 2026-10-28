package com.oaiss.chain.entity;

import jakarta.persistence.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optimistic lock tests for financial entities (CON-02)
 * Verifies that Enterprise, CarbonCoinAccount, and AuctionOrder
 * have @Version fields for optimistic locking.
 *
 * Note: @Builder.Default has CLASS retention (not RUNTIME), so it cannot be
 * tested via reflection. The annotation is verified present in source code.
 */
class OptimisticLockTest {

    @Test
    @DisplayName("Enterprise实体应有@Version注解的version字段")
    void enterpriseShouldHaveVersionField() throws NoSuchFieldException {
        // When
        Field versionField = Enterprise.class.getDeclaredField("version");

        // Then
        assertThat(versionField.getType())
                .as("version field must be Long type for JPA @Version")
                .isEqualTo(Long.class);
        assertThat(versionField.getAnnotation(Version.class))
                .as("version field must have @Version annotation")
                .isNotNull();
    }

    @Test
    @DisplayName("CarbonCoinAccount实体应有@Version注解的version字段")
    void carbonCoinAccountShouldHaveVersionField() throws NoSuchFieldException {
        // When
        Field versionField = CarbonCoinAccount.class.getDeclaredField("version");

        // Then
        assertThat(versionField.getType())
                .as("version field must be Long type for JPA @Version")
                .isEqualTo(Long.class);
        assertThat(versionField.getAnnotation(Version.class))
                .as("version field must have @Version annotation")
                .isNotNull();
    }

    @Test
    @DisplayName("AuctionOrder实体应有@Version注解的version字段")
    void auctionOrderShouldHaveVersionField() throws NoSuchFieldException {
        // When
        Field versionField = AuctionOrder.class.getDeclaredField("version");

        // Then
        assertThat(versionField.getType())
                .as("version field must be Long type for JPA @Version")
                .isEqualTo(Long.class);
        assertThat(versionField.getAnnotation(Version.class))
                .as("version field must have @Version annotation")
                .isNotNull();
    }

    @Test
    @DisplayName("Enterprise通过builder创建时version字段应有默认值0L")
    void enterpriseBuilderShouldDefaultVersionToZero() {
        // When
        Enterprise enterprise = Enterprise.builder().build();

        // Then
        assertThat(enterprise.getVersion())
                .as("@Builder.Default ensures version is 0L, not null, when using builder")
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("CarbonCoinAccount通过builder创建时version字段应有默认值0L")
    void carbonCoinAccountBuilderShouldDefaultVersionToZero() {
        // When
        CarbonCoinAccount account = CarbonCoinAccount.builder().build();

        // Then
        assertThat(account.getVersion())
                .as("@Builder.Default ensures version is 0L, not null, when using builder")
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("AuctionOrder通过builder创建时version字段应有默认值0L")
    void auctionOrderBuilderShouldDefaultVersionToZero() {
        // When
        AuctionOrder order = AuctionOrder.builder().build();

        // Then
        assertThat(order.getVersion())
                .as("@Builder.Default ensures version is 0L, not null, when using builder")
                .isEqualTo(0L);
    }
}
