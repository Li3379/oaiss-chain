/**
 * Format ISO date string to friendly display format
 * "2026-05-14T17:22:28" → "2026-05-14 17:22:28"
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return ''
  return value.replace('T', ' ').substring(0, 19)
}

/**
 * 数字千分位格式化（UI/UX 规范：长数字必须加千分位，避免 "50000" 这类难读数字）
 *
 * - 千位以上加分隔符：50000 → "50,000"
 * - 保留小数（最多 maxFractionDigits 位，去掉无意义的尾随零）：9449.98 → "9,449.98"
 * - 非数字/空值原样返回占位符，避免出现 "NaN"
 *
 * @param value 数值或数字字符串
 * @param fallback 不可解析时的占位符（默认 '-'）
 * @param maxFractionDigits 最多保留的小数位（默认 2）
 */
export function formatNumber(
  value: number | string | null | undefined,
  fallback = '-',
  maxFractionDigits = 2,
): string {
  if (value === null || value === undefined || value === '') return fallback
  const num = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(num)) return fallback
  return num.toLocaleString('zh-CN', { maximumFractionDigits: maxFractionDigits })
}

/**
 * 带单位的数量展示：50000 → "50,000 tCO₂e"
 */
export function formatNumberWithUnit(
  value: number | string | null | undefined,
  unit: string,
  fallback = '-',
  maxFractionDigits = 2,
): string {
  const formatted = formatNumber(value, fallback, maxFractionDigits)
  return formatted === fallback ? fallback : `${formatted} ${unit}`
}
