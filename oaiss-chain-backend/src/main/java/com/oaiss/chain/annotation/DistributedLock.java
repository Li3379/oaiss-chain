package com.oaiss.chain.annotation;

import java.lang.annotation.*;

/**
 * 分布式锁注解
 * Distributed Lock Annotation
 * 
 * <p>标注在方法上，自动获取分布式锁防止重复提交</p>
 * <p>Marks methods to automatically acquire distributed lock to prevent duplicate submissions</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * &#64;DistributedLock(key = "'report:' + #reportId", expireTime = 30)
 * public void submitReport(Long reportId) {
 *     // 方法执行期间会持有分布式锁
 * }
 * </pre>
 * 
 * @author OAISS Team
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁键表达式（SpEL）
     * Lock key expression (SpEL)
     * 
     * <p>支持 SpEL 表达式，可以使用方法参数</p>
     * <p>例如：'user:' + #userId</p>
     */
    String key();

    /**
     * 锁过期时间（秒）
     * Lock expire time in seconds
     */
    long expireTime() default 30;

    /**
     * 等待获取锁的时间（毫秒）
     * Wait time to acquire lock in milliseconds
     * 
     * <p>0 表示不等待，直接失败</p>
     */
    long waitTime() default 0;

    /**
     * 获取锁失败时的错误消息
     * Error message when lock acquisition fails
     */
    String errorMessage() default "操作正在处理中，请勿重复提交";
}
