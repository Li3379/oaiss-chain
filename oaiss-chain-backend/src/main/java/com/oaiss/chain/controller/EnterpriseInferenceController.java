package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.EnterpriseInferenceResponse;
import com.oaiss.chain.service.ml.EnterpriseInferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ==============================================================================
 * 【业务模块】：20. AI 企业排碳合规推断与异常偏离诊断 (Enterprise Inference ML Service)
 * 【服务角色】：企业用户 (ENTERPRISE)、审核员 (REVIEWER)、第三方监管 (THIRD_PARTY)、系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - AI企业推理页面: views/enterprise/EnterpriseInference.vue -> 路由: /enterprise/enterprise-inference
 * 【调用前端 API 模块】：src/api/enterpriseInference.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/predict/enterprise")
@RequiredArgsConstructor
@Tag(name = "20. AI企业推断", description = "企业碳排放趋势推断、合规风险评估、异常检测")
public class EnterpriseInferenceController {

    private final EnterpriseInferenceService enterpriseInferenceService;

    /**
     * 【业务功能】：基于机器学习孤立森林模型诊断企业历史排放偏离度、合规概率与节能诊断画像
     * 【对应前端页面】：views/enterprise/EnterpriseInference.vue (/enterprise/enterprise-inference)
     * 【对应前端API】：api/enterpriseInference.ts -> getEnterpriseInference()
     * 【可见UI操作】：进入企业AI推理页面，自动基于企业ID拉取多维合规评分、风险等级与建议卡片
     */
    @AuditLog(module = "AI智能", action = "INFERENTERPRISE", description = "AI智能 INFERENTERPRISE")
    @GetMapping("/{enterpriseId}/inference")
    @Operation(
            summary = "Enterprise compliance inference",
            description = "Generate enterprise compliance risk assessment "
                    + "using IsolationForest anomaly detection and XGBoost classification.",
            security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'REVIEWER', 'THIRD_PARTY', 'ADMIN')")
    @RateLimit(key = "enterprise-inference", limit = 10, period = 60)
    public ApiResponse<EnterpriseInferenceResponse> inferEnterprise(
            @Parameter(description = "Enterprise ID", required = true, example = "1")
            @PathVariable Long enterpriseId) {
        return ApiResponse.success(
                enterpriseInferenceService.inferEnterprise(enterpriseId));
    }
}