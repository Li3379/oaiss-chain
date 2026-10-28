package com.oaiss.chain.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CommonUtils 单元测试
 * Unit tests for CommonUtils
 */
@ExtendWith(MockitoExtension.class)
class CommonUtilsTest {

    // ==================== ID生成测试 ====================

    @Nested
    @DisplayName("generateUuid 方法测试")
    class GenerateUuidTests {

        @Test
        @DisplayName("生成有效的UUID - 32位无横杠字符串")
        void generateUuid_shouldGenerateValidUuid() {
            String uuid = CommonUtils.generateUuid();

            assertNotNull(uuid);
            assertEquals(32, uuid.length());
            assertTrue(uuid.matches("^[a-f0-9]{32}$"));
        }

        @Test
        @DisplayName("生成唯一的UUID值")
        void generateUuid_shouldGenerateUniqueValues() {
            Set<String> uuids = new HashSet<>();
            int count = 100;

            for (int i = 0; i < count; i++) {
                uuids.add(CommonUtils.generateUuid());
            }

            assertEquals(count, uuids.size(), "所有生成的UUID应该是唯一的");
        }
    }

    @Nested
    @DisplayName("generateRequestId 方法测试")
    class GenerateRequestIdTests {

        @Test
        @DisplayName("生成有效的请求ID格式")
        void generateRequestId_shouldGenerateValidFormat() {
            String requestId = CommonUtils.generateRequestId();

            assertNotNull(requestId);
            assertTrue(requestId.startsWith("req_"));
            assertTrue(Pattern.matches("^req_\\d+_[a-f0-9]{8}$", requestId));
        }
    }

    @Nested
    @DisplayName("generateTradeId 方法测试")
    class GenerateTradeIdTests {

        @Test
        @DisplayName("生成有效的交易ID格式")
        void generateTradeId_shouldGenerateValidFormat() {
            String tradeId = CommonUtils.generateTradeId();

            assertNotNull(tradeId);
            assertTrue(tradeId.startsWith("trade_"));
            assertTrue(Pattern.matches("^trade_\\d+_[a-f0-9]{8}$", tradeId));
        }
    }

    @Nested
    @DisplayName("generateOrderId 方法测试")
    class GenerateOrderIdTests {

        @Test
        @DisplayName("生成有效的订单ID格式")
        void generateOrderId_shouldGenerateValidFormat() {
            String orderId = CommonUtils.generateOrderId();

            assertNotNull(orderId);
            assertTrue(orderId.startsWith("order_"));
            assertTrue(Pattern.matches("^order_\\d+_[a-f0-9]{8}$", orderId));
        }
    }

    @Nested
    @DisplayName("generateReportId 方法测试")
    class GenerateReportIdTests {

        @Test
        @DisplayName("生成有效的报告ID格式")
        void generateReportId_shouldGenerateValidFormat() {
            String reportId = CommonUtils.generateReportId();

            assertNotNull(reportId);
            assertTrue(reportId.startsWith("report_"));
            assertTrue(Pattern.matches("^report_\\d+_[a-f0-9]{8}$", reportId));
        }
    }

    // ==================== 时间处理测试 ====================

    @Nested
    @DisplayName("formatDateTime 方法测试")
    class FormatDateTimeTests {

        @Test
        @DisplayName("正确格式化日期时间")
        void formatDateTime_shouldFormatCorrectly() {
            LocalDateTime dateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 45);

            String result = CommonUtils.formatDateTime(dateTime);

            assertEquals("2025-01-15 10:30:45", result);
        }

        @Test
        @DisplayName("处理null值返回null")
        void formatDateTime_shouldReturnNullForNullInput() {
            String result = CommonUtils.formatDateTime(null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("getCurrentDateTimeString 方法测试")
    class GetCurrentDateTimeStringTests {

        @Test
        @DisplayName("返回有效的日期时间格式")
        void getCurrentDateTimeString_shouldReturnValidFormat() {
            String result = CommonUtils.getCurrentDateTimeString();

            assertNotNull(result);
            assertTrue(Pattern.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", result));
        }
    }

    @Nested
    @DisplayName("getCurrentTimestamp 方法测试")
    class GetCurrentTimestampTests {

        @Test
        @DisplayName("返回正数时间戳")
        void getCurrentTimestamp_shouldReturnPositiveValue() {
            long before = System.currentTimeMillis();
            long result = CommonUtils.getCurrentTimestamp();
            long after = System.currentTimeMillis();

            assertTrue(result >= before);
            assertTrue(result <= after);
            assertTrue(result > 0);
        }
    }

    // ==================== 字符串处理测试 ====================

    @Nested
    @DisplayName("isEmpty 方法测试")
    class IsEmptyTests {

        @Test
        @DisplayName("null字符串返回true")
        void isEmpty_nullString_returnsTrue() {
            assertTrue(CommonUtils.isEmpty(null));
        }

        @Test
        @DisplayName("空字符串返回true")
        void isEmpty_emptyString_returnsTrue() {
            assertTrue(CommonUtils.isEmpty(""));
        }

        @Test
        @DisplayName("纯空白字符串返回true")
        void isEmpty_whitespaceString_returnsTrue() {
            assertTrue(CommonUtils.isEmpty("   "));
            assertTrue(CommonUtils.isEmpty("\t\n"));
        }

        @Test
        @DisplayName("非空字符串返回false")
        void isEmpty_nonEmptyString_returnsFalse() {
            assertFalse(CommonUtils.isEmpty("test"));
            assertFalse(CommonUtils.isEmpty("  test  "));
        }
    }

    @Nested
    @DisplayName("isNotEmpty 方法测试")
    class IsNotEmptyTests {

        @Test
        @DisplayName("null字符串返回false")
        void isNotEmpty_nullString_returnsFalse() {
            assertFalse(CommonUtils.isNotEmpty(null));
        }

        @Test
        @DisplayName("空字符串返回false")
        void isNotEmpty_emptyString_returnsFalse() {
            assertFalse(CommonUtils.isNotEmpty(""));
        }

        @Test
        @DisplayName("非空字符串返回true")
        void isNotEmpty_nonEmptyString_returnsTrue() {
            assertTrue(CommonUtils.isNotEmpty("test"));
            assertTrue(CommonUtils.isNotEmpty("  test  "));
        }
    }

    @Nested
    @DisplayName("uncapitalize 方法测试")
    class UncapitalizeTests {

        @Test
        @DisplayName("首字母转换为小写")
        void uncapitalize_shouldLowercaseFirstLetter() {
            assertEquals("hello", CommonUtils.uncapitalize("Hello"));
            assertEquals("wORLD", CommonUtils.uncapitalize("WORLD"));
            assertEquals("java", CommonUtils.uncapitalize("Java"));
        }

        @Test
        @DisplayName("处理null返回null")
        void uncapitalize_nullInput_returnsNull() {
            assertNull(CommonUtils.uncapitalize(null));
        }

        @Test
        @DisplayName("处理空字符串返回空字符串")
        void uncapitalize_emptyInput_returnsEmpty() {
            assertEquals("", CommonUtils.uncapitalize(""));
        }
    }

    @Nested
    @DisplayName("capitalize 方法测试")
    class CapitalizeTests {

        @Test
        @DisplayName("首字母转换为大写")
        void capitalize_shouldUppercaseFirstLetter() {
            assertEquals("Hello", CommonUtils.capitalize("hello"));
            assertEquals("World", CommonUtils.capitalize("world"));
            assertEquals("JAVA", CommonUtils.capitalize("jAVA"));
        }

        @Test
        @DisplayName("处理null返回null")
        void capitalize_nullInput_returnsNull() {
            assertNull(CommonUtils.capitalize(null));
        }

        @Test
        @DisplayName("处理空字符串返回空字符串")
        void capitalize_emptyInput_returnsEmpty() {
            assertEquals("", CommonUtils.capitalize(""));
        }
    }

    // ==================== 数值处理测试 ====================

    @Nested
    @DisplayName("parseLong 方法测试")
    class ParseLongTests {

        @Test
        @DisplayName("解析有效的Long字符串")
        void parseLong_validString_returnsValue() {
            assertEquals(123456L, CommonUtils.parseLong("123456", 0L));
            assertEquals(-999L, CommonUtils.parseLong("-999", 0L));
        }

        @Test
        @DisplayName("null字符串返回默认值")
        void parseLong_nullString_returnsDefault() {
            assertEquals(100L, CommonUtils.parseLong(null, 100L));
        }

        @Test
        @DisplayName("无效字符串返回默认值")
        void parseLong_invalidString_returnsDefault() {
            assertEquals(50L, CommonUtils.parseLong("abc", 50L));
            assertEquals(0L, CommonUtils.parseLong("12.34", 0L));
            assertEquals(-1L, CommonUtils.parseLong("", -1L));
        }
    }

    @Nested
    @DisplayName("parseInteger 方法测试")
    class ParseIntegerTests {

        @Test
        @DisplayName("解析有效的Integer字符串")
        void parseInteger_validString_returnsValue() {
            assertEquals(123, CommonUtils.parseInteger("123", 0));
            assertEquals(-999, CommonUtils.parseInteger("-999", 0));
        }

        @Test
        @DisplayName("null字符串返回默认值")
        void parseInteger_nullString_returnsDefault() {
            assertEquals(100, CommonUtils.parseInteger(null, 100));
        }

        @Test
        @DisplayName("无效字符串返回默认值")
        void parseInteger_invalidString_returnsDefault() {
            assertEquals(50, CommonUtils.parseInteger("abc", 50));
            assertEquals(0, CommonUtils.parseInteger("12.34", 0));
            assertEquals(-1, CommonUtils.parseInteger("", -1));
        }
    }

    @Nested
    @DisplayName("parseDouble 方法测试")
    class ParseDoubleTests {

        @Test
        @DisplayName("解析有效的Double字符串")
        void parseDouble_validString_returnsValue() {
            assertEquals(123.45, CommonUtils.parseDouble("123.45", 0.0), 0.001);
            assertEquals(-999.99, CommonUtils.parseDouble("-999.99", 0.0), 0.001);
        }

        @Test
        @DisplayName("null字符串返回默认值")
        void parseDouble_nullString_returnsDefault() {
            assertEquals(100.0, CommonUtils.parseDouble(null, 100.0), 0.001);
        }

        @Test
        @DisplayName("无效字符串返回默认值")
        void parseDouble_invalidString_returnsDefault() {
            assertEquals(50.0, CommonUtils.parseDouble("abc", 50.0), 0.001);
            assertEquals(0.0, CommonUtils.parseDouble("", 0.0), 0.001);
        }
    }

    // ========== TDD Cycle 3: H18 分页参数无上限 ==========

    @Nested
    @DisplayName("sanitizePageSize 方法测试")
    class SanitizePageSizeTests {

        @Test
        @DisplayName("超大pageSize应被限制为最大值")
        void sanitizePageSize_oversizedValue_shouldClampToMax() {
            assertEquals(100, CommonUtils.sanitizePageSize(999999));
        }

        @Test
        @DisplayName("正常pageSize保持不变")
        void sanitizePageSize_normalValue_shouldReturnUnchanged() {
            assertEquals(10, CommonUtils.sanitizePageSize(10));
            assertEquals(50, CommonUtils.sanitizePageSize(50));
            assertEquals(100, CommonUtils.sanitizePageSize(100));
        }

        @Test
        @DisplayName("null或零或负数应返回默认值")
        void sanitizePageSize_invalidValue_shouldReturnDefault() {
            assertEquals(10, CommonUtils.sanitizePageSize(null));
            assertEquals(10, CommonUtils.sanitizePageSize(0));
            assertEquals(10, CommonUtils.sanitizePageSize(-5));
        }
    }

    // ==================== 请求相关测试 ====================

    @Nested
    @DisplayName("getClientIp 方法测试")
    class GetClientIpTests {

        @Test
        @DisplayName("从X-Forwarded-For获取IP")
        void getClientIp_fromXForwardedFor() {
            // Given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                // When
                String ip = CommonUtils.getClientIp();

                // Then
                assertEquals("192.168.1.1", ip);
            }
        }

        @Test
        @DisplayName("从X-Forwarded-For获取第一个IP（多代理）")
        void getClientIp_fromXForwardedForMultiple() {
            // Given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                // When
                String ip = CommonUtils.getClientIp();

                // Then
                assertEquals("192.168.1.1", ip);
            }
        }

        @Test
        @DisplayName("从X-Real-IP获取IP")
        void getClientIp_fromXRealIp() {
            // Given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(mockRequest.getHeader("X-Real-IP")).thenReturn("10.0.0.2");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                // When
                String ip = CommonUtils.getClientIp();

                // Then
                assertEquals("10.0.0.2", ip);
            }
        }

        @Test
        @DisplayName("从RemoteAddr获取IP")
        void getClientIp_fromRemoteAddr() {
            // Given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(mockRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(mockRequest.getRemoteAddr()).thenReturn("127.0.0.1");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                // When
                String ip = CommonUtils.getClientIp();

                // Then
                assertEquals("127.0.0.1", ip);
            }
        }

        @Test
        @DisplayName("无请求上下文返回unknown")
        void getClientIp_noContext_returnsUnknown() {
            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

                // When
                String ip = CommonUtils.getClientIp();

                // Then
                assertEquals("unknown", ip);
            }
        }

        @Test
        @DisplayName("X-Forwarded-For为unknown时从其他头获取")
        void getClientIp_unknownHeader_fallback() {
            // Given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("unknown");
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn("172.16.0.1");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                // When
                String ip = CommonUtils.getClientIp();

                // Then
                assertEquals("172.16.0.1", ip);
            }
        }
    }

    @Nested
    @DisplayName("getRequestPath 方法测试")
    class GetRequestPathTests {

        @Test
        @DisplayName("获取请求路径成功")
        void getRequestPath_success() {
            // Given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getRequestURI()).thenReturn("/api/v1/test");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                // When
                String path = CommonUtils.getRequestPath();

                // Then
                assertEquals("/api/v1/test", path);
            }
        }

        @Test
        @DisplayName("无请求上下文返回unknown")
        void getRequestPath_noContext_returnsUnknown() {
            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

                // When
                String path = CommonUtils.getRequestPath();

                // Then
                assertEquals("unknown", path);
            }
        }
    }

    @Nested
    @DisplayName("getRequestMethod 方法测试")
    class GetRequestMethodTests {

        @Test
        @DisplayName("获取请求方法成功")
        void getRequestMethod_success() {
            // Given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getMethod()).thenReturn("POST");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                // When
                String method = CommonUtils.getRequestMethod();

                // Then
                assertEquals("POST", method);
            }
        }

        @Test
        @DisplayName("无请求上下文返回unknown")
        void getRequestMethod_noContext_returnsUnknown() {
            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

                // When
                String method = CommonUtils.getRequestMethod();

                // Then
                assertEquals("unknown", method);
            }
        }
    }

    // ==================== sanitizeHtml 测试 ====================

    @Nested
    @DisplayName("sanitizeHtml 方法测试")
    class SanitizeHtmlTests {

        @Test
        @DisplayName("null输入返回null")
        void sanitizeHtml_nullInput_returnsNull() {
            assertNull(CommonUtils.sanitizeHtml(null));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void sanitizeHtml_emptyInput_returnsEmpty() {
            assertEquals("", CommonUtils.sanitizeHtml(""));
        }

        @Test
        @DisplayName("包含HTML特殊字符应被转义")
        void sanitizeHtml_withSpecialChars_shouldEscape() {
            assertEquals("&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;",
                    CommonUtils.sanitizeHtml("<script>alert(\"xss\")</script>"));
        }

        @Test
        @DisplayName("包含&和单引号应被转义")
        void sanitizeHtml_ampersandAndSingleQuote_shouldEscape() {
            assertEquals("Tom &amp; Jerry &#x27;s",
                    CommonUtils.sanitizeHtml("Tom & Jerry 's"));
        }
    }

    // ==================== sanitizeInput 测试 ====================

    @Nested
    @DisplayName("sanitizeInput 方法测试")
    class SanitizeInputTests {

        @Test
        @DisplayName("null输入返回null")
        void sanitizeInput_nullInput_returnsNull() {
            assertNull(CommonUtils.sanitizeInput(null));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void sanitizeInput_emptyInput_returnsEmpty() {
            assertEquals("", CommonUtils.sanitizeInput(""));
        }

        @Test
        @DisplayName("包含script标签应被移除")
        void sanitizeInput_withScriptTag_shouldRemove() {
            assertEquals("Hello  World",
                    CommonUtils.sanitizeInput("Hello <script>alert('xss')</script> World"));
        }

        @Test
        @DisplayName("包含事件处理器应被移除")
        void sanitizeInput_withEventHandler_shouldRemove() {
            assertEquals("Test alert(1) input",
                    CommonUtils.sanitizeInput("Test onclick=alert(1) input"));
        }

        @Test
        @DisplayName("普通文本不应被修改")
        void sanitizeInput_plainText_shouldNotModify() {
            assertEquals("Hello World", CommonUtils.sanitizeInput("Hello World"));
        }
    }

    // ==================== Additional getClientIp 测试 ====================

    @Nested
    @DisplayName("getClientIp 额外分支测试")
    class GetClientIpAdditionalTests {

        @Test
        @DisplayName("WL-Proxy-Client-IP有值时应返回该IP")
        void getClientIp_fromWlProxyClientIp() {
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn("172.16.0.100");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                String ip = CommonUtils.getClientIp();

                assertEquals("172.16.0.100", ip);
            }
        }

        @Test
        @DisplayName("X-Forwarded-For为空字符串时应继续查找")
        void getClientIp_emptyXForwardedFor_fallback() {
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("");
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn("10.0.0.50");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                String ip = CommonUtils.getClientIp();

                assertEquals("10.0.0.50", ip);
            }
        }

        @Test
        @DisplayName("所有中间头返回unknown时应从RemoteAddr获取")
        void getClientIp_allHeadersUnknown_fallbackToRemoteAddr() {
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn("unknown");
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn("unknown");
            when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn("unknown");
            when(mockRequest.getHeader("X-Real-IP")).thenReturn("unknown");
            when(mockRequest.getRemoteAddr()).thenReturn("192.168.0.1");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                String ip = CommonUtils.getClientIp();

                assertEquals("192.168.0.1", ip);
            }
        }

        @Test
        @DisplayName("Proxy-Client-IP为空字符串时应继续查找WL头")
        void getClientIp_emptyProxyClientIp_fallbackToWl() {
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn("");
            when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn("10.0.0.99");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                String ip = CommonUtils.getClientIp();

                assertEquals("10.0.0.99", ip);
            }
        }

        @Test
        @DisplayName("WL-Proxy-Client-IP为空字符串时应继续查找X-Real-IP")
        void getClientIp_emptyWlProxyClientIp_fallbackToXRealIp() {
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn("");
            when(mockRequest.getHeader("X-Real-IP")).thenReturn("10.0.0.88");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                String ip = CommonUtils.getClientIp();

                assertEquals("10.0.0.88", ip);
            }
        }

        @Test
        @DisplayName("X-Real-IP为空字符串时应从RemoteAddr获取")
        void getClientIp_emptyXRealIp_fallbackToRemoteAddr() {
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
            when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
            when(mockRequest.getHeader("X-Real-IP")).thenReturn("");
            when(mockRequest.getRemoteAddr()).thenReturn("10.0.0.77");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                String ip = CommonUtils.getClientIp();

                assertEquals("10.0.0.77", ip);
            }
        }

        @Test
        @DisplayName("Proxy-Client-IP为unknown时继续查找WL头")
        void getClientIp_unknownProxyClientIp_fallbackToWl() {
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn("unknown");
            when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn("10.0.0.55");

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                String ip = CommonUtils.getClientIp();

                assertEquals("10.0.0.55", ip);
            }
        }
    }

    @Nested
    @DisplayName("getCurrentRequest 方法测试")
    class GetCurrentRequestTests {

        @Test
        @DisplayName("获取当前请求成功")
        void getCurrentRequest_success() {
            // Given
            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            when(attributes.getRequest()).thenReturn(mockRequest);

            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

                // When
                HttpServletRequest result = CommonUtils.getCurrentRequest();

                // Then
                assertNotNull(result);
                assertSame(mockRequest, result);
            }
        }

        @Test
        @DisplayName("无请求上下文返回null")
        void getCurrentRequest_noContext_returnsNull() {
            try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
                mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

                // When
                HttpServletRequest result = CommonUtils.getCurrentRequest();

                // Then
                assertNull(result);
            }
        }
    }

    // ==================== Additional branch coverage ====================

    @Test
    @DisplayName("WL-Proxy-Client-IP为unknown（忽略大小写）时继续查找")
    void getClientIp_wlProxyClientIpUnknownCase_fallback() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(mockRequest);
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn("Unknown");
        when(mockRequest.getHeader("X-Real-IP")).thenReturn("10.1.1.1");

        try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
            mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
            assertEquals("10.1.1.1", CommonUtils.getClientIp());
        }
    }

    @Test
    @DisplayName("X-Real-IP为unknown（忽略大小写）时回退到RemoteAddr")
    void getClientIp_xRealIpUnknownCase_fallbackToRemoteAddr() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(mockRequest);
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(mockRequest.getHeader("X-Real-IP")).thenReturn("UNKNOWN");
        when(mockRequest.getRemoteAddr()).thenReturn("192.168.0.200");

        try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
            mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
            assertEquals("192.168.0.200", CommonUtils.getClientIp());
        }
    }

    @Test
    @DisplayName("RemoteAddr包含逗号时取第一个IP")
    void getClientIp_remoteAddrContainsComma_takesFirstIp() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
        when(attributes.getRequest()).thenReturn(mockRequest);
        when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(mockRequest.getHeader("Proxy-Client-IP")).thenReturn(null);
        when(mockRequest.getHeader("WL-Proxy-Client-IP")).thenReturn(null);
        when(mockRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(mockRequest.getRemoteAddr()).thenReturn("10.0.0.1, 192.168.1.1");

        try (MockedStatic<RequestContextHolder> mockedStatic = mockStatic(RequestContextHolder.class)) {
            mockedStatic.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
            assertEquals("10.0.0.1", CommonUtils.getClientIp());
        }
    }
}
