package com.oaiss.chain.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus监控配置
 * Prometheus Metrics Configuration
 * 
 * @author OAISS Team
 */
@Configuration
public class MetricsConfig {

    @Value("${spring.application.name:oaiss-chain-backend}")
    private String applicationName;

    /**
     * 全局MeterRegistry配置
     * 为所有指标添加通用标签
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer() {
        return registry -> registry
                .config()
                .commonTags("application", applicationName)
                .meterFilter(new MeterFilter() {
                    /**
                     * 拒绝不想要的指标
                     */
                    @Override
                    public MeterFilterReply accept(Meter.Id id) {
                        // 排除Tomcat详细指标，减少指标数量
                        if (id.getName().startsWith("tomcat.")) {
                            // 只保留关键Tomcat指标
                            String name = id.getName();
                            if (name.contains("threads") || name.contains("connections")) {
                                return MeterFilterReply.NEUTRAL;
                            }
                            return MeterFilterReply.DENY;
                        }
                        // 排除JVM GC详细指标
                        if (id.getName().startsWith("jvm.gc.") && id.getName().contains(".time")) {
                            return MeterFilterReply.DENY;
                        }
                        return MeterFilterReply.NEUTRAL;
                    }

                    /**
                     * 重命名指标名称
                     */
                    @Override
                    public Meter.Id map(Meter.Id id) {
                        // 自定义指标名称映射
                        return id;
                    }
                });
    }
}
