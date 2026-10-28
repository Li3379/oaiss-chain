package com.oaiss.chain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * 
 * <p>标注在Controller方法上，用于校验用户是否有访问该接口的权限</p>
 * <p>基于用户类型和API路径进行权限匹配</p>
 * 
 * <pre>
 * 示例用法:
 * &#64;RequirePermission
 * &#64;GetMapping("/admin/users")
 * public ApiResponse&lt;List&lt;User&gt;&gt; listUsers() { ... }
 * </pre>
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 权限编码（可选）
     * 如果指定，则校验用户是否拥有该具体权限
     */
    String value() default "";

    /**
     * 是否必须为管理员
     */
    boolean adminOnly() default false;

    /**
     * 是否必须为企业用户
     */
    boolean enterpriseOnly() default false;

    /**
     * 是否必须为审核员
     */
    boolean reviewerOnly() default false;

    /**
     * 是否必须为第三方机构
     */
    boolean thirdPartyOnly() default false;

    /**
     * 错误提示消息
     */
    String message() default "您没有权限执行此操作";
}
