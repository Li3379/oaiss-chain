package com.oaiss.chain.controller;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.constant.ErrorMessage;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.AccountPermissionList;
import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.AccountPermissionListRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.AdminUserService;
import com.oaiss.chain.service.ChainReconciliationService;
import com.oaiss.chain.service.EnterpriseAdmissionService;
import com.oaiss.chain.service.ReviewerQualificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：16. 系统管理与资质证书管控 (Admin Governance & Certification)
 * 【服务角色】：系统管理员 (ADMIN)
 * 【对应前端页面与路由】：
 *   - 用户管理页面: views/admin/SystemUsers.vue -> 路由: /admin/system/users
 *   - 证书管理页面: views/admin/CertificateManage.vue -> 路由: /admin/certificates
 *   - 统计数据大盘: views/admin/DataStatistics.vue -> 路由: /admin/data/statistics
 *   - 系统配置中心: views/admin/SystemConfig.vue -> 路由: /admin/system/config
 * 【调用前端 API 模块】：src/api/admin.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "16. 管理后台", description = "管理员后台管理接口，包括用户管理、系统监控、数据统计等")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final AccountPermissionListRepository permissionRepository;
    private final EnterpriseAdmissionService enterpriseAdmissionService;
    private final ReviewerQualificationService reviewerQualificationService;
    private final AdminUserService adminUserService;
    private final ChainReconciliationService chainReconciliationService;

    /**
     * 【业务功能】：分页多条件查询系统全量用户列表（支持按角色、启用状态、用户名检索）
     * 【对应前端页面】：views/admin/SystemUsers.vue (/admin/system/users)
     * 【对应前端API】：api/admin.ts -> getUserList()
     * 【可见UI操作】：进入用户管理页面加载用户表格，或在顶部筛选栏按角色/状态过滤
     */
    @AuditLog(module = "系统管理", action = "查询用户列表", description = "系统管理 查询用户列表")
    @GetMapping("/users")
    @Operation(
        summary = "查询用户列表", 
        description = "分页查询系统用户列表。支持按用户类型、状态筛选。",
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
    public ApiResponse<Page<User>> listUsers(
            @Parameter(description = "用户类型筛选 (1-企业, 2-审核员, 3-第三方, 4-管理员)", example = "1")
            @RequestParam(required = false) Integer userType,
            @Parameter(description = "状态筛选 (0-禁用, 1-启用)", example = "1")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "用户名关键词模糊检索", example = "enterprise")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size) {

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users;
        if (keyword != null && !keyword.isBlank()) {
            users = userRepository.searchUsers(keyword.trim(), userType, status, pageable);
        } else if (userType != null) {
            users = userRepository.findByUserTypeAndDeletedFalse(userType, pageable);
        } else if (status != null) {
            users = userRepository.findByStatusAndDeletedFalse(status, pageable);
        } else {
            users = userRepository.findByDeletedFalse(pageable);
        }

        return ApiResponse.success(users);
    }

    /**
     * 【业务功能】：启用或禁用系统用户账号（安全管控与账号冻结）
     * 【对应前端页面】：views/admin/SystemUsers.vue (/admin/system/users)
     * 【对应前端API】：api/admin.ts -> updateUserStatus()
     * 【可见UI操作】：在用户表格操作列点击【禁用】或【启用】开关，弹出确认框后提交
     */
    @AuditLog(module = "系统管理", action = "更新用户状态", description = "系统管理 更新用户状态")
    @PutMapping("/users/{userId}/status")
    @Operation(
        summary = "更新用户状态", 
        description = "启用或禁用指定用户账号。禁用后用户将无法登录系统。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "更新成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "用户不存在"
        )
    })
    public ApiResponse<Void> updateUserStatus(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "目标状态 (0-禁用, 1-启用)", required = true, example = "1")
            @RequestParam Integer status,
            @AuthenticationPrincipal JwtUserDetails currentUser) {

        // 防止管理员禁用自己的账号
        if (currentUser != null && currentUser.getUserId().equals(userId) && status == 0) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_SELF, ErrorMessage.CANNOT_DISABLE_SELF);
        }

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        user.setStatus(status);
        userRepository.save(user);

        return ApiResponse.success();
    }

    /**
     * 【业务功能】：获取管理后台仪表板的总体用户数与活跃用户数
     * 【对应前端页面】：views/admin/DataStatistics.vue (/admin/data/statistics)
     * 【对应前端API】：api/admin.ts -> getDashboard()
     * 【可见UI操作】：进入数据大屏自动加载顶部统计数字卡片
     */
    @AuditLog(module = "系统管理", action = "获取仪表板数据", description = "系统管理 获取仪表板数据")
    @GetMapping("/dashboard")
    @Operation(
        summary = "获取仪表板数据", 
        description = "获取管理后台仪表板的统计数据，包括用户数、活跃用户等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", 
            description = "无权限"
        )
    })
    public ApiResponse<java.util.Map<String, Object>> getDashboard() {
        java.util.Map<String, Object> dashboard = new java.util.HashMap<>();
        dashboard.put("totalUsers", userRepository.count());
        dashboard.put("activeUsers", userRepository.findByDeletedFalse(
                PageRequest.of(0, 1)).getTotalElements());
        return ApiResponse.success(dashboard);
    }

    /**
     * 【业务功能】：获取系统用户角色构成统计与各组织类型宏观分布指标
     * 【对应前端页面】：views/admin/DataStatistics.vue (/admin/data/statistics)
     * 【对应前端API】：api/admin.ts -> getStatistics()
     * 【可见UI操作】：大屏中心区域渲染“用户角色构成饼图”与各项组织统计卡片
     */
    @AuditLog(module = "系统管理", action = "获取系统统计数据", description = "系统管理 获取系统统计数据")
    @GetMapping("/statistics")
    @Operation(
        summary = "获取系统统计数据", 
        description = "获取系统运营统计数据，包括各类型用户数量、注册趋势等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功"
        )
    })
    public ApiResponse<java.util.Map<String, Object>> getStatistics() {
        java.util.Map<String, Object> statistics = new java.util.HashMap<>();
        
        // 用户统计
        statistics.put("totalUsers", userRepository.count());
        statistics.put("enterpriseCount", userRepository.findByUserTypeAndDeletedFalse(1, PageRequest.of(0, 1)).getTotalElements());
        statistics.put("reviewerCount", userRepository.findByUserTypeAndDeletedFalse(2, PageRequest.of(0, 1)).getTotalElements());
        statistics.put("thirdPartyCount", userRepository.findByUserTypeAndDeletedFalse(3, PageRequest.of(0, 1)).getTotalElements());
        
        return ApiResponse.success(statistics);
    }

    /**
     * 【业务功能】：获取平台基础运行配置与参数（区块链开启状态、上传上限、会话超时等）
     * 【对应前端页面】：views/admin/SystemConfig.vue (/admin/system/config)
     * 【对应前端API】：api/admin.ts -> getConfig()
     * 【可见UI操作】：进入系统配置页面展示当前只读参数列表
     */
    @AuditLog(module = "系统管理", action = "获取系统配置", description = "系统管理 获取系统配置")
    @GetMapping("/config")
    @Operation(
        summary = "获取系统配置",
        description = "获取系统运行配置参数。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    public ApiResponse<java.util.Map<String, Object>> getConfig() {
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("systemName", "OAISS CHAIN 双碳链动系统");
        config.put("version", "1.0.0");
        // 与 spring.servlet.multipart.max-file-size(100MB) 保持一致，避免配置页展示值与真实上限不符
        config.put("maxUploadSize", "100MB");
        config.put("sessionTimeout", 3600);
        config.put("enableCaptcha", true);
        config.put("enableBlockChain", true);
        return ApiResponse.success(config);
    }

    /**
     * 【业务功能】：查询系统权限清单列表与角色访问控制规则定义
     * 【对应前端页面】：views/admin/SystemConfig.vue (/admin/system/config)
     * 【对应前端API】：api/admin.ts -> getPermissions()
     * 【可见UI操作】：系统配置页面切换至“权限矩阵与安全策略”Tab查看
     */
    @AuditLog(module = "系统管理", action = "GETPERMISSIONS", description = "系统管理 GETPERMISSIONS")
    @GetMapping("/permissions")
    @Operation(
        summary = "获取权限列表",
        description = "获取系统所有权限定义列表。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    public ApiResponse<java.util.List<AccountPermissionList>> getPermissions() {
        java.util.List<AccountPermissionList> permissions = permissionRepository.findByDeletedFalseOrderBySortOrderAsc();
        return ApiResponse.success(permissions);
    }

    // ==================== 企业准入证书管理 ====================

    /**
     * 【业务功能】：审核并向合规排碳企业正式签发市场准入证书
     * 【对应前端页面】：views/admin/CertificateManage.vue (/admin/certificates)
     * 【对应前端API】：api/admin.ts -> issueEnterpriseAdmission()
     * 【可见UI操作】：在企业准入列表操作列点击【签发准入证书】按钮
     */
    @AuditLog(module = "系统管理", action = "ISSUEADMISSION", description = "系统管理 ISSUEADMISSION")
    @PostMapping("/enterprise-admission/{enterpriseId}/issue")
    @Operation(
        summary = "签发企业准入证书",
        description = "为指定企业签发准入证书。同一企业已有有效证书时拒绝重复签发。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "签发成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "企业不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "已有有效证书")
    })
    public ApiResponse<EnterpriseAdmission> issueAdmission(
            @Parameter(description = "企业ID", required = true, example = "1")
            @PathVariable Long enterpriseId) {
        EnterpriseAdmission admission = enterpriseAdmissionService.issueCertificate(enterpriseId);
        return ApiResponse.success(admission);
    }

    /**
     * 【业务功能】：吊销或作废违规/退出市场的企业准入资格证书
     * 【对应前端页面】：views/admin/CertificateManage.vue (/admin/certificates)
     * 【对应前端API】：api/admin.ts -> revokeEnterpriseAdmission()
     * 【可见UI操作】：在企业准入列表操作列点击【吊销证书】按钮并二次确认
     */
    @AuditLog(module = "系统管理", action = "REVOKEADMISSION", description = "系统管理 REVOKEADMISSION")
    @DeleteMapping("/enterprise-admission/{enterpriseId}")
    @Operation(
        summary = "吊销企业准入证书",
        description = "吊销指定企业的有效准入证书。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "吊销成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "无有效证书")
    })
    public ApiResponse<Void> revokeAdmission(
            @Parameter(description = "企业ID", required = true, example = "1")
            @PathVariable Long enterpriseId,
            @Parameter(description = "吊销原因（可选，二次确认框可填写，最长200字）", example = "连续三期报告数据不合规")
            @RequestParam(required = false) String reason) {
        enterpriseAdmissionService.revokeCertificate(enterpriseId, reason);
        return ApiResponse.success();
    }

    /**
     * 【业务功能】：分页查询企业准入资格证书列表（Enterprise Admission）
     * 【对应前端页面】：views/admin/CertificateManage.vue (/admin/certificates)
     * 【对应前端API】：api/admin.ts -> getEnterpriseAdmissionList()
     * 【可见UI操作】：证书管理页面“企业准入资质”Tab加载表格
     */
    @AuditLog(module = "系统管理", action = "LISTADMISSIONS", description = "系统管理 LISTADMISSIONS")
    @GetMapping("/enterprise-admission")
    @Operation(
        summary = "查询准入证书列表",
        description = "分页查询企业准入证书列表。支持按状态筛选。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    public ApiResponse<Page<EnterpriseAdmission>> listAdmissions(
            @Parameter(description = "状态筛选 (1-有效, 2-已吊销)", example = "1")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size) {
        Page<EnterpriseAdmission> admissions = enterpriseAdmissionService.listCertificates(status, page, size);
        return ApiResponse.success(admissions);
    }

    // ==================== 审核员资格证管理 ====================

    /**
     * 【业务功能】：向通过资质考核的核查员签发专业执业资格证书
     * 【对应前端页面】：views/admin/CertificateManage.vue (/admin/certificates)
     * 【对应前端API】：api/admin.ts -> issueReviewerQualification()
     * 【可见UI操作】：在审核员资格列表操作列点击【签发资格证】按钮
     */
    @AuditLog(module = "系统管理", action = "ISSUEQUALIFICATION", description = "系统管理 ISSUEQUALIFICATION")
    @PostMapping("/reviewer-qualification/{reviewerId}/issue")
    @Operation(
        summary = "签发审核员资格证",
        description = "为指定审核员签发资格证。同一审核员已有有效资格证时拒绝重复签发。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "签发成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "已有有效资格证")
    })
    public ApiResponse<ReviewerQualification> issueQualification(
            @Parameter(description = "审核员ID", required = true, example = "1")
            @PathVariable Long reviewerId) {
        ReviewerQualification qualification = reviewerQualificationService.issueCertificate(reviewerId);
        return ApiResponse.success(qualification);
    }

    /**
     * 【业务功能】：吊销或废止违规审核员的专业执业资格证书
     * 【对应前端页面】：views/admin/CertificateManage.vue (/admin/certificates)
     * 【对应前端API】：api/admin.ts -> revokeReviewerQualification()
     * 【可见UI操作】：在审核员资格列表操作列点击【吊销资质】按钮并二次确认
     */
    @AuditLog(module = "系统管理", action = "REVOKEQUALIFICATION", description = "系统管理 REVOKEQUALIFICATION")
    @DeleteMapping("/reviewer-qualification/{reviewerId}")
    @Operation(
        summary = "吊销审核员资格证",
        description = "吊销指定审核员的有效资格证。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "吊销成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "无有效资格证")
    })
    public ApiResponse<Void> revokeQualification(
            @Parameter(description = "审核员ID", required = true, example = "1")
            @PathVariable Long reviewerId,
            @Parameter(description = "吊销原因（可选，二次确认框可填写，最长200字）", example = "审核过程存在利益冲突")
            @RequestParam(required = false) String reason) {
        reviewerQualificationService.revokeCertificate(reviewerId, reason);
        return ApiResponse.success();
    }

    /**
     * 【业务功能】：分页查询第三方审核员专业执业资格证书列表（Reviewer Qualification）
     * 【对应前端页面】：views/admin/CertificateManage.vue (/admin/certificates)
     * 【对应前端API】：api/admin.ts -> getReviewerQualificationList()
     * 【可见UI操作】：证书管理页面“审核员执业资格”Tab加载表格
     */
    @AuditLog(module = "系统管理", action = "LISTQUALIFICATIONS", description = "系统管理 LISTQUALIFICATIONS")
    @GetMapping("/reviewer-qualification")
    @Operation(
        summary = "查询审核员资格证列表",
        description = "分页查询审核员资格证列表。支持按状态筛选。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限")
    })
    public ApiResponse<Page<ReviewerQualification>> listQualifications(
            @Parameter(description = "状态筛选 (1-有效, 2-已吊销)", example = "1")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size) {
        Page<ReviewerQualification> qualifications = reviewerQualificationService.listCertificates(status, page, size);
        return ApiResponse.success(qualifications);
    }

    // ==================== 用户创建与软删除 (TDD GREEN) ====================

    /**
     * 【业务功能】：管理员创建用户账号（审核员等无法自助注册的角色由此通道开通）
     * 【对应前端页面】：views/admin/SystemUsers.vue (/admin/system/users)
     * 【对应前端API】：api/admin.ts -> createUser()
     * 【可见UI操作】：点击【新增用户】按钮，在弹窗表单中录入账号信息提交
     * 【需求依据】：docs/raw 01 §3.4.1 用户管理(A级)；审核员/管理员禁止自助注册，创建通道唯一
     */
    @AuditLog(module = "系统管理", action = "创建用户", description = "系统管理 创建用户")
    @PostMapping("/users")
    @Operation(
        summary = "创建用户",
        description = "管理员创建用户账号。允许 1-企业/2-审核员/3-第三方监管；禁止创建管理员(4)。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ApiResponse<User> createUser(@org.springframework.validation.annotation.Validated @RequestBody com.oaiss.chain.dto.AdminCreateUserRequest request) {
        return ApiResponse.success(adminUserService.createUser(request));
    }

    /**
     * 【业务功能】：管理员删除用户（软删除=下线语义，对应原始需求「信誉分不足用户暂时退出系统」）
     * 【对应前端页面】：views/admin/SystemUsers.vue (/admin/system/users)
     * 【对应前端API】：api/admin.ts -> deleteUser()
     * 【可见UI操作】：在用户表格操作列点击【删除】按钮，二次确认后提交
     * 【守卫】：不可删除自己；不可删除管理员账号；不存在/已删除统一 404 语义
     */
    @AuditLog(module = "系统管理", action = "删除用户", description = "系统管理 删除用户")
    @DeleteMapping("/users/{userId}")
    @Operation(
        summary = "删除用户",
        description = "软删除用户：deleted=true + status=0 + 吊销全部会话。不可删除自己与管理员账号。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ApiResponse<Void> deleteUser(
            @Parameter(description = "用户ID", required = true, example = "7")
            @PathVariable Long userId,
            @AuthenticationPrincipal JwtUserDetails currentUser) {

        if (currentUser != null && currentUser.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF, ErrorMessage.CANNOT_DELETE_SELF);
        }

        adminUserService.deleteUser(userId);
        return ApiResponse.success();
    }

    /**
     * 【业务功能】：链上对账 — 逐条比对「本地已标记上链的报告」与链上真实快照
     * 【对应前端页面】：views/admin/ChainReconciliation.vue (/admin/blockchain)
     * 【对应前端API】：api/admin.ts -> getChainReconciliation()
     * 【可见UI操作】：打开链上管理页，查看四类结果（一致 / 链上缺失 / 内容不一致 / 无法判定）
     *
     * <p>系统原本只有「DB → 链」单向写入，本地是否真的上链只能靠自填的 txHash 相信。
     * 这个端点把链上事实读回来比对，是「还原」唯一可行的前置。</p>
     */
    @AuditLog(module = "系统管理", action = "链上对账", description = "系统管理 链上对账")
    @GetMapping("/blockchain/reconciliation")
    @Operation(summary = "链上对账", description = "比对本地已上链报告与链上快照，输出差异分类与明细")
    public ApiResponse<ChainReconciliationService.ReconciliationResult> reconcileChain(
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10") @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(chainReconciliationService.reconcile(page, size));
    }

    /**
     * 【业务功能】：查询死信队列（超过重试上限仍未上链的消息）
     * 【对应前端页面】：views/admin/ChainReconciliation.vue (/admin/blockchain)
     * 【对应前端API】：api/admin.ts -> getDeadLetters()
     * 【可见UI操作】：链上管理页「死信队列」Tab
     *
     * <p>此前 outbox 转 DEAD 后没有任何查询入口，消息永久滞留库里且无人可见。</p>
     */
    @AuditLog(module = "系统管理", action = "查询死信队列", description = "系统管理 查询死信队列")
    @GetMapping("/outbox/dead-letters")
    @Operation(summary = "查询死信队列", description = "分页查询状态为 DEAD 的发件箱消息")
    public ApiResponse<Page<com.oaiss.chain.entity.OutboxMessage>> listDeadLetters(
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10") @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(chainReconciliationService.listDeadLetters(page, size));
    }

    /**
     * 【业务功能】：重放一条死信（复位为 PENDING，交由后台调度器重新投递）
     * 【对应前端页面】：views/admin/ChainReconciliation.vue (/admin/blockchain)
     * 【对应前端API】：api/admin.ts -> replayDeadLetter()
     * 【可见UI操作】：死信列表操作列点击【重放】
     *
     * <p>只接受 DEAD 状态。重放不会改写链上任何数据——Fabric 账本不可修改，
     * 这里做的只是让「本该上链却没上」的业务重新走一遍正常投递流程。</p>
     */
    @AuditLog(module = "系统管理", action = "重放死信", description = "系统管理 重放死信")
    @PostMapping("/outbox/{id}/replay")
    @Operation(summary = "重放死信", description = "将 DEAD 状态的发件箱消息复位为 PENDING 并清零重试计数")
    public ApiResponse<com.oaiss.chain.entity.OutboxMessage> replayDeadLetter(
            @Parameter(description = "发件箱消息主键", required = true, example = "1")
            @PathVariable Long id) {
        return ApiResponse.success(chainReconciliationService.replayDeadLetter(id));
    }
}
