package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradeTypeEnum unit tests")
class TradeTypeEnumTest {

    @Test
    @DisplayName("fromCode should resolve all known codes")
    void fromCode_shouldResolveKnownCodes() {
        assertEquals(TradeTypeEnum.AUCTION, TradeTypeEnum.fromCode(1));
        assertEquals(TradeTypeEnum.P2P, TradeTypeEnum.fromCode(2));
        assertEquals(TradeTypeEnum.ALLOCATION, TradeTypeEnum.fromCode(3));
    }

    @Test
    @DisplayName("fromCode should throw for unknown code")
    void fromCode_shouldThrowForUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> TradeTypeEnum.fromCode(99));
    }

    @Test
    @DisplayName("getCode should return correct integer codes")
    void getCode_shouldReturnCorrectCodes() {
        assertEquals(1, TradeTypeEnum.AUCTION.getCode());
        assertEquals(2, TradeTypeEnum.P2P.getCode());
        assertEquals(3, TradeTypeEnum.ALLOCATION.getCode());
    }

    @Test
    @DisplayName("getDescription should return non-null descriptions")
    void getDescription_shouldReturnNonNullDescriptions() {
        for (TradeTypeEnum e : TradeTypeEnum.values()) {
            assertNotNull(e.getDescription());
            assertFalse(e.getDescription().isEmpty());
        }
    }
}
