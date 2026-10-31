package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * ==============================================================================
 * 【业务模块】：23. AI 双碳智囊 Copilot 后端统一代理 (AI Copilot BFF Proxy)
 * 【服务角色】：ENTERPRISE / REVIEWER / THIRD_PARTY / ADMIN（全角色，需登录）
 * 【对应前端页面与路由】：
 *   - 国标公式计算器右侧智囊: views/enterprise/CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
 *   - 碳报告 AI 诊断: views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload)
 *   - 市场 AI 采购顾问: views/enterprise/TradingMarket.vue (/enterprise/trading/market)
 * 【调用前端 API 模块】：src/api/carbonFormula.ts / carbon.ts / auction.ts
 * ==============================================================================
 *
 * 设计说明（安全边界）：
 * 浏览器不再直连 ML 服务（原路径 /copilot/* 经 Vite 代理绕过认证），
 * 由本 Controller 统一携带 X-ML-Service-Secret 转发，ML 服务密钥不进入浏览器产物。
 */
@Slf4j
@RestController
@RequestMapping("/copilot")
@RequiredArgsConstructor
@Tag(name = "23. AI 双碳智囊 Copilot", description = "政策法规问答、碳报告诊断、履约采购策略（后端代理 ML 服务）")
// 显式声明授权角色：三个调用方全部位于企业端页面（CarbonFormulaCalculator / CarbonUpload / TradingMarket）。
// 历史缺陷（2026-10-31）：三个写方法均无 @PreAuthorize，回落到 anyRequest().authenticated()，
// 任意登录角色（含第三方监管）均可调用，与“监管只读核查”定位相悖。
@PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
public class CopilotController {

    private final WebClient mlWebClient;

    /**
     * 【业务功能】：双碳政策法规与国标核算依据 AI 智能问答
     * 【对应前端页面】：CarbonFormulaCalculator.vue
     * 【后端对应端点】：POST /copilot/policy-qa
     */
    @AuditLog(module = "AI智囊", action = "POLICY_QA", description = "AI智囊 政策法规问答")
    @PostMapping("/policy-qa")
    @RateLimit(key = "copilot_policy_qa", limit = 20, period = 60)
    @Operation(summary = "政策法规问答", description = "透传至 ML 服务 /copilot/policy-qa")
    public ApiResponse<Map<String, Object>> policyQa(@RequestBody Map<String, Object> payload) {
        return ApiResponse.success(forward("/copilot/policy-qa", payload, "policy-qa"));
    }

    /**
     * 【业务功能】：碳报告排放明细 AI 综合诊断与减排建议推演
     * 【对应前端页面】：CarbonUpload.vue
     * 【后端对应端点】：POST /copilot/report-analysis
     */
    @AuditLog(module = "AI智囊", action = "REPORT_ANALYSIS", description = "AI智囊 碳报告诊断")
    @PostMapping("/report-analysis")
    @RateLimit(key = "copilot_report_analysis", limit = 20, period = 60)
    @Operation(summary = "碳报告智能诊断", description = "透传至 ML 服务 /copilot/report-analysis")
    public ApiResponse<Map<String, Object>> reportAnalysis(@RequestBody Map<String, Object> payload) {
        return ApiResponse.success(forward("/copilot/report-analysis", payload, "report-analysis"));
    }

    /**
     * 【业务功能】：企业履约缺口诊断与采购策略建议
     * 【对应前端页面】：TradingMarket.vue
     * 【后端对应端点】：POST /copilot/procurement-advice
     */
    @AuditLog(module = "AI智囊", action = "PROCUREMENT_ADVICE", description = "AI智囊 采购策略建议")
    @PostMapping("/procurement-advice")
    @RateLimit(key = "copilot_procurement_advice", limit = 20, period = 60)
    @Operation(summary = "采购策略建议", description = "透传至 ML 服务 /copilot/procurement-advice")
    public ApiResponse<Map<String, Object>> procurementAdvice(@RequestBody Map<String, Object> payload) {
        return ApiResponse.success(forward("/copilot/procurement-advice", payload, "procurement-advice"));
    }

    /**
     * 统一转发：mlWebClient 已由 MlServiceConfig 注入 X-ML-Service-Secret 默认头。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> forward(String uri, Map<String, Object> payload, String endpoint) {
        try {
            Map<String, Object> result = mlWebClient.post()
                    .uri(uri)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(30));
            if (result == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 智囊服务返回空响应");
            }
            return result;
        } catch (WebClientResponseException e) {
            log.warn("AI copilot {} upstream failed: status={}, body={}",
                    endpoint, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                // ML 侧密钥不匹配属于服务端配置问题，不应暴露给终端用户为登录失效
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "AI 智囊服务鉴权配置异常，请联系管理员检查 ML_SERVICE_SECRET");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 智囊服务暂时不可用");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI copilot {} call failed", endpoint, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 智囊服务暂时不可用");
        }
    }
}
