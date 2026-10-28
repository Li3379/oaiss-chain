package com.oaiss.chain.service.ml;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.MarketForecastRequest;
import com.oaiss.chain.dto.MarketForecastResponse;
import com.oaiss.chain.entity.AuctionOrder;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.AuctionOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketPredictionService unit tests")
class MarketPredictionServiceTest {

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private AuctionOrderRepository auctionOrderRepository;

    @InjectMocks
    private MarketPredictionService marketPredictionService;

    private List<AuctionOrder> sampleOrders;
    private MarketForecastResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleOrders = IntStream.range(0, 30)
                .mapToObj(i -> {
                    AuctionOrder order = AuctionOrder.builder()
                            .price(BigDecimal.valueOf(50.0 + i))
                            .quantity(BigDecimal.valueOf(100 + i * 5))
                            .direction(0)
                            .build();
                    order.setId((long) i);
                    order.setCreatedAt(LocalDateTime.now().minusDays(30 - i));
                    return order;
                })
                .collect(Collectors.toList());

        sampleResponse = MarketForecastResponse.builder()
                .forecastDates(List.of("2025-02-01", "2025-02-02", "2025-02-03"))
                .forecastPrices(List.of(55.0, 56.0, 57.0))
                .lowerBound(List.of(52.0, 53.0, 54.0))
                .upperBound(List.of(58.0, 59.0, 60.0))
                .trend("up")
                .modelVersion("1.0.0")
                .build();
    }

    @Nested
    @DisplayName("predictMarketTrend")
    class PredictMarketTrendTests {

        @Test
        @DisplayName("should return forecast when sufficient data exists")
        void shouldReturnForecastWhenSufficientData() {
            when(auctionOrderRepository.findTop60ByDeletedFalseOrderByCreatedAtDesc())
                    .thenReturn(sampleOrders);
            when(mlServiceClient.predictMarketTrend(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            MarketForecastResponse result = marketPredictionService.predictMarketTrend(30);

            assertThat(result.getTrend()).isEqualTo("up");
            assertThat(result.getForecastPrices()).hasSize(3);
            assertThat(result.getModelVersion()).isEqualTo("1.0.0");
            verify(mlServiceClient).predictMarketTrend(any(MarketForecastRequest.class));
        }

        @Test
        @DisplayName("should throw BusinessException when insufficient data")
        void shouldThrowWhenInsufficientData() {
            when(auctionOrderRepository.findTop60ByDeletedFalseOrderByCreatedAtDesc())
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> marketPredictionService.predictMarketTrend(30))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_DATA);

            verifyNoInteractions(mlServiceClient);
        }
    }

    @Nested
    @DisplayName("predictCarbonPrice")
    class PredictCarbonPriceTests {

        @Test
        @DisplayName("should call ML service price endpoint")
        void shouldCallPriceEndpoint() {
            when(auctionOrderRepository.findTop60ByDeletedFalseOrderByCreatedAtDesc())
                    .thenReturn(sampleOrders);
            when(mlServiceClient.predictCarbonPrice(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            MarketForecastResponse result = marketPredictionService.predictCarbonPrice(7);

            assertThat(result).isNotNull();
            verify(mlServiceClient).predictCarbonPrice(any(MarketForecastRequest.class));
        }

        @Test
        @DisplayName("should throw BusinessException when insufficient data")
        void shouldThrowWhenInsufficientData() {
            when(auctionOrderRepository.findTop60ByDeletedFalseOrderByCreatedAtDesc())
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> marketPredictionService.predictCarbonPrice(7))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_DATA);

            verifyNoInteractions(mlServiceClient);
        }
    }

    @Nested
    @DisplayName("predictSupplyDemand")
    class PredictSupplyDemandTests {

        @Test
        @DisplayName("should call ML service supply-demand endpoint")
        void shouldCallSupplyDemandEndpoint() {
            when(auctionOrderRepository.findTop60ByDeletedFalseOrderByCreatedAtDesc())
                    .thenReturn(sampleOrders);
            when(mlServiceClient.predictSupplyDemand(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            MarketForecastResponse result = marketPredictionService.predictSupplyDemand(14);

            assertThat(result).isNotNull();
            verify(mlServiceClient).predictSupplyDemand(any(MarketForecastRequest.class));
        }

        @Test
        @DisplayName("should throw BusinessException when insufficient data")
        void shouldThrowWhenInsufficientData() {
            when(auctionOrderRepository.findTop60ByDeletedFalseOrderByCreatedAtDesc())
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> marketPredictionService.predictSupplyDemand(14))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_DATA);

            verifyNoInteractions(mlServiceClient);
        }
    }

    @Nested
    @DisplayName("buildForecastRequest")
    class BuildForecastRequestTests {

        @Test
        @DisplayName("should correctly map auction orders to ML request")
        void shouldCorrectlyMapOrdersToRequest() {
            when(auctionOrderRepository.findTop60ByDeletedFalseOrderByCreatedAtDesc())
                    .thenReturn(sampleOrders);
            when(mlServiceClient.predictMarketTrend(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            marketPredictionService.predictMarketTrend(30);

            verify(mlServiceClient).predictMarketTrend(any(MarketForecastRequest.class));
        }
    }
}