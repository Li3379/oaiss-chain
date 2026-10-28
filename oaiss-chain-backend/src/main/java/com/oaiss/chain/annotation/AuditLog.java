package com.oaiss.chain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计日志注解
 * 
 * <p>标注在Controller方法上，用于记录用户操作日志</p>
 * <p>通过AOP切面自动捕获操作信息并持久化到数据库</p>
 * 
 * <pre>
 * 示例用法:
 * &#64;AuditLog(module = "用户管理", action = "创建用户", description = "创建新用户账号")
 * &#64;PostMapping("/users")
 * public ApiResponse&lt;User&gt; createUser(&#64;RequestBody UserDTO dto) { ... }
 * </pre>
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 操作模块
     */
    String module();

    /**
     * 操作类型（如：新增、修改、删除、查询、导出等）
     */
    String action();

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录请求参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录响应结果
     */
    boolean recordResult() default false;

    /**
     * 敏感字段（不记录，多个用逗号分隔）
     */
    String sensitiveFields() default "password,token,secret";
}
