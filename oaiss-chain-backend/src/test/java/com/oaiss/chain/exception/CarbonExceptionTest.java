package com.oaiss.chain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CarbonException 单元测试
 * CarbonException Unit Tests
 */
class CarbonExceptionTest {

    @Test
    @DisplayName("创建基本异常")
    void testBasicException() {
        CarbonException ex = new CarbonException(1001, "Test error");
        assertEquals(1001, ex.getCode());
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    @DisplayName("创建带原因的异常")
    void testExceptionWithCause() {
        Throwable cause = new RuntimeException("Original cause");
        CarbonException ex = new CarbonException(1002, "Test error", cause);
        assertEquals(1002, ex.getCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("reportNotFound工厂方法")
    void testReportNotFound() {
        CarbonException ex = CarbonException.reportNotFound(1L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("1"));
    }

    @Test
    @DisplayName("submitFailed工厂方法")
    void testSubmitFailed() {
        CarbonException ex = CarbonException.submitFailed("Network error");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Network error"));
    }

    @Test
    @DisplayName("calculationFailed工厂方法")
    void testCalculationFailed() {
        CarbonException ex = CarbonException.calculationFailed("Division by zero");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Division by zero"));
    }

    @Test
    @DisplayName("dataFormatError工厂方法")
    void testDataFormatError() {
        CarbonException ex = CarbonException.dataFormatError("dateField");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("dateField"));
    }

    @Test
    @DisplayName("dataMissing工厂方法")
    void testDataMissing() {
        CarbonException ex = CarbonException.dataMissing("requiredField");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("requiredField"));
    }

    @Test
    @DisplayName("dataOutOfRange工厂方法")
    void testDataOutOfRange() {
        CarbonException ex = CarbonException.dataOutOfRange("percentage", 150);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("percentage"));
    }

    @Test
    @DisplayName("reportAlreadySubmitted工厂方法")
    void testReportAlreadySubmitted() {
        CarbonException ex = CarbonException.reportAlreadySubmitted(2L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("2"));
    }

    @Test
    @DisplayName("reportAlreadyReviewed工厂方法")
    void testReportAlreadyReviewed() {
        CarbonException ex = CarbonException.reportAlreadyReviewed(3L);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("3"));
    }

    @Test
    @DisplayName("signatureVerificationFailed工厂方法")
    void testSignatureVerificationFailed() {
        CarbonException ex = CarbonException.signatureVerificationFailed();
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("签名验证失败"));
    }

    @Test
    @DisplayName("emissionFactorNotFound工厂方法")
    void testEmissionFactorNotFound() {
        CarbonException ex = CarbonException.emissionFactorNotFound("EF001");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("EF001"));
    }

    @Test
    @DisplayName("invalidAccountingPeriod工厂方法")
    void testInvalidAccountingPeriod() {
        CarbonException ex = CarbonException.invalidAccountingPeriod("2024-Q5");
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("2024-Q5"));
    }
}
