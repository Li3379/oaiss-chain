package com.oaiss.chain.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ML 服务健康指示器测试。
 *
 * <p>核心语义：ML 是**可选**依赖。它不可达时整体健康检查不应变 DOWN——
 * 否则只要推理服务没起，整个应用就被判定为不健康，K8s 探针会反复重启、
 * CI 的「等待后端就绪」也会一直失败。</p>
 */
@DisplayName("MlServiceHealthIndicator unit tests")
class MlServiceHealthIndicatorTest {

    private MlServiceHealthIndicator indicatorWith(WebClient client) {
        MlServiceConfig config = new MlServiceConfig();
        config.setUrl("http://localhost:8001");
        return new MlServiceHealthIndicator(client, config);
    }

    @Test
    @DisplayName("health should be UP when ml service responds")
    void health_shouldBeUpWhenServiceAvailable() {
        WebClient client = mock(WebClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(client.get().uri(anyString()).retrieve().bodyToMono(eq(String.class)))
                .thenReturn(Mono.just("{\"status\":\"healthy\"}"));

        Health health = indicatorWith(client).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("available", health.getDetails().get("ml-service"));
    }

    @Test
    @DisplayName("health should be UNKNOWN (not DOWN) when ml service unreachable")
    void health_shouldBeUnknownWhenServiceUnavailable() {
        WebClient client = mock(WebClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(client.get().uri(anyString()).retrieve().bodyToMono(eq(String.class)))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        Health health = indicatorWith(client).health();

        // 关键断言：不可达 ≠ 应用不健康。ML 是可选能力，
        // 它的缺席只应表现为「该能力不可用」，而不是把整个应用判死。
        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals("unavailable", health.getDetails().get("ml-service"));
    }
}