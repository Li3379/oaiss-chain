/**
 * ==============================================================================
 * 【API 模块】：AI 企业排碳合规推断与异常偏离诊断 (Enterprise Inference API)
 * 【对应后端】：com.oaiss.chain.controller.EnterpriseInferenceController (/predict/enterprise)
 * 【主要服务页面】：
 *   - AI企业推理页面: views/enterprise/EnterpriseInference.vue (/enterprise/enterprise-inference)
 * ==============================================================================
 */
import request from './request'
import type { EnterpriseInferenceResponse } from '@/types/ai'

/**
 * 【业务功能】：基于机器学习孤立森林模型诊断企业历史排放偏离度、合规概率与节能诊断画像
 * 【对应前端页面】：EnterpriseInference.vue (/enterprise/enterprise-inference)
 * 【界面触发操作】：进入企业AI推理页面，自动基于企业ID拉取多维合规评分、风险等级与建议
 * 【后端对应端点】：EnterpriseInferenceController -> GET /predict/enterprise/{id}/inference
 *
 * suppressErrorMessage：企业尚未填报报告时后端返回 3004，页面用空态引导承接这个语义，
 * 不需要再叠加一个红色错误弹窗。
 */
export function getEnterpriseInference(enterpriseId: number): Promise<EnterpriseInferenceResponse> {
  return request.get(`/predict/enterprise/${enterpriseId}/inference`, {
    suppressErrorMessage: true,
  })
}
