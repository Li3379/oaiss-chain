package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配额发放开关的配置绑定测试。
 *
 * <p><b>为什么需要这个测试</b>：代码里读的是
 * {@code @Value("${app.quota.allocation.enabled:false}")}。
 * 运维看到 {@code .env.example} 里的 {@code QUOTA_ALLOCATION_ENABLED}，
 * 设成 {@code true}，以为打开了——但 Spring 的 relaxed binding 只会把它
 * 绑到 {@code quota.allocation.enabled}（没有 {@code app.} 前缀），
 * 与代码读的键名不是同一个。结果是开关**设了也不生效**，而没有任何报错，
 * 只有「审核通过了但配额没发」这种很难归因的现象。
 *
 * <p>{@code application.yml} 里必须有一行显式桥接：
 * <pre>
 * app:
 *   quota:
 *     allocation:
 *       enabled: ${QUOTA_ALLOCATION_ENABLED:false}
 * </pre>
 * 本测试直接读 yml 绑定后的值，桥接缺失就会红。
 */
class QuotaAllocationFlagBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProbeConfig.class);

    /**
     * 探针：用 {@code app.quota.allocation.enabled} 这个键名取值，
     * 与 CarbonService 的 {@code @Value} 完全一致。
     */
    @Configuration
    @EnableConfigurationProperties
    static class ProbeConfig {
        @Value("${app.quota.allocation.enabled:false}")
        boolean enabled;
    }

    @Test
    @DisplayName("环境变量 QUOTA_ALLOCATION_ENABLED=true → app.quota.allocation.enabled 为 true")
    void envVarReachesTheKeyCarbonServiceReads() {
        runner.withPropertyValues("QUOTA_ALLOCATION_ENABLED=true")
                .run(ctx -> {
                    assertThat(ctx.getBean(ProbeConfig.class).enabled)
                            .as("QUOTA_ALLOCATION_ENABLED 没有映射到 app.quota.allocation.enabled，"
                                    + "开关会设了不生效")
                            .isTrue();
                });
    }

    @Test
    @DisplayName("不设时默认关闭")
    void defaultsToFalse() {
        runner.run(ctx -> assertThat(ctx.getBean(ProbeConfig.class).enabled).isFalse());
    }
}
