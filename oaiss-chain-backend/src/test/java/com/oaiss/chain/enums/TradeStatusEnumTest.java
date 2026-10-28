package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradeStatusEnum unit tests")
class TradeStatusEnumTest {

    @Test
    @DisplayName("fromCode should resolve all known codes")
    void fromCode_shouldResolveKnownCodes() {
        assertEquals(TradeStatusEnum.PENDING, TradeStatusEnum.fromCode(0));
        assertEquals(TradeStatusEnum.PROCESSING, TradeStatusEnum.fromCode(1));
        assertEquals(TradeStatusEnum.COMPLETED, TradeStatusEnum.fromCode(2));
        assertEquals(TradeStatusEnum.CANCELLED, TradeStatusEnum.fromCode(3));
        assertEquals(TradeStatusEnum.FAILED, TradeStatusEnum.fromCode(4));
    }

    @Test
    @DisplayName("fromCode should throw for unknown code")
    void fromCode_shouldThrowForUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> TradeStatusEnum.fromCode(99));
    }

    @Test
    @DisplayName("getCode should return correct integer codes")
    void getCode_shouldReturnCorrectCodes() {
        assertEquals(0, TradeStatusEnum.PENDING.getCode());
        assertEquals(1, TradeStatusEnum.PROCESSING.getCode());
        assertEquals(2, TradeStatusEnum.COMPLETED.getCode());
        assertEquals(3, TradeStatusEnum.CANCELLED.getCode());
        assertEquals(4, TradeStatusEnum.FAILED.getCode());
    }

    @Test
    @DisplayName("isCancellable should be true for PENDING and PROCESSING")
    void isCancellable_shouldBeTrueForPendingAndProcessing() {
        assertTrue(TradeStatusEnum.PENDING.isCancellable());
        assertTrue(TradeStatusEnum.PROCESSING.isCancellable());
    }

    @Test
    @DisplayName("isCancellable should be false for finalized statuses")
    void isCancellable_shouldBeFalseForFinalizedStatuses() {
        assertFalse(TradeStatusEnum.COMPLETED.isCancellable());
        assertFalse(TradeStatusEnum.CANCELLED.isCancellable());
        assertFalse(TradeStatusEnum.FAILED.isCancellable());
    }

    @Test
    @DisplayName("isFinalized should be true for COMPLETED, CANCELLED, FAILED")
    void isFinalized_shouldBeTrueForFinalStatuses() {
        assertTrue(TradeStatusEnum.COMPLETED.isFinalized());
        assertTrue(TradeStatusEnum.CANCELLED.isFinalized());
        assertTrue(TradeStatusEnum.FAILED.isFinalized());
    }

    @Test
    @DisplayName("isFinalized should be false for PENDING and PROCESSING")
    void isFinalized_shouldBeFalseForActiveStatuses() {
        assertFalse(TradeStatusEnum.PENDING.isFinalized());
        assertFalse(TradeStatusEnum.PROCESSING.isFinalized());
    }

    @Test
    @DisplayName("getDescription should return non-null descriptions")
    void getDescription_shouldReturnNonNullDescriptions() {
        for (TradeStatusEnum e : TradeStatusEnum.values()) {
            assertNotNull(e.getDescription());
            assertFalse(e.getDescription().isEmpty());
        }
    }
}
