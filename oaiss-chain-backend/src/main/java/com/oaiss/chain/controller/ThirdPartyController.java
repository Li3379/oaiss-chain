package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.ThirdPartyOrg;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.ThirdPartyService;
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

import java.util.Map;
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：15. 第三方监管部门穿透式监控与监管工作台 (Third Party Supervision)
 * 【服务角色】：第三方监管机构 (THIRD_PARTY)
 * 【对应前端页面与路由】：
 *   - 监管面板: views/third-party/Monitor.vue -> 路由: /third-party/monitor
 * 【调用前端 API 模块】：src/api/thirdParty.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/third-party")
@RequiredArgsConstructor
@PreAuthorize("hasRole('THIRD_PARTY')")
@Tag(name = "15. 第三方监管管理", description = "第三方监管机构数据查询、碳报告审核、监管统计")
public class ThirdPartyController {

    private final ThirdPartyService thirdPartyService;

    /**
     * 【业务功能】：获取当前第三方认证/监管机构的主体基本档案与资质信息
     * 【对应前端页面】：views/third-party/Monitor.vue (/third-party/monitor)
     * 【对应前端API】：api/thirdParty.ts -> getOrgInfo()
     * 【可见UI操作】：监管看板“机构档案”卡片展示机构全称、统一代码与联系方式
     */
    @AuditLog(module = "第三方监管", action = "GETORGINFO", description = "第三方监管 GETORGINFO")
    @GetMapping("/org-info")
    @Operation(summary = "获取当前机构信息", description = "获取当前登录的第三方监管机构的详细信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非第三方监管机构用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<ThirdPartyOrg> getOrgInfo(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        return ApiResponse.success(thirdPartyService.getCurrentOrg(currentUser));
    }

    /**
     * 【业务功能】：监管方视角穿透式查询辖区内企业碳核算报告列表（支持按企业ID、状态、时间范围多维检索）
     * 【对应前端页面】：views/third-party/Monitor.vue (/third-party/monitor)
     * 【对应前端API】：api/thirdParty.ts -> getCarbonReports()
     * 【可见UI操作】：监管看板下方“全网企业排碳报告全量审计表”分页加载与检索
     */
    @AuditLog(module = "第三方监管", action = "QUERYCARBONREPORTS", description = "第三方监管 QUERYCARBONREPORTS")
    @GetMapping("/carbon-reports")
    @Operation(summary = "查询碳报告（监管视角）", description = "第三方监管机构查询所有企业碳报告，支持按企业、状态、关键字筛选")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非第三方监管机构用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<CarbonReport>> queryCarbonReports(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "企业ID", example = "1") @RequestParam(required = false) Long enterpriseId,
            @Parameter(description = "报告状态：0-草稿, 1-待审核, 2-已通过, 3-已驳回", example = "1") @RequestParam(required = false) Integer status,
            @Parameter(description = "搜索关键字", example = "年度") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size) {
        return thirdPartyService.queryCarbonReports(
                currentUser, enterpriseId, status, keyword, page, size);
    }

    /**
     * 【业务功能】：监管视角宏观业务统计数据（辖区已核算企业数、审核合规达标率、累计排碳监控总量）
     * 【对应前端页面】：views/third-party/Monitor.vue (/third-party/monitor)
     * 【对应前端API】：api/thirdParty.ts -> getStatistics()
     * 【可见UI操作】：监管大盘顶部核心宏观统计卡片（合规率、监测企业数、总排量等）
     */
    @AuditLog(module = "第三方监管", action = "获取监管统计数据", description = "第三方监管 获取监管统计数据")
    @GetMapping("/statistics")
    @Operation(summary = "获取监管统计数据", description = "获取第三方监管机构管辖范围内的统计数据，包括审核报告数、通过率等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非第三方监管机构用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Map<String, Object>> getStatistics(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        return thirdPartyService.getStatistics(currentUser);
    }

    }
