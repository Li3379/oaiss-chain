package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.annotation.DataIsolation;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.TradeRequest;
import com.oaiss.chain.dto.TradeResponse;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.TradeService;
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
import org.springframework.web.bind.annotation.*;

/**
 * ==============================================================================
 * 【业务模块】：04. 碳配额交易与协议转让引擎 (Carbon Trading & P2P)
 * 【服务角色】：企业用户 (ENTERPRISE)、系统管理员 (ADMIN)、第三方监管 (THIRD_PARTY)
 * 【对应前端页面与路由】：
 *   - P2P定向转让: views/enterprise/TradingP2P.vue -> 路由: /enterprise/trading/p2p
 *   - 订单管理页面: views/enterprise/OrdersManage.vue -> 路由: /enterprise/orders/manage
 *   - 企业综合大屏: views/enterprise/CompanyDashboard.vue -> 路由: /enterprise/company/dashboard
 * 【调用前端 API 模块】：src/api/trade.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/trade")
@RequiredArgsConstructor
@Tag(name = "04. 碳交易管理", description = "P2P交易、拍卖挂单、交易确认、交易查询等碳交易相关接口")
public class TradeController {

    private final TradeService tradeService;

    /**
     * 【业务功能】：发起点对点（P2P）定向碳配额协商转让委托
     * 【对应前端页面】：views/enterprise/TradingP2P.vue (/enterprise/trading/p2p)
     * 【对应前端API】：api/trade.ts -> createP2PTrade()
     * 【可见UI操作】：输入目标买方企业ID、转让配额数量与单价后，点击【发起定向转让】按钮
     */
    @AuditLog(module = "碳交易", action = "创建P2P交易", description = "碳交易 创建P2P交易")
    @PostMapping("/p2p")
    @Operation(
        summary = "创建P2P交易", 
        description = "创建点对点碳配额交易。卖方发起交易，指定买方和交易数量、价格。需要审核通过后生效。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "交易创建成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = TradeResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "请求参数错误或碳配额不足"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限，仅企业用户可创建"
        )
    })
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<TradeResponse> createP2PTrade(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "P2P交易请求", required = true)
            @Valid @RequestBody TradeRequest request) {
        return ApiResponse.success(tradeService.createP2PTrade(currentUser, request), "交易创建成功");
    }

    /**
     * 【业务功能】：交易对手方确认成交，触发原子化配额划转与碳币资金结算
     * 【对应前端页面】：views/enterprise/TradingP2P.vue (/enterprise/trading/p2p), views/enterprise/OrdersManage.vue
     * 【对应前端API】：api/trade.ts -> confirmTrade()
     * 【可见UI操作】：买方企业在收到的待确认转让订单卡片上点击【确认承接/支付】
     */
    @AuditLog(module = "碳交易", action = "确认交易", description = "碳交易 确认交易")
    @PostMapping("/{tradeId}/confirm")
    @Operation(
        summary = "确认交易", 
        description = "确认并完成碳交易。交易双方确认后，碳配额和资金完成交割。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "交易完成",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = TradeResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "交易状态不允许确认"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "交易不存在"
        )
    })
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    public ApiResponse<TradeResponse> confirmTrade(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "交易ID", required = true, example = "1")
            @PathVariable Long tradeId) {
        return ApiResponse.success(tradeService.confirmTrade(tradeId, currentUser.getUserId()), "交易完成");
    }

    /**
     * 【业务功能】：撤销未成交或待对方确认的碳交易订单
     * 【对应前端页面】：views/enterprise/OrdersManage.vue (/enterprise/orders/manage), views/enterprise/TradingP2P.vue
     * 【对应前端API】：api/trade.ts -> cancelTrade()
     * 【可见UI操作】：在活动订单列表中点击【撤单】按钮，确认后释放锁定的配额或资金
     */
    @AuditLog(module = "碳交易", action = "取消交易", description = "碳交易 取消交易")
    @PostMapping("/{tradeId}/cancel")
    @Operation(
        summary = "取消交易", 
        description = "取消待处理的碳交易。仅交易发起方可取消。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "交易已取消",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = TradeResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "交易状态不允许取消"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限取消此交易"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "交易不存在"
        )
    })
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<TradeResponse> cancelTrade(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "交易ID", required = true, example = "1")
            @PathVariable Long tradeId) {
        return ApiResponse.success(tradeService.cancelTrade(tradeId, currentUser), "交易已取消");
    }

    /**
     * 【业务功能】：根据交易ID查询单笔交易详情与清算日志
     * 【对应前端页面】：views/enterprise/OrdersManage.vue 订单详情弹窗
     * 【对应前端API】：api/trade.ts -> getTrade()
     * 【可见UI操作】：点击订单行中的【查看明细】链接
     */
    @AuditLog(module = "碳交易", action = "获取交易详情", description = "碳交易 获取交易详情")
    @GetMapping("/{tradeId}")
    @Operation(
        summary = "获取交易详情", 
        description = "根据交易ID获取碳交易的完整信息，包括交易双方、配额数量、价格、状态等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = TradeResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "交易不存在"
        )
    })
    public ApiResponse<TradeResponse> getTrade(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "交易ID", required = true, example = "1")
            @PathVariable Long tradeId) {
        return ApiResponse.success(tradeService.getTrade(tradeId, currentUser));
    }

    /**
     * 【业务功能】：管理端/监管端分页查询公开碳交易市场订单与履约记录
     * 【对应前端页面】：views/admin/DataStatistics.vue, views/third-party/Monitor.vue
     * 【对应前端API】：api/trade.ts -> listTrades()
     * 【可见UI操作】：查看全网碳交易流水列表
     */
    @AuditLog(module = "碳交易", action = "查询交易列表", description = "碳交易 查询交易列表")
    @GetMapping("/list")
    @Operation(
        summary = "查询交易列表", 
        description = "分页查询碳交易记录。管理员、审核员、第三方机构可访问。支持多条件筛选。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "查询成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限访问"
        )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'THIRD_PARTY')")
    public ApiResponse<Page<TradeResponse>> listTrades(
            @Parameter(description = "卖方企业ID筛选", example = "1")
            @RequestParam(required = false) Long sellerId,
            @Parameter(description = "买方企业ID筛选", example = "2")
            @RequestParam(required = false) Long buyerId,
            @Parameter(description = "交易类型筛选 (1-P2P, 2-拍卖)", example = "1")
            @RequestParam(required = false) Integer tradeType,
            @Parameter(description = "交易状态筛选 (0-待审核, 1-已完成, 2-已取消)", example = "1")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(tradeService.listTrades(sellerId, buyerId, tradeType, status, page, size));
    }

    /**
     * 【业务功能】：企业用户查询自身参与的交易订单（买方/卖方，支持编号、对手方、时间段多维筛选）
     * 【对应前端页面】：views/enterprise/OrdersManage.vue, views/enterprise/CompanyDashboard.vue
     * 【对应前端API】：api/trade.ts -> getMyTrades()
     * 【可见UI操作】：订单管理页面按交易状态、交易类型（P2P/拍卖）筛选列表
     */
    @AuditLog(module = "碳交易", action = "查询我的交易", description = "碳交易 查询我的交易")
    @GetMapping("/my-trades")
    @Operation(
        summary = "查询我的交易", 
        description = "企业用户查询与自己相关的交易列表（作为买方或卖方）。",
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
    public ApiResponse<Page<TradeResponse>> listMyTrades(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "交易类型筛选", example = "1")
            @RequestParam(required = false) Integer tradeType,
            @Parameter(description = "交易状态筛选", example = "1")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "交易编号模糊搜索")
            @RequestParam(required = false) String tradeNo,
            @Parameter(description = "Counterparty keyword filter")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Identity filter (buyer/seller)", example = "buyer")
            @RequestParam(required = false) String identity,
            @Parameter(description = "开始时间（yyyy-MM-dd HH:mm:ss）")
            @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间（yyyy-MM-dd HH:mm:ss）")
            @RequestParam(required = false) String endTime,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size) {
        java.time.LocalDateTime start = parseDateTime(startTime);
        java.time.LocalDateTime end = parseDateTime(endTime);
        return ApiResponse.success(tradeService.listMyTrades(
                currentUser, tradeType, status, tradeNo, keyword, identity, start, end, page, size));
    }

    private java.time.LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return java.time.LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (java.time.format.DateTimeParseException e) {
            try {
                return java.time.LocalDate.parse(value).atStartOfDay();
            } catch (java.time.format.DateTimeParseException ex) {
                return null;
            }
        }
    }
}
