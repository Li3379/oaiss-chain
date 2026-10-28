package com.oaiss.chain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA审计配置类
 * JPA Auditing Configuration
 * 
 * 将@EnableJpaAuditing从主应用类移至此独立配置类，
 * 以避免@WebMvcTest测试时因缺少JPA实体而导致的ApplicationContext加载失败。
 * 
 * @author OAISS Team
 * @version 1.0.0
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
    // JPA Auditing configuration
    // This class is only loaded when JPA is available
}
