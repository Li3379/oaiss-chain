package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BusinessException unit tests")
class BusinessExceptionTest {

    @Test
    @DisplayName("Constructor with raw message should store raw message")
    void constructor_withRawMessage_shouldStoreRawMessage() {
        BusinessException ex = new BusinessException(1001, "Something went wrong");
        assertEquals(1001, ex.getCode());
        assertEquals("Something went wrong", ex.getMessage());
        assertNull(ex.getMessageKey());
        assertEquals("Something went wrong", ex.getRawMessage());
    }

    @Test
    @DisplayName("Constructor with message key should detect 'error.' prefix")
    void constructor_withMessageKey_shouldDetectErrorPrefix() {
        BusinessException ex = new BusinessException(1001, "error.param");
        assertEquals(1001, ex.getCode());
        assertEquals("error.param", ex.getMessageKey());
        assertNull(ex.getRawMessage());
    }

    @Test
    @DisplayName("Constructor with cause should preserve cause")
    void constructor_withCause_shouldPreserveCause() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException(1001, "Something went wrong", cause);
        assertEquals(1001, ex.getCode());
        assertSame(cause, ex.getCause());
        assertEquals("Something went wrong", ex.getRawMessage());
    }

    @Test
    @DisplayName("Constructor with cause and message key should detect 'error.' prefix")
    void constructor_withCauseAndMessageKey_shouldDetectErrorPrefix() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException(1001, "error.param", cause);
        assertEquals(1001, ex.getCode());
        assertEquals("error.param", ex.getMessageKey());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("Constructor with null message should handle gracefully")
    void constructor_withNullMessage_shouldHandleGracefully() {
        BusinessException ex = new BusinessException(1001, null);
        assertEquals(1001, ex.getCode());
        assertNull(ex.getMessageKey());
        assertNull(ex.getRawMessage());
    }

    @Test
    @DisplayName("of factory method should create exception with message key")
    void of_shouldCreateExceptionWithMessageKey() {
        BusinessException ex = BusinessException.of(1001, "error.param", "field1");
        assertEquals(1001, ex.getCode());
        assertEquals("error.param", ex.getMessageKey());
        assertArrayEquals(new Object[]{"field1"}, ex.getArgs());
    }

    @Test
    @DisplayName("systemError factory method should use SYSTEM_ERROR code")
    void systemError_shouldUseSystemErrorCode() {
        BusinessException ex = BusinessException.systemError("error.system");
        assertEquals(ErrorCode.SYSTEM_ERROR, ex.getCode());
        assertEquals("error.system", ex.getMessageKey());
    }

    @Test
    @DisplayName("systemError with cause should preserve cause")
    void systemError_withCause_shouldPreserveCause() {
        RuntimeException cause = new RuntimeException("db error");
        BusinessException ex = BusinessException.systemError("error.system", cause);
        assertEquals(ErrorCode.SYSTEM_ERROR, ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("paramError factory method should use PARAM_ERROR code")
    void paramError_shouldUseParamErrorCode() {
        BusinessException ex = BusinessException.paramError("error.param");
        assertEquals(ErrorCode.PARAM_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("notFound factory method should use RESOURCE_NOT_FOUND code")
    void notFound_shouldUseResourceNotFoundCode() {
        BusinessException ex = BusinessException.notFound("error.resource.not.found");
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("authFailed factory method should use LOGIN_FAILED code")
    void authFailed_shouldUseLoginFailedCode() {
        BusinessException ex = BusinessException.authFailed("error.login.failed");
        assertEquals(ErrorCode.LOGIN_FAILED, ex.getCode());
    }

    @Test
    @DisplayName("permissionDenied factory method should use PERMISSION_DENIED code")
    void permissionDenied_shouldUsePermissionDeniedCode() {
        BusinessException ex = BusinessException.permissionDenied("error.permission.denied");
        assertEquals(ErrorCode.PERMISSION_DENIED, ex.getCode());
    }

    @Test
    @DisplayName("BusinessException should extend RuntimeException")
    void shouldExtendRuntimeException() {
        BusinessException ex = new BusinessException(1001, "test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    @DisplayName("getMessage with raw message should return raw message directly")
    void getMessage_withRawMessage_shouldReturnRawMessage() {
        BusinessException ex = new BusinessException(1001, "direct message");
        assertEquals("direct message", ex.getMessage());
    }

    @Test
    @DisplayName("getMessage with messageKey should resolve via MessageUtils")
    void getMessage_withMessageKey_shouldResolveViaMessageUtils() {
        BusinessException ex = BusinessException.of(1001, "error.param", "field1");
        // MessageUtils has no messageSource set, so falls back to key and args format
        String message = ex.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("error.param"));
    }

    @Test
    @DisplayName("getMessage with both rawMessage and messageKey null should return super message")
    void getMessage_withBothNull_shouldReturnSuperMessage() {
        BusinessException ex = new BusinessException(1001, null);
        // super.getMessage() returns the message passed to RuntimeException constructor (which is null)
        String message = ex.getMessage();
        assertNull(message);
    }
}
