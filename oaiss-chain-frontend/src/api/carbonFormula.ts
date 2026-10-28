/**
 * ==============================================================================
 * 【API 模块】：国家标准碳核算公式计算引擎与政策问答 (Carbon Formula API)
 * 【对应后端】：com.oaiss.chain.controller.CarbonController (/carbon/calculate)
 * 【主要服务页面】：
 *   - 碳核算公式计算器: views/enterprise/CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
 * ==============================================================================
 */
import request from './request'
import type {
  CoalOxidationRateRequest,
  PowerGenerationCalculationRequest,
  PowerGenerationCalculationResponse,
  PowerGridCalculationRequest,
  PowerGridCalculationResponse,
} from '@/types/carbonFormula'

/**
 * 【业务功能】：GB/T 32150 发电企业碳排放综合核算（含化石燃料燃烧、脱硫过程及净购入电力等 25 参模型）
 * 【对应前端页面】：CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
 * 【界面触发操作】：在“火力发电核算”表单录入各发电机组活动数据与排放因子后，点击【计算发电综合排放】
 * 【后端对应端点】：CarbonController -> POST /carbon/calculate/power-generation
 */
export function calculatePowerGeneration(data: PowerGenerationCalculationRequest): Promise<PowerGenerationCalculationResponse> {
  return request.post('/carbon/calculate/power-generation', data)
}

/**
 * 【业务功能】：电网企业碳排放量标准核算（含六氟化硫 SF6 设备绝缘泄漏与输配电线损模型）
 * 【对应前端页面】：CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
 * 【界面触发操作】：切换至“电网输配核算”Tab，填报线损电量与设备检修逸散量后，点击【计算电网排放量】
 * 【后端对应端点】：CarbonController -> POST /carbon/calculate/power-grid
 */
export function calculatePowerGrid(data: PowerGridCalculationRequest): Promise<PowerGridCalculationResponse> {
  return request.post('/carbon/calculate/power-grid', data)
}

/**
 * 【业务功能】：燃煤碳氧化率实测值反推计算（根据炉渣、飞灰含碳量与产渣率测算燃烧充分度）
 * 【对应前端页面】：CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
 * 【界面触发操作】：点击“反推实测碳氧化率”按钮，在弹出弹窗中录入灰渣化验数据并点击【确认反推】
 * 【后端对应端点】：CarbonController -> POST /carbon/calculate/power-generation/oxidation-rate
 */
export function calculateCoalOxidationRate(data: CoalOxidationRateRequest): Promise<number> {
  return request.post('/carbon/calculate/power-generation/oxidation-rate', data)
}

export interface PolicyQaResponse {
  answer: string
  relevant_standards: string[]
  model_used: string
}

/**
 * 【业务功能】：双碳政策法规与国标核算依据 AI 智能问答（Policy QA Copilot）
 * 【对应前端页面】：CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
 * 【界面触发操作】：在页面右侧或底部“双碳政策法规智能解答助手”输入框输入问题并点击发送
 * 【后端对应端点】：CopilotController -> POST /copilot/policy-qa（后端统一携带 ML 密钥转发）
 *
 * 注意：必须走 request 实例。直接 fetch 会绕过 JWT 注入，且需浏览器直连 ML 服务，
 * 导致 X-ML-Service-Secret 缺失 → ML 侧 401（历史缺陷）。
 */
export function askPolicyQa(question: string, contextTopic?: string): Promise<PolicyQaResponse> {
  return request.post('/copilot/policy-qa', { question, context_topic: contextTopic })
}
