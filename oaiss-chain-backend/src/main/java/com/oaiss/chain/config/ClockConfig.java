package com.oaiss.chain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时钟来源配置。
 *
 * <p><b>为什么需要显式的 Clock bean</b>：项目此前所有「当前时间」都直接调
 * {@code LocalDateTime.now()}（全仓 20+ 处），这带来两个问题：
 * <ol>
 *   <li><b>不可测</b>：无法在测试里把时间固定在口径分界日前后，从而无法断言
 *       「分界前按旧口径、分界后按新口径」这条边界行为</li>
 *   <li><b>时区隐式依赖 JVM</b>：容器镜像默认 UTC，而 JDBC 串与 Jackson 都配了
 *       {@code Asia/Shanghai}，两者叠加会造成 8 小时的静默错位</li>
 * </ol>
 *
 * <p>引入 Clock 后，业务代码通过注入获取「现在」，测试可用
 * {@code @TestConfiguration} 提供 {@code Clock.fixed(...)} 覆盖，
 * 时间相关的断言才成为真正的断言而非时序巧合。
 *
 * <p>时区的根治仍在部署侧：Dockerfile 与 compose 显式设 {@code TZ=Asia/Shanghai}。
 * 本 bean 只解决「可注入、可固定」，不解决「容器默认 UTC」。
 *
 * @author OAISS Team
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}