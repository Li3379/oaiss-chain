package com.oaiss.chain.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MlServiceConfigTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("ML service secret configured时应自动附加请求头")
    void mlWebClient_withSecret_shouldAttachHeader() throws Exception {
        MlServiceConfig config = new MlServiceConfig();
        config.setUrl(mockWebServer.url("/").toString());
        config.setSecret("shared-secret");
        config.setConnectTimeout(Duration.ofSeconds(2));
        config.setReadTimeout(Duration.ofSeconds(2));

        WebClient webClient = config.mlWebClient(WebClient.builder());

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"ok\":true}")
                .setHeader("Content-Type", "application/json"));

        webClient.post()
                .uri("/predict/market/trend")
                .bodyValue("{\"sample\":true}")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5));

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("shared-secret", request.getHeader("X-ML-Service-Secret"));
    }

    @Test
    @DisplayName("ML service secret为空时不应附加请求头")
    void mlWebClient_withoutSecret_shouldNotAttachHeader() throws Exception {
        MlServiceConfig config = new MlServiceConfig();
        config.setUrl(mockWebServer.url("/").toString());
        config.setSecret("");
        config.setConnectTimeout(Duration.ofSeconds(2));
        config.setReadTimeout(Duration.ofSeconds(2));

        WebClient webClient = config.mlWebClient(WebClient.builder());

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"ok\":true}")
                .setHeader("Content-Type", "application/json"));

        webClient.post()
                .uri("/predict/emission/forecast")
                .bodyValue("{\"sample\":true}")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5));

        RecordedRequest request = mockWebServer.takeRequest();
        assertNull(request.getHeader("X-ML-Service-Secret"));
    }

    @Test
    @DisplayName("ML service secret为null时不应附加请求头")
    void mlWebClient_withNullSecret_shouldNotAttachHeader() throws Exception {
        MlServiceConfig config = new MlServiceConfig();
        config.setUrl(mockWebServer.url("/").toString());
        config.setSecret(null);
        config.setConnectTimeout(Duration.ofSeconds(2));
        config.setReadTimeout(Duration.ofSeconds(2));

        WebClient webClient = config.mlWebClient(WebClient.builder());

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"ok\":true}")
                .setHeader("Content-Type", "application/json"));

        webClient.post()
                .uri("/predict/emission/forecast")
                .bodyValue("{\"sample\":true}")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5));

        RecordedRequest request = mockWebServer.takeRequest();
        assertNull(request.getHeader("X-ML-Service-Secret"));
    }
}
