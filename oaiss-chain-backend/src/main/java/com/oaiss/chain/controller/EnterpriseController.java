package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.ContactUpdateRequest;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.EnterpriseAdmissionService;
import com.oaiss.chain.service.EnterpriseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：17. 控排企业主体档案与法定配额资产 (Enterprise Account & Quota)
 * 【服务角色】：企业用户 (ENTERPRISE)、系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - 企业档案管理: views/enterprise/EnterpriseInfo.vue -> 路由: /enterprise/info
 *   - 企业综合大屏: views/enterprise/CompanyDashboard.vue -> 路由: /enterprise/company/dashboard
 *   - 个人中心准入证书: views/enterprise/UserProfile.vue -> 路由: /enterprise/user/profile
 *   - P2P与拍卖大厅: views/enterprise/TradingP2P.vue, TradingMarket.vue
 * 【调用前端 API 模块】：src/api/enterprise.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/enterprise")
@RequiredArgsConstructor
@Tag(name = "17. 企业用户管理", description = "企业用户专属功能，包括企业信息、配额管理等")
public class EnterpriseController {

    private final EnterpriseService enterpriseService;
    private final EnterpriseAdmissionService enterpriseAdmissionService;

    /**
     * 【业务功能】：企业用户查看自身的市场准入证书列表与生效资质
     * 【对应前端页面】：views/enterprise/UserProfile.vue (/enterprise/user/profile), CompanyDashboard.vue
     * 【对应前端API】：api/enterprise.ts -> getMyEnterpriseAdmission()
     * 【可见UI操作】：个人中心/企业主页展示准入证书卡片及审核状态（有效/吊销）
     */
    @AuditLog(module = "企业管理", action = "GETMYADMISSION", description = "企业管理 GETMYADMISSION")
    @GetMapping("/admission/my")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @Operation(summary = "查看自身准入证书", description = "企业用户查看自身的准入证书列表。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限，仅企业用户可访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<List<EnterpriseAdmission>> getMyAdmission(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        Long enterpriseId = currentUser.getEnterpriseId();
        if (enterpriseId == null) {
            throw BusinessException.of(ErrorCode.PARAM_ERROR, "error.user.notEnterprise");
        }
        List<EnterpriseAdmission> admissions = enterpriseAdmissionService.getMyCertificate(enterpriseId);
        return ApiResponse.success(admissions);
    }

    /**
     * 【业务功能】：获取当前登录企业的主体工商档案与资质信息
     * 【对应前端页面】：views/enterprise/EnterpriseInfo.vue (/enterprise/info), CompanyDashboard.vue
     * 【对应前端API】：api/enterprise.ts -> getEnterpriseInfo()
     * 【可见UI操作】：进入企业信息页面或综合大屏自动加载企业名称、行业、统一社会信用代码等
     */
    @AuditLog(module = "企业管理", action = "GETENTERPRISEINFO", description = "企业管理 GETENTERPRISEINFO")
    @GetMapping("/info")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @Operation(summary = "获取企业信息", description = "获取当前登录企业的详细信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非企业用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Enterprise> getEnterpriseInfo(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        Enterprise enterprise = enterpriseService.getEnterpriseInfo(currentUser.getUserId());
        return ApiResponse.success(enterprise);
    }

    /**
     * 【业务功能】：查询当前企业的法定碳配额资产账户（总配额、可用配额、已履约配额与使用率）
     * 【对应前端页面】：views/enterprise/CompanyDashboard.vue, views/enterprise/EnterpriseInfo.vue
     * 【对应前端API】：api/enterprise.ts -> getQuotaInfo()
     * 【可见UI操作】：综合看板上方核心资产卡片与配额使用率环形进度条渲染
     */
    @AuditLog(module = "企业管理", action = "获取碳配额信息", description = "企业管理 获取碳配额信息")
    @GetMapping("/quota")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @Operation(summary = "获取碳配额信息", description = "获取企业的碳配额详情，包括总额度、已用额度、可交易额度等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非企业用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Map<String, Object>> getQuotaInfo(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        Map<String, Object> quotaInfo = enterpriseService.getQuotaInfo(currentUser.getUserId());
        return ApiResponse.success(quotaInfo);
    }

    /**
     * 【业务功能】：修改企业法定联系人与应急联系电话
     * 【对应前端页面】：views/enterprise/EnterpriseInfo.vue (/enterprise/info)
     * 【对应前端API】：api/enterprise.ts -> updateContact()
     * 【可见UI操作】：在“联系人信息”表单修改后，点击【保存联系人】按钮
     */
    @AuditLog(module = "企业管理", action = "UPDATECONTACT", description = "企业管理 UPDATECONTACT")
    @PutMapping("/contact")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @Operation(summary = "更新联系方式", description = "更新企业的联系人和联系电话")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非企业用户")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Void> updateContact(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @Valid @RequestBody ContactUpdateRequest request) {
        enterpriseService.updateContact(currentUser.getUserId(), request.getContactPerson(), request.getContactPhone());
        return ApiResponse.success(null, "联系方式更新成功");
    }

    /**
     * 【业务功能】：根据企业ID查询指定公开企业的主体档案信息
     * 【对应前端页面】：views/enterprise/TradingP2P.vue, views/enterprise/OrdersManage.vue, views/auditor/AuditList.vue
     * 【对应前端API】：api/enterprise.ts -> getEnterpriseById()
     * 【可见UI操作】：在P2P转让中选择对手方，或查看交易对手/报告申报企业基本资料
     */
    @AuditLog(module = "企业管理", action = "GETENTERPRISEBYID", description = "企业管理 GETENTERPRISEBYID")
    @GetMapping("/{enterpriseId}")
    @Operation(summary = "获取指定企业信息", description = "根据企业ID获取企业公开信息（需要权限）")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "企业不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ApiResponse<Enterprise> getEnterpriseById(
            @Parameter(description = "企业ID", required = true, example = "1") @PathVariable Long enterpriseId) {
        Enterprise enterprise = enterpriseService.getEnterpriseById(enterpriseId);
        return ApiResponse.success(enterprise);
    }
}
