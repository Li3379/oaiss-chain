package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.constant.ErrorMessage;
import com.oaiss.chain.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 全局异常处理器测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    // ==================== Business Exceptions ====================

    @Test
    @DisplayName("处理业务异常 - 认证授权错误码返回401")
    void handleBusinessException_AuthCode_Returns401() {
        BusinessException ex = new BusinessException(ErrorCode.LOGIN_FAILED, "登录失败");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ErrorCode.LOGIN_FAILED, response.getBody().getCode());
    }

    @Test
    @DisplayName("处理业务异常 - 权限拒绝错误码返回403")
    void handleBusinessException_PermissionDenied_Returns403() {
        BusinessException ex = new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex, request);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("处理业务异常 - 资源不存在错误码返回404")
    void handleBusinessException_ResourceNotFound_Returns404() {
        BusinessException ex = new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "不存在");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex, request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("处理业务异常 - 公共错误码返回400")
    void handleBusinessException_CommonCode_Returns400() {
        BusinessException ex = new BusinessException(ErrorCode.PARAM_ERROR, "参数错误");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("处理业务异常 - 未知错误码返回400")
    void handleBusinessException_UnknownCode_Returns400() {
        BusinessException ex = new BusinessException(9999, "未知");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("处理认证异常 - 返回401")
    void handleAuthenticationException_Returns401() {
        AuthenticationException ex = new AuthenticationException(ErrorCode.TOKEN_INVALID, "Token无效");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthenticationException(ex, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("处理授权异常 - 返回403")
    void handleAuthorizationException_Returns403() {
        AuthorizationException ex = AuthorizationException.permissionDenied();
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthorizationException(ex, request);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ==================== Validation Exceptions ====================

    @Test
    @DisplayName("处理@Valid校验失败异常")
    void handleMethodArgumentNotValidException_Returns400() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("obj", "name", "不能为空")));

        ResponseEntity<?> response = handler.handleMethodArgumentNotValidException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("处理@Validated校验失败异常")
    void handleConstraintViolationException_Returns400() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<String> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("不能为空");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<?> response = handler.handleConstraintViolationException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("处理缺少请求参数异常")
    void handleMissingServletRequestParameterException_Returns400() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("id", "Long");
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingServletRequestParameterException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("id"));
    }

    @Test
    @DisplayName("处理参数类型不匹配异常")
    void handleMethodArgumentTypeMismatchException_Returns400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentTypeMismatchException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("处理请求体解析异常")
    void handleHttpMessageNotReadableException_Returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("解析失败", (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadableException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ==================== HTTP Exceptions ====================

    @Test
    @DisplayName("处理请求方法不支持异常")
    void handleHttpRequestMethodNotSupportedException_Returns405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpRequestMethodNotSupportedException(ex, request);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
    }

    @Test
    @DisplayName("处理404异常")
    void handleNoHandlerFoundException_Returns404() {
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/api/v1/missing", null);
        ResponseEntity<ApiResponse<Void>> response = handler.handleNoHandlerFoundException(ex, request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("处理文件上传大小超限异常")
    void handleMaxUploadSizeExceededException_Returns400() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(104857600);
        ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSizeExceededException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ==================== Spring Security Exceptions ====================

    @Test
    @DisplayName("处理BadCredentialsException - 返回401")
    void handleBadCredentialsException_Returns401() {
        BadCredentialsException ex = new BadCredentialsException("密码错误");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBadCredentialsException(ex, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("处理AccessDeniedException - 返回403")
    void handleAccessDeniedException_Returns403() {
        AccessDeniedException ex = new AccessDeniedException("拒绝访问");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDeniedException(ex, request);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ==================== Unknown Exception ====================

    @Test
    @DisplayName("处理未知异常 - 返回500")
    void handleException_Returns500() {
        Exception ex = new RuntimeException("未知错误");
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex, request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.SYSTEM_ERROR, response.getBody().getCode());
    }
}
