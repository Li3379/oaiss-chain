package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;


/**
 * 操作日志实体
 *
 * <p>用于记录用户操作审计日志</p>
 *
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "operation_log", indexes = {
    @Index(name = "idx_operation_log_user_id", columnList = "user_id"),
    @Index(name = "idx_operation_log_created_at", columnList = "created_at"),
    @Index(name = "idx_operation_log_module", columnList = "module")
})
public class OperationLog extends BaseEntity {

    /**
     * 操作用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 用户名
     */
    @Column(name = "username", length = 50)
    private String username;

    /**
     * 用户类型
     */
    @Column(name = "user_type", nullable = false)
    private Integer userType;

    /**
     * 操作模块
     */
    @Column(name = "module", length = 50, nullable = false)
    private String module;

    /**
     * 操作类型
     */
    @Column(name = "action", length = 50, nullable = false)
    private String action;

    /**
     * 操作描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 请求方法（GET/POST/PUT/DELETE）
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /**
     * 请求路径
     */
    @Column(name = "request_url", length = 255)
    private String requestUrl;

    /**
     * 请求IP
     */
    @Column(name = "request_ip", length = 50)
    private String requestIp;

    /**
     * 请求参数（JSON格式）
     */
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    /**
     * 响应结果（JSON格式）
     */
    @Column(name = "response_result", columnDefinition = "TEXT")
    private String responseResult;

    /**
     * 操作状态（1-成功，2-失败）
     */
    @Column(name = "status", nullable = false)
    private Integer status;

    /**
     * 错误消息
     */
    @Column(name = "error_msg", length = 1000)
    private String errorMsg;

    /**
     * 执行时间（毫秒）
     */
    @Column(name = "execution_time")
    private Long executionTime;

    /**
     * 浏览器信息
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
