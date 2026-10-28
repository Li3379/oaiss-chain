package com.oaiss.chain.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SecurityStartupValidator 单元测试
 * Tests for C2/C3: Secret validation on startup
 */
class SecurityStartupValidatorTest {

    private static final String STRONG_JWT_SECRET =
            "a-very-strong-and-unique-jwt-secret-key-that-is-at-least-256-bits";
    private static final String VALID_RSA_KEK =
            Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());

    private Environment environment;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
    }

    @Test
    @DisplayName("生产环境+弱JWT密钥应抛出SecurityException")
    void validateOnStartup_productionWithWeakJwtSecret_shouldThrow() {
        // Given: production profile with weak JWT secret
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret",
                "oaiss-chain-dev-jwt-secret-key-must-be-at-least-256-bits-long");
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "https://app.example.com");
        setField(validator, "fabricCaEnabled", false);
        setField(validator, "requireOpsSecrets", false);

        // When & Then
        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+弱数据库密码应抛出SecurityException")
    void validateOnStartup_productionWithWeakDbPassword_shouldThrow() {
        // Given: production profile with weak DB password
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "dbPassword", "123456");

        // When & Then
        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+强密钥应通过校验")
    void validateOnStartup_productionWithStrongSecrets_shouldPass() {
        // Given: production profile with strong secrets
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);

        // When & Then - should not throw
        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+弱JWT密钥应仅警告不阻止启动")
    void validateOnStartup_devWithWeakJwtSecret_shouldOnlyWarn() {
        // Given: dev profile with weak JWT secret
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret",
                "dev-only-jwt-secret-key-must-be-at-least-256-bits-long-for-hmac-sha");
        setField(validator, "dbPassword", "123456");
        setField(validator, "redisPassword", "");
        setField(validator, "minioAccessKey", "minioadmin");
        setField(validator, "minioSecretKey", "minioadmin");
        setField(validator, "rsaKek", "not-base64");
        setField(validator, "allowedOrigins", "http://localhost:5173");

        // When & Then - should NOT throw, just warn
        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("无profile+弱密钥应仅警告不阻止启动")
    void validateOnStartup_noProfileWithWeakSecrets_shouldOnlyWarn() {
        // Given: no active profile with weak secrets
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret",
                "oaiss-chain-dev-jwt-secret-key-must-be-at-least-256-bits-long");
        setField(validator, "dbPassword", "123456");
        setField(validator, "redisPassword", "");
        setField(validator, "minioAccessKey", "minioadmin");
        setField(validator, "minioSecretKey", "minioadmin");
        setField(validator, "rsaKek", "not-base64");
        setField(validator, "allowedOrigins", "http://localhost:5173");

        // When & Then - should NOT throw
        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+弱密码password应抛出SecurityException")
    void validateOnStartup_productionWithPasswordAsDbPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "dbPassword", "password");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+弱MinIO凭证应抛出SecurityException")
    void validateOnStartup_productionWithWeakMinioCredentials_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "minioAccessKey", "minioadmin");
        setField(validator, "minioSecretKey", "minioadmin");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+模板占位符凭证应抛出SecurityException")
    void validateOnStartup_productionWithPlaceholderCredentials_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "dbPassword", "replace_with_strong_database_password");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+弱MinIO凭证应仅警告不阻止启动")
    void validateOnStartup_devWithWeakMinioCredentials_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "minioadmin");
        setField(validator, "minioSecretKey", "minioadmin");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+空JWT密钥应抛出SecurityException")
    void validateOnStartup_productionWithBlankJwtSecret_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "jwtSecret", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+短JWT密钥应抛出SecurityException")
    void validateOnStartup_productionWithShortJwtSecret_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "jwtSecret", "short-secret");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+空Redis密码应抛出SecurityException")
    void validateOnStartup_productionWithBlankRedisPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "redisPassword", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+无效RSA_KEK应抛出SecurityException")
    void validateOnStartup_productionWithInvalidRsaKek_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "rsaKek", "not-base64");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+长度错误RSA_KEK应抛出SecurityException")
    void validateOnStartup_productionWithWrongLengthRsaKek_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "rsaKek", Base64.getEncoder().encodeToString("too-short".getBytes()));

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+CORS包含localhost应抛出SecurityException")
    void validateOnStartup_productionWithLocalhostCors_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "allowedOrigins", "https://app.example.com,http://localhost:5173");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+Fabric CA使用HTTP应抛出SecurityException")
    void validateOnStartup_productionWithHttpFabricCa_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "fabricCaEnabled", true);
        setField(validator, "fabricCaEndpoint", "http://ca.org1.example.com:7054");
        setField(validator, "fabricCaAdminPassword", "strongFabricPassword123!");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+要求运维密钥但Grafana密码为空应抛出SecurityException")
    void validateOnStartup_productionWithRequiredOpsSecretsBlankGrafanaPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "requireOpsSecrets", true);
        setField(validator, "grafanaAdminPassword", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+ML_SERVICE_SECRET为空应抛出SecurityException")
    void validateOnStartup_productionWithBlankMlServiceSecret_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "mlServiceSecret", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+ML_SERVICE_SECRET为占位值应抛出SecurityException")
    void validateOnStartup_productionWithPlaceholderMlServiceSecret_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "mlServiceSecret", "replace_with_internal_ml_service_secret");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境缺少 fabric profile 应阻止启动")
    void validateOnStartup_productionWithoutFabricProfile_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("staging 环境缺少 fabric profile 应阻止启动")
    void validateOnStartup_stagingWithoutFabricProfile_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"staging"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("FABRIC_ENABLED 为 true 但未启用 fabric profile 应阻止启动")
    void validateOnStartup_fabricEnabledWithoutFabricProfile_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("staging + fabric + 强密钥应通过校验")
    void validateOnStartup_stagingWithFabricProfile_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"staging", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+空ML_SERVICE_SECRET应仅警告不阻止启动")
    void validateOnStartup_devWithBlankMlServiceSecret_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "mlServiceSecret", "");
        setField(validator, "fabricEnabled", false);

        // Should NOT throw — just warn via log
        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+CORS包含非URL格式值应抛出SecurityException")
    void validateOnStartup_productionWithInvalidCorsFormat_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "allowedOrigins", "not-a-valid-url, https://app.example.com");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+CORS包含无协议主机应抛出SecurityException")
    void validateOnStartup_productionWithCorsMissingScheme_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "allowedOrigins", "app.example.com");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    private void setStrongProductionFields(SecurityStartupValidator validator) {
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "https://app.example.com");
        setField(validator, "fabricEnabled", false);
        setField(validator, "fabricCaEnabled", false);
        setField(validator, "fabricCaEndpoint", "");
        setField(validator, "fabricCaAdminPassword", "");
        setField(validator, "mlServiceSecret", "strong-ml-service-secret");
        setField(validator, "requireOpsSecrets", false);
        setField(validator, "grafanaAdminPassword", "");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    // ==================== Additional coverage tests ====================

    @Test
    @DisplayName("生产环境+弱Redis密码应抛出SecurityException")
    void validateOnStartup_productionWithWeakRedisPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "redisPassword", "123456");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+弱Grafana密码(运维密钥要求)应抛出SecurityException")
    void validateOnStartup_productionWithWeakGrafanaPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "requireOpsSecrets", true);
        setField(validator, "grafanaAdminPassword", "admin");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("运维密钥不要求时Grafana密码弱应通过校验")
    void validateOnStartup_opsSecretsNotRequired_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "requireOpsSecrets", false);
        setField(validator, "grafanaAdminPassword", "admin");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+弱Redis密码应仅警告")
    void validateOnStartup_devWithWeakRedisPassword_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "123456");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+无效RSA_KEK应仅警告")
    void validateOnStartup_devWithInvalidRsaKek_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", "not-base64");
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+长度错误RSA_KEK应仅警告")
    void validateOnStartup_devWithWrongLengthRsaKek_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", Base64.getEncoder().encodeToString("too-short".getBytes()));
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+CORS空白应仅警告")
    void validateOnStartup_devWithBlankCors_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+空白CORS应抛出SecurityException")
    void validateOnStartup_productionWithBlankCors_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "allowedOrigins", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+CORS包含127.0.0.1应抛出SecurityException")
    void validateOnStartup_productionWith127_0_0_1Cors_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "allowedOrigins", "https://app.example.com,http://127.0.0.1:8080");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("fabric profile未启用时Fabric CA校验应跳过")
    void validateOnStartup_fabricProfileNotActive_fabricCaSkipped() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "fabricCaEnabled", true);
        setField(validator, "fabricCaEndpoint", "http://ca.example.com");
        setField(validator, "fabricCaAdminPassword", "");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("Fabric CA未启用时应跳过校验")
    void validateOnStartup_fabricCaNotEnabled_shouldSkip() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "fabricCaEnabled", false);
        setField(validator, "fabricCaAdminPassword", "");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+Fabric CA空白密码应仅警告")
    void validateOnStartup_devWithBlankFabricCaPassword_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "fabricCaEnabled", true);
        setField(validator, "fabricCaEndpoint", "http://ca.example.com");
        setField(validator, "fabricCaAdminPassword", "");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+Fabric CA弱密码应仅警告")
    void validateOnStartup_devWithWeakFabricCaPassword_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "fabricCaEnabled", true);
        setField(validator, "fabricCaEndpoint", "http://ca.example.com");
        setField(validator, "fabricCaAdminPassword", "admin");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+空白MinIO凭证应仅警告")
    void validateOnStartup_devWithBlankMinioCredentials_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "");
        setField(validator, "minioSecretKey", "");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+空白MinIO凭证应抛出SecurityException")
    void validateOnStartup_productionWithBlankMinioCredentials_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "minioAccessKey", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+空白RSA_KEK应抛出SecurityException")
    void validateOnStartup_productionWithBlankRsaKek_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "rsaKek", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+Fabric CA弱密码应抛出SecurityException")
    void validateOnStartup_productionWithWeakFabricCaPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "fabricCaEnabled", true);
        setField(validator, "fabricCaEndpoint", "https://ca.org1.example.com:7054");
        setField(validator, "fabricCaAdminPassword", "admin");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+Fabric CA空白密码应抛出SecurityException")
    void validateOnStartup_productionWithBlankFabricCaPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "fabricCaEnabled", true);
        setField(validator, "fabricCaEndpoint", "https://ca.org1.example.com:7054");
        setField(validator, "fabricCaAdminPassword", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("JWT secret使用change_me前缀应被识别为占位符")
    void validateOnStartup_changeMePrefixJwtSecret_shouldBeDetectedAsPlaceholder() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "jwtSecret", "change_me_to_a_random_256bit_secret");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("DB密码使用placeholder值应被检测为占位符")
    void validateOnStartup_placeholderDbPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "dbPassword", "placeholder");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("Redis密码使用todo值应被检测为占位符")
    void validateOnStartup_todoRedisPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "redisPassword", "todo");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("MinIO凭证使用dummy值应被检测为占位符")
    void validateOnStartup_dummyMinioCredentials_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "minioAccessKey", "dummy");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+Grafana空白密码(运维密钥要求)应仅警告")
    void validateOnStartup_devWithBlankGrafanaPassword_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "requireOpsSecrets", true);
        setField(validator, "grafanaAdminPassword", "");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+Grafana弱密码(运维密钥要求)应仅警告")
    void validateOnStartup_devWithWeakGrafanaPassword_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "requireOpsSecrets", true);
        setField(validator, "grafanaAdminPassword", "admin");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+空白DB密码应抛出SecurityException")
    void validateOnStartup_productionWithBlankDbPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "dbPassword", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+有效HTTPS CORS应通过校验")
    void validateOnStartup_productionWithValidHttpsCors_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "allowedOrigins", "https://app.example.com,https://api.example.com");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    // ==================== Additional Branch Coverage ====================

    @Test
    @DisplayName("JWT secret使用change_me前缀且不在弱密码集中应被识别为占位符")
    void validateOnStartup_changeMePrefixJwtNotInWeakSet_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        // 32+ bytes, starts with "change_me" but NOT in WEAK_JWT_SECRETS
        setField(validator, "jwtSecret", "change_me_to_a_strong_random_key_for_jwt");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+非空白ML服务密钥应直接通过")
    void validateOnStartup_devWithNonBlankMlServiceSecret_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "mlServiceSecret", "my-non-blank-ml-secret");
        setField(validator, "fabricEnabled", false);

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+短JWT密钥应仅警告")
    void validateOnStartup_devWithShortJwtSecret_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", "short");
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+空白JWT密钥应仅警告")
    void validateOnStartup_devWithBlankJwtSecret_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", "");
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+空白DB密码应仅警告")
    void validateOnStartup_devWithBlankDbPassword_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+空白RSA KEK应仅警告")
    void validateOnStartup_devWithBlankRsaKek_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", "");
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+仅MinIO secretKey为空应抛出SecurityException")
    void validateOnStartup_productionWithBlankMinioSecretKeyOnly_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+MinIO secretKey为占位符应抛出SecurityException")
    void validateOnStartup_productionWithPlaceholderMinioSecretKey_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "replace_with_strong_secret_key");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+运维密钥要求+有效Grafana密码应通过")
    void validateOnStartup_productionWithOpsSecretsAndValidGrafana_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "requireOpsSecrets", true);
        setField(validator, "grafanaAdminPassword", "strongGrafanaPassword123!");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+运维密钥要求+Grafana占位符密码应抛出SecurityException")
    void validateOnStartup_productionWithPlaceholderGrafanaPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "requireOpsSecrets", true);
        setField(validator, "grafanaAdminPassword", "replace_with_grafana_password");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+HTTPS Fabric CA端点+强密码应通过")
    void validateOnStartup_productionWithHttpsFabricCaAndStrongPassword_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "fabricCaEnabled", true);
        setField(validator, "fabricCaEndpoint", "https://ca.org1.example.com:7054");
        setField(validator, "fabricCaAdminPassword", "strongFabricPassword123!");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+CORS使用http://开头应通过校验")
    void validateOnStartup_productionWithHttpCors_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "allowedOrigins", "http://app.example.com");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+强JWT密钥且非弱密码非占位符应通过JWT校验")
    void validateOnStartup_productionWithStrongJwtNotWeakNotPlaceholder_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "jwtSecret", "a-unique-random-jwt-key-1234567890abcdefghij");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("production profile名称为production应被识别为生产环境")
    void validateOnStartup_productionProfileName_shouldBeProduction() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+占位符MinIO密钥应仅警告")
    void validateOnStartup_devWithPlaceholderMinioSecretKey_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "replace_with_minio_secret");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+Fabric CA占位符密码应仅警告")
    void validateOnStartup_devWithPlaceholderFabricCaPassword_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "strongDbPassword123!");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");
        setField(validator, "fabricCaEnabled", true);
        setField(validator, "fabricCaEndpoint", "https://ca.example.com");
        setField(validator, "fabricCaAdminPassword", "replace_with_fabric_ca_pw");

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("isPlaceholderSecret: DB密码使用todo值应被检测")
    void validateOnStartup_todoDbPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "dbPassword", "todo");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("isPlaceholderSecret: Redis密码使用dummy值应被检测")
    void validateOnStartup_dummyRedisPassword_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "redisPassword", "dummy");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("isPlaceholderSecret: JWT密钥使用replace_with前缀应被检测")
    void validateOnStartup_replaceWithPrefixJwt_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "jwtSecret", "replace_with_a_strong_jwt_secret_key_for_production_12345");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境+CORS无协议格式但非localhost应抛出SecurityException")
    void validateOnStartup_productionWithCorsNoProtocolNonLocalhost_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "fabric"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setStrongProductionFields(validator);
        setField(validator, "fabricEnabled", true);
        setField(validator, "allowedOrigins", "app.example.com,https://api.example.com");

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("开发环境+占位符DB密码应仅警告")
    void validateOnStartup_devWithPlaceholderDbPassword_shouldOnlyWarn() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        SecurityStartupValidator validator = new SecurityStartupValidator(environment);
        setField(validator, "jwtSecret", STRONG_JWT_SECRET);
        setField(validator, "dbPassword", "replace_with_db_password");
        setField(validator, "redisPassword", "strongRedisPassword123!");
        setField(validator, "minioAccessKey", "strong-access-key");
        setField(validator, "minioSecretKey", "strong-secret-key");
        setField(validator, "rsaKek", VALID_RSA_KEK);
        setField(validator, "allowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(validator::validateOnStartup);
    }
}
