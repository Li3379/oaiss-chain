package com.oaiss.chain.service.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying that Java ML DTOs serialize to snake_case JSON
 * and that snake_case JSON responses deserialize correctly back to Java DTOs.
 *
 * This test catches the camelCase/snake_case mismatch (CR-02) that would
 * silently cause all ML predictions to return null/default values.
 */
class MlServiceClientContractTest {

    private MockWebServer mockWebServer;
    private MlServiceClient mlServiceClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);
        CircuitBreaker circuitBreaker = registry.circuitBreaker("mlService");
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        mlServiceClient = new MlServiceClient(webClient, circuitBreaker, meterRegistry);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("EmissionForecastRequest serializes to snake_case JSON")
    void emissionRequest_serializesToSnakeCase() throws InterruptedException, IOException {
        EmissionForecastRequest request = EmissionForecastRequest.builder()
                .enterpriseId(1L)
                .dates(List.of("2025-01", "2025-02"))
                .emissions(List.of(100.0, 120.0))
                .sector("power_generation")
                .horizonDays(180)
                .build();

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(EmissionForecastResponse.builder()
                        .enterpriseId(1L)
                        .forecastDates(List.of("2025-03"))
                        .forecastEmissions(List.of(130.0))
                        .trend("upward")
                        .confidence(0.85)
                        .modelVersion("1.0.0")
                        .build()))
                .setHeader("Content-Type", "application/json"));

        mlServiceClient.predictEmission(request);

        RecordedRequest recorded = mockWebServer.takeRequest();
        String body = recorded.getBody().readUtf8();

        // Verify snake_case keys in serialized JSON
        assertTrue(body.contains("enterprise_id"), "enterpriseId must serialize as enterprise_id");
        assertTrue(body.contains("horizon_days"), "horizonDays must serialize as horizon_days");
        assertFalse(body.contains("enterpriseId"), "camelCase enterpriseId must NOT appear in JSON");
        assertFalse(body.contains("horizonDays"), "camelCase horizonDays must NOT appear in JSON");
    }

    @Test
    @DisplayName("EmissionForecastResponse deserializes from snake_case JSON")
    void emissionResponse_deserializesFromSnakeCase() throws IOException {
        String snakeCaseJson = """
                {
                  "enterprise_id": 2,
                  "forecast_dates": ["2025-04"],
                  "forecast_emissions": [150.0],
                  "lower_bound": [140.0],
                  "upper_bound": [160.0],
                  "trend": "stable",
                  "confidence": 0.90,
                  "model_version": "1.0.0"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(snakeCaseJson)
                .setHeader("Content-Type", "application/json"));

        EmissionForecastRequest request = EmissionForecastRequest.builder()
                .enterpriseId(2L)
                .dates(List.of("2025-01"))
                .emissions(List.of(100.0))
                .build();

        EmissionForecastResponse response = mlServiceClient.predictEmission(request);

        assertEquals(2L, response.getEnterpriseId());
        assertEquals(List.of("2025-04"), response.getForecastDates());
        assertEquals(List.of(150.0), response.getForecastEmissions());
        assertEquals("stable", response.getTrend());
        assertEquals(0.90, response.getConfidence());
        assertEquals("1.0.0", response.getModelVersion());
    }

    @Test
    @DisplayName("EnterpriseInferenceRequest serializes to snake_case JSON")
    void inferenceRequest_serializesToSnakeCase() throws InterruptedException, IOException {
        EnterpriseInferenceRequest request = EnterpriseInferenceRequest.builder()
                .enterpriseId(5L)
                .reportCount(10)
                .totalEmissions(500.0)
                .creditScore(80.0)
                .complianceFlags(2)
                .build();

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(EnterpriseInferenceResponse.builder()
                        .enterpriseId(5L)
                        .complianceStatus("compliant")
                        .confidence(0.92)
                        .modelVersion("1.0.0")
                        .build()))
                .setHeader("Content-Type", "application/json"));

        mlServiceClient.inferEnterprise(request);

        RecordedRequest recorded = mockWebServer.takeRequest();
        String body = recorded.getBody().readUtf8();

        assertTrue(body.contains("enterprise_id"), "enterpriseId must serialize as enterprise_id");
        assertTrue(body.contains("report_count"), "reportCount must serialize as report_count");
        assertTrue(body.contains("total_emissions"), "totalEmissions must serialize as total_emissions");
        assertTrue(body.contains("credit_score"), "creditScore must serialize as credit_score");
        assertTrue(body.contains("compliance_flags"), "complianceFlags must serialize as compliance_flags");
        assertFalse(body.contains("enterpriseId"), "camelCase must NOT appear");
    }

    @Test
    @DisplayName("MarketForecastRequest serializes to snake_case JSON")
    void marketRequest_serializesToSnakeCase() throws InterruptedException, IOException {
        MarketForecastRequest request = MarketForecastRequest.builder()
                .dates(List.of("2025-01", "2025-02"))
                .prices(List.of(50.0, 55.0))
                .volumes(List.of(1000.0, 1200.0))
                .horizonDays(30)
                .build();

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(MarketForecastResponse.builder()
                        .trend("upward")
                        .modelVersion("1.0.0")
                        .build()))
                .setHeader("Content-Type", "application/json"));

        mlServiceClient.predictMarketTrend(request);

        RecordedRequest recorded = mockWebServer.takeRequest();
        String body = recorded.getBody().readUtf8();

        assertTrue(body.contains("horizon_days"), "horizonDays must serialize as horizon_days");
        assertFalse(body.contains("horizonDays"), "camelCase horizonDays must NOT appear");
    }

    @Test
    @DisplayName("Market trend falls back on HTTP error")
    void marketTrend_httpError_shouldFallback() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        MarketForecastResponse response = mlServiceClient.predictMarketTrend(MarketForecastRequest.builder()
                .dates(List.of("2025-01-01"))
                .prices(List.of(55.0))
                .volumes(List.of(100.0))
                .horizonDays(7)
                .build());

        assertEquals("unknown", response.getTrend());
        assertEquals("fallback", response.getModelVersion());
    }

    @Test
    @DisplayName("Carbon price uses market endpoint and parses success response")
    void carbonPrice_success_shouldUseDedicatedEndpoint() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(MarketForecastResponse.builder()
                        .trend("upward")
                        .modelVersion("1.0.1")
                        .build()))
                .setHeader("Content-Type", "application/json"));

        MarketForecastResponse response = mlServiceClient.predictCarbonPrice(MarketForecastRequest.builder()
                .dates(List.of("2025-01"))
                .prices(List.of(60.0))
                .volumes(List.of(500.0))
                .horizonDays(14)
                .build());

        RecordedRequest recorded = mockWebServer.takeRequest();

        assertEquals("/predict/market/price", recorded.getPath());
        assertEquals("upward", response.getTrend());
        assertEquals("1.0.1", response.getModelVersion());
    }

    @Test
    @DisplayName("Carbon price falls back on HTTP error")
    void carbonPrice_httpError_shouldFallback() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        MarketForecastResponse response = mlServiceClient.predictCarbonPrice(MarketForecastRequest.builder()
                .dates(List.of("2025-01"))
                .prices(List.of(60.0))
                .volumes(List.of(500.0))
                .horizonDays(14)
                .build());

        assertEquals("unknown", response.getTrend());
        assertEquals("fallback", response.getModelVersion());
    }

    @Test
    @DisplayName("Carbon price falls back on transport error")
    void carbonPrice_transportError_shouldFallback() {
        assertDoesNotThrow(() -> mockWebServer.shutdown());

        MarketForecastResponse response = mlServiceClient.predictCarbonPrice(MarketForecastRequest.builder()
                .dates(List.of("2025-01"))
                .prices(List.of(60.0))
                .volumes(List.of(500.0))
                .horizonDays(14)
                .build());

        assertEquals("unknown", response.getTrend());
        assertEquals("fallback", response.getModelVersion());
    }

    @Test
    @DisplayName("Supply demand uses supply-demand endpoint and parses success response")
    void supplyDemand_success_shouldUseDedicatedEndpoint() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(MarketForecastResponse.builder()
                        .trend("stable")
                        .modelVersion("1.0.2")
                        .build()))
                .setHeader("Content-Type", "application/json"));

        MarketForecastResponse response = mlServiceClient.predictSupplyDemand(MarketForecastRequest.builder()
                .dates(List.of("2025-02"))
                .prices(List.of(58.0))
                .volumes(List.of(900.0))
                .horizonDays(21)
                .build());

        RecordedRequest recorded = mockWebServer.takeRequest();

        assertEquals("/predict/market/supply-demand", recorded.getPath());
        assertEquals("stable", response.getTrend());
        assertEquals("1.0.2", response.getModelVersion());
    }

    @Test
    @DisplayName("Supply demand falls back on transport error")
    void supplyDemand_transportError_shouldFallback() {
        assertDoesNotThrow(() -> mockWebServer.shutdown());

        MarketForecastResponse response = mlServiceClient.predictSupplyDemand(MarketForecastRequest.builder()
                .dates(List.of("2025-02"))
                .prices(List.of(58.0))
                .volumes(List.of(900.0))
                .horizonDays(21)
                .build());

        assertEquals("unknown", response.getTrend());
        assertEquals("fallback", response.getModelVersion());
    }

    @Test
    @DisplayName("Enterprise inference falls back on HTTP error")
    void enterpriseInference_httpError_shouldFallback() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        EnterpriseInferenceResponse response = mlServiceClient.inferEnterprise(EnterpriseInferenceRequest.builder()
                .enterpriseId(123L)
                .reportCount(5)
                .totalEmissions(200.0)
                .creditScore(60.0)
                .complianceFlags(1)
                .build());

        assertEquals(123L, response.getEnterpriseId());
        assertEquals("unknown", response.getComplianceStatus());
        assertEquals("fallback", response.getModelVersion());
    }

    @Test
    @DisplayName("Enterprise inference deserializes successful snake_case response")
    void enterpriseInference_success_shouldDeserializeSnakeCase() {
        String responseJson = """
                {
                  "enterprise_id": 123,
                  "compliance_status": "at_risk",
                  "confidence": 0.67,
                  "risk_score": 0.88,
                  "model_version": "1.1.0"
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        EnterpriseInferenceResponse response = mlServiceClient.inferEnterprise(EnterpriseInferenceRequest.builder()
                .enterpriseId(123L)
                .reportCount(5)
                .totalEmissions(200.0)
                .creditScore(60.0)
                .complianceFlags(1)
                .build());

        assertEquals(123L, response.getEnterpriseId());
        assertEquals("at_risk", response.getComplianceStatus());
        assertEquals(0.67, response.getConfidence());
        assertEquals("1.1.0", response.getModelVersion());
    }

    @Test
    @DisplayName("Emission predict falls back on HTTP error")
    void emissionPredict_httpError_shouldFallback() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(504));

        EmissionForecastResponse response = mlServiceClient.predictEmission(EmissionForecastRequest.builder()
                .enterpriseId(99L)
                .dates(List.of("2025-01"))
                .emissions(List.of(22.0))
                .build());

        assertEquals(99L, response.getEnterpriseId());
        assertEquals("unknown", response.getTrend());
        assertEquals("fallback", response.getModelVersion());
        assertTrue(response.getForecastDates().isEmpty());
        assertTrue(response.getForecastEmissions().isEmpty());
    }

    @Test
    @DisplayName("Emission predict falls back on transport error")
    void emissionPredict_transportError_shouldFallback() {
        assertDoesNotThrow(() -> mockWebServer.shutdown());

        EmissionForecastResponse response = mlServiceClient.predictEmission(EmissionForecastRequest.builder()
                .enterpriseId(99L)
                .dates(List.of("2025-01"))
                .emissions(List.of(22.0))
                .build());

        assertEquals(99L, response.getEnterpriseId());
        assertEquals("unknown", response.getTrend());
        assertEquals("fallback", response.getModelVersion());
    }

    // ==================== Additional coverage tests ====================

    @Test
    @DisplayName("Supply demand falls back on HTTP error")
    void supplyDemand_httpError_shouldFallback() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(502));

        MarketForecastResponse response = mlServiceClient.predictSupplyDemand(MarketForecastRequest.builder()
                .dates(List.of("2025-02"))
                .prices(List.of(58.0))
                .volumes(List.of(900.0))
                .horizonDays(21)
                .build());

        assertEquals("unknown", response.getTrend());
        assertEquals("fallback", response.getModelVersion());
    }

    @Test
    @DisplayName("Enterprise inference falls back on transport error")
    void enterpriseInference_transportError_shouldFallback() {
        assertDoesNotThrow(() -> mockWebServer.shutdown());

        EnterpriseInferenceResponse response = mlServiceClient.inferEnterprise(EnterpriseInferenceRequest.builder()
                .enterpriseId(456L)
                .reportCount(3)
                .totalEmissions(150.0)
                .creditScore(70.0)
                .complianceFlags(0)
                .build());

        assertEquals(456L, response.getEnterpriseId());
        assertEquals("unknown", response.getComplianceStatus());
        assertEquals("fallback", response.getModelVersion());
    }

    @Test
    @DisplayName("Market trend success uses correct endpoint")
    void marketTrend_success_shouldUseCorrectEndpoint() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(MarketForecastResponse.builder()
                        .trend("downward")
                        .modelVersion("2.0.0")
                        .build()))
                .setHeader("Content-Type", "application/json"));

        MarketForecastResponse response = mlServiceClient.predictMarketTrend(MarketForecastRequest.builder()
                .dates(List.of("2025-03"))
                .prices(List.of(45.0))
                .volumes(List.of(800.0))
                .horizonDays(30)
                .build());

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertEquals("/predict/market/trend", recorded.getPath());
        assertEquals("downward", response.getTrend());
        assertEquals("2.0.0", response.getModelVersion());
    }

    @Test
    @DisplayName("Market trend transport error falls back")
    void marketTrend_transportError_shouldFallback() {
        assertDoesNotThrow(() -> mockWebServer.shutdown());

        MarketForecastResponse response = mlServiceClient.predictMarketTrend(MarketForecastRequest.builder()
                .dates(List.of("2025-01"))
                .prices(List.of(55.0))
                .volumes(List.of(100.0))
                .horizonDays(7)
                .build());

        assertEquals("unknown", response.getTrend());
        assertEquals("fallback", response.getModelVersion());
    }

    @Test
    @DisplayName("timedCall catch block exercised when error handler throws")
    void timedCall_catchBlockTriggered_whenErrorHandlerThrows() {
        // MeterRegistry that throws on counter() calls, forcing errors through both
        // onErrorResume handlers and into the timedCall catch block.
        MeterRegistry throwingRegistry = new SimpleMeterRegistry() {
            @Override
            protected Counter newCounter(Meter.Id id) {
                throw new RuntimeException("counter error");
            }
        };

        WebClient freshClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .build();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        CircuitBreaker cb = cbRegistry.circuitBreaker("timedCallCatchTest");

        MlServiceClient client = new MlServiceClient(freshClient, cb, throwingRegistry);

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // WebClientResponseException handler throws (counter() throws)
        // => Exception handler catches, but also throws (counter() throws)
        // => block() throws => timedCall catch block catches and re-throws
        assertThrows(RuntimeException.class, () ->
                client.predictCarbonPrice(MarketForecastRequest.builder()
                        .dates(List.of("2025-01"))
                        .prices(List.of(60.0))
                        .volumes(List.of(500.0))
                        .horizonDays(14)
                        .build()));
    }
}
