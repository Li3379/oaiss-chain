import { ref, onMounted, readonly } from 'vue';
import { getPerformanceMetrics, initWebVitals } from '../utils/webVitals';

interface PerformanceState {
  metrics: Record<string, number>;
  loaded: boolean;
}

/**
 * usePerformance composable
 * R113 - Provides reactive Web Vitals and page load metrics
 * 
 * Usage:
 * const { metrics, loaded } = usePerformance();
 */
export function usePerformance() {
  const state = ref<PerformanceState>({
    metrics: {},
    loaded: false,
  });

  onMounted(() => {
    // Collect existing metrics after mount
    setTimeout(() => {
      state.value.metrics = getPerformanceMetrics();
      state.value.loaded = true;
    }, 0);

    // Start observing new Web Vitals
    initWebVitals((metric) => {
      state.value.metrics = {
        ...state.value.metrics,
        [`${metric.name.toLowerCase()}`]: metric.value,
        [`${metric.name.toLowerCase()}_rating`]: metric.rating === 'good' ? 1 : 0,
      };
    });
  });

  return {
    metrics: readonly(ref(state.value.metrics)),
    loaded: readonly(ref(state.value.loaded)),
  };
}

/**
 * Format a metric value for display
 */
export function formatMetric(value: number, unit: 'ms' | 'score' = 'ms'): string {
  if (unit === 'score') return value.toFixed(2);
  return value < 1000 ? `${Math.round(value)}ms` : `${(value / 1000).toFixed(1)}s`;
}

/**
 * Get rating color for display
 */
export function getMetricRatingColor(name: string, value: number): string {
  const thresholds: Record<string, { good: number; poor: number }> = {
    fcp: { good: 1800, poor: 3000 },
    lcp: { good: 2500, poor: 4000 },
    cls: { good: 0.1, poor: 0.25 },
    inp: { good: 200, poor: 500 },
    ttfb: { good: 800, poor: 1800 },
  };
  const t = thresholds[name.toLowerCase()];
  if (!t) return 'info';
  if (value <= t.good) return 'success';
  if (value <= t.poor) return 'warning';
  return 'danger';
}
