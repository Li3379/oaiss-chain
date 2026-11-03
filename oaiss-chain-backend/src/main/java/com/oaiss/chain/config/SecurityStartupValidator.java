package com.oaiss.chain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * 安全启动校验器
 * 检测缺失/弱密钥和默认密码，在生产环境下阻止启动
 *
 * @author OAISS Team
 */
@Slf4j
@Component
public class SecurityStartupValidator {

    private static final Set<String> WEAK_JWT_SECRETS = Set.of(
            "oaiss-chain-dev-jwt-secret-key-must-be-at-least-256-bits-long",
            "dev-only-jwt-secret-key-must-be-at-least-256-bits-long-for-hmac-sha",
            "oaiss-chain-jwt-secret-key-must-be-at-least-256-bits-long",
            "change_me_to_a_random_256bit_secret"
    );

    private static final Set<String> WEAK_DB_PASSWORDS = Set.of(
            "123456", "password", "root", "admin", "test", "change_me_db_password"
    );

    private static final Set<String> WEAK_MINIO_CREDENTIALS = Set.of(
            "minioadmin", "minio", "admin", "accesskey", "secretkey",
            "change_me_minio_access_key", "change_me_minio_secret_key"
    );

    private static final Set<String> WEAK_REDIS_PASSWORDS = Set.of(
            "123456", "password", "redis", "admin", "test", "change_me_redis_password"
    );

    private static final Set<String> WEAK_FABRIC_PASSWORDS = Set.of(
            "admin", "adminpw", "password", "change_me_fabric_ca_password"
    );

    private static final Set<String> WEAK_GRAFANA_PASSWORDS = Set.of(
            "admin", "admin123", "password", "change_me_grafana_password"
    );

    private static final Set<String> PRODUCTION_PROFILES = Set.of(
            "prod", "production", "staging"
    );

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${minio.access-key:}")
    private String minioAccessKey;

    @Value("${minio.secret-key:}")
    private String minioSecretKey;

    @Value("${RSA_KEK:}")
    private String rsaKek;

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${fabric.ca.enabled:false}")
    private boolean fabricCaEnabled;

    @Value("${fabric.ca.endpoint:}")
    private String fabricCaEndpoint;

    @Value("${fabric.ca.admin-password:}")
    private String fabricCaAdminPassword;

    @Value("${fabric.enabled:false}")
    private boolean fabricEnabled;

    @Value("${ml.service.secret:}")
    private String mlServiceSecret;

    @Value("${app.security.require-ops-secrets:false}")
    private boolean requireOpsSecrets;

    @Value("${ops.grafana.admin-password:}")
    private String grafanaAdminPassword;

    /**
     * 显式逃生门：允许在没有 fabric profile 的环境下启动。
     *
     * <p><b>为什么需要它</b>：{@code MockBlockchainService} 用 {@code @Profile("!fabric")} 生效，
     * 意味着只要没激活 fabric 就会静默用假链——历史上这条路径往库里写入了 11 份
     * {@code tx_mock_} 哈希的报告，而对账页把它们当成「已上链」。默认必须拒绝启动，
     * 否则「全真」只是一句口号。</p>
     *
     * <p><b>为什么不能一刀切</b>：单元测试用 H2 跑，没有 Fabric 网络可用；
     * 本地开发者做业务逻辑调试也不需要起整套 Fabric。所以保留一个**显式**开关：
     * 必须由人主动设置 {@code OAISS_ALLOW_MOCK_CHAIN=true} 才放行，并且此时
     * Mock 服务不再产出假哈希（报告停留在「审核通过」而非「已上链」）。</p>
     */
    @Value("${OAISS_ALLOW_MOCK_CHAIN:false}")
    private boolean allowMockChain;

    private final Environment environment;

    public SecurityStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void validateOnStartup() {
        boolean isProduction = isProductionProfile();

        validateJwtSecret(isProduction);
        validateDbPassword(isProduction);
        validateRedisPassword(isProduction);
        validateMinioCredentials(isProduction);
        validateRsaKek(isProduction);
        validateCorsOrigins(isProduction);
        validateFabricProfileAlignment(isProduction);
        validateFabricCa(isProduction);
        validateMlServiceSecret(isProduction);
        validateOpsSecrets(isProduction);

        if (isProduction) {
            log.info("Security startup validation PASSED for production environment");
        }
    }

    private void validateJwtSecret(boolean isProduction) {
        if (isBlank(jwtSecret)) {
            failOrWarn(isProduction, "SECURITY WARNING: JWT secret is blank. "
                    + "Set JWT_SECRET to a strong random key (at least 32 bytes).");
            return;
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            failOrWarn(isProduction, "SECURITY WARNING: JWT secret is shorter than 32 bytes. "
                    + "Set JWT_SECRET to a strong random key (at least 32 bytes).");
            return;
        }
        if (WEAK_JWT_SECRETS.contains(jwtSecret) || isPlaceholderSecret(jwtSecret)) {
            String message = "SECURITY WARNING: JWT secret is using a known default value. "
                    + "Set JWT_SECRET environment variable to a strong random key (at least 256 bits).";
            failOrWarn(isProduction, message);
        }
    }

    private void validateDbPassword(boolean isProduction) {
        if (isBlank(dbPassword)) {
            failOrWarn(isProduction, "SECURITY WARNING: Database password is blank. "
                    + "Set DB_PASSWORD environment variable to a strong password.");
            return;
        }
        if (WEAK_DB_PASSWORDS.contains(dbPassword) || isPlaceholderSecret(dbPassword)) {
            String message = "SECURITY WARNING: Database password is using a weak default value. "
                    + "Set DB_PASSWORD environment variable to a strong password.";
            failOrWarn(isProduction, message);
        }
    }

    private void validateRedisPassword(boolean isProduction) {
        if (isBlank(redisPassword)) {
            failOrWarn(isProduction, "SECURITY WARNING: Redis password is blank. "
                    + "Set REDIS_PASSWORD environment variable to a strong password.");
            return;
        }
        if (WEAK_REDIS_PASSWORDS.contains(redisPassword) || isPlaceholderSecret(redisPassword)) {
            failOrWarn(isProduction, "SECURITY WARNING: Redis password is using a weak default value. "
                    + "Set REDIS_PASSWORD environment variable to a strong password.");
        }
    }

    private void validateMinioCredentials(boolean isProduction) {
        if (isBlank(minioAccessKey) || isBlank(minioSecretKey)) {
            failOrWarn(isProduction, "SECURITY WARNING: MinIO credentials are blank. "
                    + "Set MINIO_ACCESS_KEY and MINIO_SECRET_KEY environment variables to strong values.");
            return;
        }
        if (WEAK_MINIO_CREDENTIALS.contains(minioAccessKey)
                || WEAK_MINIO_CREDENTIALS.contains(minioSecretKey)
                || isPlaceholderSecret(minioAccessKey)
                || isPlaceholderSecret(minioSecretKey)) {
            String message = "SECURITY WARNING: MinIO credentials are using weak default values. "
                    + "Set MINIO_ACCESS_KEY and MINIO_SECRET_KEY environment variables to strong values.";
            failOrWarn(isProduction, message);
        }
    }

    private void validateRsaKek(boolean isProduction) {
        if (isBlank(rsaKek)) {
            failOrWarn(isProduction, "SECURITY WARNING: RSA_KEK is blank. "
                    + "Generate a 32-byte AES key with: openssl rand -base64 32");
            return;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(rsaKek);
            if (keyBytes.length != 32) {
                failOrWarn(isProduction, "SECURITY WARNING: RSA_KEK must decode to exactly 32 bytes.");
            }
        } catch (IllegalArgumentException e) {
            failOrWarn(isProduction, "SECURITY WARNING: RSA_KEK is not valid Base64.");
        }
    }

    private void validateCorsOrigins(boolean isProduction) {
        if (isBlank(allowedOrigins)) {
            failOrWarn(isProduction, "SECURITY WARNING: CORS allowed origins are blank. "
                    + "Set CORS_ALLOWED_ORIGINS to the production frontend origin.");
            return;
        }
        if (isProduction && containsLocalOrigin(allowedOrigins)) {
            throw new SecurityException("FATAL: SECURITY WARNING: Production CORS_ALLOWED_ORIGINS "
                    + "must not contain localhost or 127.0.0.1.");
        }
        if (isProduction) {
            for (String origin : allowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
                    throw new SecurityException("FATAL: SECURITY WARNING: CORS origin '"
                            + trimmed + "' is not a valid URL (must start with http:// or https://).");
                }
            }
        }
    }

    private void validateFabricCa(boolean isProduction) {
        if (!isProfileActive("fabric")) {
            return;
        }
        if (!fabricCaEnabled) {
            return;
        }
        if (isProduction && !isBlank(fabricCaEndpoint) && !fabricCaEndpoint.startsWith("https://")) {
            throw new SecurityException("FATAL: SECURITY WARNING: Fabric CA endpoint must use HTTPS in production.");
        }
        if (isBlank(fabricCaAdminPassword)) {
            failOrWarn(isProduction, "SECURITY WARNING: Fabric CA admin password is blank. "
                    + "Set FABRIC_CA_ADMIN_PASSWORD to a strong password.");
            return;
        }
        if (WEAK_FABRIC_PASSWORDS.contains(fabricCaAdminPassword) || isPlaceholderSecret(fabricCaAdminPassword)) {
            failOrWarn(isProduction, "SECURITY WARNING: Fabric CA admin password is using a weak default value.");
        }
    }

    private void validateFabricProfileAlignment(boolean isProduction) {
        boolean fabricProfileActive = isProfileActive("fabric");

        if (fabricEnabled && !fabricProfileActive) {
            throw new SecurityException("FATAL: SECURITY WARNING: FABRIC_ENABLED=true requires the Spring 'fabric' profile. "
                    + "Set SPRING_PROFILES_ACTIVE to include 'fabric' so production does not fall back to MockBlockchainService.");
        }

        if (!fabricProfileActive && !allowMockChain) {
            // 非 fabric 且未显式放行：拒绝启动。
            //
            // 此前只有生产环境才拦（见下方 isProduction 分支的历史实现），
            // 后果是 docker-compose 默认的 "docker" profile（不含 fabric）
            // 会静默加载 MockBlockchainService，把 tx_mock_ 哈希写进库里，
            // 而对账页把它们当作已上链——这正是「全真」要消除的假数据来源。
            throw new SecurityException(
                    "FATAL: 未激活 'fabric' profile，区块链将退化为 MockBlockchainService（伪造 tx_mock_ 哈希）。\n"
                            + "  真实链上存证：在 SPRING_PROFILES_ACTIVE 中加入 'fabric'。\n"
                            + "  仅本地开发/单元测试：显式设置 OAISS_ALLOW_MOCK_CHAIN=true（此模式下不再产出假哈希，"
                            + "报告停留在「审核通过」而不标记为「已上链」）。");
        }

        if (!fabricProfileActive) {
            log.warn("Mock chain mode active via OAISS_ALLOW_MOCK_CHAIN=true: "
                    + "no tx hash will be produced and reports will stay in APPROVED state. "
                    + "This mode must never be used for demonstration or production.");
        }
    }

    private void validateOpsSecrets(boolean isProduction) {
        if (!requireOpsSecrets) {
            return;
        }
        if (isBlank(grafanaAdminPassword)) {
            failOrWarn(isProduction, "SECURITY WARNING: Grafana admin password is blank. "
                    + "Set GRAFANA_ADMIN_PASSWORD to a strong password.");
            return;
        }
        if (WEAK_GRAFANA_PASSWORDS.contains(grafanaAdminPassword) || isPlaceholderSecret(grafanaAdminPassword)) {
            failOrWarn(isProduction, "SECURITY WARNING: Grafana admin password is using a weak default value.");
        }
    }

    private void validateMlServiceSecret(boolean isProduction) {
        if (!isProduction) {
            if (isBlank(mlServiceSecret)) {
                log.warn("SECURITY WARNING: ML service secret is blank in non-production environment. "
                        + "ML service calls will proceed without authentication. "
                        + "Set ML_SERVICE_SECRET for staging/demo parity.");
            }
            return;
        }
        if (isBlank(mlServiceSecret)) {
            throw new SecurityException("FATAL: SECURITY WARNING: ML service secret is blank. "
                    + "Set ML_SERVICE_SECRET to a strong shared secret.");
        }
        if (isPlaceholderSecret(mlServiceSecret)) {
            throw new SecurityException("FATAL: SECURITY WARNING: ML service secret is using a placeholder value.");
        }
    }

    private boolean isProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.stream(activeProfiles)
                .anyMatch(PRODUCTION_PROFILES::contains);
    }

    private boolean isProfileActive(String profile) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    private boolean containsLocalOrigin(String origins) {
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isPlaceholderSecret(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("change_me")
                || normalized.startsWith("replace_with")
                || normalized.equals("placeholder")
                || normalized.equals("todo")
                || normalized.equals("dummy");
    }

    private void failOrWarn(boolean isProduction, String message) {
        if (isProduction) {
            throw new SecurityException("FATAL: " + message);
        }
        log.warn(message);
    }
}
