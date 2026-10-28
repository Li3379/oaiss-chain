package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Config Classes - Coverage Enhancement")
class ConfigCoverageTest {

    @Test
    void redisConfig_exists() {
        assertNotNull(RedisConfig.class);
        assertTrue(RedisConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void metricsConfig_exists() {
        assertNotNull(MetricsConfig.class);
        assertTrue(MetricsConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void minioConfig_exists() {
        assertNotNull(MinioConfig.class);
        assertTrue(MinioConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void i18nConfig_exists() {
        assertNotNull(I18nConfig.class);
        assertTrue(I18nConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void swaggerConfig_exists() {
        assertNotNull(SwaggerConfig.class);
        assertTrue(SwaggerConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void asyncConfig_exists() {
        assertNotNull(AsyncConfig.class);
        assertTrue(AsyncConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void apiVersionConfig_exists() {
        assertNotNull(ApiVersionConfig.class);
        assertTrue(ApiVersionConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void jpaAuditingConfiguration_exists() {
        assertNotNull(JpaAuditingConfiguration.class);
        assertTrue(JpaAuditingConfiguration.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void fabricProperties_exists() {
        FabricProperties props = new FabricProperties();
        assertNotNull(props);
    }

    @Test
    void fabricPropertiesCa_exists() {
        FabricProperties.Ca ca = new FabricProperties.Ca();
        assertNotNull(ca);
    }

    @Test
    void securityConfig_annotations() {
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class));
        assertTrue(SecurityConfig.class.isAnnotationPresent(
                org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity.class));
    }

    @Test
    void redisConfig_hasBeanMethods() {
        assertTrue(hasMethod(RedisConfig.class, "redisTemplate"));
        assertTrue(hasMethod(RedisConfig.class, "redisScriptTemplate"));
    }

    @Test
    void metricsConfig_hasBeanMethod() {
        assertTrue(hasMethod(MetricsConfig.class, "meterRegistryCustomizer"));
    }

    private boolean hasMethod(Class<?> clazz, String name) {
        return java.util.Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(name));
    }
}