package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.CarbonPredictionRequest;
import com.oaiss.chain.dto.CarbonPredictionResponse;
import com.oaiss.chain.dto.EmissionRatingRequest;
import com.oaiss.chain.entity.EmissionRating;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CarbonPredictionService;
import com.oaiss.chain.service.EmissionRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ==============================================================================
 * 【业务模块】：13. 企业碳排放能效评级码与时序AI预测 (Emission Rating & Forecasting)
 * 【服务角色】：企业用户 (ENTERPRISE)、系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - 排放数据与行业对标: views/enterprise/EmissionData.vue -> 路由: /enterprise/emission/data
 *   - 企业综合数据看板: views/enterprise/CompanyDashboard.vue -> 路由: /enterprise/company/dashboard
 * 【调用前端 API 模块】：src/api/emission.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/emission")
@RequiredArgsConstructor
@Tag(name = "13. 碳排放评级管理", description = "企业碳排放评级、行业排名、AI碳排放趋势预测")
@Validated
public class EmissionController {

    private final EmissionRatingService ratingService;
    private final CarbonPredictionService predictionService;
    private final EnterpriseRepository enterpriseRepository;

    /**
     * 【业务功能】：获取当前登录企业用户的碳效评级记录（碳效码 A~E 等级）
     * 【对应前端页面】：views/enterprise/EmissionData.vue (/enterprise/emission/data), views/enterprise/CompanyDashboard.vue
     * 【对应前端API】：api/emission.ts -> getMyRating()
     * 【可见UI操作】：进入排放数据页面自动加载碳效码等级勋章与各项能效得分
     */
    @AuditLog(module = "排放管理", action = "GETMYRATING", description = "排放管理 GETMYRATING")
    @GetMapping("/my-rating")
    @Operation(summary = "我的评级", description = "获取当前登录企业用户的碳排放评级记录")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ENTERPRISE')")
    public ApiResponse<List<EmissionRating>> getMyRating(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new com.oaiss.chain.exception.BusinessException(4004, "未找到关联企业信息"));
        return ApiResponse.success(ratingService.getEnterpriseRatings(enterprise.getId()));
    }

    /**
     * 【业务功能】：获取指定企业历年碳排放评级记录与对标数据
     * 【对应前端页面】：views/enterprise/EmissionData.vue, views/third-party/Monitor.vue
     * 【对应前端API】：api/emission.ts -> getEnterpriseRatings()
     * 【可见UI操作】：查看同行业其他对标企业的历史评级记录
     */
    @AuditLog(module = "排放管理", action = "GETRATINGS", description = "排放管理 GETRATINGS")
    @GetMapping("/ratings/{enterpriseId}")
    @Operation(summary = "企业评级历史", description = "获取指定企业历年碳排放评级记录，包括评级等级、碳排放量、排名变化等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "企业ID无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "企业不存在或无评级记录")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @Parameter(name = "enterpriseId", description = "企业ID", required = true, example = "1")
    public ApiResponse<List<EmissionRating>> getRatings(@PathVariable Long enterpriseId) {
        return ApiResponse.success(ratingService.getEnterpriseRatings(enterpriseId));
    }

    /**
     * 【业务功能】：根据企业年度碳排放数据与经济产值评定生成碳效等级记录
     * 【对应前端页面】：views/enterprise/EmissionData.vue
     * 【对应前端API】：api/emission.ts -> createRating()
     * 【可见UI操作】：系统评级服务或管理端评定企业碳效等级
     */
    @AuditLog(module = "排放管理", action = "CREATERATING", description = "排放管理 CREATERATING")
    @PostMapping("/ratings")
    @Operation(summary = "生成评级", description = "根据企业年度碳排放数据生成碳排放评级，自动计算评级等级")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "评级生成成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "评级参数无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限，仅管理员/审核员可评定评级")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    // 评级是对他人企业的合规评定结论，必须限定评定主体。
    // 历史缺陷（2026-10-31）：此处无任何 @PreAuthorize，回落到 anyRequest().authenticated()，
    // 任意登录角色（含企业自身、第三方监管）可创建/覆盖任意企业的年度碳排放评级。
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    public ApiResponse<EmissionRating> createRating(@Valid @RequestBody EmissionRatingRequest request) {
        return ApiResponse.success(ratingService.rateEnterprise(
                request.getEnterpriseId(), request.getYear(),
                request.getTotalEmission(), request.getRevenue(),
                request.getRatedBy()));
    }

    /**
     * 【业务功能】：获取指定自然年份的行业碳排放强度与能效排行榜单
     * 【对应前端页面】：views/enterprise/EmissionData.vue (/enterprise/emission/data)
     * 【对应前端API】：api/emission.ts -> getIndustryRankings()
     * 【可见UI操作】：切换年份下拉框，展示行业内部排放对标排名表格
     */
    @AuditLog(module = "排放管理", action = "GETRANKINGS", description = "排放管理 GETRANKINGS")
    @GetMapping("/rankings/{year}")
    @Operation(summary = "行业排名", description = "获取指定年度全行业碳排放排名，按碳排放强度排序")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "年份格式无效")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @Parameter(name = "year", description = "统计年份", required = true, example = "2025")
    public ApiResponse<List<EmissionRating>> getRankings(@PathVariable String year) {
        return ApiResponse.success(ratingService.getIndustryRanking(year));
    }

    /**
     * 【业务功能】：基于企业历史排碳时序数据，使用机器学习预测模型预测未来周期排放量
     * 【对应前端页面】：views/enterprise/EmissionData.vue (/enterprise/emission/data)
     * 【对应前端API】：api/emission.ts -> predictEmission()
     * 【可见UI操作】：在“未来排放趋势预测”卡片选择预测月数后，点击【执行排放量预测】按钮
     */
    @AuditLog(module = "排放管理", action = "PREDICT", description = "排放管理 PREDICT")
    @PostMapping("/predict")
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    @RateLimit(key = "emission_predict", limit = 10, period = 60)
    @Operation(summary = "AI碳排放预测", description = "基于企业历史碳排放数据，使用AI模型预测未来碳排放趋势")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "预测成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "预测参数无效或历史数据不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<CarbonPredictionResponse> predict(@Valid @RequestBody CarbonPredictionRequest request) {
        return ApiResponse.success(predictionService.predict(request));
    }
}
