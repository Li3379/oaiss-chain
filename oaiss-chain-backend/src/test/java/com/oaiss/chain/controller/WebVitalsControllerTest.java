package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WebVitalsControllerTest {

    @InjectMocks
    private WebVitalsController webVitalsController;

    private WebVitalsController.WebVitalMetric buildMetric(String name, double value, String rating, String path) {
        WebVitalsController.WebVitalMetric m = new WebVitalsController.WebVitalMetric();
        // Use reflection-free approach: set fields via constructor test or just test the report endpoint
        return m;
    }

    @Test
    void reportVitals_withValidMetrics_returnsSuccess() {
        List<WebVitalsController.WebVitalMetric> metrics = new ArrayList<>();
        WebVitalsController.WebVitalMetric m = new WebVitalsController.WebVitalMetric();
        metrics.add(m);

        ApiResponse<Void> response = webVitalsController.reportVitals(metrics);
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertNull(response.getData());
    }

    @Test
    void reportVitals_withEmptyList_returnsSuccess() {
        List<WebVitalsController.WebVitalMetric> metrics = new ArrayList<>();

        ApiResponse<Void> response = webVitalsController.reportVitals(metrics);
        assertNotNull(response);
        assertEquals(200, response.getCode());
    }

    @Test
    void reportVitals_withMultipleMetrics_returnsSuccess() {
        List<WebVitalsController.WebVitalMetric> metrics = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            metrics.add(new WebVitalsController.WebVitalMetric());
        }

        ApiResponse<Void> response = webVitalsController.reportVitals(metrics);
        assertNotNull(response);
        assertEquals(200, response.getCode());
    }

    @Test
    void webVitalMetric_defaultConstructor_hasDefaultValues() {
        WebVitalsController.WebVitalMetric metric = new WebVitalsController.WebVitalMetric();
        assertNull(metric.getName());
        assertEquals(0.0, metric.getValue());
        assertNull(metric.getRating());
        assertNull(metric.getId());
        assertNull(metric.getNavigationType());
        assertEquals(0L, metric.getTimestamp());
        assertNull(metric.getPath());
    }
}