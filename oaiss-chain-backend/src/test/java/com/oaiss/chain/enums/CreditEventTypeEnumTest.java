package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreditEventTypeEnum unit tests")
class CreditEventTypeEnumTest {

    @Test
    @DisplayName("fromCode should resolve all known codes")
    void fromCode_shouldResolveKnownCodes() {
        assertEquals(CreditEventTypeEnum.DATA_FALSIFICATION, CreditEventTypeEnum.fromCode(1));
        assertEquals(CreditEventTypeEnum.LATE_SUBMISSION, CreditEventTypeEnum.fromCode(2));
        assertEquals(CreditEventTypeEnum.MINOR_VIOLATION, CreditEventTypeEnum.fromCode(3));
        assertEquals(CreditEventTypeEnum.MAJOR_VIOLATION, CreditEventTypeEnum.fromCode(4));
        assertEquals(CreditEventTypeEnum.BONUS_GOOD_BEHAVIOR, CreditEventTypeEnum.fromCode(5));
    }

    @Test
    @DisplayName("fromCode should return null for unknown code")
    void fromCode_shouldReturnNullForUnknownCode() {
        assertNull(CreditEventTypeEnum.fromCode(99));
    }

    @Test
    @DisplayName("fromCode should return null for null input")
    void fromCode_shouldReturnNullForNullInput() {
        assertNull(CreditEventTypeEnum.fromCode(null));
    }

    @Test
    @DisplayName("getCode should return correct integer codes")
    void getCode_shouldReturnCorrectCodes() {
        assertEquals(1, CreditEventTypeEnum.DATA_FALSIFICATION.getCode());
        assertEquals(2, CreditEventTypeEnum.LATE_SUBMISSION.getCode());
        assertEquals(3, CreditEventTypeEnum.MINOR_VIOLATION.getCode());
        assertEquals(4, CreditEventTypeEnum.MAJOR_VIOLATION.getCode());
        assertEquals(5, CreditEventTypeEnum.BONUS_GOOD_BEHAVIOR.getCode());
    }

    @Test
    @DisplayName("getDefaultPoints should return correct point values")
    void getDefaultPoints_shouldReturnCorrectValues() {
        assertEquals(-20, CreditEventTypeEnum.DATA_FALSIFICATION.getDefaultPoints());
        assertEquals(-5, CreditEventTypeEnum.LATE_SUBMISSION.getDefaultPoints());
        assertEquals(-10, CreditEventTypeEnum.MINOR_VIOLATION.getDefaultPoints());
        assertEquals(-30, CreditEventTypeEnum.MAJOR_VIOLATION.getDefaultPoints());
        assertEquals(5, CreditEventTypeEnum.BONUS_GOOD_BEHAVIOR.getDefaultPoints());
    }

    @Test
    @DisplayName("getDescription should return non-null descriptions")
    void getDescription_shouldReturnNonNullDescriptions() {
        for (CreditEventTypeEnum e : CreditEventTypeEnum.values()) {
            assertNotNull(e.getDescription());
            assertFalse(e.getDescription().isEmpty());
        }
    }
}
