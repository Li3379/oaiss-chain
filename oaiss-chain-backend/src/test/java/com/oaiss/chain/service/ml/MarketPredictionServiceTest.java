package com.oaiss.chain.service.ml;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.MarketForecastRequest;
import com.oaiss.chain.dto.MarketForecastResponse;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketPredictionService unit tests")
class MarketPredictionServiceTest {

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private MarketPredictionService marketPredictionService;

    private List<Transaction> sampleTrades;
    private MarketForecastResponse sampleResponse;

    @BeforeEach
    void setUp() {
        // 20 天 × 每天 1 笔成交：聚合后应有 20 个日数据点
        sampleTrades = IntStream.range(0, 20)
                .mapToObj(i -> {
                    Transaction t = Transaction.builder()
                            .unitPrice(BigDecimal.valueOf(50.0 + i))
                            .quantity(BigDecimal.valueOf(100 + i * 5))
                            .totalAmount(BigDecimal.valueOf(50.0 + i).multiply(BigDecimal.valueOf(100 + i * 5)))
                            .build();
                    t.setId((long) i);
                    t.setCompletedAt(LocalDateTime.now().minusDays(19 - i));
                    return t;
                })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        sampleResponse = MarketForecastResponse.builder()
                .forecastDates(List.of("2025-02-01", "2025-02-02", "2025-02-03"))
                .forecastPrices(List.of(55.0, 56.0, 57.0))
                .lowerBound(List.of(52.0, 53.0, 54.0))
                .upperBound(List.of(58.0, 59.0, 60.0))
                .trend("up")
                .modelVersion("1.0.0")
                .build();
    }

    private void stubTrades(List<Transaction> trades) {
        when(transactionRepository.findRecentCompletedForForecast(any(Pageable.class)))
                .thenReturn(trades);
    }

    @Nested
    @DisplayName("predictMarketTrend")
    class PredictMarketTrendTests {

        @Test
        @DisplayName("should return forecast when sufficient data exists")
        void shouldReturnForecastWhenSufficientData() {
            stubTrades(sampleTrades);
            when(mlServiceClient.predictMarketTrend(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            MarketForecastResponse result = marketPredictionService.predictMarketTrend(30);

            assertThat(result.getTrend()).isEqualTo("up");
            assertThat(result.getForecastPrices()).hasSize(3);
            assertThat(result.getModelVersion()).isEqualTo("1.0.0");
            verify(mlServiceClient).predictMarketTrend(any(MarketForecastRequest.class));
        }

        @Test
        @DisplayName("should throw BusinessException when no completed trades")
        void shouldThrowWhenInsufficientData() {
            stubTrades(Collections.emptyList());

            assertThatThrownBy(() -> marketPredictionService.predictMarketTrend(30))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_DATA);

            verifyNoInteractions(mlServiceClient);
        }

        @Test
        @DisplayName("同日多笔成交必须聚合成一个日数据点（不是逐条当交易日）")
        void sameDayTradesShouldCollapseIntoOneDataPoint() {
            // 2 天，每天 5 笔 —— 历史实现会当成 10 个「交易日」
            List<Transaction> trades = new ArrayList<>();
            for (int day = 0; day < 2; day++) {
                for (int k = 0; k < 5; k++) {
                    Transaction t = Transaction.builder()
                            .unitPrice(BigDecimal.valueOf(50 + day * 10))
                            .quantity(BigDecimal.TEN)
                            .totalAmount(BigDecimal.valueOf(50 + day * 10).multiply(BigDecimal.TEN))
                            .build();
                    t.setCompletedAt(LocalDateTime.now().minusDays(1 - day).withHour(10 + k));
                    trades.add(t);
                }
            }
            stubTrades(trades);
            when(mlServiceClient.predictMarketTrend(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            marketPredictionService.predictMarketTrend(7);

            ArgumentCaptor<MarketForecastRequest> captor =
                    ArgumentCaptor.forClass(MarketForecastRequest.class);
            verify(mlServiceClient).predictMarketTrend(captor.capture());
            assertThat(captor.getValue().getDates())
                    .as("10 笔成交分布在 2 天，聚合后应只有 2 个数据点")
                    .hasSize(2);
            assertThat(captor.getValue().getVolumes())
                    .as("每天 5 笔 × 10 吨 = 50 吨")
                    .containsExactly(50.0, 50.0);
        }

        @Test
        @DisplayName("日期必须升序（倒序会让 Prophet 反转趋势方向）")
        void datesShouldBeAscending() {
            stubTrades(sampleTrades);
            when(mlServiceClient.predictMarketTrend(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            marketPredictionService.predictMarketTrend(7);

            ArgumentCaptor<MarketForecastRequest> captor =
                    ArgumentCaptor.forClass(MarketForecastRequest.class);
            verify(mlServiceClient).predictMarketTrend(captor.capture());
            List<String> dates = captor.getValue().getDates();
            List<String> sorted = new ArrayList<>(dates);
            Collections.sort(sorted);
            assertThat(dates).isEqualTo(sorted);
        }

        @Test
        @DisplayName("价格必须是成交量加权均价")
        void priceShouldBeVolumeWeightedAverage() {
            // 同一天：100 吨 @ 10 元 + 300 吨 @ 20 元
            // 加权均价 = (100*10 + 300*20) / 400 = 17.5（非算术均值 15）
            LocalDateTime day = LocalDateTime.now().withHour(12);
            Transaction a = Transaction.builder()
                    .unitPrice(BigDecimal.TEN).quantity(BigDecimal.valueOf(100))
                    .totalAmount(BigDecimal.valueOf(1000)).build();
            a.setCompletedAt(day);
            Transaction b = Transaction.builder()
                    .unitPrice(BigDecimal.valueOf(20)).quantity(BigDecimal.valueOf(300))
                    .totalAmount(BigDecimal.valueOf(6000)).build();
            b.setCompletedAt(day);
            Transaction c = Transaction.builder()
                    .unitPrice(BigDecimal.TEN).quantity(BigDecimal.TEN)
                    .totalAmount(BigDecimal.valueOf(100)).build();
            c.setCompletedAt(day.minusDays(1));

            stubTrades(List.of(a, b, c));
            when(mlServiceClient.predictMarketTrend(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            marketPredictionService.predictMarketTrend(7);

            ArgumentCaptor<MarketForecastRequest> captor =
                    ArgumentCaptor.forClass(MarketForecastRequest.class);
            verify(mlServiceClient).predictMarketTrend(captor.capture());
            assertThat(captor.getValue().getPrices().get(1))
                    .as("第二天成交量加权均价应为 17.5，而非算术均值 15")
                    .isEqualTo(17.5);
        }

        @Test
        @DisplayName("只有 1 个交易日应判定数据不足")
        void singleTradingDayShouldBeInsufficient() {
            stubTrades(List.of(sampleTrades.get(0)));

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
            stubTrades(sampleTrades);
            when(mlServiceClient.predictCarbonPrice(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            MarketForecastResponse result = marketPredictionService.predictCarbonPrice(7);

            assertThat(result).isNotNull();
            verify(mlServiceClient).predictCarbonPrice(any(MarketForecastRequest.class));
        }

        @Test
        @DisplayName("should throw BusinessException when insufficient data")
        void shouldThrowWhenInsufficientData() {
            stubTrades(Collections.emptyList());

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
        @DisplayName("should call supply-demand endpoint and carry real volumes")
        void shouldCallSupplyDemandEndpoint() {
            stubTrades(sampleTrades);
            when(mlServiceClient.predictSupplyDemand(any(MarketForecastRequest.class)))
                    .thenReturn(sampleResponse);

            MarketForecastResponse result = marketPredictionService.predictSupplyDemand(7);

            assertThat(result).isNotNull();
            ArgumentCaptor<MarketForecastRequest> captor =
                    ArgumentCaptor.forClass(MarketForecastRequest.class);
            verify(mlServiceClient).predictSupplyDemand(captor.capture());
            assertThat(captor.getValue().getVolumes())
                    .as("供需预测必须携带真实成交量序列，而不是复用价格")
                    .hasSize(20)
                    .allMatch(v -> v > 0);
        }

        @Test
        @DisplayName("should throw BusinessException when insufficient data")
        void shouldThrowWhenInsufficientData() {
            stubTrades(Collections.emptyList());

            assertThatThrownBy(() -> marketPredictionService.predictSupplyDemand(7))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_DATA);
            verifyNoInteractions(mlServiceClient);
        }
    }
}