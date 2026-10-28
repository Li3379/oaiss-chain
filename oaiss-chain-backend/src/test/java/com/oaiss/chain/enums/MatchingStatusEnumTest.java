package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MatchingStatusEnum unit tests")
class MatchingStatusEnumTest {

    @Test
    @DisplayName("fromCode should resolve all known codes")
    void fromCode_shouldResolveKnownCodes() {
        assertEquals(MatchingStatusEnum.PENDING_SETTLEMENT, MatchingStatusEnum.fromCode(0));
        assertEquals(MatchingStatusEnum.SETTLED, MatchingStatusEnum.fromCode(1));
        assertEquals(MatchingStatusEnum.FAILED, MatchingStatusEnum.fromCode(2));
    }

    @Test
    @DisplayName("fromCode should return null for unknown code")
    void fromCode_shouldReturnNullForUnknownCode() {
        assertNull(MatchingStatusEnum.fromCode(99));
    }

    @Test
    @DisplayName("fromCode should return null for null input")
    void fromCode_shouldReturnNullForNullInput() {
        assertNull(MatchingStatusEnum.fromCode(null));
    }

    @Test
    @DisplayName("getCode should return correct integer codes")
    void getCode_shouldReturnCorrectCodes() {
        assertEquals(0, MatchingStatusEnum.PENDING_SETTLEMENT.getCode());
        assertEquals(1, MatchingStatusEnum.SETTLED.getCode());
        assertEquals(2, MatchingStatusEnum.FAILED.getCode());
    }

    @Test
    @DisplayName("getDescription should return non-null descriptions")
    void getDescription_shouldReturnNonNullDescriptions() {
        for (MatchingStatusEnum e : MatchingStatusEnum.values()) {
            assertNotNull(e.getDescription());
            assertFalse(e.getDescription().isEmpty());
        }
    }
}
