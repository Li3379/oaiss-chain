package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Reviewer;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.ReviewerRepository;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.ReviewerQualificationService;
import com.oaiss.chain.service.ReviewerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：10. 第三方专业核查员工作台与资质管理 (Reviewer Workbench)
 * 【服务角色】：专业核查员 (REVIEWER)
 * 【对应前端页面与路由】：
 *   - 碳排放审核列表: views/auditor/AuditList.vue -> 路由: /auditor/audit/list
 *   - 审核历史归档: views/auditor/ReviewHistory.vue -> 路由: /auditor/review/history
 * 【调用前端 API 模块】：src/api/reviewer.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/reviewer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('REVIEWER')")
@Tag(name = "10. 审核员管理", description = "审核员专属功能，包括待审核报告、审核历史等")
public class ReviewerController {

    private final ReviewerService reviewerService;
    private final ReviewerQualificationService reviewerQualificationService;
    private final ReviewerRepository reviewerRepository;

    /**
     * 【业务功能】：审核员查询自身的专业执业资格证书（Reviewer Qualification）及生效状态
     * 【对应前端页面】：views/auditor/ReviewHistory.vue, views/auditor/AuditList.vue
     * 【对应前端API】：api/reviewer.ts -> getMyReviewerQualification()
     * 【可见UI操作】：查看个人执业执照有效期与授权核查行业领域
     */
    @AuditLog(module = "审核员管理", action = "GETMYQUALIFICATION", description = "审核员管理 GETMYQUALIFICATION")
    @GetMapping("/qualification/my")
    @Operation(summary = "查看自身资格证", description = "审核员查看自身的资格证列表。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限，仅审核员可访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<List<ReviewerQualification>> getMyQualification(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        Reviewer reviewer = reviewerRepository.findByUserIdAndDeletedFalse(currentUser.getUserId())
                .orElseThrow(() -> BusinessException.notFound("error.reviewer.notFound"));
        List<ReviewerQualification> qualifications = reviewerQualificationService.getMyCertificate(reviewer.getId());
        return ApiResponse.success(qualifications);
    }

    /**
     * 【业务功能】：获取当前登录核查员的个人执业档案、所属机构与擅长行业
     * 【对应前端页面】：views/auditor/AuditList.vue (/auditor/audit/list), ReviewHistory.vue
     * 【对应前端API】：api/reviewer.ts -> getReviewerInfo()
     * 【可见UI操作】：审核员工作台顶部核查员身份信息条展示
     */
    @AuditLog(module = "审核员管理", action = "GETREVIEWERINFO", description = "审核员管理 GETREVIEWERINFO")
    @GetMapping("/info")
    @Operation(summary = "获取审核员信息", description = "获取当前登录审核员的详细信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非审核员用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Reviewer> getReviewerInfo(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        Reviewer reviewer = reviewerService.getReviewerInfo(currentUser.getUserId());
        return ApiResponse.success(reviewer);
    }

    /**
     * 【业务功能】：查询待当前核查员初审的排队碳核算报告任务池
     * 【对应前端页面】：views/auditor/AuditList.vue (/auditor/audit/list)
     * 【对应前端API】：api/reviewer.ts -> getPendingReports()
     * 【可见UI操作】：进入审核工作台，加载“待初审报告”表格，支持企业筛选与排序
     */
    @AuditLog(module = "审核员管理", action = "GETPENDINGREPORTS", description = "审核员管理 GETPENDINGREPORTS")
    @GetMapping("/reports/pending")
    @Operation(summary = "获取待审核报告", description = "分页查询待审核的碳报告列表")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非审核员用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<CarbonReport>> getPendingReports(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10") @RequestParam(defaultValue = "10") Integer size) {
        Page<CarbonReport> reports = reviewerService.getPendingReports(currentUser.getUserId(), page, size);
        return ApiResponse.success(reports);
    }

    /**
     * 【业务功能】：查询核查员个人经办的历史审核报告归档记录与评审决议
     * 【对应前端页面】：views/auditor/ReviewHistory.vue (/auditor/review/history)
     * 【对应前端API】：api/reviewer.ts -> getReviewHistory()
     * 【可见UI操作】：进入审核历史页面，表格展示已通过或已驳回的历史工单记录
     */
    @AuditLog(module = "审核员管理", action = "GETREVIEWHISTORY", description = "审核员管理 GETREVIEWHISTORY")
    @GetMapping("/history")
    @Operation(summary = "获取审核历史", description = "分页查询已审核的报告历史记录")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非审核员用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<CarbonReport>> getReviewHistory(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10") @RequestParam(defaultValue = "10") Integer size) {
        Page<CarbonReport> history = reviewerService.getReviewHistory(currentUser.getUserId(), page, size);
        return ApiResponse.success(history);
    }

    /**
     * 【业务功能】：统计核查员的工作量与绩效指标（已审报告数、初审通过率、平均审核时长）
     * 【对应前端页面】：views/auditor/AuditList.vue (/auditor/audit/list)
     * 【对应前端API】：api/reviewer.ts -> getStatistics()
     * 【可见UI操作】：审核页面顶部统计卡片（已审核总数、待办任务数、通过率等）
     */
    @AuditLog(module = "审核员管理", action = "获取审核统计", description = "审核员管理 获取审核统计")
    @GetMapping("/statistics")
    @Operation(summary = "获取审核统计", description = "获取审核员的审核统计数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非审核员用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Map<String, Object>> getStatistics(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        Map<String, Object> stats = reviewerService.getStatistics(currentUser.getUserId());
        return ApiResponse.success(stats);
    }
}
