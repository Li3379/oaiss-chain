package com.oaiss.chain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ml.service")
public class MlServiceConfig {

    private String url = "http://localhost:8001";
    private String secret = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);

    @Bean
    public WebClient mlWebClient(WebClient.Builder webClientBuilder) {
        ObjectMapper snakeCaseMapper = new ObjectMapper();
        snakeCaseMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        snakeCaseMapper.findAndRegisterModules();

        ConnectionProvider provider = ConnectionProvider.builder("ml-service")
                .maxConnections(50)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) connectTimeout.toMillis())
                .responseTimeout(readTimeout)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler((int) readTimeout.toSeconds()))
                        .addHandlerLast(new WriteTimeoutHandler((int) readTimeout.toSeconds())));

        WebClient.Builder builder = webClientBuilder
                .baseUrl(url)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(snakeCaseMapper));
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(snakeCaseMapper));
                });

        if (secret != null && !secret.isBlank()) {
            builder.defaultHeader("X-ML-Service-Secret", secret);
        }

        return builder.build();
    }

    @Bean
    public CircuitBreaker mlService(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("mlService", "default");
    }
}
