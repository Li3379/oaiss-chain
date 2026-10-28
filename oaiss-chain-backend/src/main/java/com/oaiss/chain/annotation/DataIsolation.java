package com.oaiss.chain.annotation;

import java.lang.annotation.*;

/**
 * 数据隔离注解
 * Data Isolation Annotation
 * 
 * <p>标注在方法上，表示该方法需要进行企业数据隔离检查</p>
 * <p>Marks methods that require enterprise data isolation checks</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * &#64;DataIsolation
 * public CarbonReport getReport(Long reportId) {
 *     // 会自动检查报告是否属于当前用户的企业
 * }
 * </pre>
 * 
 * @author OAISS Team
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataIsolation {

    /**
     * 是否启用数据隔离
     * 默认启用
     */
    boolean enabled() default true;

    /**
     * 是否跳过管理员
     * 管理员默认跳过数据隔离检查
     */
    boolean skipAdmin() default true;

    /**
     * 需要检查的资源参数名称
     * 例如：reportId, enterpriseId
     * 如果指定，将从方法参数中获取该值进行检查
     */
    String resourceIdParam() default "";

    /**
     * 资源类型
     * 用于日志记录和错误提示
     */
    String resourceType() default "资源";
}
