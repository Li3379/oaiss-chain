package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserTypeEnum unit tests")
class UserTypeEnumTest {

    @Test
    @DisplayName("fromCode should resolve all known codes")
    void fromCode_shouldResolveKnownCodes() {
        assertEquals(UserTypeEnum.ENTERPRISE, UserTypeEnum.fromCode(1));
        assertEquals(UserTypeEnum.REVIEWER, UserTypeEnum.fromCode(2));
        assertEquals(UserTypeEnum.THIRD_PARTY, UserTypeEnum.fromCode(3));
        assertEquals(UserTypeEnum.ADMIN, UserTypeEnum.fromCode(4));
    }

    @Test
    @DisplayName("fromCode should throw for unknown code")
    void fromCode_shouldThrowForUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> UserTypeEnum.fromCode(99));
    }

    @Test
    @DisplayName("getCode should return correct integer codes")
    void getCode_shouldReturnCorrectCodes() {
        assertEquals(1, UserTypeEnum.ENTERPRISE.getCode());
        assertEquals(2, UserTypeEnum.REVIEWER.getCode());
        assertEquals(3, UserTypeEnum.THIRD_PARTY.getCode());
        assertEquals(4, UserTypeEnum.ADMIN.getCode());
    }

    @Test
    @DisplayName("getDescription should return non-null descriptions")
    void getDescription_shouldReturnNonNullDescriptions() {
        for (UserTypeEnum e : UserTypeEnum.values()) {
            assertNotNull(e.getDescription());
            assertFalse(e.getDescription().isEmpty());
        }
    }
}
