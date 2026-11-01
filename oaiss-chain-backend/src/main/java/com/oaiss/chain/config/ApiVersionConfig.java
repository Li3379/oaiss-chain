package com.oaiss.chain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

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
     *
     * 用不可变 List 而非 public static final String[]：
     * 数组的 final 只保证引用不变，内容仍可被任何调用方改写
     * （ApiVersionConfig.SUPPORTED_VERSIONS[0] = "v9" 合法且无异常），
     * 一旦被改，版本协商会静默接受未实现的版本。List.of 创建的是不可变实例。
     */
    public static final List<String> SUPPORTED_VERSIONS = List.of("v1");
    
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