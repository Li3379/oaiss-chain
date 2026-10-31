/**
 * 碳核算参数的单位口径归一。
 *
 * 背景（真实故障）：
 * 本系统的「碳氧化率实测反推」接口
 * `POST /carbon/calculate/power-generation/oxidation-rate` 的 DTO 约定输入
 * **kJ/kg**（低位发热量）与 **tC/TJ**（单位热值含碳量）——即 GB/T 32150 表 1 的口径。
 *
 * 但公式计算器页面的模板数据用的是 **GJ/t** 与 **tC/GJ**（数值分别小 1000 倍）。
 * 早期实现把模板值原样传给反推接口，导致：
 *   分母 `FC × NCV × CC` 小 1000² 倍 → 反推比值爆到 1e3 量级
 *   → `OF = 1 - 比值` 变成大负数 → 后端钳制为 0
 *   → 用户点一次「实测反推」，碳氧化率变成 0，燃烧排放归零（且无任何报错）
 *
 * 这里用「量级判据」而不是「字段来源」来归一：化肥厂的真实 kJ/kg 与 GJ/t 在数量级上
 * 相差 1000 倍，用阈值即可稳定区分，且对用户手工填写同样成立。
 */

/** kJ/kg 量级通常 ≥ 1000；GJ/t 量级通常 < 100。低于阈值视为 GJ/t 并换算。 */
const NCV_GJ_PER_T_THRESHOLD = 100

/** tC/TJ 量级通常 ≥ 1；tC/GJ 量级通常 < 1。低于阈值视为 tC/GJ 并换算。 */
const CC_TC_PER_GJ_THRESHOLD = 1

const UNIT_FACTOR = 1000

/**
 * 把**低位发热量**归一到 kJ/kg。
 *
 * @param value 表单里的原始值（可能是 GJ/t 或已是 kJ/kg）
 * @param fallback 空值时使用的国标量级默认值
 */
export function toStandardNcv(value: number | null | undefined, fallback: number): number {
  if (value === null || value === undefined || value === 0 || Number.isNaN(value)) {
    return fallback
  }
  return Math.abs(value) < NCV_GJ_PER_T_THRESHOLD ? value * UNIT_FACTOR : value
}

/**
 * 把**单位热值含碳量**归一到 tC/TJ。
 *
 * @param value 表单里的原始值（可能是 tC/GJ 或已是 tC/TJ）
 * @param fallback 空值时使用的国标量级默认值
 */
export function toStandardCarbonContent(
  value: number | null | undefined,
  fallback: number,
): number {
  if (value === null || value === undefined || value === 0 || Number.isNaN(value)) {
    return fallback
  }
  return Math.abs(value) < CC_TC_PER_GJ_THRESHOLD ? value * UNIT_FACTOR : value
}