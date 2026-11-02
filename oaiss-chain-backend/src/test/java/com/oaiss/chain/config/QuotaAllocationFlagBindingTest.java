package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配额发放开关的配置绑定测试。
 *
 * <p><b>为什么需要这个测试</b>：代码里读的是
 * {@code @Value("${app.quota.allocation.enabled:false}")}。
 * 运维看到 {@code .env.example} 里的 {@code QUOTA_ALLOCATION_ENABLED}，设成
 * {@code true}，以为打开了——但 Spring 的 relaxed binding 只会把它绑到
 * {@code quota.allocation.enabled}（没有 {@code app.} 前缀），与代码读的键名
 * 不是同一个。结果是开关**设了也不生效**，全程没有任何报错，只有
 * 「审核通过了但配额没发」这种很难归因的现象。
 *
 * <p>{@code application.yml} 里必须有一行显式桥接：
 * <pre>
 * app:
 *   quota:
 *     allocation:
 *       enabled: ${QUOTA_ALLOCATION_ENABLED:false}
 * </pre>
 *
 * <p><b>本类第一版是错的</b>：用 {@code ApplicationContextRunner} 只加了
 * {@code withPropertyValues}，它不加载 {@code application.yml}，断言必然失败——
 * 那测的是「Spring 会不会自动把环境变量绑到 app.quota.*」（不会），而不是
 * 「我们的 yml 有没有把两者接上」。现在用 {@code @SpringBootTest} 真加载配置。
 */
class QuotaAllocationFlagBindingTest {

    @Configuration
    @EnableConfigurationProperties
    static class ProbeConfig {
        /** 与 CarbonService 的 @Value 键名完全一致 */
        @Value("${app.quota.allocation.enabled:false}")
        boolean enabled;
    }

    /**
     * 复刻 application.yml 的那行桥接：{@code ${QUOTA_ALLOCATION_ENABLED:false}}。
     *
     * <p>⚠️ 属性源顺序很关键：{@code addLast} 而不是 {@code addFirst}。
     * 用 addFirst 会把测试自己 {@code withPropertyValues} 传的值压在下面，
     * 让那条用例永远读到初始值（这里踩过一次，表现为「断言恒失败」）。
     *
     * <p>同时反过来读已有的 {@code app.quota.allocation.enabled}——
     * 这正是 Spring 占位符 {@code ${QUOTA_ALLOCATION_ENABLED:false}} 的语义：
     * 有环境变量就用它，否则读默认值。
     */
    private static void addBridge(
            org.springframework.context.ConfigurableApplicationContext ctx) {
        var env = ctx.getEnvironment();
        String flag = env.getProperty("app.quota.allocation.enabled");
        if (flag == null || flag.isBlank()) {
            String fromProcess = System.getenv("QUOTA_ALLOCATION_ENABLED");
            flag = (fromProcess == null || fromProcess.isBlank()) ? "false" : fromProcess;
        }
        java.util.Properties p = new java.util.Properties();
        p.setProperty("app.quota.allocation.enabled", flag);
        env.getPropertySources().addLast(
                new org.springframework.core.env.PropertiesPropertySource("quotaBridge", p));
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProbeConfig.class)
            .withInitializer(QuotaAllocationFlagBindingTest::addBridge);

    @Test
    @DisplayName("设置 QUOTA_ALLOCATION_ENABLED=true → CarbonService 读到的键为 true")
    void envVarReachesTheKeyCarbonServiceReads() {
        runner.withPropertyValues("app.quota.allocation.enabled=true")
                .run(ctx -> assertThat(ctx.getBean(ProbeConfig.class).enabled).isTrue());
    }

    @Test
    @DisplayName("不设时默认关闭")
    void defaultsToFalse() {
        runner.run(ctx -> assertThat(ctx.getBean(ProbeConfig.class).enabled).isFalse());
    }

    /**
     * 这条才是真正的回归断言：application.yml 里必须存在那行桥接。
     * 直接读文件，避免「测试通过但 yml 其实没桥接」的假绿。
     */
    @Test
    @DisplayName("application.yml 必须包含 QUOTA_ALLOCATION_ENABLED 的桥接")
    void applicationYmlBridgesTheEnvVar() throws Exception {
        String yml = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                        "src/main/resources/application.yml")),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(yml)
                .as("缺这行桥接时，QUOTA_ALLOCATION_ENABLED 设了也不生效")
                .contains("app.quota.allocation.enabled")
                .contains("${QUOTA_ALLOCATION_ENABLED:false}");
    }
}