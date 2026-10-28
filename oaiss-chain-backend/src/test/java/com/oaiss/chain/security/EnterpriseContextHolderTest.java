package com.oaiss.chain.security;

import com.oaiss.chain.enums.UserTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnterpriseContextHolder 单元测试
 * Unit tests for EnterpriseContextHolder
 *
 * @author OAISS Team
 */
class EnterpriseContextHolderTest {

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    // ==================== EnterpriseId Tests ====================

    @Test
    @DisplayName("setEnterpriseId and getEnterpriseId - success")
    void setEnterpriseIdAndGetEnterpriseId_Success() {
        // Arrange
        Long expectedEnterpriseId = 100L;

        // Act
        EnterpriseContextHolder.setEnterpriseId(expectedEnterpriseId);
        Long actualEnterpriseId = EnterpriseContextHolder.getEnterpriseId();

        // Assert
        assertEquals(expectedEnterpriseId, actualEnterpriseId);
    }

    @Test
    @DisplayName("getEnterpriseId - returns null when not set")
    void getEnterpriseId_ReturnsNullWhenNotSet() {
        // Act
        Long actualEnterpriseId = EnterpriseContextHolder.getEnterpriseId();

        // Assert
        assertNull(actualEnterpriseId);
    }

    // ==================== UserId Tests ====================

    @Test
    @DisplayName("setUserId and getUserId - success")
    void setUserIdAndGetUserId_Success() {
        // Arrange
        Long expectedUserId = 200L;

        // Act
        EnterpriseContextHolder.setUserId(expectedUserId);
        Long actualUserId = EnterpriseContextHolder.getUserId();

        // Assert
        assertEquals(expectedUserId, actualUserId);
    }

    @Test
    @DisplayName("getUserId - returns null when not set")
    void getUserId_ReturnsNullWhenNotSet() {
        // Act
        Long actualUserId = EnterpriseContextHolder.getUserId();

        // Assert
        assertNull(actualUserId);
    }

    // ==================== UserType Tests ====================

    @Test
    @DisplayName("setUserType and getUserType - success")
    void setUserTypeAndGetUserType_Success() {
        // Arrange
        Integer expectedUserType = 1;

        // Act
        EnterpriseContextHolder.setUserType(expectedUserType);
        Integer actualUserType = EnterpriseContextHolder.getUserType();

        // Assert
        assertEquals(expectedUserType, actualUserType);
    }

    @Test
    @DisplayName("getUserType - returns null when not set")
    void getUserType_ReturnsNullWhenNotSet() {
        // Act
        Integer actualUserType = EnterpriseContextHolder.getUserType();

        // Assert
        assertNull(actualUserType);
    }

    // ==================== isEnterpriseUser Tests ====================

    @Test
    @DisplayName("isEnterpriseUser - returns true when userType is 1")
    void isEnterpriseUser_ReturnsTrueWhenUserTypeIs1() {
        // Arrange
        EnterpriseContextHolder.setUserType(1);

        // Act
        boolean result = EnterpriseContextHolder.isEnterpriseUser();

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("isEnterpriseUser - returns false when userType is not 1")
    void isEnterpriseUser_ReturnsFalseWhenUserTypeIsNot1() {
        // Arrange
        EnterpriseContextHolder.setUserType(2);

        // Act
        boolean result = EnterpriseContextHolder.isEnterpriseUser();

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("isEnterpriseUser - returns false when userType is null")
    void isEnterpriseUser_ReturnsFalseWhenUserTypeIsNull() {
        // Act
        boolean result = EnterpriseContextHolder.isEnterpriseUser();

        // Assert
        assertFalse(result);
    }

    // ==================== isAdmin Tests ====================

    @Test
    @DisplayName("isAdmin - 真实管理员角色码(4)必须返回 true")
    void isAdmin_ReturnsTrueForRealAdminCode() {
        // Arrange — 必须用 UserTypeEnum.ADMIN 的真实取值，避免魔法数字与枚举漂移
        EnterpriseContextHolder.setUserType(UserTypeEnum.ADMIN.getCode());

        // Act
        boolean result = EnterpriseContextHolder.isAdmin();

        // Assert
        assertTrue(result, "真实管理员角色码应被识别为管理员");
    }

    @Test
    @DisplayName("isAdmin - 企业角色码返回 false")
    void isAdmin_ReturnsFalseForEnterpriseCode() {
        // Arrange
        EnterpriseContextHolder.setUserType(UserTypeEnum.ENTERPRISE.getCode());

        // Act
        boolean result = EnterpriseContextHolder.isAdmin();

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("isAdmin - returns false when userType is null")
    void isAdmin_ReturnsFalseWhenUserTypeIsNull() {
        // Act
        boolean result = EnterpriseContextHolder.isAdmin();

        // Assert
        assertFalse(result);
    }

    // ==================== isReviewer Tests ====================

    @Test
    @DisplayName("isReviewer - returns true when userType is 2")
    void isReviewer_ReturnsTrueWhenUserTypeIs2() {
        // Arrange
        EnterpriseContextHolder.setUserType(2);

        // Act
        boolean result = EnterpriseContextHolder.isReviewer();

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("isReviewer - returns false when userType is not 2")
    void isReviewer_ReturnsFalseWhenUserTypeIsNot2() {
        // Arrange
        EnterpriseContextHolder.setUserType(1);

        // Act
        boolean result = EnterpriseContextHolder.isReviewer();

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("isReviewer - returns false when userType is null")
    void isReviewer_ReturnsFalseWhenUserTypeIsNull() {
        // Act
        boolean result = EnterpriseContextHolder.isReviewer();

        // Assert
        assertFalse(result);
    }

    // ==================== clear Tests ====================

    @Test
    @DisplayName("clear - removes all values")
    void clear_RemovesAllValues() {
        // Arrange
        EnterpriseContextHolder.setEnterpriseId(100L);
        EnterpriseContextHolder.setUserId(200L);
        EnterpriseContextHolder.setUserType(1);

        // Act
        EnterpriseContextHolder.clear();

        // Assert
        assertNull(EnterpriseContextHolder.getEnterpriseId());
        assertNull(EnterpriseContextHolder.getUserId());
        assertNull(EnterpriseContextHolder.getUserType());
    }

    // ==================== initFromJwtUserDetails Tests ====================

    @Test
    @DisplayName("initFromJwtUserDetails - initializes all fields")
    void initFromJwtUserDetails_InitializesAllFields() {
        // Arrange
        Long expectedUserId = 300L;
        Integer expectedUserType = 1;
        Long expectedEnterpriseId = 400L;

        JwtUserDetails userDetails = JwtUserDetails.builder()
                .userId(expectedUserId)
                .userType(expectedUserType)
                .enterpriseId(expectedEnterpriseId)
                .build();

        // Act
        EnterpriseContextHolder.initFromJwtUserDetails(userDetails);

        // Assert
        assertEquals(expectedUserId, EnterpriseContextHolder.getUserId());
        assertEquals(expectedUserType, EnterpriseContextHolder.getUserType());
        assertEquals(expectedEnterpriseId, EnterpriseContextHolder.getEnterpriseId());
    }

    @Test
    @DisplayName("initFromJwtUserDetails - handles null")
    void initFromJwtUserDetails_HandlesNull() {
        // Arrange - set some initial values
        EnterpriseContextHolder.setUserId(100L);
        EnterpriseContextHolder.setUserType(1);
        EnterpriseContextHolder.setEnterpriseId(200L);

        // Act
        EnterpriseContextHolder.initFromJwtUserDetails(null);

        // Assert - values should remain unchanged (null JwtUserDetails is ignored)
        assertEquals(100L, EnterpriseContextHolder.getUserId());
        assertEquals(1, EnterpriseContextHolder.getUserType());
        assertEquals(200L, EnterpriseContextHolder.getEnterpriseId());
    }
}
