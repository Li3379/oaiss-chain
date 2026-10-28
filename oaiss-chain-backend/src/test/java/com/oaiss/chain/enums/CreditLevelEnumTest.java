package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreditLevelEnum unit tests")
class CreditLevelEnumTest {

    @Test
    @DisplayName("fromScore should return EXCELLENT for scores >= 80")
    void fromScore_shouldReturnExcellent() {
        assertEquals(CreditLevelEnum.EXCELLENT, CreditLevelEnum.fromScore(80));
        assertEquals(CreditLevelEnum.EXCELLENT, CreditLevelEnum.fromScore(90));
        assertEquals(CreditLevelEnum.EXCELLENT, CreditLevelEnum.fromScore(100));
    }

    @Test
    @DisplayName("fromScore should return GOOD for scores 60-79")
    void fromScore_shouldReturnGood() {
        assertEquals(CreditLevelEnum.GOOD, CreditLevelEnum.fromScore(60));
        assertEquals(CreditLevelEnum.GOOD, CreditLevelEnum.fromScore(70));
        assertEquals(CreditLevelEnum.GOOD, CreditLevelEnum.fromScore(79));
    }

    @Test
    @DisplayName("fromScore should return WARNING for scores 40-59")
    void fromScore_shouldReturnWarning() {
        assertEquals(CreditLevelEnum.WARNING, CreditLevelEnum.fromScore(40));
        assertEquals(CreditLevelEnum.WARNING, CreditLevelEnum.fromScore(50));
        assertEquals(CreditLevelEnum.WARNING, CreditLevelEnum.fromScore(59));
    }

    @Test
    @DisplayName("fromScore should return DANGER for scores 20-39")
    void fromScore_shouldReturnDanger() {
        assertEquals(CreditLevelEnum.DANGER, CreditLevelEnum.fromScore(20));
        assertEquals(CreditLevelEnum.DANGER, CreditLevelEnum.fromScore(30));
        assertEquals(CreditLevelEnum.DANGER, CreditLevelEnum.fromScore(39));
    }

    @Test
    @DisplayName("fromScore should return FROZEN for scores 0-19")
    void fromScore_shouldReturnFrozen() {
        assertEquals(CreditLevelEnum.FROZEN, CreditLevelEnum.fromScore(0));
        assertEquals(CreditLevelEnum.FROZEN, CreditLevelEnum.fromScore(10));
        assertEquals(CreditLevelEnum.FROZEN, CreditLevelEnum.fromScore(19));
    }

    @Test
    @DisplayName("fromScore should return FROZEN for null input")
    void fromScore_shouldReturnFrozenForNull() {
        assertEquals(CreditLevelEnum.FROZEN, CreditLevelEnum.fromScore(null));
    }

    @Test
    @DisplayName("getCode should return correct string codes")
    void getCode_shouldReturnCorrectCodes() {
        assertEquals("EXCELLENT", CreditLevelEnum.EXCELLENT.getCode());
        assertEquals("GOOD", CreditLevelEnum.GOOD.getCode());
        assertEquals("WARNING", CreditLevelEnum.WARNING.getCode());
        assertEquals("DANGER", CreditLevelEnum.DANGER.getCode());
        assertEquals("FROZEN", CreditLevelEnum.FROZEN.getCode());
    }

    @Test
    @DisplayName("getMinScore and getMaxScore should define correct ranges")
    void getScoreRanges_shouldBeCorrect() {
        assertEquals(80, CreditLevelEnum.EXCELLENT.getMinScore());
        assertEquals(100, CreditLevelEnum.EXCELLENT.getMaxScore());
        assertEquals(60, CreditLevelEnum.GOOD.getMinScore());
        assertEquals(79, CreditLevelEnum.GOOD.getMaxScore());
        assertEquals(40, CreditLevelEnum.WARNING.getMinScore());
        assertEquals(59, CreditLevelEnum.WARNING.getMaxScore());
        assertEquals(20, CreditLevelEnum.DANGER.getMinScore());
        assertEquals(39, CreditLevelEnum.DANGER.getMaxScore());
        assertEquals(0, CreditLevelEnum.FROZEN.getMinScore());
        assertEquals(19, CreditLevelEnum.FROZEN.getMaxScore());
    }

    @Test
    @DisplayName("getDescription should return non-null descriptions")
    void getDescription_shouldReturnNonNullDescriptions() {
        for (CreditLevelEnum e : CreditLevelEnum.values()) {
            assertNotNull(e.getDescription());
            assertFalse(e.getDescription().isEmpty());
        }
    }
}
