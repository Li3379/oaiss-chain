package com.oaiss.chain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一接口响应包装类
 * Unified API Response Wrapper
 *
 * @param <T> 响应数据类型
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 响应状态码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 响应元数据
     */
    private ResponseMeta meta;

    /**
     * 响应元数据内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMeta {
        /**
         * 请求ID，用于链路追踪
         */
        private String requestId;

        /**
         * 响应时间戳
         */
        private LocalDateTime timestamp;

        /**
         * 分页信息（可选）
         */
        private PaginationInfo pagination;
    }

    /**
     * 分页信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationInfo {
        /**
         * 当前页码
         */
        private Integer page;

        /**
         * 每页大小
         */
        private Integer size;

        /**
         * 总记录数
         */
        private Long total;

        /**
         * 总页数
         */
        private Integer totalPages;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .code(200)
                .message("操作成功")
                .meta(buildMeta())
                .build();
    }

    /**
     * 成功响应（单对象）
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("操作成功")
                .data(data)
                .meta(buildMeta())
                .build();
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .meta(buildMeta())
                .build();
    }

    /**
     * 成功响应（分页）
     */
    public static <T> ApiResponse<T> success(T data, Integer page, Integer size, Long total) {
        int totalPages = (int) Math.ceil((double) total / size);
        
        return ApiResponse.<T>builder()
                .code(200)
                .message("操作成功")
                .data(data)
                .meta(ResponseMeta.builder()
                        .requestId(generateRequestId())
                        .timestamp(LocalDateTime.now())
                        .pagination(PaginationInfo.builder()
                                .page(page)
                                .size(size)
                                .total(total)
                                .totalPages(totalPages)
                                .build())
                        .build())
                .build();
    }

    /**
     * 错误响应
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .meta(buildMeta())
                .build();
    }

    /**
     * 错误响应（带详细信息）
     */
    public static <T> ApiResponse<T> error(Integer code, String message, T errorDetails) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .data(errorDetails)
                .meta(buildMeta())
                .build();
    }

    // ==================== 私有辅助方法 ====================

    private static ResponseMeta buildMeta() {
        return ResponseMeta.builder()
                .requestId(generateRequestId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static String generateRequestId() {
        return "req_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
}
