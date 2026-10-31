/**
 * `emissionData` JSON 的行形状类型。
 *
 * 后端 `EmissionDataValidator` 支持四种历史形态（见该类 `calculateScopeTotal` 注释），
 * 前端解析时也需要同一套形状定义，集中放在这里避免各处重复声明。
 */
export interface EmissionRowShape {
  /** 排放源名称（可选，仅展示用） */
  name?: unknown
  /** 活动数据（与 emission_factor 相乘得排放量） */
  activity_data?: unknown
  /** 排放因子 */
  emission_factor?: unknown
  /** 已算好的排放量（与 activity_data × emission_factor 二选一） */
  emission?: unknown
  /** 直接数值（旧数据的简化形态） */
  value?: unknown
}