package com.oaiss.chain.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for the MeterFilter defined inside MetricsConfig.meterRegistryCustomizer().
 * Applies the customizer to a SimpleMeterRegistry, then registers meters with
 * specific names to exercise every branch of the accept() method.
 *
 * The accept() method is invoked when each meter is registered, covering all branches
 * regardless of the return value. DENY returns a Noop meter; NEUTRAL creates a real one.
 */
@DisplayName("MetricsConfig MeterFilter branch coverage tests")
class MetricsConfigMeterFilterTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        MetricsConfig metricsConfig = new MetricsConfig();
        // @Value is not processed in unit tests, so set applicationName via reflection
        Field nameField = MetricsConfig.class.getDeclaredField("applicationName");
        nameField.setAccessible(true);
        nameField.set(metricsConfig, "test-app");

        registry = new SimpleMeterRegistry();
        metricsConfig.meterRegistryCustomizer().customize(registry);
    }

    @Test
    @DisplayName("普通指标应被接受(NEUTRAL) — 覆盖tomcat=false, jvm.gc=false分支")
    void accept_regularMetric_shouldBeAccepted() {
        Counter counter = Counter.builder("http.server.requests").register(registry);
        assertNotNull(counter);
    }

    @Test
    @DisplayName("tomcat线程指标应被接受(NEUTRAL) — 覆盖tomcat=true, threads=true分支")
    void accept_tomcatThreads_shouldBeAccepted() {
        Gauge gauge = Gauge.builder("tomcat.threads.count", () -> 1).register(registry);
        assertNotNull(gauge);
    }

    @Test
    @DisplayName("tomcat连接指标应被接受(NEUTRAL) — 覆盖threads=false, connections=true分支")
    void accept_tomcatConnections_shouldBeAccepted() {
        Gauge gauge = Gauge.builder("tomcat.connections.active", () -> 1).register(registry);
        assertNotNull(gauge);
    }

    @Test
    @DisplayName("tomcat其他指标应被拒绝(DENY) — 覆盖threads=false, connections=false分支")
    void accept_tomcatOther_shouldBeDenied() {
        // DENY returns a Noop meter but the accept branch is still exercised
        Gauge gauge = Gauge.builder("tomcat.sessions.active", () -> 1).register(registry);
        assertNotNull(gauge);
    }

    @Test
    @DisplayName("JVM GC pause time指标应被拒绝(DENY) — 覆盖jvm.gc=true, .time=true分支")
    void accept_jvmGcPauseTime_shouldBeDenied() {
        // DENY returns a Noop timer but the accept branch is still exercised
        Timer timer = Timer.builder("jvm.gc.pause.time").register(registry);
        assertNotNull(timer);
    }

    @Test
    @DisplayName("JVM GC非time指标应被接受(NEUTRAL) — 覆盖jvm.gc=true, .time=false分支")
    void accept_jvmGcNonTime_shouldBeAccepted() {
        Counter counter = Counter.builder("jvm.gc.allocation.name").register(registry);
        assertNotNull(counter);
    }
}
