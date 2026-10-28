package com.oaiss.chain.controller;

import com.oaiss.chain.dto.AuctionOrderRequest;
import com.oaiss.chain.dto.AuctionOrderResponse;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.MatchingResultResponse;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.DoubleAuctionService;
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

import java.util.List;
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：12. 碳配额双向连续竞价拍卖撮合引擎 (Double Auction Market)
 * 【服务角色】：企业用户 (ENTERPRISE)、系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - 双向拍卖交易大厅: views/enterprise/TradingMarket.vue -> 路由: /enterprise/trading/market
 *   - 订单管理页面: views/enterprise/OrdersManage.vue -> 路由: /enterprise/orders/manage
 *   - 企业数据综合大屏: views/enterprise/CompanyDashboard.vue -> 路由: /enterprise/company/dashboard
 * 【调用前端 API 模块】：src/api/auction.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/auction")
@RequiredArgsConstructor
@Tag(name = "12. 双向拍卖管理", description = "碳配额双向拍卖挂单、撮合匹配、撮合结果查询")
public class DoubleAuctionController {

    private final DoubleAuctionService doubleAuctionService;

    /**
     * 【业务功能】：企业提交碳配额双向竞价买入挂单（买盘申报价与申购量）
     * 【对应前端页面】：views/enterprise/TradingMarket.vue (/enterprise/trading/market)
     * 【对应前端API】：api/auction.ts -> submitBuyOrder()
     * 【可见UI操作】：在“买入配额”面板输入最高买入限价与买入吨数后，点击【确认提交买单】按钮
     */
    @AuditLog(module = "双向拍卖", action = "PLACEBUYORDER", description = "双向拍卖 PLACEBUYORDER")
    @PostMapping("/buy")
    @Operation(summary = "提交买入挂单", description = "企业提交碳配额买入挂单，指定期望价格和数量，等待撮合匹配")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "挂单成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "挂单参数无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非企业用户无法挂单")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ENTERPRISE')")
    public ApiResponse<AuctionOrderResponse> placeBuyOrder(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Valid @RequestBody AuctionOrderRequest request) {
        return ApiResponse.success(doubleAuctionService.placeBuyOrder(currentUser, request));
    }

    /**
     * 【业务功能】：企业提交碳配额双向竞价卖出挂单（卖盘出让价与出让量）
     * 【对应前端页面】：views/enterprise/TradingMarket.vue (/enterprise/trading/market)
     * 【对应前端API】：api/auction.ts -> submitSellOrder()
     * 【可见UI操作】：在“卖出配额”面板输入最低出让限价与出让吨数后，点击【确认提交卖单】按钮
     */
    @AuditLog(module = "双向拍卖", action = "PLACESELLORDER", description = "双向拍卖 PLACESELLORDER")
    @PostMapping("/sell")
    @Operation(summary = "提交卖出挂单", description = "企业提交碳配额卖出挂单，指定期望价格和数量，等待撮合匹配")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "挂单成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "挂单参数无效或配额不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非企业用户无法挂单")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ENTERPRISE')")
    public ApiResponse<AuctionOrderResponse> placeSellOrder(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Valid @RequestBody AuctionOrderRequest request) {
        return ApiResponse.success(doubleAuctionService.placeSellOrder(currentUser, request));
    }

    /**
     * 【业务功能】：管理员或撮合定时器触发双向拍卖撮合算法执行（K-Double Auction 机制出清）
     * 【对应前端页面】：views/enterprise/TradingMarket.vue (或后台撮合定时任务)
     * 【对应前端API】：api/auction.ts -> executeMatching()
     * 【可见UI操作】：点击【执行撮合】按钮手动出清或由撮合守护线程自动触发
     */
    @AuditLog(module = "双向拍卖", action = "EXECUTEMATCHING", description = "双向拍卖 EXECUTEMATCHING")
    @PostMapping("/match")
    @Operation(summary = "执行撮合", description = "执行双向拍卖撮合算法，按价格优先、时间优先原则匹配买卖挂单")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "撮合执行成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "仅管理员可执行撮合")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MatchingResultResponse>> executeMatching() {
        return ApiResponse.success(doubleAuctionService.executeMatching());
    }

    /**
     * 【业务功能】：查询当前拍卖市场的公开订单簿（买一至买五、卖一至卖五盘口深度）
     * 【对应前端页面】：views/enterprise/TradingMarket.vue (/enterprise/trading/market)
     * 【对应前端API】：api/auction.ts -> getAuctionOrders()
     * 【可见UI操作】：大厅实时渲染红绿盘口买卖档位深度图表与公开订单簿队列
     */
    @AuditLog(module = "双向拍卖", action = "LISTORDERS", description = "双向拍卖 LISTORDERS")
    @GetMapping("/orders")
    @Operation(summary = "查询挂单列表", description = "分页查询所有拍卖挂单，支持按方向和状态筛选")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENTERPRISE', 'REVIEWER', 'THIRD_PARTY')")
    public ApiResponse<Page<AuctionOrderResponse>> listOrders(
            @Parameter(description = "挂单方向：1-买入, 2-卖出", example = "1") @RequestParam(required = false) Integer direction,
            @Parameter(description = "挂单状态：0-待撮合, 1-已撮合, 2-已取消", example = "0") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(doubleAuctionService.listOrders(direction, status, page, size));
    }

    /**
     * 【业务功能】：查询当前登录企业提交的拍卖委托挂单列表与成交状态
     * 【对应前端页面】：views/enterprise/TradingMarket.vue (我的挂单Tab), views/enterprise/OrdersManage.vue
     * 【对应前端API】：api/auction.ts -> getMyOrders()
     * 【可见UI操作】：查看本人当前进行中与已出清的历史拍卖报单
     */
    @AuditLog(module = "双向拍卖", action = "LISTMYORDERS", description = "双向拍卖 LISTMYORDERS")
    @GetMapping("/my-orders")
    @Operation(summary = "查询我的挂单", description = "查询当前用户提交的所有拍卖挂单")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<AuctionOrderResponse>> listMyOrders(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "挂单方向：1-买入, 2-卖出", example = "1") @RequestParam(required = false) Integer direction,
            @Parameter(description = "挂单状态：0-待撮合, 1-已撮合, 2-已取消", example = "0") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(doubleAuctionService.listMyOrders(currentUser, direction, status, page, size));
    }

    /**
     * 【业务功能】：查询拍卖市场的历史撮合成交明细流水（出清价格、撮合成交量、成交时间）
     * 【对应前端页面】：views/enterprise/TradingMarket.vue (成交历史Tab)
     * 【对应前端API】：api/auction.ts -> getMatchResults()
     * 【可见UI操作】：查看拍卖市场近期撮合成功的成交流水记录
     */
    @AuditLog(module = "双向拍卖", action = "LISTMATCHINGRESULTS", description = "双向拍卖 LISTMATCHINGRESULTS")
    @GetMapping("/results")
    @Operation(summary = "查询撮合结果", description = "查询当前用户的撮合成交结果")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<MatchingResultResponse>> listMatchingResults(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(doubleAuctionService.listMatchingResults(currentUser, page, size));
    }
}
