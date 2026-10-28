package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("QualificationStatusEnum unit tests")
class QualificationStatusEnumTest {

    @Test
    @DisplayName("fromCode should resolve known values")
    void fromCode_shouldResolveKnownValues() {
        assertEquals(QualificationStatusEnum.ACTIVE, QualificationStatusEnum.fromCode(1));
        assertEquals(QualificationStatusEnum.REVOKED, QualificationStatusEnum.fromCode(2));
        assertEquals(1, QualificationStatusEnum.ACTIVE.getCode());
        assertEquals(2, QualificationStatusEnum.REVOKED.getCode());
    }

    @Test
    @DisplayName("fromCode should throw for unknown value")
    void fromCode_shouldThrowForUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> QualificationStatusEnum.fromCode(99));
    }
}
