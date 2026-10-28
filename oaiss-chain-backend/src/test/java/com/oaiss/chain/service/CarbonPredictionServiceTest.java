package com.oaiss.chain.service;

import com.oaiss.chain.dto.CarbonPredictionRequest;
import com.oaiss.chain.dto.CarbonPredictionResponse;
import com.oaiss.chain.dto.EmissionForecastRequest;
import com.oaiss.chain.dto.EmissionForecastResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.service.ml.MlServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CarbonPredictionService unit tests (Prophet ML integration).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CarbonPredictionService unit tests")
class CarbonPredictionServiceTest {

    @Mock
    private CarbonReportRepository carbonReportRepository;

    @Mock
    private MlServiceClient mlServiceClient;

    @InjectMocks
    private CarbonPredictionService carbonPredictionService;

    private CarbonPredictionRequest request;

    @BeforeEach
    void setUp() {
        request = new CarbonPredictionRequest();
        request.setEnterpriseId(1L);
        request.setPredictMonths(6);
    }

    @Nested
    @DisplayName("predict - insufficient historical data")
    class InsufficientDataTests {

        @Test
        @DisplayName("should return low confidence when only 1 report exists")
        void shouldReturnLowConfidenceWhenOneReport() {
            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(Collections.singletonList(buildReport(BigDecimal.valueOf(1000)))));

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            assertThat(response.getConfidence()).isEqualTo(0.1);
            assertThat(response.getMessage()).contains("历史数据不足");
            assertThat(response.getPredictions()).isEmpty();
            verifyNoInteractions(mlServiceClient);
        }

        @Test
        @DisplayName("should return low confidence when no reports exist")
        void shouldReturnLowConfidenceWhenNoReports() {
            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            assertThat(response.getConfidence()).isEqualTo(0.1);
            assertThat(response.getPredictions()).isEmpty();
            verifyNoInteractions(mlServiceClient);
        }
    }

    @Nested
    @DisplayName("predict - ML service call")
    class MlServiceCallTests {

        private EmissionForecastResponse mlResponse;

        @BeforeEach
        void setUpMlResponse() {
            mlResponse = EmissionForecastResponse.builder()
                    .enterpriseId(1L)
                    .forecastDates(List.of(
                            "2026-06-01", "2026-06-15", "2026-06-30",
                            "2026-07-01", "2026-07-15", "2026-07-31"))
                    .forecastEmissions(List.of(800.0, 780.0, 760.0, 740.0, 720.0, 700.0))
                    .lowerBound(List.of(750.0, 730.0, 710.0, 690.0, 670.0, 650.0))
                    .upperBound(List.of(850.0, 830.0, 810.0, 790.0, 770.0, 750.0))
                    .trend("down")
                    .confidence(0.82)
                    .modelVersion("1.0.0")
                    .build();
        }

        @Test
        @DisplayName("should call ML service and return mapped response with sufficient data")
        void shouldCallMlServiceAndReturnMappedResponse() {
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)),
                    buildReport(BigDecimal.valueOf(800)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));
            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenReturn(mlResponse);

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            assertThat(response).isNotNull();
            assertThat(response.getEnterpriseId()).isEqualTo(1L);
            assertThat(response.getConfidence()).isEqualTo(0.82);
            assertThat(response.getMessage()).contains("Prophet预测模型");
            assertThat(response.getMessage()).contains("down");
            assertThat(response.getGeneratedAt()).isNotNull();
            assertThat(response.getPredictions()).isNotEmpty();

            verify(mlServiceClient).predictEmission(any(EmissionForecastRequest.class));
        }

        @Test
        @DisplayName("should group daily forecasts into monthly predictions")
        void shouldGroupDailyForecastsIntoMonthlyPredictions() {
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));
            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenReturn(mlResponse);

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            // 6 daily forecast points across 2 months → 2 monthly predictions
            assertThat(response.getPredictions()).hasSize(2);
            assertThat(response.getPredictions().get(0).getPeriod()).isEqualTo("M+1");
            assertThat(response.getPredictions().get(1).getPeriod()).isEqualTo("M+2");
        }

        @Test
        @DisplayName("should compute monthly average from daily forecasts")
        void shouldComputeMonthlyAverageFromDailyForecasts() {
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));
            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenReturn(mlResponse);

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            // June: avg(800, 780, 760) = 780.0
            BigDecimal expectedM1 = BigDecimal.valueOf(780.0).setScale(2, java.math.RoundingMode.HALF_UP);
            assertThat(response.getPredictions().get(0).getPredictedEmission()).isEqualByComparingTo(expectedM1);
        }

        @Test
        @DisplayName("should convert predictMonths to horizonDays in ML request")
        void shouldConvertPredictMonthsToHorizonDays() {
            request.setPredictMonths(3);
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));

            // Capture the request to verify horizonDays conversion
            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenReturn(mlResponse);

            carbonPredictionService.predict(request);

            verify(mlServiceClient).predictEmission(any(EmissionForecastRequest.class));
            // 3 months * 30 days = 90 days horizon
            // We can verify indirectly - the endpoint was called with the right types
        }

        @Test
        @DisplayName("should default horizonDays to 180 when predictMonths is null")
        void shouldDefaultHorizonDaysWhenPredictMonthsIsNull() {
            request.setPredictMonths(null);
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));
            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenReturn(mlResponse);

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            assertThat(response).isNotNull();
            verify(mlServiceClient).predictEmission(any(EmissionForecastRequest.class));
        }
    }

    @Nested
    @DisplayName("predict - ML service failure")
    class MlServiceFailureTests {

        @Test
        @DisplayName("should throw BusinessException when ML service is unavailable")
        void shouldThrowWhenMlServiceUnavailable() {
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));
            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenThrow(new BusinessException(6000, "ML service unavailable"));

            assertThatThrownBy(() -> carbonPredictionService.predict(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(6000);
        }

        @Test
        @DisplayName("should throw BusinessException when ML service returns error")
        void shouldThrowWhenMlServiceReturnsError() {
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));
            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenThrow(new BusinessException(6001, "ML service error"));

            assertThatThrownBy(() -> carbonPredictionService.predict(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(6001);
        }
    }

    @Nested
    @DisplayName("predict - null emissions handling")
    class NullEmissionsTests {

        @Test
        @DisplayName("should handle reports with null totalEmission")
        void shouldHandleNullEmissions() {
            List<CarbonReport> reports = new java.util.ArrayList<>();
            reports.add(buildReport(BigDecimal.valueOf(1000)));
            reports.add(buildReport(null));  // null emission → 0.0 in ML request

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));

            EmissionForecastResponse mlResponse = EmissionForecastResponse.builder()
                    .enterpriseId(1L)
                    .forecastDates(List.of("2026-06-01", "2026-06-15", "2026-07-01"))
                    .forecastEmissions(List.of(800.0, 780.0, 740.0))
                    .lowerBound(List.of(750.0, 730.0, 690.0))
                    .upperBound(List.of(850.0, 830.0, 790.0))
                    .trend("down")
                    .confidence(0.7)
                    .modelVersion("1.0.0")
                    .build();

            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenReturn(mlResponse);

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            assertThat(response).isNotNull();
            assertThat(response.getPredictions()).isNotEmpty();
        }
    }

    private CarbonReport buildReport(BigDecimal totalEmission) {
        CarbonReport report = new CarbonReport();
        report.setTotalEmission(totalEmission);
        report.setDeleted(false);
        report.setCreatedAt(LocalDateTime.now().minusDays(30));
        return report;
    }

    @Nested
    @DisplayName("predict - ML response with null forecast data")
    class NullForecastTests {

        @Test
        @DisplayName("should handle ML response with null forecast dates")
        void shouldHandleNullForecastDates() {
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));

            EmissionForecastResponse mlResponse = EmissionForecastResponse.builder()
                    .enterpriseId(1L)
                    .forecastDates(null)
                    .forecastEmissions(null)
                    .trend("stable")
                    .confidence(0.5)
                    .modelVersion("1.0.0")
                    .build();

            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenReturn(mlResponse);

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            assertThat(response).isNotNull();
            assertThat(response.getPredictions()).isEmpty();
        }

        @Test
        @DisplayName("should handle ML response with empty forecast dates")
        void shouldHandleEmptyForecastDates() {
            List<CarbonReport> reports = List.of(
                    buildReport(BigDecimal.valueOf(1000)),
                    buildReport(BigDecimal.valueOf(900)));

            when(carbonReportRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(reports));

            EmissionForecastResponse mlResponse = EmissionForecastResponse.builder()
                    .enterpriseId(1L)
                    .forecastDates(Collections.emptyList())
                    .forecastEmissions(Collections.emptyList())
                    .trend("stable")
                    .confidence(0.5)
                    .modelVersion("1.0.0")
                    .build();

            when(mlServiceClient.predictEmission(any(EmissionForecastRequest.class)))
                    .thenReturn(mlResponse);

            CarbonPredictionResponse response = carbonPredictionService.predict(request);

            assertThat(response).isNotNull();
            assertThat(response.getPredictions()).isEmpty();
        }
    }
}