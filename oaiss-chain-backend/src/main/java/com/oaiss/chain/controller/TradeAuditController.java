package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.TradeAudit;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.TradeAuditService;
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

/**
 * ==============================================================================
 * 【业务模块】：25. 成交审核 (Trade Audit)
 * 【服务角色】：审核员（主审）、管理员（兜底）
 * 【对应前端页面】：views/auditor/TradeAudit.vue (/auditor/trade-audit)
 * 【对应前端API】：src/api/tradeAudit.ts -> listPendingAudits / approveTrade / rejectTrade
 * 【可见UI操作】：审核员在工单列表打开待审成交 → 查看风险理由 → 通过或驳回（驳回须填理由）
 *
 * 【授权口径】：
 *   审核员主审 + 管理员兜底，与项目既有的「管理员保留兜底补救」一致。
 *   **第三方监管不得裁决**——监管定位是穿透式只读核查，裁决成交等于干预市场；
 *   监管对审核质量的监督走独立的监督意见，不在这条链路上。
 *   企业不得裁决自己的成交（既是运动员又是裁判）。
 * ==============================================================================
 */
@Tag(name = "25. 成交审核", description = "待审成交工单查询、通过结算、驳回解冻")
@RestController
@RequestMapping("/trade-audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
public class TradeAuditController {

    private final TradeAuditService tradeAuditService;

    /**
     * 裁决请求体。
     *
     * @param remark 审核意见（必填，裁决必须可解释、可追责）
     * @param reason 驳回主理由（可选，缺省 OTHER；通过时忽略）
     */
    public record AuditDecisionRequest(String remark, String reason) {
        public AuditDecisionRequest(String remark) {
            this(remark, null);
        }
    }

    /**
     * 【业务功能】：分页查询成交审核工单（默认待审核，也支持按已通过/已驳回筛选复盘）
     */
    @AuditLog(module = "成交审核", action = "LIST_AUDITS", description = "成交审核 查询工单")
    @GetMapping
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Operation(summary = "查询成交审核工单", description = "按审核态分页查询，不传 status 默认待审核")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<TradeAudit>> listPending(
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "审核态：0无需审 1待审 2通过 3驳回；不传默认 1")
            @RequestParam(required = false) Integer status) {
        Integer target = status != null ? status : TradeAudit.AUDIT_PENDING;
        return ApiResponse.success(tradeAuditService.listByStatus(target, page, size, null));
    }

    /**
     * 【业务功能】：审核通过——此时才真正结算额度与碳币，并触发上链存证
     */
    @AuditLog(module = "成交审核", action = "APPROVE", description = "成交审核 通过")
    @PostMapping("/{tradeId}/approve")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Operation(summary = "审核通过", description = "通过后结算额度与碳币，并触发上链存证")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "审核通过"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "审核意见为空或非待审状态"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Void> approve(
            @Parameter(description = "交易ID", required = true, example = "1") @PathVariable Long tradeId,
            @RequestBody AuditDecisionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        tradeAuditService.approve(tradeId, currentUser.getUserId(), request.remark());
        return ApiResponse.success(null);
    }

    /**
     * 【业务功能】：审核驳回——解冻额度与碳币、扣双方信誉分并归因到该笔交易
     */
    @AuditLog(module = "成交审核", action = "REJECT", description = "成交审核 驳回")
    @PostMapping("/{tradeId}/reject")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    @Operation(summary = "审核驳回", description = "驳回后解冻资产、扣信誉分，交易置已取消")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "驳回成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "审核意见为空或非待审状态"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Void> reject(
            @Parameter(description = "交易ID", required = true, example = "1") @PathVariable Long tradeId,
            @RequestBody AuditDecisionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        String reason = request.reason() == null || request.reason().isBlank()
                ? TradeAudit.REASON_OTHER : request.reason();
        tradeAuditService.reject(tradeId, currentUser.getUserId(), request.remark(), reason);
        return ApiResponse.success(null);
    }
}