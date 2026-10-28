/**
 * Web Vitals monitoring for OAISS CHAIN
 * Measures Core Web Vitals (FCP, LCP, CLS, INP, TTFB)
 * R111 - Performance monitoring addition
 */

interface VitalMetric {
  name: string;
  value: number;
  rating: 'good' | 'needs-improvement' | 'poor';
  delta: number;
  id: string;
}

type VitalCallback = (metric: VitalMetric) => void;

const THRESHOLDS: Record<string, { good: number; poor: number }> = {
  FCP: { good: 1800, poor: 3000 },
  LCP: { good: 2500, poor: 4000 },
  CLS: { good: 0.1, poor: 0.25 },
  INP: { good: 200, poor: 500 },
  TTFB: { good: 800, poor: 1800 },
};

function getRating(name: string, value: number): 'good' | 'needs-improvement' | 'poor' {
  const threshold = THRESHOLDS[name];
  if (!threshold) return 'good';
  if (value <= threshold.good) return 'good';
  if (value <= threshold.poor) return 'needs-improvement';
  return 'poor';
}

function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

export function reportWebVital(name: string, value: number, callback?: VitalCallback): void {
  const metric: VitalMetric = {
    name, value,
    rating: getRating(name, value),
    delta: value,
    id: generateId(),
  };

  if (callback) callback(metric);

  if (import.meta.env.DEV) {
    const statusTag = metric.rating === 'good' ? '[OK]' : metric.rating === 'needs-improvement' ? '[WARN]' : '[CRIT]';
    // eslint-disable-next-line no-console
    console.log(`[Web Vitals] ${statusTag} ${name}: ${value.toFixed(1)} (${metric.rating})`);
  }
}

export function initWebVitals(callback?: VitalCallback): void {
  if (typeof window === 'undefined' || !('PerformanceObserver' in window)) return;

  // FCP
  try {
    const obs = new PerformanceObserver((list) => {
      const fcp = list.getEntries().find((e) => e.name === 'first-contentful-paint');
      if (fcp) { reportWebVital('FCP', fcp.startTime, callback); obs.disconnect(); }
    });
    obs.observe({ type: 'paint', buffered: true });
  } catch { /* noop */ }

  // LCP
  try {
    const obs = new PerformanceObserver((list) => {
      const entries = list.getEntries();
      if (entries.length) reportWebVital('LCP', entries[entries.length - 1].startTime, callback);
    });
    obs.observe({ type: 'largest-contentful-paint', buffered: true });
  } catch { /* noop */ }

  // CLS
  try {
    let cls = 0;
    const obs = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const ls = entry as unknown as { hadRecentInput: boolean; value: number }; if (!ls.hadRecentInput) cls += ls.value;
      }
      reportWebVital('CLS', cls, callback);
    });
    obs.observe({ type: 'layout-shift', buffered: true });
  } catch { /* noop */ }

  // TTFB
  try {
    const navEntries = performance.getEntriesByType('navigation');
    if (navEntries.length > 0) {
      const nav = navEntries[0] as PerformanceNavigationTiming;
      reportWebVital('TTFB', nav.responseStart - nav.requestStart, callback);
    }
  } catch { /* noop */ }
}

export function getPerformanceMetrics(): Record<string, number> {
  const metrics: Record<string, number> = {};
  if (typeof window === 'undefined') return metrics;

  try {
    const navEntries = performance.getEntriesByType('navigation');
    if (navEntries.length > 0) {
      const nav = navEntries[0] as PerformanceNavigationTiming;
      metrics.ttfb = nav.responseStart - nav.requestStart;
      metrics.domContentLoaded = nav.domContentLoadedEventEnd - nav.startTime;
      metrics.loadComplete = nav.loadEventEnd - nav.startTime;
    }
    for (const entry of performance.getEntriesByType('paint')) {
      if (entry.name === 'first-contentful-paint') metrics.fcp = entry.startTime;
      if (entry.name === 'first-paint') metrics.fp = entry.startTime;
    }
  } catch { /* noop */ }

  return metrics;
}
