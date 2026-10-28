package com.oaiss.chain.service.ml;

import com.oaiss.chain.dto.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Slf4j
@Component
public class MlServiceClient {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;

    public MlServiceClient(WebClient mlWebClient,
                           @Qualifier("mlService") CircuitBreaker circuitBreaker,
                           MeterRegistry meterRegistry) {
        this.webClient = mlWebClient;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;
    }

    public MarketForecastResponse predictMarketTrend(MarketForecastRequest request) {
        return timedCall("market_trend", () ->
                webClient.post()
                        .uri("/predict/market/trend")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(MarketForecastResponse.class)
                        .transform(CircuitBreakerOperator.of(circuitBreaker))
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.warn("ML market trend call failed: {}", e.getStatusCode());
                            meterRegistry.counter("ml.service.error", "endpoint", "market_trend",
                                    "status", String.valueOf(e.getStatusCode().value())).increment();
                            return fallbackMarketForecast();
                        })
                        .onErrorResume(Exception.class, e -> {
                            log.warn("ML market trend circuit breaker open or timeout: {}", e.getMessage());
                            meterRegistry.counter("ml.service.degraded", "endpoint", "market_trend").increment();
                            return fallbackMarketForecast();
                        })
                        .block(Duration.ofSeconds(10))
        );
    }

    public MarketForecastResponse predictCarbonPrice(MarketForecastRequest request) {
        return timedCall("market_price", () ->
                webClient.post()
                        .uri("/predict/market/price")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(MarketForecastResponse.class)
                        .transform(CircuitBreakerOperator.of(circuitBreaker))
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.warn("ML carbon price call failed: {}", e.getStatusCode());
                            meterRegistry.counter("ml.service.error", "endpoint", "market_price",
                                    "status", String.valueOf(e.getStatusCode().value())).increment();
                            return fallbackMarketForecast();
                        })
                        .onErrorResume(Exception.class, e -> {
                            log.warn("ML carbon price circuit breaker open or timeout: {}", e.getMessage());
                            meterRegistry.counter("ml.service.degraded", "endpoint", "market_price").increment();
                            return fallbackMarketForecast();
                        })
                        .block(Duration.ofSeconds(10))
        );
    }

    public MarketForecastResponse predictSupplyDemand(MarketForecastRequest request) {
        return timedCall("supply_demand", () ->
                webClient.post()
                        .uri("/predict/market/supply-demand")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(MarketForecastResponse.class)
                        .transform(CircuitBreakerOperator.of(circuitBreaker))
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.warn("ML supply-demand call failed: {}", e.getStatusCode());
                            meterRegistry.counter("ml.service.error", "endpoint", "supply_demand",
                                    "status", String.valueOf(e.getStatusCode().value())).increment();
                            return fallbackMarketForecast();
                        })
                        .onErrorResume(Exception.class, e -> {
                            log.warn("ML supply-demand circuit breaker open or timeout: {}", e.getMessage());
                            meterRegistry.counter("ml.service.degraded", "endpoint", "supply_demand").increment();
                            return fallbackMarketForecast();
                        })
                        .block(Duration.ofSeconds(10))
        );
    }

    public EnterpriseInferenceResponse inferEnterprise(EnterpriseInferenceRequest request) {
        return timedCall("enterprise_inference", () ->
                webClient.post()
                        .uri("/api/v1/predict/enterprise/")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(EnterpriseInferenceResponse.class)
                        .transform(CircuitBreakerOperator.of(circuitBreaker))
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.warn("ML enterprise inference call failed: {}", e.getStatusCode());
                            meterRegistry.counter("ml.service.error", "endpoint", "enterprise_inference",
                                    "status", String.valueOf(e.getStatusCode().value())).increment();
                            return fallbackEnterpriseInference(request.getEnterpriseId());
                        })
                        .onErrorResume(Exception.class, e -> {
                            log.warn("ML enterprise inference circuit breaker open or timeout: {}", e.getMessage());
                            meterRegistry.counter("ml.service.degraded", "endpoint", "enterprise_inference").increment();
                            return fallbackEnterpriseInference(request.getEnterpriseId());
                        })
                        .block(Duration.ofSeconds(10))
        );
    }

    public EmissionForecastResponse predictEmission(EmissionForecastRequest request) {
        return timedCall("emission_predict", () ->
                webClient.post()
                        .uri("/predict/emission/forecast")
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(EmissionForecastResponse.class)
                        .transform(CircuitBreakerOperator.of(circuitBreaker))
                        .onErrorResume(WebClientResponseException.class, e -> {
                            log.warn("ML emission predict call failed: {}", e.getStatusCode());
                            meterRegistry.counter("ml.service.error", "endpoint", "emission_predict",
                                    "status", String.valueOf(e.getStatusCode().value())).increment();
                            return fallbackEmissionForecast(request.getEnterpriseId());
                        })
                        .onErrorResume(Exception.class, e -> {
                            log.warn("ML emission predict circuit breaker open or timeout: {}", e.getMessage());
                            meterRegistry.counter("ml.service.degraded", "endpoint", "emission_predict").increment();
                            return fallbackEmissionForecast(request.getEnterpriseId());
                        })
                        .block(Duration.ofSeconds(10))
        );
    }

    private <T> T timedCall(String endpoint, java.util.function.Supplier<T> call) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = call.get();
            sample.stop(meterRegistry.timer("ml.service.latency", "endpoint", endpoint, "status", "success"));
            return result;
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("ml.service.latency", "endpoint", endpoint, "status", "error"));
            throw e;
        }
    }

    private reactor.core.publisher.Mono<MarketForecastResponse> fallbackMarketForecast() {
        log.info("Using fallback market forecast");
        return reactor.core.publisher.Mono.just(MarketForecastResponse.builder()
                .trend("unknown")
                .modelVersion("fallback")
                .build());
    }

    private reactor.core.publisher.Mono<EnterpriseInferenceResponse> fallbackEnterpriseInference(Long enterpriseId) {
        log.info("Using fallback enterprise inference for enterpriseId={}", enterpriseId);
        return reactor.core.publisher.Mono.just(EnterpriseInferenceResponse.builder()
                .enterpriseId(enterpriseId)
                .complianceStatus("unknown")
                .modelVersion("fallback")
                .build());
    }

    private reactor.core.publisher.Mono<EmissionForecastResponse> fallbackEmissionForecast(Long enterpriseId) {
        log.info("Using fallback emission forecast for enterpriseId={}", enterpriseId);
        return reactor.core.publisher.Mono.just(EmissionForecastResponse.builder()
                .enterpriseId(enterpriseId)
                .confidence(0.0)
                .trend("unknown")
                .modelVersion("fallback")
                .forecastDates(java.util.Collections.emptyList())
                .forecastEmissions(java.util.Collections.emptyList())
                .build());
    }
}