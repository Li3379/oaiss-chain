package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.dto.*;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ==============================================================================
 * 【业务模块】：01. 统一身份认证与访问控制 (User Authentication & IAM)
 * 【服务角色】：全系统角色（企业 ENTERPRISE、审核员 REVIEWER、监管 THIRD_PARTY、管理员 ADMIN）
 * 【对应前端页面与路由】：
 *   - 登录页面: views/Login.vue -> 路由: /login
 *   - 顶部导航栏: layout/index.vue (右上角登出、用户信息展示)
 *   - 全局认证鉴权: router/index.ts (路由守卫), stores/user.ts (状态持久化)
 * 【调用前端 API 模块】：src/api/auth.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "01. 认证管理", description = "用户登录、注册、验证码、Token刷新等认证相关接口")
public class AuthController {

    private final AuthService authService;

    /**
     * 【业务功能】：用户账号密码认证登录，签发双 Token (Access + Refresh Token)
     * 【对应前端页面】：views/Login.vue (/login)
     * 【对应前端API】：api/auth.ts -> login()
     * 【可见UI操作】：输入用户名、密码与验证码后，点击【登录】按钮或按回车键
     */
    @AuditLog(module = "User Authentication", action = "用户登录", description = "User Authentication 用户登录")
    @PostMapping("/login")
    @RateLimit(key = "auth:login", limit = 5, period = 60, limitType = RateLimit.LimitType.IP_USER)
    @Operation(
        summary = "用户登录", 
        description = "使用用户名密码进行登录，支持图形验证码校验。登录成功返回JWT令牌。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "登录成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = LoginResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", 
            description = "用户名或密码错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "1005", 
            description = "验证码错误或已过期"
        )
    })
    public ApiResponse<LoginResponse> login(
            @Parameter(description = "登录请求参数", required = true)
            @Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 【业务功能】：新排碳企业或用户注册申报
     * 【对应前端页面】：views/Login.vue (/login 注册Tab)
     * 【对应前端API】：api/auth.ts -> register()
     * 【可见UI操作】：切换至“企业注册”Tab，填写组织机构代码、账号密码等后点击【注册账号】
     */
    @AuditLog(module = "User Authentication", action = "用户注册", description = "User Authentication 用户注册")
    @PostMapping("/register")
    @RateLimit(key = "auth:register", limit = 3, period = 300, limitType = RateLimit.LimitType.IP)
    @Operation(
        summary = "用户注册", 
        description = "注册新用户账号。支持企业用户、审核员、第三方机构、管理员等不同类型用户注册。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "注册成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = LoginResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "1001", 
            description = "用户名已存在"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "1002", 
            description = "邮箱已被注册"
        )
    })
    public ApiResponse<LoginResponse> register(
            @Parameter(description = "注册请求参数", required = true)
            @Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request), "注册成功");
    }

    /**
     * 【业务功能】：生成防刷图形验证码
     * 【对应前端页面】：views/Login.vue (/login)
     * 【对应前端API】：api/auth.ts / api/captcha.ts -> generateCaptcha()
     * 【可见UI操作】：进入登录页自动渲染验证码图片，或用户点击验证码图片刷新
     */
    @AuditLog(module = "User Authentication", action = "GETCAPTCHA", description = "User Authentication GETCAPTCHA")
    @PostMapping("/captcha")
    @RateLimit(key = "auth:captcha", limit = 20, period = 60, limitType = RateLimit.LimitType.IP)
    @Operation(
        summary = "获取图形验证码", 
        description = "生成图形验证码，返回验证码图片Base64和验证码Key。验证码有效期5分钟。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CaptchaResponse.class))
        )
    })
    public ApiResponse<CaptchaResponse> getCaptcha() {
        return ApiResponse.success(authService.generateCaptcha());
    }

    /**
     * 【业务功能】：刷新 Access Token 延长会话有效期（双Token无感刷新机制）
     * 【对应前端页面】：全局网络请求拦截器 api/request.ts
     * 【对应前端API】：api/request.ts 自动拦截 401 触发刷新重试
     * 【可见UI操作】：后台无感刷新，避免用户操作过程中被强制退出
     */
    @AuditLog(module = "User Authentication", action = "刷新访问令牌", description = "User Authentication 刷新访问令牌")
    @PostMapping("/refresh")
    @RateLimit(key = "auth:refresh", limit = 10, period = 60, limitType = RateLimit.LimitType.IP_USER)
    @Operation(
        summary = "刷新访问令牌", 
        description = "使用RefreshToken获取新的AccessToken，延长用户会话有效期。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "刷新成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = LoginResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", 
            description = "RefreshToken无效或已过期"
        )
    })
    public ApiResponse<LoginResponse> refreshToken(
            @Parameter(description = "刷新令牌", required = true)
            @RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
        return ApiResponse.success(authService.refreshToken(refreshToken));
    }

    /**
     * 【业务功能】：客户端网络 IP 安全检查与访问受限检测
     * 【对应前端页面】：views/Login.vue (/login)
     * 【对应前端API】：api/auth.ts -> checkIp()
     * 【可见UI操作】：登录前置网络安全探针，若在黑名单则弹出阻断提示
     */
    @AuditLog(module = "User Authentication", action = "IP访问检查", description = "User Authentication IP访问检查")
    @GetMapping("/check-ip")
    @RateLimit(key = "auth:check-ip", limit = 30, period = 60, limitType = RateLimit.LimitType.IP)
    @Operation(
        summary = "IP访问检查", 
        description = "检查当前IP地址是否在允许访问的白名单中。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "检查成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "1003", 
            description = "IP不在白名单中"
        )
    })
    public ApiResponse<Boolean> checkIp() {
        return ApiResponse.success(true);
    }

    /**
     * 【业务功能】：获取当前已登录用户的详细身份上下文与权限角色
     * 【对应前端页面】：layout/index.vue, stores/user.ts
     * 【对应前端API】：api/auth.ts -> getCurrentUser()
     * 【可见UI操作】：页面刷新或登录跳转后，拉取用户姓名、角色并在顶部导航栏和侧边栏菜单展示
     */
    @AuditLog(module = "User Authentication", action = "获取当前用户信息", description = "User Authentication 获取当前用户信息")
    @GetMapping("/me")
    @Operation(
        summary = "获取当前用户信息", 
        description = "获取当前已登录用户的详细信息，包括用户ID、用户名、用户类型等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = JwtUserDetails.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", 
            description = "未登录或Token已过期"
        )
    })
    public ApiResponse<JwtUserDetails> getCurrentUser(
            @Parameter(hidden = true)
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        return ApiResponse.success(userDetails);
    }

    /**
     * 【业务功能】：用户安全登出，销毁服务端会话并废止当前 Token
     * 【对应前端页面】：layout/index.vue
     * 【对应前端API】：api/auth.ts -> logout()
     * 【可见UI操作】：点击右上角个人头像下拉框中的【退出登录】菜单项
     */
    @AuditLog(module = "User Authentication", action = "用户登出", description = "User Authentication 用户登出")
    @PostMapping("/logout")
    @Operation(
        summary = "用户登出", 
        description = "用户退出登录，清除服务端Token缓存。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "登出成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", 
            description = "未登录"
        )
    })
    public ApiResponse<Void> logout(
            @Parameter(hidden = true)
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @Parameter(hidden = true)
            HttpServletRequest request) {
        // 提取当前Token
        String bearerToken = request.getHeader("Authorization");
        String token = (bearerToken != null && bearerToken.startsWith("Bearer "))
                ? bearerToken.substring(7) : null;
        authService.logout(userDetails.getUsername(), token);
        return ApiResponse.success(null, "登出成功");
    }

    /**
     * 【业务功能】：用户修改自身登录密码
     * 【对应前端页面】：views/enterprise/UserProfile.vue (/enterprise/user/profile)
     * 【对应前端API】：api/user.ts -> changePassword()
     * 【可见UI操作】：个人中心“安全设置”面板录入原密码与新密码后，点击【确认修改】按钮
     */
    @AuditLog(module = "User Authentication", action = "修改密码", description = "User Authentication 修改密码")
    @PutMapping("/password")
    @Operation(
        summary = "修改密码", 
        description = "用户修改自己的登录密码，需要验证原密码。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "修改成功"
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
            @Parameter(description = "修改密码请求参数", required = true)
            @Valid @RequestBody PasswordChangeRequest request,
            @Parameter(hidden = true)
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        authService.changePassword(userDetails.getUsername(), request);
        return ApiResponse.success(null, "密码修改成功");
    }
}
