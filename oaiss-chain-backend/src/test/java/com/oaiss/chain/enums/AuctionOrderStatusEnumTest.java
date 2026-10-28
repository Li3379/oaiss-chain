package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionOrderStatusEnum unit tests")
class AuctionOrderStatusEnumTest {

    @Test
    @DisplayName("fromCode should resolve all known codes")
    void fromCode_shouldResolveKnownCodes() {
        assertEquals(AuctionOrderStatusEnum.PENDING, AuctionOrderStatusEnum.fromCode(0));
        assertEquals(AuctionOrderStatusEnum.PARTIALLY_MATCHED, AuctionOrderStatusEnum.fromCode(1));
        assertEquals(AuctionOrderStatusEnum.FULLY_MATCHED, AuctionOrderStatusEnum.fromCode(2));
        assertEquals(AuctionOrderStatusEnum.CANCELLED, AuctionOrderStatusEnum.fromCode(3));
    }

    @Test
    @DisplayName("fromCode should return null for unknown code")
    void fromCode_shouldReturnNullForUnknownCode() {
        assertNull(AuctionOrderStatusEnum.fromCode(99));
    }

    @Test
    @DisplayName("fromCode should return null for null input")
    void fromCode_shouldReturnNullForNullInput() {
        assertNull(AuctionOrderStatusEnum.fromCode(null));
    }

    @Test
    @DisplayName("getCode should return correct integer codes")
    void getCode_shouldReturnCorrectCodes() {
        assertEquals(0, AuctionOrderStatusEnum.PENDING.getCode());
        assertEquals(1, AuctionOrderStatusEnum.PARTIALLY_MATCHED.getCode());
        assertEquals(2, AuctionOrderStatusEnum.FULLY_MATCHED.getCode());
        assertEquals(3, AuctionOrderStatusEnum.CANCELLED.getCode());
    }

    @Test
    @DisplayName("getDescription should return non-null descriptions")
    void getDescription_shouldReturnNonNullDescriptions() {
        for (AuctionOrderStatusEnum e : AuctionOrderStatusEnum.values()) {
            assertNotNull(e.getDescription());
            assertFalse(e.getDescription().isEmpty());
        }
    }

    @Test
    @DisplayName("isCancellable should be true for PENDING and PARTIALLY_MATCHED")
    void isCancellable_shouldBeTrueForPendingAndPartiallyMatched() {
        assertTrue(AuctionOrderStatusEnum.PENDING.isCancellable());
        assertTrue(AuctionOrderStatusEnum.PARTIALLY_MATCHED.isCancellable());
    }

    @Test
    @DisplayName("isCancellable should be false for FULLY_MATCHED and CANCELLED")
    void isCancellable_shouldBeFalseForFullyMatchedAndCancelled() {
        assertFalse(AuctionOrderStatusEnum.FULLY_MATCHED.isCancellable());
        assertFalse(AuctionOrderStatusEnum.CANCELLED.isCancellable());
    }

    @Test
    @DisplayName("valueOf should resolve all enum names")
    void valueOf_shouldResolveAllNames() {
        assertNotNull(AuctionOrderStatusEnum.valueOf("PENDING"));
        assertNotNull(AuctionOrderStatusEnum.valueOf("PARTIALLY_MATCHED"));
        assertNotNull(AuctionOrderStatusEnum.valueOf("FULLY_MATCHED"));
        assertNotNull(AuctionOrderStatusEnum.valueOf("CANCELLED"));
    }
}
