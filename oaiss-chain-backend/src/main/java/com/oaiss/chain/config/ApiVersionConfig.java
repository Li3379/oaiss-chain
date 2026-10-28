package com.oaiss.chain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API版本管理配置
 * API Version Management Configuration
 * 
 * 支持多版本API共存，通过URL路径区分版本
 * Supports multiple API versions coexisting, distinguished by URL path
 *
 * 当前版本: v1
 * 访问路径: /api/v1/*
 *
 * @author OAISS Team
 */
@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    /**
     * 当前API版本
     */
    public static final String CURRENT_VERSION = "v1";
    
    /**
     * 支持的API版本列表
     */
    public static final String[] SUPPORTED_VERSIONS = {"v1"};
    
    /**
     * API版本前缀
     */
    public static final String API_PREFIX = "/api";
    
    /**
     * 配置路径匹配
     * Configure path matching for API versioning
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 启用后缀模式匹配（用于版本协商）
        configurer.setUseSuffixPatternMatch(false);
        // 启用尾部斜杠匹配
        configurer.setUseTrailingSlashMatch(true);
    }
}