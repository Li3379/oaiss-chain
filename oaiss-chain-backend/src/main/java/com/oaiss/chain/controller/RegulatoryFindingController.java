package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.RegulatoryFinding;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.RegulatoryFindingService;
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
 * 【业务模块】：26. 监管线索 (Regulatory Finding)
 * 【服务角色】：第三方监管（本职）、管理员（运维排障）
 * 【对应前端页面】：views/third-party/Monitor.vue (/third-party/monitor) — 线索清单区块
 * 【对应前端API】：src/api/regulatoryFinding.ts -> listFindings / resolveFinding / scanNow
 * 【可见UI操作】：监管在工作台查看未处置线索 → 展开触发依据 → 标记已处置
 *
 * 【授权口径】：
 *   监管看线索、处置线索（记录自己的处理动作）都是本职，属只读延伸——
 *   处置线索不改变任何业务状态，只是给这条"发现"打上处理标记。
 *   手动触发扫描仅管理员（运维动作，且会对全表跑聚合）。
 *   **管理员也保留只读可见性**，便于排障时确认系统是否真的在产出线索。
 * ==============================================================================
 */
@Tag(name = "26. 监管线索", description = "监管线索查询、处置标记、手动触发扫描")
@RestController
@RequestMapping("/regulatory")
@RequiredArgsConstructor
public class RegulatoryFindingController {

    private final RegulatoryFindingService findingService;

    /**
     * 【业务功能】：分页查询未处置线索（监管工作台主视图）
     */
    @AuditLog(module = "监管线索", action = "LIST_FINDINGS", description = "监管线索 查询")
    @GetMapping("/findings")
    @PreAuthorize("hasAnyRole('THIRD_PARTY', 'ADMIN')")
    @Operation(summary = "查询未处置线索", description = "按严重等级排序返回未处置线索")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Page<RegulatoryFinding>> listFindings(
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(findingService.listUnresolved(page, size));
    }

    /**
     * 【业务功能】：标记线索已处置。
     *
     * <p>注意：处置线索不改变任何业务状态（不冻结、不扣分、不下市），
     * 只记录监管已看过并处理了这条发现。真正影响企业状态的动作走管理员，
     * 以守住「监管不干预市场」的定位。
     */
    @AuditLog(module = "监管线索", action = "RESOLVE_FINDING", description = "监管线索 处置")
    @PutMapping("/findings/{findingId}/resolve")
    @PreAuthorize("hasAnyRole('THIRD_PARTY', 'ADMIN')")
    @Operation(summary = "标记线索已处置", description = "记录监管的处理动作，不改变业务状态")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "处置成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "线索已处置"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<RegulatoryFinding> resolveFinding(
            @Parameter(description = "线索ID", required = true, example = "1") @PathVariable Long findingId,
            @RequestBody(required = false) ResolveRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        String note = request != null ? request.note() : null;
        return ApiResponse.success(findingService.resolve(findingId, currentUser.getUserId(), note));
    }

    /**
     * 【业务功能】：手动触发一轮监管核查（运维动作，定时任务之外的补偿入口）
     */
    @AuditLog(module = "监管线索", action = "TRIGGER_SCAN", description = "监管线索 手动扫描")
    @PostMapping("/scan")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "手动触发核查", description = "立即对全部企业跑一轮线索聚合，返回本轮产出统计")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "扫描完成"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Integer> triggerScan() {
        return ApiResponse.success(findingService.scanAll().size());
    }

    /**
     * 【业务功能】：查询未履约清单——实排超出配额且尚未用碳信用抵扣完的报告
     *
     * <p>用途：监管要知道「哪些企业超排了、还差多少吨」。
     * 线索列表（{@code /findings}）只回答「有没有问题」，
     * 本端点回答「差多少」——后者才是催缴与处罚的依据。
     *
     * <p>缺口算法：{@code scope1Emission − quotaAllocatedAmount − shortfallOffset}。
     * 未结算（配额为空）的报告不参与，因为它们还没有可比基准。
     */
    @AuditLog(module = "监管线索", action = "LIST_SHORTFALL", description = "监管线索 未履约清单")
    @GetMapping("/shortfalls")
    @PreAuthorize("hasAnyRole('THIRD_PARTY', 'ADMIN')")
    @Operation(summary = "查询未履约清单",
            description = "返回实排超出配额、且碳信用抵扣后仍有缺口的报告")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> listShortfalls() {
        return ApiResponse.success(findingService.listShortfalls());
    }

    /**
     * 处置说明请求体。
     */
    public record ResolveRequest(String note) {
    }
}