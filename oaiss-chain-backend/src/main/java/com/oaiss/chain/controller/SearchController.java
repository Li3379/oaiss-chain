package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.SearchResponse;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.SearchService;
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
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：14. 全站多维数据综合搜索与市场大盘总览 (Search & Market Overview)
 * 【服务角色】：全系统认证用户（企业 ENTERPRISE、审核员 REVIEWER、第三方监管 THIRD_PARTY、管理员 ADMIN）
 * 【对应前端页面与路由】：
 *   - 官网首页大盘: views/OfficialHome.vue -> 路由: /official-home
 *   - 企业综合看板: views/enterprise/CompanyDashboard.vue -> 路由: /enterprise/company/dashboard
 *   - 市场大盘与搜索: views/enterprise/TradingMarket.vue, OrdersManage.vue
 * 【调用前端 API 模块】：src/api/search.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "14. 搜索查询管理", description = "碳报告搜索、交易记录搜索、市场宏观数据统计")
public class SearchController {

    private final SearchService searchService;

    /**
     * 【业务功能】：多条件组合全文检索碳核算报告（支持关键字、企业ID与状态综合模糊匹配）
     * 【对应前端页面】：views/admin/SystemCarbon.vue, views/third-party/Monitor.vue
     * 【对应前端API】：api/search.ts -> searchReports()
     * 【可见UI操作】：输入全局关键词检索相关历史报告
     */
    @AuditLog(module = "搜索查询", action = "SEARCHREPORTS", description = "搜索查询 SEARCHREPORTS")
    @GetMapping("/reports")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "搜索碳报告", description = "根据关键字、企业ID、状态搜索碳报告，支持模糊匹配和分页")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "搜索成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<SearchResponse.CarbonReportSummary>> searchReports(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "搜索关键字", example = "年度报告") @RequestParam(required = false) String keyword,
            @Parameter(description = "企业ID", example = "1") @RequestParam(required = false) Long enterpriseId,
            @Parameter(description = "报告状态：0-草稿, 1-待审核, 2-已通过, 3-已驳回", example = "2") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(searchService.searchCarbonReports(currentUser, keyword, enterpriseId, status, page, size));
    }

    /**
     * 【业务功能】：多维度组合检索全市场碳交易流水记录（支持买卖双方、交易类型、成交状态过滤）
     * 【对应前端页面】：views/enterprise/OrdersManage.vue, views/enterprise/TradingMarket.vue
     * 【对应前端API】：api/search.ts -> searchTrades()
     * 【可见UI操作】：搜索市场历史成交委托与履约明细
     */
    @AuditLog(module = "搜索查询", action = "SEARCHTRADES", description = "搜索查询 SEARCHTRADES")
    @GetMapping("/trades")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "搜索交易记录", description = "根据条件搜索碳交易记录，支持按买卖双方、交易类型、状态筛选")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "搜索成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<SearchResponse.TradeSummary>> searchTrades(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "卖方企业ID", example = "1") @RequestParam(required = false) Long sellerId,
            @Parameter(description = "买方企业ID", example = "2") @RequestParam(required = false) Long buyerId,
            @Parameter(description = "交易类型：1-P2P, 2-拍卖, 3-挂牌", example = "1") @RequestParam(required = false) Integer tradeType,
            @Parameter(description = "交易状态：0-待付款, 1-已完成, 2-已取消", example = "1") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(searchService.searchTrades(currentUser, sellerId, buyerId, tradeType, status, page, size));
    }

    /**
     * 【业务功能】：获取碳交易市场全网宏观大盘统计数据（全网累计交易量、成交均价、累计核证减排量）
     * 【对应前端页面】：views/OfficialHome.vue (/official-home), views/enterprise/CompanyDashboard.vue
     * 【对应前端API】：api/search.ts -> getMarketOverview()
     * 【可见UI操作】：进入官网首页或企业综合看板，加载全网大盘实时概览卡片
     */
    @AuditLog(module = "搜索查询", action = "GETMARKETOVERVIEW", description = "搜索查询 GETMARKETOVERVIEW")
    @GetMapping("/market-overview")
    @Operation(summary = "市场概览", description = "获取全市场宏观数据统计，包括交易总量、平均价格、活跃企业数等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功")
    })
    public ApiResponse<SearchResponse.MarketOverview> getMarketOverview() {
        return ApiResponse.success(searchService.getMarketOverview());
    }
}
