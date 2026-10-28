package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.annotation.DataIsolation;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.BatchCertifyRequest;
import com.oaiss.chain.dto.BatchCertifyResponse;
import com.oaiss.chain.dto.CarbonReportRequest;
import com.oaiss.chain.dto.CarbonReportResponse;
import com.oaiss.chain.dto.CoalOxidationRateRequest;
import com.oaiss.chain.dto.PowerGenerationCalculationRequest;
import com.oaiss.chain.dto.PowerGenerationCalculationResponse;
import com.oaiss.chain.dto.PowerGridCalculationRequest;
import com.oaiss.chain.dto.PowerGridCalculationResponse;
import com.oaiss.chain.dto.ReviewRequest;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CarbonService;
import com.oaiss.chain.service.PowerGenerationFormulaService;
import com.oaiss.chain.service.PowerGridFormulaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ==============================================================================
 * 【业务模块】：03. 碳核算与排放报告生命周期管理 (Carbon Accounting)
 * 【服务角色】：企业用户 (ENTERPRISE)、核查员 (REVIEWER)、系统管理员 (ADMIN)、监管机构 (THIRD_PARTY)
 * 【对应前端页面与路由】：
 *   - 企业端填报: views/enterprise/CarbonUpload.vue -> 路由: /enterprise/carbon/upload
 *   - 国标公式计算器: views/enterprise/CarbonFormulaCalculator.vue -> 路由: /enterprise/carbon-formula
 *   - 核查员初审列表: views/auditor/AuditList.vue -> 路由: /auditor/audit/list
 *   - 管理员终审上链: views/admin/VerifyList.vue -> 路由: /admin/verify/list
 *   - 管理员报告监管: views/admin/SystemCarbon.vue -> 路由: /admin/system/carbon
 *   - 监管看板穿透审计: views/third-party/Monitor.vue -> 路由: /third-party/monitor
 * 【调用前端 API 模块】：src/api/carbon.ts, src/api/carbonFormula.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/carbon")
@RequiredArgsConstructor
@Tag(name = "03. Carbon Accounting", description = "Carbon report submission, review, certification, and formula calculations")
public class CarbonController {

    private final CarbonService carbonService;
    private final PowerGridFormulaService powerGridFormulaService;
    private final PowerGenerationFormulaService powerGenerationFormulaService;

    /**
     * 【业务功能】：企业用户创建碳排放核算报告草稿（保存草稿）
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload)
     * 【对应前端API】：api/carbon.ts -> createReport()
     * 【可见UI操作】：填报范围一/范围二排碳数据后，点击卡片下方【保存为草稿】按钮
     */
    @AuditLog(module = "碳核算管理", action = "Create carbon report", description = "碳核算管理 Create carbon report")
    @PostMapping("/reports")
    @Operation(
            summary = "Create carbon report",
            description = "Enterprise users create draft carbon reports.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Report created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CarbonReportResponse.class))
            )
    })
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<CarbonReportResponse> createReport(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Valid @RequestBody CarbonReportRequest request) {
        return ApiResponse.success(carbonService.createReport(currentUser, request), "报告创建成功");
    }

    /**
     * 【业务功能】：企业正式提交碳报告进入第三方核查审核流程
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload)
     * 【对应前端API】：api/carbon.ts -> submitReport()
     * 【可见UI操作】：点击报告列表或填报卡片中的【提交审核】按钮，自动触发RSA数字签名并流转
     */
    @AuditLog(module = "碳核算管理", action = "SUBMITREPORT", description = "碳核算管理 SUBMITREPORT")
    @PostMapping("/reports/{reportId}/submit")
    @Operation(
            summary = "Submit carbon report",
            description = "Submit a draft report for reviewer audit.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<CarbonReportResponse> submitReport(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @PathVariable Long reportId) {
        return ApiResponse.success(carbonService.submitReport(currentUser, reportId), "报告提交成功");
    }

    /**
     * 【业务功能】：根据报告ID查询单个碳报告的完整明细数据与结构化因子
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue, views/auditor/AuditList.vue
     * 【对应前端API】：api/carbon.ts -> getReport()
     * 【可见UI操作】：点击报告行操作列的【查看详情】链接展开抽屉
     */
    @AuditLog(module = "碳核算管理", action = "GETREPORT", description = "碳核算管理 GETREPORT")
    @GetMapping("/reports/{reportId}")
    @Operation(
            summary = "Get report detail",
            description = "Retrieve a carbon report by id.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ApiResponse<CarbonReportResponse> getReport(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @PathVariable Long reportId) {
        return ApiResponse.success(carbonService.getReport(currentUser, reportId));
    }

    /**
     * 【业务功能】：管理员/监管端/核查员分页检索全平台碳核算报告列表
     * 【对应前端页面】：views/admin/VerifyList.vue, views/admin/SystemCarbon.vue, views/third-party/Monitor.vue
     * 【对应前端API】：api/carbon.ts -> getReportList()
     * 【可见UI操作】：进入报告管理或认证列表，按企业名称、状态过滤查询
     */
    @AuditLog(module = "碳核算管理", action = "LISTREPORTS", description = "碳核算管理 LISTREPORTS")
    @GetMapping("/reports")
    @Operation(
            summary = "List reports",
            description = "Paginated query for carbon reports.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'THIRD_PARTY')")
    public ApiResponse<Page<CarbonReportResponse>> listReports(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @RequestParam(required = false) Long enterpriseId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(carbonService.listReports(currentUser, enterpriseId, status, keyword, page, size));
    }

    /**
     * 【业务功能】：企业用户分页查询自身提报的历史碳核算报告
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload), views/enterprise/CompanyDashboard.vue
     * 【对应前端API】：api/carbon.ts -> getMyReports()
     * 【可见UI操作】：填报页面下方“历史填报记录”表格自动分页加载与状态展示
     */
    @AuditLog(module = "碳核算管理", action = "LISTMYREPORTS", description = "碳核算管理 LISTMYREPORTS")
    @GetMapping("/my-reports")
    @Operation(
            summary = "List my reports",
            description = "Enterprise users query their own reports.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<Page<CarbonReportResponse>> listMyReports(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String accountingPeriod,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(carbonService.listMyReports(currentUser, status, title, accountingPeriod, page, size));
    }

    /**
     * 【业务功能】：企业用户删除草稿状态的碳报告
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload)
     * 【对应前端API】：api/carbon.ts -> deleteReport()
     * 【可见UI操作】：草稿状态报告行点击【删除】图标并在确认弹窗中确认
     */
    @AuditLog(module = "碳核算管理", action = "DELETEREPORT", description = "碳核算管理 DELETEREPORT")
    @DeleteMapping("/reports/{reportId}")
    @Operation(
            summary = "Delete report",
            description = "Delete a draft report.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<Void> deleteReport(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @PathVariable Long reportId) {
        carbonService.deleteReport(currentUser, reportId);
        return ApiResponse.success();
    }

    /**
     * 【业务功能】：核查员初审评审报告（审核通过或驳回修改并附审核意见）
     * 【对应前端页面】：views/auditor/AuditList.vue (/auditor/audit/list)
     * 【对应前端API】：api/carbon.ts -> reviewReport()
     * 【可见UI操作】：审核抽屉审查凭证后，点击【通过审核】或【驳回修改】按钮提交
     */
    @AuditLog(module = "碳核算管理", action = "REVIEWREPORT", description = "碳核算管理 REVIEWREPORT")
    @PostMapping("/review")
    @Operation(
            summary = "Review report",
            description = "Reviewer approves or rejects a submitted report.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('REVIEWER')")
    public ApiResponse<CarbonReportResponse> reviewReport(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(carbonService.reviewReport(currentUser, request), "审核完成");
    }

    /**
     * 【业务功能】：对已审核通过的碳报告执行上链补救或审核后驳回（方案 A 后的补救入口）
     * 【对应前端页面】：views/auditor/VerifyList.vue (/auditor/verify/list)
     * 【对应前端API】：api/carbon.ts -> certifyReport()
     * 【可见UI操作】：认证列表单条记录操作列点击【上链补救】/【驳回】
     * <p>
     * 职责变更（2026-10-21 · 方案 A）：原始需求规定「当数据审核员点击提交碳排放报告后，
     * 系统后台自动将该报告进行上链」——上链已并入 {@code POST /carbon/review} 审核通过流程。
     * 本端点保留为**幂等补救入口**：自动上链失败（链端抖动）时由审核员重试，
     * 或对审核通过的报告执行合规驳回。权限由 ADMIN 收敛为 REVIEWER（ADMIN 保留运维兜底）。
     */
    @AuditLog(module = "碳核算管理", action = "CERTIFYREPORT", description = "碳核算管理 CERTIFYREPORT")
    @PostMapping("/certify")
    @Operation(
            summary = "Certify report (remediation)",
            description = "Remediation entry: retry on-chain for an approved report whose auto-commit failed, or reject after review.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('REVIEWER') or hasRole('ADMIN')")
    public ApiResponse<CarbonReportResponse> certifyReport(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(carbonService.certifyReport(currentUser, request), "认证完成");
    }

    /**
     * 【业务功能】：批量上链补救——对多份自动上链失败的已审核报告重试存证（方案 A 补救路径）
     * 【对应前端页面】：views/auditor/VerifyList.vue (/auditor/verify/list)
     * 【对应前端API】：api/carbon.ts -> batchCertifyReports()
     * 【可见UI操作】：勾选多个复选框后点击表格上方的【批量上链补救】按钮
     */
    @AuditLog(module = "碳核算管理", action = "BATCH_CERTIFY", description = "碳核算管理 批量认证报告上链")
    @PostMapping("/certify/batch")
    @Operation(
            summary = "Batch certify reports",
            description = "Admin batch certifies approved reports and pushes them on-chain or batch rejects certification.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('REVIEWER') or hasRole('ADMIN')")
    public ApiResponse<BatchCertifyResponse> batchCertifyReports(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Valid @RequestBody BatchCertifyRequest request) {
        return ApiResponse.success(carbonService.batchCertifyReports(currentUser, request), "批量认证处理完成");
    }

    /**
     * [DEFENSE-TRACE-CARBON-CALC-01]
     * 【业务功能】：GB/T 32150 电网行业碳排放公式核算（六氟化硫泄漏与线损模型）
     * 【对应前端页面】：views/enterprise/CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
     * 【对应前端API】：api/carbonFormula.ts -> calculatePowerGrid()
     * 【可见UI操作】：在“电网输配核算”Tab录入参数后点击【计算电网排放量】按钮
     * 【底层核心服务】：PowerGridFormulaService.calculate
     */
    @PostMapping("/calculate/power-grid")
    @Operation(
            summary = "Calculate power-grid emission",
            description = "Run the GB/T 32150-2015 power-grid formula.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ENTERPRISE')")
    @AuditLog(module = "碳核算管理", action = "CALCULATE_POWER_GRID_EMISSION")
    public ApiResponse<PowerGridCalculationResponse> calculatePowerGrid(
            @Valid @RequestBody PowerGridCalculationRequest request) {
        return ApiResponse.success(powerGridFormulaService.calculate(request));
    }

    /**
     * [DEFENSE-TRACE-CARBON-CALC-02]
     * 【业务功能】：GB/T 32150 发电行业综合碳排放公式核算（化石燃烧、脱硫与净购入电力25参）
     * 【对应前端页面】：views/enterprise/CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
     * 【对应前端API】：api/carbonFormula.ts -> calculatePowerGeneration()
     * 【可见UI操作】：在“火力发电核算”Tab录入机组数据后点击【计算发电综合排放】按钮
     * 【底层核心服务】：PowerGenerationFormulaService.calculate
     */
    @PostMapping("/calculate/power-generation")
    @Operation(
            summary = "Calculate power-generation emission",
            description = "Run the GB/T 32150-2015 power-generation formula.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ENTERPRISE')")
    @AuditLog(module = "碳核算管理", action = "CALCULATE_POWER_GENERATION_EMISSION")
    public ApiResponse<PowerGenerationCalculationResponse> calculatePowerGeneration(
            @Valid @RequestBody PowerGenerationCalculationRequest request) {
        return ApiResponse.success(powerGenerationFormulaService.calculate(request));
    }

    /**
     * 【业务功能】：燃煤碳氧化率实测值反推计算（根据炉渣、飞灰含碳量测算燃烧充分度）
     * 【对应前端页面】：views/enterprise/CarbonFormulaCalculator.vue (/enterprise/carbon-formula)
     * 【对应前端API】：api/carbonFormula.ts -> calculateCoalOxidationRate()
     * 【可见UI操作】：点击【反推实测碳氧化率】弹窗录入灰渣化验数据并点击【确认反推】
     */
    @PostMapping("/calculate/power-generation/oxidation-rate")
    @Operation(
            summary = "Calculate coal oxidation rate from ash",
            description = "Calculate coal carbon oxidation rate (OF) based on slag and fly ash carbon content measurements (GB/T 32150-2015).",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @PreAuthorize("hasRole('ENTERPRISE')")
    @AuditLog(module = "碳核算管理", action = "CALCULATE_COAL_OXIDATION_RATE", description = "计算燃煤碳氧化率实测反推")
    public ApiResponse<java.math.BigDecimal> calculateCoalOxidationRate(
            @Valid @RequestBody CoalOxidationRateRequest request) {
        return ApiResponse.success(powerGenerationFormulaService.calculateOxidationRateFromAsh(request));
    }
}
