package com.oaiss.chain.controller;

import com.oaiss.chain.dto.*;
import com.oaiss.chain.entity.CarbonCoinTransaction;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.CarbonCoinService;
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
import com.oaiss.chain.annotation.DataIsolation;

/**
 * ==============================================================================
 * 【业务模块】：18. 碳币金融资产代币钱包 (Carbon Coin Wallet)
 * 【服务角色】：企业用户 (ENTERPRISE)、系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - 碳币账户页面: views/enterprise/CarbonCoin.vue -> 路由: /enterprise/carbon-coin/account
 *   - 企业综合大屏: views/enterprise/CompanyDashboard.vue -> 路由: /enterprise/company/dashboard
 *   - 拍卖大厅资金卡片: views/enterprise/TradingMarket.vue
 * 【调用前端 API 模块】：src/api/carbonCoin.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/carbon-coin")
@RequiredArgsConstructor
@Tag(name = "18. 碳币交易管理", description = "碳币账户管理、充值、交易流水查询、碳配额购买/出售")
public class CarbonCoinController {

    private final CarbonCoinService carbonCoinService;

    /**
     * 【业务功能】：查询当前企业/用户的碳币钱包账户信息与当前可用/冻结余额
     * 【对应前端页面】：views/enterprise/CarbonCoin.vue (/enterprise/carbon-coin/account), views/enterprise/CompanyDashboard.vue
     * 【对应前端API】：api/carbonCoin.ts -> getMyAccount()
     * 【可见UI操作】：进入碳币账户页面自动展示当前碳币总资产余额卡片
     */
    @AuditLog(module = "碳币管理", action = "GETACCOUNT", description = "碳币管理 GETACCOUNT")
    @GetMapping("/account")
    @Operation(summary = "获取碳币账户", description = "获取碳币账户信息。不传userId时获取当前登录用户的账户，传userId时获取指定用户的账户（需管理员权限）。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取账户信息"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "用户ID无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @DataIsolation
    public ApiResponse<CarbonCoinAccountResponse> getAccount(
            @Parameter(description = "用户ID（不传则获取当前用户）", example = "1")
            @RequestParam(required = false) Long userId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        Long targetUserId = userId != null ? userId : currentUser.getUserId();
        if (userId != null && !userId.equals(currentUser.getUserId())
                && !currentUser.getRoles().contains("ADMIN")) {
            throw com.oaiss.chain.exception.AuthorizationException.permissionDenied("无权限查询他人账户");
        }
        return ApiResponse.success(carbonCoinService.getOrCreateAccount(targetUserId));
    }

    /**
     * 【业务功能】：向指定用户或企业碳币账户进行充值（资金入账）
     * 【对应前端页面】：views/enterprise/CarbonCoin.vue (/enterprise/carbon-coin/account)
     * 【对应前端API】：api/carbonCoin.ts -> recharge()
     * 【可见UI操作】：点击【充值】按钮输入充值金额并确认
     */
    @AuditLog(module = "碳币管理", action = "RECHARGE", description = "碳币管理 RECHARGE")
    @PostMapping("/recharge")
    @Operation(summary = "充值碳币", description = "充值碳币到指定用户的账户，支持人民币兑换碳币。仅管理员可操作。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "充值成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "充值金额无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户账户不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    @Parameter(name = "userId", description = "用户ID", required = true, example = "1")
    public ApiResponse<CarbonCoinAccountResponse> recharge(
            @RequestParam Long userId,
            @Valid @RequestBody CarbonCoinRechargeRequest request) {
        return ApiResponse.success(carbonCoinService.recharge(userId, request));
    }

    /**
     * 【业务功能】：用碳币兑换配额（1 枚碳币 = 1 吨排放权）
     * 【对应前端页面】：views/enterprise/CarbonCoin.vue (/enterprise/carbon-coin/account)
     * 【可见UI操作】：在碳币账户页点【兑换配额】，输入枚数后确认，配额进入可交易余额
     *
     * <p>这是碳币的**唯一出口**。碳币不支持提现，只能换回配额用于履约或挂单。
     * 没有这个出口，企业拿到碳币后无处可用，「减排有奖」就只是账面数字。
     */
    @AuditLog(module = "碳币管理", action = "REDEEMQUOTA", description = "碳币管理 REDEEMQUOTA")
    @PostMapping("/redeem-quota")
    @Operation(summary = "碳币兑换配额",
            description = "1 枚碳币兑换 1 吨配额。扣减碳币余额，等量配额进入可交易余额。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "兑换成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "数量无效或余额不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ENTERPRISE') or hasRole('ADMIN')")
    public ApiResponse<CarbonCoinAccountResponse> redeemQuota(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "兑换枚数", required = true, example = "285.80")
            @RequestParam java.math.BigDecimal amount) {
        return ApiResponse.success(carbonCoinService.redeemQuota(
                currentUser.getUserId(), amount, currentUser.getUserId()));
    }

    /**
     * 【业务功能】：分页查询碳币账户收支交易明细
     * 【对应前端页面】：views/enterprise/CarbonCoin.vue (/enterprise/carbon-coin/account)
     * 【对应前端API】：api/carbonCoin.ts -> getTransactions()
     * 【可见UI操作】：查看“交易明细”表格，展示充值、扣减与交易结算记录
     */
    @AuditLog(module = "碳币管理", action = "GETTRANSACTIONS", description = "碳币管理 GETTRANSACTIONS")
    @GetMapping("/transactions")
    @Operation(summary = "交易流水", description = "查询碳币交易流水记录，支持按交易类型筛选，分页返回。不传userId时查询当前用户。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取交易流水"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @DataIsolation
    public ApiResponse<Page<CarbonCoinTransaction>> getTransactions(
            @Parameter(description = "用户ID（不传则查询当前用户）", example = "1") @RequestParam(required = false) Long userId,
            @Parameter(description = "交易类型：1-充值, 2-购买配额, 3-出售配额, 4-历史转账遗留值（转账功能已下线）", example = "1") @RequestParam(required = false) Integer txType,
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        // 权限校验：仅管理员可指定 userId 查阅任意企业流水，普通企业强制绑定查询本账号流水
        Long targetUserId = (currentUser != null && currentUser.isAdmin() && userId != null)
                ? userId
                : (currentUser != null ? currentUser.getUserId() : userId);
        return ApiResponse.success(carbonCoinService.getTransactions(targetUserId, txType, page, size));
    }
}
