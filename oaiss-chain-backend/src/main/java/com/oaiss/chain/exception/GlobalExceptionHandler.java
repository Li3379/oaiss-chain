package com.oaiss.chain.exception;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.constant.ErrorMessage;
import com.oaiss.chain.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * Global Exception Handler
 * 
 * 统一处理所有异常，返回标准格式的错误响应
 * 
 * @author OAISS Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常处理 ====================

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常: {} - {} - {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        
        return ResponseEntity
                .status(getHttpStatus(ex.getCode()))
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("认证异常: {} - {}", request.getRequestURI(), ex.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理授权异常
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationException(
            AuthorizationException ex, HttpServletRequest request) {
        log.warn("授权异常: {} - {}", request.getRequestURI(), ex.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    // ==================== 参数校验异常处理 ====================

    /**
     * 处理 @Valid 校验失败异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        
        log.warn("参数校验失败: {} - {}", request.getRequestURI(), errors);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, "参数校验失败", errors));
    }

    /**
     * 处理 @Validated 校验失败异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<String>>> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());
        
        log.warn("参数校验失败: {} - {}", request.getRequestURI(), errors);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, "参数校验失败", errors));
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("缺少请求参数: {} - {}", request.getRequestURI(), ex.getParameterName());
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, 
                        "缺少必要参数: " + ex.getParameterName()));
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("参数类型不匹配: {} - {}", request.getRequestURI(), ex.getName());
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, 
                        "参数类型错误: " + ex.getName()));
    }

    /**
     * 处理请求体解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("请求体解析失败: {} - {}", request.getRequestURI(), ex.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, "请求体格式错误"));
    }

    // ==================== HTTP异常处理 ====================

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("请求方法不支持: {} - {}", request.getRequestURI(), ex.getMethod());
        
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_SUPPORTED, 
                        "不支持的请求方法: " + ex.getMethod()));
    }

    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在: {}", request.getRequestURI());
        
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, ErrorMessage.RESOURCE_NOT_FOUND));
    }

    /**
     * 处理静态资源未命中异常（Spring Boot 3.2+ 对未知路径抛 NoResourceFoundException）
     *
     * <p>此前该异常落入通用分支被当作「系统异常」记 ERROR 并返回 500，
     * 使访问错误路径看起来像服务端故障；实际语义应为 404。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("请求路径不存在: {}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, ErrorMessage.RESOURCE_NOT_FOUND));
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("文件大小超限: {}", request.getRequestURI());
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.FILE_SIZE_EXCEEDED, ErrorMessage.FILE_SIZE_EXCEEDED));
    }

    // ==================== Spring Security异常处理 ====================

    /**
     * 处理认证失败异常 (Spring Security)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn("认证失败: {} - {}", request.getRequestURI(), ex.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.LOGIN_FAILED, ErrorMessage.LOGIN_FAILED));
    }

    /**
     * 处理访问拒绝异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("访问拒绝: {} - {}", request.getRequestURI(), ex.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.PERMISSION_DENIED, ErrorMessage.PERMISSION_DENIED));
    }

    // ==================== 并发冲突异常处理 ====================

    /**
     * 处理乐观锁冲突异常（@Version并发更新）
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockException(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent modification conflict: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.OPERATION_IN_PROGRESS, "数据已被其他操作修改，请刷新后重试"));
    }

    // ==================== 参数校验异常处理 ====================

    /**
     * 处理非法参数异常（如分页参数越界）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, ex.getMessage()));
    }

    // ==================== 未知异常处理 ====================

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex, HttpServletRequest request) {
        log.error("系统异常: {} - {}", request.getRequestURI(), ex.getMessage(), ex);
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.SYSTEM_ERROR, ErrorMessage.SYSTEM));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据错误码获取HTTP状态码
     */
    private HttpStatus getHttpStatus(Integer code) {
        if (code >= 2000 && code < 3000) {
            // 认证授权错误
            if (code == ErrorCode.PERMISSION_DENIED) {
                return HttpStatus.FORBIDDEN;
            }
            // 业务规则限制（如不能禁用自己的账号）返回 400
            if (code == ErrorCode.CANNOT_DISABLE_SELF
                    || code == ErrorCode.CANNOT_DELETE_SELF
                    || code == ErrorCode.CANNOT_DELETE_ADMIN
                    || code == ErrorCode.ADMIN_USER_TYPE_FORBIDDEN) {
                return HttpStatus.BAD_REQUEST;
            }
            return HttpStatus.UNAUTHORIZED;
        } else if (code >= 1000 && code < 2000) {
            // 公共错误
            if (code == ErrorCode.RESOURCE_NOT_FOUND) {
                return HttpStatus.NOT_FOUND;
            }
            return HttpStatus.BAD_REQUEST;
        } else if (code == ErrorCode.ADMISSION_REQUIRED || code == ErrorCode.QUALIFICATION_REQUIRED) {
            // 证书门禁：主体「没有资格执行该操作」，不是请求参数有误。
            // 归到 400 会误导调用方以为改参数就能过，从而无谓重试。
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
