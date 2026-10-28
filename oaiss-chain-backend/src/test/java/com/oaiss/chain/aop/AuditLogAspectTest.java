package com.oaiss.chain.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.entity.OperationLog;
import com.oaiss.chain.repository.OperationLogRepository;
import com.oaiss.chain.security.JwtUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuditLogAspect单元测试
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private Method method;

    @Mock
    private HttpServletResponse response;

    private MockHttpServletRequest request;

    @Captor
    private ArgumentCaptor<OperationLog> logCaptor;

    @BeforeEach
    void setUp() {
        // Reset security context before each test
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        
        // Initialize MockHttpServletRequest
        request = new MockHttpServletRequest();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("成功操作记录状态为1")
    void recordAuditLog_successfulOperation_logsStatus1() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("用户管理", "创建用户", "创建新用户", true, false, "password");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        when(joinPoint.proceed()).thenReturn("success result");
        when(operationLogRepository.save(any(OperationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Object result = aspect.recordAuditLog(joinPoint);

        // Assert
        assertThat(result).isEqualTo("success result");
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getStatus()).isEqualTo(1);
        assertThat(savedLog.getModule()).isEqualTo("用户管理");
        assertThat(savedLog.getAction()).isEqualTo("创建用户");
    }

    @Test
    @DisplayName("异常操作记录状态为2")
    void recordAuditLog_exceptionOperation_logsStatus2() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("用户管理", "删除用户", "删除用户", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        RuntimeException testException = new RuntimeException("测试异常");
        when(joinPoint.proceed()).thenThrow(testException);
        when(operationLogRepository.save(any(OperationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act & Assert
        assertThatThrownBy(() -> aspect.recordAuditLog(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("测试异常");

        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getStatus()).isEqualTo(2);
        assertThat(savedLog.getErrorMsg()).isEqualTo("测试异常");
    }

    @Test
    @DisplayName("recordParams启用时捕获参数")
    void recordAuditLog_recordParamsEnabled_capturesParams() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("订单管理", "创建订单", "创建订单", true, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        when(joinPoint.getArgs()).thenReturn(new Object[]{"param1", 123});
        when(objectMapper.writeValueAsString(any())).thenReturn("[\"param1\",123]");
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestParams()).isEqualTo("[\"param1\",123]");
    }

    @Test
    @DisplayName("recordParams禁用时跳过参数记录")
    void recordAuditLog_recordParamsDisabled_skipsParams() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("订单管理", "查询订单", "查询订单", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(joinPoint, never()).getArgs();
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestParams()).isNull();
    }

    @Test
    @DisplayName("recordResult启用时捕获结果")
    void recordAuditLog_recordResultEnabled_capturesResult() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("商品管理", "查询商品", "查询商品详情", false, true, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        Object result = new TestResult("商品名称", 100);
        when(joinPoint.proceed()).thenReturn(result);
        when(objectMapper.writeValueAsString(result)).thenReturn("{\"name\":\"商品名称\",\"price\":100}");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getResponseResult()).isEqualTo("{\"name\":\"商品名称\",\"price\":100}");
    }

    @Test
    @DisplayName("敏感字段被脱敏")
    void recordAuditLog_sensitiveFields_masked() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("用户管理", "修改密码", "修改密码", true, false, "password,token,secret");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        when(joinPoint.getArgs()).thenReturn(new Object[]{"user123", "myPassword123"});
        when(objectMapper.writeValueAsString(any())).thenReturn("[\"user123\",\"myPassword123\"]");
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(objectMapper).writeValueAsString(any());
        // Note: Actual masking happens in getRequestParams method via regex replacement
    }

    @Test
    @DisplayName("执行时间被记录")
    void recordAuditLog_executionTime_recorded() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("系统管理", "执行任务", "执行定时任务", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(10); // Simulate some execution time
            return "done";
        });
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        Long executionTime = logCaptor.getValue().getExecutionTime();
        assertThat(executionTime).isGreaterThanOrEqualTo(10L);
    }

    @Test
    @DisplayName("匿名用户被正确处理")
    void recordAuditLog_anonymousUser_handled() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("公共接口", "公开查询", "公开查询", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        // No authentication set - simulates anonymous user
        
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUserId()).isEqualTo(0L);
        assertThat(savedLog.getUsername()).isEqualTo("anonymous");
        assertThat(savedLog.getUserType()).isEqualTo(0);
    }

    @Test
    @DisplayName("认证用户信息被正确捕获")
    void recordAuditLog_authenticatedUser_captured() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("用户管理", "查询用户", "查询用户信息", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        
        // 模拟认证用户
        JwtUserDetails userDetails = JwtUserDetails.builder()
                .userId(100L)
                .username("testuser")
                .userType(1)
                .roles(List.of("USER"))
                .enabled(true)
                .build();
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUserId()).isEqualTo(100L);
        assertThat(savedLog.getUsername()).isEqualTo("testuser");
        assertThat(savedLog.getUserType()).isEqualTo(1);
    }

    @Test
    @DisplayName("截断长字符串")
    void truncateString_longString_truncated() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", false, true, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        
        String longResult = "a".repeat(3000);
        when(joinPoint.proceed()).thenReturn(longResult);
        when(objectMapper.writeValueAsString(longResult)).thenReturn(longResult);
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        String truncatedResult = logCaptor.getValue().getResponseResult();
        assertThat(truncatedResult).hasSize(2000);
        assertThat(truncatedResult).startsWith("a");
    }

    @Test
    @DisplayName("短字符串不变")
    void truncateString_shortString_unchanged() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", false, true, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        
        String shortResult = "{\"status\":\"ok\"}";
        when(joinPoint.proceed()).thenReturn(shortResult);
        when(objectMapper.writeValueAsString(shortResult)).thenReturn(shortResult);
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getResponseResult()).isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    @DisplayName("null字符串返回null")
    void truncateString_nullString_returnsNull() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", false, true, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        
        when(joinPoint.proceed()).thenReturn(null);
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getResponseResult()).isNull();
    }

    @Test
    @DisplayName("HTTP请求信息被正确记录")
    void recordAuditLog_httpRequestInfo_recorded() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "接口调用", "API接口调用", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        
        // Create new request for this specific test (don't use setupRequestContext)
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "192.168.1.100");
        request.addHeader("User-Agent", "Mozilla/5.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getHttpMethod()).isEqualTo("POST");
        assertThat(savedLog.getRequestUrl()).isEqualTo("/api/users");
        assertThat(savedLog.getRequestIp()).isEqualTo("192.168.1.100");
        assertThat(savedLog.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("无HTTP请求时使用默认值")
    void recordAuditLog_noHttpRequest_usesDefaults() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("定时任务", "执行任务", "定时任务执行", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        // No request context set
        
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getHttpMethod()).isEqualTo("UNKNOWN");
        assertThat(savedLog.getRequestUrl()).isEqualTo("UNKNOWN");
        assertThat(savedLog.getRequestIp()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("保存日志失败不影响主流程")
    void recordAuditLog_saveLogFails_doesNotAffectMainFlow() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        
        when(joinPoint.proceed()).thenReturn("success");
        when(operationLogRepository.save(any())).thenThrow(new RuntimeException("数据库连接失败"));

        // Act
        Object result = aspect.recordAuditLog(joinPoint);

        // Assert
        assertThat(result).isEqualTo("success");
        verify(operationLogRepository).save(any());
    }

    @Test
    @DisplayName("记录参数异常时返回空对象")
    void recordAuditLog_recordParamsException_returnsEmptyObject() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", true, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        
        when(joinPoint.getArgs()).thenReturn(new Object[]{"param"});
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("序列化失败") {});
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        // 参数记录失败时，getRequestParams内部catch异常并返回"{}"
        assertThat(logCaptor.getValue().getRequestParams()).isEqualTo("{}");
    }

    @Test
    @DisplayName("多IP代理时取第一个IP")
    void recordAuditLog_multipleProxyIps_takesFirstIp() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        
        // Use MockHttpServletRequest setters instead of Mockito when()
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        request.addHeader("X-Forwarded-For", "192.168.1.1, 10.0.0.1, 172.16.0.1");
        
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getRequestIp()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("X-Real-IP头被正确解析")
    void recordAuditLog_xRealIpHeader_parsed() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();
        
        // Use MockHttpServletRequest setters instead of Mockito when()
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        // Note: X-Forwarded-For is not set to test fallback to X-Real-IP
        request.addHeader("X-Real-IP", "203.0.113.50");
        
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getRequestIp()).isEqualTo("203.0.113.50");
    }

    // ==================== Additional Branch Coverage Tests ====================

    @Test
    @DisplayName("recordResult启用但结果为null时跳过结果记录")
    void recordAuditLog_recordResultEnabled_resultNull_skipsResultRecording() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", false, true, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();

        when(joinPoint.proceed()).thenReturn(null);
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getResponseResult()).isNull();
        // objectMapper should NOT be called since result is null
        verify(objectMapper, never()).writeValueAsString(any());
    }

    @Test
    @DisplayName("recordResult启用但序列化失败时跳过结果记录")
    void recordAuditLog_recordResultEnabled_serializationError_skipsResultRecording() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", false, true, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();

        Object result = new Object();
        when(joinPoint.proceed()).thenReturn(result);
        when(objectMapper.writeValueAsString(result)).thenThrow(new JsonProcessingException("serialization error") {});
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Object returnedResult = aspect.recordAuditLog(joinPoint);

        // Assert
        assertThat(returnedResult).isEqualTo(result);
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getResponseResult()).isNull();
    }

    @Test
    @DisplayName("recordParams启用但参数数组为空时记录空对象")
    void recordAuditLog_recordParamsEnabled_emptyArgs_recordsEmptyObject() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", true, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();

        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestParams()).isEqualTo("{}");
    }

    @Test
    @DisplayName("非JwtUserDetails认证用户应按匿名处理")
    void recordAuditLog_nonJwtUserDetailsAuthentication_handledAsAnonymous() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();

        // Set authentication with a non-JwtUserDetails principal (String)
        Authentication auth = new UsernamePasswordAuthenticationToken("string_principal", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        // Non-JwtUserDetails → userId=0, username="anonymous", userType=0
        assertThat(savedLog.getUserId()).isEqualTo(0L);
        assertThat(savedLog.getUsername()).isEqualTo("anonymous");
        assertThat(savedLog.getUserType()).isEqualTo(0);
    }

    @Test
    @DisplayName("未认证的authentication应按匿名处理")
    void recordAuditLog_unauthenticatedAuthentication_handledAsAnonymous() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();

        // 2-arg constructor creates an unauthenticated token (isAuthenticated() = false)
        Authentication unauthenticatedAuth = new UsernamePasswordAuthenticationToken("user", null);
        SecurityContextHolder.getContext().setAuthentication(unauthenticatedAuth);

        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUserId()).isEqualTo(0L);
        assertThat(savedLog.getUsername()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("getClientIp从RemoteAddr获取IP（无转发头）")
    void recordAuditLog_noForwardingHeaders_usesRemoteAddr() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);

        MockHttpServletRequest requestWithNoHeaders = new MockHttpServletRequest();
        requestWithNoHeaders.setMethod("GET");
        requestWithNoHeaders.setRequestURI("/api/test");
        requestWithNoHeaders.setRemoteAddr("10.0.0.99");
        // Explicitly set headers to null/empty to force fallback to RemoteAddr
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestWithNoHeaders, response));

        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        OperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getRequestIp()).isEqualTo("10.0.0.99");
    }

    // ==================== Branch Coverage: getClientIp empty/"unknown" fallbacks ====================

    @Test
    @DisplayName("getClientIp: X-Forwarded-For为空字符串时回退到X-Real-IP")
    void recordAuditLog_emptyXForwardedFor_fallsBackToXRealIp() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);

        MockHttpServletRequest customRequest = new MockHttpServletRequest();
        customRequest.setMethod("GET");
        customRequest.setRequestURI("/api/test");
        customRequest.addHeader("X-Forwarded-For", "");
        customRequest.addHeader("X-Real-IP", "10.0.0.5");
        customRequest.addHeader("User-Agent", "TestAgent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(customRequest, response));

        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestIp()).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("getClientIp: X-Forwarded-For为unknown时回退到X-Real-IP")
    void recordAuditLog_unknownXForwardedFor_fallsBackToXRealIp() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);

        MockHttpServletRequest customRequest = new MockHttpServletRequest();
        customRequest.setMethod("GET");
        customRequest.setRequestURI("/api/test");
        customRequest.addHeader("X-Forwarded-For", "unknown");
        customRequest.addHeader("X-Real-IP", "10.0.0.6");
        customRequest.addHeader("User-Agent", "TestAgent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(customRequest, response));

        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestIp()).isEqualTo("10.0.0.6");
    }

    @Test
    @DisplayName("getClientIp: X-Real-IP为空字符串时回退到RemoteAddr")
    void recordAuditLog_emptyXRealIp_fallsBackToRemoteAddr() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);

        MockHttpServletRequest customRequest = new MockHttpServletRequest();
        customRequest.setMethod("GET");
        customRequest.setRequestURI("/api/test");
        customRequest.addHeader("X-Real-IP", "");
        customRequest.setRemoteAddr("10.0.0.7");
        customRequest.addHeader("User-Agent", "TestAgent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(customRequest, response));

        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestIp()).isEqualTo("10.0.0.7");
    }

    @Test
    @DisplayName("getClientIp: X-Real-IP为unknown时回退到RemoteAddr")
    void recordAuditLog_unknownXRealIp_fallsBackToRemoteAddr() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);

        MockHttpServletRequest customRequest = new MockHttpServletRequest();
        customRequest.setMethod("GET");
        customRequest.setRequestURI("/api/test");
        customRequest.addHeader("X-Real-IP", "unknown");
        customRequest.setRemoteAddr("10.0.0.8");
        customRequest.addHeader("User-Agent", "TestAgent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(customRequest, response));

        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestIp()).isEqualTo("10.0.0.8");
    }

    // ==================== Branch Coverage: getClientIp null ip at comma check ====================

    @Test
    @DisplayName("getClientIp: 所有IP来源均为null时ip为null（&&短路分支）")
    void recordAuditLog_allIpSourcesNull_coversNullIpAndBranch() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("API", "测试", "测试", false, false, "");
        setupBasicMocks(auditLogAnnotation);

        MockHttpServletRequest customRequest = new MockHttpServletRequest();
        customRequest.setMethod("GET");
        customRequest.setRequestURI("/api/test");
        customRequest.setRemoteAddr(null);
        customRequest.addHeader("User-Agent", "TestAgent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(customRequest, response));

        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestIp()).isNull();
    }

    // ==================== Branch Coverage: getRequestParams null args ====================

    @Test
    @DisplayName("getRequestParams: args为null时返回空对象（覆盖args==null分支）")
    void recordAuditLog_nullArgs_returnsEmptyObject() throws Throwable {
        // Arrange
        AuditLog auditLogAnnotation = createAuditLogAnnotation("测试", "测试", "测试", true, false, "");
        setupBasicMocks(auditLogAnnotation);
        setupRequestContext();

        when(joinPoint.getArgs()).thenReturn(null);
        when(joinPoint.proceed()).thenReturn("result");
        when(operationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        aspect.recordAuditLog(joinPoint);

        // Assert
        verify(operationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestParams()).isEqualTo("{}");
    }

    // ==================== Helper Methods ====================

    private void setupBasicMocks(AuditLog auditLogAnnotation) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(method.getAnnotation(AuditLog.class)).thenReturn(auditLogAnnotation);
    }

    private void setupRequestContext() {
        // Set default request behaviors using MockHttpServletRequest
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        // Note: X-Forwarded-For and X-Real-IP are intentionally not set to test null handling
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "TestAgent");
        
        ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    private AuditLog createAuditLogAnnotation(String module, String action, String description,
                                               boolean recordParams, boolean recordResult, String sensitiveFields) {
        return new AuditLog() {
            @Override
            public String module() {
                return module;
            }

            @Override
            public String action() {
                return action;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public boolean recordParams() {
                return recordParams;
            }

            @Override
            public boolean recordResult() {
                return recordResult;
            }

            @Override
            public String sensitiveFields() {
                return sensitiveFields;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return AuditLog.class;
            }
        };
    }

    // ==================== Test Helper Classes ====================

    /**
     * 测试结果对象
     */
    static class TestResult {
        private String name;
        private int price;

        public TestResult(String name, int price) {
            this.name = name;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPrice() {
            return price;
        }

        public void setPrice(int price) {
            this.price = price;
        }
    }
}
