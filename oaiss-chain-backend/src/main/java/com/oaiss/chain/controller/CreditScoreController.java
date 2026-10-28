package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.CreditDeductionRequest;
import jakarta.validation.Valid;
import com.oaiss.chain.dto.CreditEventResponse;
import com.oaiss.chain.dto.CreditScoreResponse;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CreditScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.annotation.DataIsolation;

/**
 * ==============================================================================
 * 【业务模块】：05. 企业双碳履约信用评级与风控拦截 (Credit Score & Risk Management)
 * 【服务角色】：企业用户 (ENTERPRISE)、审核员 (REVIEWER)、系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - 信誉评分页面: views/enterprise/CreditScore.vue -> 路由: /enterprise/credit/score
 *   - 企业综合大屏: views/enterprise/CompanyDashboard.vue -> 路由: /enterprise/company/dashboard
 *   - 项目核查扣分: views/auditor/ProjectReview.vue -> 路由: /auditor/project/review
 *   - 准入与黑名单: views/admin/CertificateManage.vue -> 路由: /admin/certificates
 *   - 交易风控拦截: views/enterprise/TradingMarket.vue, TradingP2P.vue
 * 【调用前端 API 模块】：src/api/credit.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/credit")
@RequiredArgsConstructor
@Tag(name = "05. 信誉评分管理", description = "企业信誉评分查询、扣分、加分、等级评估等信誉管理接口")
public class CreditScoreController {

    private final CreditScoreService creditScoreService;

    /**
     * 【业务功能】：查询当前企业的信誉分值、信用评级（AAA~D）与履约维度雷达数据
     * 【对应前端页面】：views/enterprise/CreditScore.vue (/enterprise/credit/score), CompanyDashboard.vue
     * 【对应前端API】：api/credit.ts -> getMyScore()
     * 【可见UI操作】：进入信誉评分页面展示综合得分卡片、评级标签及雷达图
     */
    @AuditLog(module = "信用评分", action = "获取当前用户企业的信誉分", description = "信用评分 获取当前用户企业的信誉分")
    @GetMapping("/my-score")
    @Operation(
        summary = "获取当前用户企业的信誉分",
        description = "企业用户获取自己的信誉评分信息，包括总分、等级、状态等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = CreditScoreResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "无权限，仅企业用户可访问"
        )
    })
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<CreditScoreResponse> getMyScore(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        return ApiResponse.success(creditScoreService.getScoreByUserId(currentUser.getUserId()));
    }

    /**
     * 【业务功能】：分页查询当前企业自身的信誉奖惩变更事件流水（扣分/加分原因及分值）
     * 【对应前端页面】：views/enterprise/CreditScore.vue (/enterprise/credit/score)
     * 【对应前端API】：api/credit.ts -> getScoreHistory()
     * 【可见UI操作】：信誉历史事件表格加载，支持按事件类型筛选违约扣分或守约加分
     */
    @AuditLog(module = "信用评分", action = "获取当前用户企业的信誉历史", description = "信用评分 获取当前用户企业的信誉历史")
    @GetMapping("/history")
    @Operation(
        summary = "获取当前用户企业的信誉历史",
        description = "企业用户获取自己的信誉分变动记录。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "查询成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "无权限，仅企业用户可访问"
        )
    })
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<Page<CreditEventResponse>> getMyCreditHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "事件类型筛选", example = "1")
            @RequestParam(required = false) Integer eventType,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(creditScoreService.getCreditHistoryByUserId(currentUser.getUserId(), eventType, page, size));
    }

    /**
     * 【业务功能】：查询全市场企业信誉排名榜单
     * 【对应前端页面】：views/enterprise/CreditScore.vue (/enterprise/credit/score), TradingP2P.vue, CertificateManage.vue
     * 【对应前端API】：api/credit.ts -> getScoreRanking()
     * 【可见UI操作】：信誉大厅右侧“行业信用排行榜”列表展示
     */
    @AuditLog(module = "信用评分", action = "GETSCORERANKING", description = "信用评分 GETSCORERANKING")
    @GetMapping("/ranking")
    @Operation(
        summary = "获取信誉排名列表",
        description = "获取企业信誉评分排名列表，支持分页。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "查询成功"
        )
    })
    public ApiResponse<Page<CreditScoreResponse>> getScoreRanking(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(creditScoreService.getScoreRanking(page, size));
    }

    /**
     * 【业务功能】：查询指定企业的信誉积分与信用等级信息
     * 【对应前端页面】：views/auditor/AuditList.vue, views/enterprise/TradingP2P.vue
     * 【对应前端API】：api/credit.ts -> getEnterpriseScore()
     * 【可见UI操作】：查看交易对手或被审核企业的信用画像
     */
    @AuditLog(module = "信用评分", action = "查询企业信誉分", description = "信用评分 查询企业信誉分")
    @GetMapping("/{enterpriseId}")
    @Operation(
        summary = "查询企业信誉分",
        description = "获取指定企业的信誉评分信息，包括总分、等级、状态等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "查询成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CreditScoreResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "企业不存在"
        )
    })
    public ApiResponse<CreditScoreResponse> getScore(
            @Parameter(description = "企业ID", required = true, example = "1")
            @PathVariable Long enterpriseId) {
        return ApiResponse.success(creditScoreService.getScore(enterpriseId));
    }

    /**
     * 【业务功能】：查询指定企业的历史信誉奖惩事件记录
     * 【对应前端页面】：views/auditor/ProjectReview.vue, views/third-party/Monitor.vue
     * 【对应前端API】：api/credit.ts -> getEnterpriseCreditHistory()
     * 【可见UI操作】：审核员或监管员审查企业历史违约扣分记录
     */
    @AuditLog(module = "信用评分", action = "查询信誉事件历史", description = "信用评分 查询信誉事件历史")
    @GetMapping("/{enterpriseId}/history")
    @Operation(
        summary = "查询信誉事件历史", 
        description = "分页查询企业的信誉分变动记录，包括加分、扣分事件。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "查询成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "企业不存在"
        )
    })
    public ApiResponse<Page<CreditEventResponse>> getCreditHistory(
            @Parameter(description = "企业ID", required = true, example = "1")
            @PathVariable Long enterpriseId,
            @Parameter(description = "事件类型筛选", example = "1")
            @RequestParam(required = false) Integer eventType,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(creditScoreService.getCreditHistory(enterpriseId, eventType, page, size));
    }

    /**
     * 【业务功能】：审核员或监管方对弄虚作假、逾期未履约企业执行违规扣减信誉积分处罚
     * 【对应前端页面】：views/auditor/ProjectReview.vue (/auditor/project/review), views/third-party/Monitor.vue
     * 【对应前端API】：api/credit.ts -> deductPoints()
     * 【可见UI操作】：项目核证审核驳回时，勾选“同步扣减企业信誉分”并填报扣除分值
     */
    @AuditLog(module = "信用评分", action = "扣除信誉分", description = "信用评分 扣除信誉分")
    @PostMapping("/deduct")
    @Operation(
        summary = "扣除信誉分", 
        description = "审核员或管理员对违规企业扣除信誉分。扣分可能导致企业被限制交易或冻结。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "扣分成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CreditScoreResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限，仅审核员和管理员可操作"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "企业不存在"
        )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    public ApiResponse<CreditScoreResponse> deductPoints(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "扣分请求", required = true)
            @Valid @RequestBody CreditDeductionRequest request) {
        return ApiResponse.success(creditScoreService.deductPoints(
                request.getEnterpriseId(),
                request.getEventType(),
                request.getDescription(),
                currentUser.getUserId(),
                request.getRelatedReportId()));
    }

    /**
     * 【业务功能】：对按时履约、核算规范或有突出碳中和贡献的企业给予奖励加分
     * 【对应前端页面】：views/admin/SystemCarbon.vue, views/admin/CertificateManage.vue
     * 【对应前端API】：api/credit.ts -> addBonus()
     * 【可见UI操作】：管理员在企业管理或证书管理页面点击【信用奖励加分】弹窗操作
     */
    @AuditLog(module = "信用评分", action = "添加信誉奖励分", description = "信用评分 添加信誉奖励分")
    @PostMapping("/bonus")
    @Operation(
        summary = "添加信誉奖励分", 
        description = "管理员为企业添加信誉奖励分。用于奖励诚信行为或特殊情况补偿。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "加分成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CreditScoreResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限，仅管理员可操作"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "企业不存在"
        )
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreditScoreResponse> addBonus(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "企业ID", required = true, example = "1")
            @RequestParam Long enterpriseId,
            @Parameter(description = "奖励分数", required = true, example = "10")
            @RequestParam Integer points,
            @Parameter(description = "奖励原因", required = true, example = "积极参与碳交易")
            @RequestParam String description) {
        return ApiResponse.success(creditScoreService.addBonusPoints(
                enterpriseId, points, description, currentUser.getUserId()));
    }

    /**
     * 【业务功能】：基于企业履约率与历史行为重新计算并评定信用等级
     * 【对应前端页面】：views/enterprise/CreditScore.vue (/enterprise/credit/score)
     * 【对应前端API】：api/credit.ts -> evaluateLevel()
     * 【可见UI操作】：点击【重新评估信用等级】按钮手动触发评定
     */
    @AuditLog(module = "信用评分", action = "重新评估信誉等级", description = "信用评分 重新评估信誉等级")
    @PostMapping("/evaluate/{enterpriseId}")
    @Operation(
        summary = "重新评估信誉等级", 
        description = "管理员手动触发企业信誉等级评估。根据当前信誉分重新计算等级。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "评估完成",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CreditScoreResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限，仅管理员可操作"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "企业不存在"
        )
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CreditScoreResponse> evaluateLevel(
            @Parameter(description = "企业ID", required = true, example = "1")
            @PathVariable Long enterpriseId) {
        return ApiResponse.success(creditScoreService.evaluateLevel(enterpriseId));
    }

    /**
     * 【业务功能】：获取当前被列入交易受限名单的企业列表（信用分低于警戒阈值）
     * 【对应前端页面】：views/admin/CertificateManage.vue (/admin/certificates)
     * 【对应前端API】：api/credit.ts -> getRestrictedEnterprises()
     * 【可见UI操作】：证书管理页面切换至“交易受限名单”Tab加载列表
     */
    @AuditLog(module = "信用评分", action = "查询被限制交易的企业", description = "信用评分 查询被限制交易的企业")
    @GetMapping("/restricted")
    @Operation(
        summary = "查询被限制交易的企业", 
        description = "获取所有因信誉分过低而被限制交易的企业列表。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "查询成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限，仅管理员可访问"
        )
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<CreditScoreResponse>> getRestrictedEnterprises() {
        return ApiResponse.success(creditScoreService.getRestrictedEnterprises());
    }

    /**
     * 【业务功能】：获取账户被完全冻结的企业清单（严重失信企业）
     * 【对应前端页面】：views/admin/CertificateManage.vue (/admin/certificates), views/admin/SystemUsers.vue
     * 【对应前端API】：api/credit.ts -> getFrozenEnterprises()
     * 【可见UI操作】：查看黑名单冻结企业列表
     */
    @AuditLog(module = "信用评分", action = "查询被冻结的企业", description = "信用评分 查询被冻结的企业")
    @GetMapping("/frozen")
    @Operation(
        summary = "查询被冻结的企业", 
        description = "获取所有因信誉分严重不足而被冻结的企业列表。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "查询成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限，仅管理员可访问"
        )
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<CreditScoreResponse>> getFrozenEnterprises() {
        return ApiResponse.success(creditScoreService.getFrozenEnterprises());
    }

    /**
     * 【业务功能】：碳交易前置风控拦截校验（检查企业信誉等级是否允许挂牌与撮合）
     * 【对应前端页面】：views/enterprise/TradingMarket.vue, views/enterprise/TradingP2P.vue
     * 【对应前端API】：api/credit.ts -> checkTradePermission()
     * 【可见UI操作】：在提交买卖订单前调用防风控穿透，拦截低信誉企业
     */
    @AuditLog(module = "信用评分", action = "检查交易权限", description = "信用评分 检查交易权限")
    @GetMapping("/check-permission/{enterpriseId}")
    @Operation(
        summary = "检查交易权限", 
        description = "检查企业是否有交易权限。信誉分低于阈值的企业将被限制交易。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "检查完成"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "企业不存在"
        )
    })
    public ApiResponse<Boolean> checkTradePermission(
            @Parameter(description = "企业ID", required = true, example = "1")
            @PathVariable Long enterpriseId) {
        return ApiResponse.success(creditScoreService.checkTradePermission(enterpriseId));
    }
}
