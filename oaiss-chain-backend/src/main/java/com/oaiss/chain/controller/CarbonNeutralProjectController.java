package com.oaiss.chain.controller;

import com.oaiss.chain.dto.*;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CarbonNeutralProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：11. 碳中和项目培育、核证与碳信用抵消 (Carbon Neutral Projects & CCER)
 * 【服务角色】：企业用户 (ENTERPRISE)、审核员 (REVIEWER)、系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - 碳中和项目列表: views/enterprise/CarbonNeutral.vue -> 路由: /enterprise/carbon-neutral/projects
 *   - 碳中和项目全景详情: views/enterprise/CarbonNeutralDetail.vue -> 路由: /enterprise/carbon-neutral/projects/:id
 *   - 核查员项目核证: views/auditor/ProjectReview.vue -> 路由: /auditor/project/review
 * 【调用前端 API 模块】：src/api/carbonNeutral.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/carbon-neutral")
@RequiredArgsConstructor
@Tag(name = "11. 碳中和项目管理", description = "碳减排、碳汇、CCUS等碳中和项目的创建、审核、认证、追踪")
public class CarbonNeutralProjectController {

    private final CarbonNeutralProjectService projectService;

    /**
     * 【业务功能】：企业发起申报新的碳中和/林业碳汇/CCUS减排项目（草稿立项）
     * 【对应前端页面】：views/enterprise/CarbonNeutral.vue (/enterprise/carbon-neutral/projects)
     * 【对应前端API】：api/carbonNeutral.ts -> createProject()
     * 【可见UI操作】：点击【新建碳中和项目】按钮，填报项目名称、预期减排量等并保存
     */
    @AuditLog(module = "碳中和项目", action = "CREATE", description = "碳中和项目 CREATE")
    @PostMapping
    @Operation(summary = "创建项目", description = "创建新的碳中和项目（草稿状态），包括项目基本信息、类型、预计减排量等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "项目创建成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "项目信息无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<CarbonNeutralProjectResponse> create(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Valid @RequestBody CarbonNeutralProjectRequest request) {
        return ApiResponse.success(projectService.createProject(currentUser, request));
    }

    /**
     * 【业务功能】：更新修改草稿阶段的项目设计方案资料
     * 【对应前端页面】：views/enterprise/CarbonNeutralDetail.vue
     * 【对应前端API】：api/carbonNeutral.ts -> updateProject()
     * 【可见UI操作】：项目详情页点击【编辑项目信息】
     */
    @AuditLog(module = "碳中和项目", action = "UPDATE", description = "碳中和项目 UPDATE")
    @PutMapping("/{id}")
    @Operation(summary = "更新项目", description = "更新项目信息，仅草稿或被拒绝状态的项目可修改")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "项目状态不允许修改"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "项目不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<CarbonNeutralProjectResponse> update(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "项目ID", required = true, example = "1") @PathVariable Long id,
            @Valid @RequestBody CarbonNeutralProjectRequest request) {
        return ApiResponse.success(projectService.updateProject(currentUser, id, request));
    }

    /**
     * 【业务功能】：获取单个碳中和项目的全景详情（基本方案、监测数据、核查历史与碳信用签发）
     * 【对应前端页面】：views/enterprise/CarbonNeutralDetail.vue (/enterprise/carbon-neutral/projects/:id)
     * 【对应前端API】：api/carbonNeutral.ts -> getProject()
     * 【可见UI操作】：在项目列表中点击【查看项目详情】进入全景看板
     */
    @AuditLog(module = "碳中和项目", action = "GET", description = "碳中和项目 GET")
    @GetMapping("/{id}")
    @Operation(summary = "获取项目详情", description = "获取指定项目的详细信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "项目不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<CarbonNeutralProjectResponse> get(
            @Parameter(description = "项目ID", required = true, example = "1") @PathVariable Long id) {
        return ApiResponse.success(projectService.getProject(id));
    }

    /**
     * 【业务功能】：多维度检索公开的碳中和项目列表（按项目类型、状态、地区过滤）
     * 【对应前端页面】：views/enterprise/CarbonNeutral.vue (/enterprise/carbon-neutral/projects)
     * 【对应前端API】：api/carbonNeutral.ts -> getProjects()
     * 【可见UI操作】：项目列表页按类型、阶段筛选与分页展示
     */
    @AuditLog(module = "碳中和项目", action = "SEARCH", description = "碳中和项目 SEARCH")
    @GetMapping("/search")
    @Operation(summary = "搜索项目", description = "根据项目类型、状态、关键字搜索碳中和项目，支持分页")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "搜索成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数无效")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENTERPRISE', 'REVIEWER', 'THIRD_PARTY')")
    public ApiResponse<Page<CarbonNeutralProjectResponse>> search(
            @Parameter(description = "项目类型：1-碳汇, 2-CCUS, 3-可再生能源, 4-节能改造, 5-其他", example = "1")
            @RequestParam(required = false) Integer projectType,
            @Parameter(description = "项目状态：0-筹备, 1-待审核, 2-审核通过, 3-实施中, 4-已完成, 5-已终止, 6-审核拒绝", example = "3")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "搜索关键字（项目名称/编号）", example = "林业碳汇")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码（从1开始）", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(projectService.searchProjects(projectType, status, keyword, page, size));
    }

    @Deprecated // Use GET /search instead
    @AuditLog(module = "碳中和项目", action = "LISTPROJECTS", description = "碳中和项目 LISTPROJECTS")
    @GetMapping("/projects")
    @Operation(summary = "获取项目列表", description = "获取碳中和项目列表（别名端点），支持分页。已弃用，请使用 /search 端点。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数无效")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENTERPRISE', 'REVIEWER', 'THIRD_PARTY')")
    public ApiResponse<Page<CarbonNeutralProjectResponse>> listProjects(
            @Parameter(description = "项目类型", example = "1")
            @RequestParam(required = false) Integer projectType,
            @Parameter(description = "项目状态", example = "3")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "搜索关键字", example = "林业碳汇")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码（从1开始）", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(projectService.searchProjects(projectType, status, keyword, page, size));
    }

    /**
     * 【业务功能】：企业用户查询自身申报的所有碳中和项目列表
     * 【对应前端页面】：views/enterprise/CarbonNeutral.vue (/enterprise/carbon-neutral/projects)
     * 【对应前端API】：api/carbonNeutral.ts -> getMyProjects()
     * 【可见UI操作】：进入项目列表页，加载“我申报的项目”Tab
     */
    @AuditLog(module = "碳中和项目", action = "GETMYPROJECTS", description = "碳中和项目 GETMYPROJECTS")
    @GetMapping("/my")
    @Operation(summary = "我的项目", description = "获取当前用户企业的所有碳中和项目列表")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<CarbonNeutralProjectResponse>> getMyProjects(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "项目状态过滤") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(projectService.getMyProjects(currentUser, status, page, size));
    }

    /**
     * 【业务功能】：将碳中和项目立项方案正式提交至专家评审
     * 【对应前端页面】：views/enterprise/CarbonNeutral.vue, views/enterprise/CarbonNeutralDetail.vue
     * 【对应前端API】：api/carbonNeutral.ts -> submitProject()
     * 【可见UI操作】：在项目卡片或详情页点击【提交立项审批】按钮
     */
    @AuditLog(module = "碳中和项目", action = "SUBMITFORREVIEW", description = "碳中和项目 SUBMITFORREVIEW")
    @PostMapping("/{id}/submit")
    @Operation(summary = "提交审核", description = "将项目提交给管理员审核")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "提交成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "项目状态不允许提交"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "项目不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<CarbonNeutralProjectResponse> submitForReview(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "项目ID", required = true, example = "1") @PathVariable Long id) {
        return ApiResponse.success(projectService.submitForReview(currentUser, id));
    }

    /**
     * 【业务功能】：专家/监管方审核碳中和项目立项方案（审批通过或驳回）
     * 【对应前端页面】：views/auditor/ProjectReview.vue (/auditor/project/review)
     * 【对应前端API】：api/carbonNeutral.ts -> reviewProject()
     * 【可见UI操作】：审核员在待审项目抽屉中点击【通过立项】或【驳回修改】按钮
     */
    @AuditLog(module = "碳中和项目", action = "REVIEW", description = "碳中和项目 REVIEW")
    @PostMapping("/{id}/review")
    @Operation(summary = "审核项目", description = "管理员审核项目，通过或拒绝")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "审核成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "项目不在待审核状态"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "项目不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REVIEWER')")
    public ApiResponse<CarbonNeutralProjectResponse> review(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "项目ID", required = true, example = "1") @PathVariable Long id,
            @Valid @RequestBody ProjectReviewRequest request) {
        return ApiResponse.success(projectService.reviewProject(currentUser, id, request.getApproved(), request.getComment()));
    }

    /**
     * 【业务功能】：通过立项审批后，正式启动项目工程落地实施
     * 【对应前端页面】：views/enterprise/CarbonNeutralDetail.vue
     * 【对应前端API】：api/carbonNeutral.ts -> startProject()
     * 【可见UI操作】：立项通过后，企业在项目详情页点击【启动项目实施】按钮
     */
    @AuditLog(module = "碳中和项目", action = "STARTIMPLEMENTATION", description = "碳中和项目 STARTIMPLEMENTATION")
    @PostMapping("/{id}/start")
    @Operation(summary = "启动实施", description = "审核通过后启动项目实施")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "启动成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "项目状态不允许启动"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "项目不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<CarbonNeutralProjectResponse> startImplementation(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "项目ID", required = true, example = "1") @PathVariable Long id) {
        return ApiResponse.success(projectService.startImplementation(currentUser, id));
    }

    
    
    /**
     * 【业务功能】：企业使用/注销项目签发的碳信用抵消自身的超额碳排放
     * 【对应前端页面】：views/enterprise/CarbonNeutralDetail.vue
     * 【对应前端API】：api/carbonNeutral.ts -> useCredits()
     * 【可见UI操作】：在“碳信用抵消”区块输入拟核销抵扣的碳信用额度并确认抵销
     */
    @AuditLog(module = "碳中和项目", action = "USECREDITS", description = "碳中和项目 USECREDITS")
    @PostMapping("/{id}/use-credits")
    @Operation(summary = "使用碳信用", description = "消耗项目已签发的碳信用")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "使用成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "可用碳信用不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "项目不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<CarbonNeutralProjectResponse> useCredits(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "项目ID", required = true, example = "1") @PathVariable Long id,
            @Valid @RequestBody UseCreditsRequest request) {
        return ApiResponse.success(projectService.useCredits(currentUser, id, request.getAmount()));
    }

    
    
    
    
    }
