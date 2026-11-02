package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.PasswordChangeRequest;
import com.oaiss.chain.dto.UserInfoResponse;
import com.oaiss.chain.dto.UserProfileUpdateRequest;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.annotation.RateLimit;

/**
 * ==============================================================================
 * 【业务模块】：02. 用户中心与个人资料安全管理 (User Profile & Account)
 * 【服务角色】：全系统角色（企业、审核员、第三方监管、管理员）
 * 【对应前端页面与路由】：
 *   - 个人中心页面: views/enterprise/UserProfile.vue -> 路由: /enterprise/user/profile
 *   - 全局会话活跃度统计: layout/index.vue (停留时长定时打点)
 *   - 注册查重: views/Login.vue (/login)
 * 【调用前端 API 模块】：src/api/user.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "02. 用户中心", description = "用户个人资料管理、密码修改等个人账户相关接口")
public class UserController {

    private final UserService userService;

    /**
     * 【业务功能】：获取当前登录用户的完整个人资料、企业关联信息与权限
     * 【对应前端页面】：views/enterprise/UserProfile.vue (/enterprise/user/profile)
     * 【对应前端API】：api/user.ts -> getProfile()
     * 【可见UI操作】：进入个人中心页面，自动加载并填充个人资料表单与安全设置状态
     */
    @AuditLog(module = "用户管理", action = "获取当前用户信息", description = "用户管理 获取当前用户信息")
    @GetMapping("/profile")
    @Operation(
        summary = "获取当前用户信息", 
        description = "获取当前登录用户的完整个人资料信息，包括基本信息、企业信息（企业用户）、权限等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = UserInfoResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", 
            description = "未登录或Token已过期"
        )
    })
    public ApiResponse<UserInfoResponse> getProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        return ApiResponse.success(userService.getCurrentUserInfo(currentUser));
    }

    /**
     * 【业务功能】：根据用户ID查询指定用户的公开基本信息
     * 【对应前端页面】：系统管理用户详情、各端审计人查看弹窗
     * 【对应前端API】：api/user.ts -> getUserById()
     * 【可见UI操作】：查看审核员或用户详情时触发
     */
    @AuditLog(module = "用户管理", action = "根据ID获取用户信息", description = "用户管理 根据ID获取用户信息")
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    @Operation(
        summary = "根据ID获取用户信息", 
        description = "根据用户ID获取用户信息。仅管理员或用户本人可获取该用户资料。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = UserInfoResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "用户不存在"
        )
    })
    public ApiResponse<UserInfoResponse> getUserById(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId) {
        return ApiResponse.success(userService.getUserById(userId, currentUser));
    }

    /**
     * 【业务功能】：修改当前用户的个人资料（姓名、联系方式、头像等）
     * 【对应前端页面】：views/enterprise/UserProfile.vue (/enterprise/user/profile)
     * 【对应前端API】：api/user.ts -> updateProfile()
     * 【可见UI操作】：在“基本资料”表单编辑姓名、手机号等后，点击【保存修改】按钮
     */
    @AuditLog(module = "用户管理", action = "更新用户资料", description = "用户管理 更新用户资料")
    @PutMapping("/profile")
    @Operation(
        summary = "更新用户资料", 
        description = "更新当前用户的个人资料信息，如姓名、联系方式、头像等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "更新成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = UserInfoResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", 
            description = "未登录"
        )
    })
    public ApiResponse<UserInfoResponse> updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "用户资料更新请求", required = true)
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.success(userService.updateProfile(currentUser, request));
    }

    /**
     * 【业务功能】：用户安全修改登录密码
     * 【对应前端页面】：views/enterprise/UserProfile.vue (/enterprise/user/profile)
     * 【对应前端API】：api/user.ts -> changePassword()
     * 【可见UI操作】：在“安全设置”面板录入原密码与新密码后，点击【确认修改密码】按钮
     */
    @AuditLog(module = "用户管理", action = "修改密码", description = "用户管理 修改密码")
    @PutMapping("/password")
    @Operation(
        summary = "修改密码", 
        description = "修改当前用户的登录密码。需要验证原密码，新密码需满足安全要求。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "密码修改成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", 
            description = "原密码错误"
        )
    })
    public ApiResponse<Void> changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "修改密码请求", required = true)
            @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(currentUser, request);
        return ApiResponse.success();
    }

    /**
     * 【业务功能】：校验用户名是否已被注册占用（注册实时查重）
     * 【对应前端页面】：views/Login.vue (/login 注册Tab)
     * 【对应前端API】：api/user.ts -> checkUsername()
     * 【可见UI操作】：用户名输入框失焦后触发异步查重，若占用显示红色提示
     */
    @AuditLog(module = "用户管理", action = "CHECKUSERNAME", description = "用户管理 CHECKUSERNAME")
    @GetMapping("/check-username")
    @RateLimit(key = "user:check-username", limit = 20, period = 60, limitType = RateLimit.LimitType.IP)
    @Operation(
        summary = "检查用户名是否可用", 
        description = "检查指定用户名是否已被注册，用于注册时实时校验。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "检查完成，返回是否可用"
        )
    })
    public ApiResponse<Boolean> checkUsername(
            @Parameter(description = "要检查的用户名", required = true, example = "test_user")
            @RequestParam String username) {
        return ApiResponse.success(userService.isUsernameAvailable(username));
    }

    /**
     * 【业务功能】：校验邮箱是否已被绑定占用
     * 【对应前端页面】：views/Login.vue (/login 注册Tab), UserProfile.vue
     * 【对应前端API】：api/user.ts -> checkEmail()
     * 【可见UI操作】：邮箱输入框失焦后触发异步查重
     */
    @AuditLog(module = "用户管理", action = "CHECKEMAIL", description = "用户管理 CHECKEMAIL")
    @GetMapping("/check-email")
    @RateLimit(key = "user:check-email", limit = 20, period = 60, limitType = RateLimit.LimitType.IP)
    @Operation(
        summary = "检查邮箱是否可用", 
        description = "检查指定邮箱是否已被注册，用于注册时实时校验。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "检查完成，返回是否可用"
        )
    })
    public ApiResponse<Boolean> checkEmail(
            @Parameter(description = "要检查的邮箱", required = true, example = "test@example.com")
            @RequestParam String email) {
        return ApiResponse.success(userService.isEmailAvailable(email));
    }

    /**
     * 【业务功能】：定时上报用户当前页面会话停留活跃时长
     * 【对应前端页面】：全局布局 layout/index.vue, UserProfile.vue
     * 【对应前端API】：api/user.ts -> reportStayTime()
     * 【可见UI操作】：前端心跳定时器静默上报当前在线停留秒数
     */
    @AuditLog(module = "用户管理", action = "RECORD_STAY_TIME", description = "用户管理 累加在线停留时长")
    @PostMapping("/stay-time")
    @Operation(summary = "上报在线停留时间", description = "上报用户会话增量停留时长（秒），后端按用户累加统计")
    public ApiResponse<Void> recordStayTime(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            @Parameter(description = "增量停留秒数", required = true, example = "60")
            @RequestParam Long seconds) {
        userService.recordStayTime(currentUser.getUserId(), seconds);
        return ApiResponse.success(null);
    }

    /**
     * 【业务功能】：查询当前用户的累计系统在线停留时长与活跃度指标
     * 【对应前端页面】：views/enterprise/UserProfile.vue (/enterprise/user/profile)
     * 【对应前端API】：api/user.ts -> getStayTimeStats()
     * 【可见UI操作】：个人中心卡片展示用户累计在线时长（时/分/秒）
     */
    @AuditLog(module = "用户管理", action = "GET_STAY_TIME", description = "用户管理 获取用户在线停留时间统计")
    @GetMapping("/stay-time")
    @Operation(summary = "获取在线停留时间统计", description = "获取当前用户的在线停留时间统计（累计秒数、小时分钟秒格式）")
    public ApiResponse<java.util.Map<String, Object>> getStayTime(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        return ApiResponse.success(userService.getStayTimeStats(currentUser.getUserId()));
    }
}
