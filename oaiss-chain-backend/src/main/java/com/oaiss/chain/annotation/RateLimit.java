package com.oaiss.chain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * 
 * <p>标注在Controller方法上，用于限制接口访问频率</p>
 * <p>基于Redis + 令牌桶算法实现分布式限流</p>
 * 
 * <pre>
 * 示例用法:
 * &#64;RateLimit(key = "login", limit = 5, period = 60)
 * &#64;PostMapping("/login")
 * public ApiResponse&lt;LoginResponse&gt; login(&#64;RequestBody LoginRequest request) { ... }
 * </pre>
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流Key（用于区分不同的限流策略）
     */
    String key() default "";

    /**
     * 限流时间窗口（秒）
     */
    int period() default 60;

    /**
     * 限流次数（在时间窗口内允许的最大请求数）
     */
    int limit() default 100;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.DEFAULT;

    /**
     * 限流提示消息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /**
         * 默认限流（全局限流）
         */
        DEFAULT,

        /**
         * 按IP限流
         */
        IP,

        /**
         * 按用户限流
         */
        USER,

        /**
         * 按IP和用户组合限流
         */
        IP_USER
    }
}
