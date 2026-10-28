import { describe, it, expect } from 'vitest';
import { formatMetric, getMetricRatingColor } from '../usePerformance';

describe('usePerformance', () => {
  describe('formatMetric', () => {
    it('should format milliseconds under 1s', () => {
      expect(formatMetric(850)).toBe('850ms');
    });

    it('should format seconds over 1s', () => {
      expect(formatMetric(2500)).toBe('2.5s');
    });

    it('should format score values', () => {
      expect(formatMetric(0.05, 'score')).toBe('0.05');
    });
  });

  describe('getMetricRatingColor', () => {
    it('should return success for good FCP', () => {
      expect(getMetricRatingColor('fcp', 1500)).toBe('success');
    });

    it('should return warning for needs-improvement FCP', () => {
      expect(getMetricRatingColor('fcp', 2500)).toBe('warning');
    });

    it('should return danger for poor FCP', () => {
      expect(getMetricRatingColor('fcp', 4000)).toBe('danger');
    });

    it('should return success for good CLS', () => {
      expect(getMetricRatingColor('cls', 0.05)).toBe('success');
    });

    it('should return danger for poor CLS', () => {
      expect(getMetricRatingColor('cls', 0.3)).toBe('danger');
    });

    it('should return info for unknown metric', () => {
      expect(getMetricRatingColor('unknown', 100)).toBe('info');
    });

    it('should return success for good LCP', () => {
      expect(getMetricRatingColor('lcp', 2000)).toBe('success');
    });

    it('should return success for good TTFB', () => {
      expect(getMetricRatingColor('ttfb', 500)).toBe('success');
    });
  });
  describe('usePerformance composable availability', () => {
    it('should export usePerformance function', async () => {
      const mod = await import('../usePerformance')
      expect(mod.usePerformance).toBeDefined()
      expect(typeof mod.usePerformance).toBe('function')
    })

    it('should export formatMetric function', async () => {
      const mod = await import('../usePerformance')
      expect(mod.formatMetric).toBeDefined()
    })

    it('should export getMetricRatingColor function', async () => {
      const mod = await import('../usePerformance')
      expect(mod.getMetricRatingColor).toBeDefined()
    })
  })

});
