package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.CaptchaResponse;
import com.oaiss.chain.dto.CaptchaSendRequest;
import com.oaiss.chain.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.annotation.RateLimit;

/**
 * ==============================================================================
 * 【业务模块】：06. 安全验证码与多通道身份核验 (Captcha & Verification)
 * 【服务角色】：全系统角色（企业、审核员、第三方监管、管理员）
 * 【对应前端页面与路由】：
 *   - 登录页面: views/Login.vue -> 路由: /login
 *   - 个人中心验证: views/enterprise/UserProfile.vue -> 路由: /enterprise/user/profile
 * 【调用前端 API 模块】：src/api/captcha.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
@Tag(name = "06. 验证码管理", description = "图形验证码生成与验证接口")
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * 【业务功能】：生成防刷图形验证码（Base64图片与缓存Key）
     * 【对应前端页面】：views/Login.vue (/login)
     * 【对应前端API】：api/captcha.ts -> generateCaptcha()
     * 【可见UI操作】：登录表单右侧验证码图片加载，或用户点击验证码图片刷新
     */
    @AuditLog(module = "验证码", action = "GENERATE", description = "验证码 GENERATE")
    @RateLimit(key = "captcha:generate", limit = 30, period = 60, limitType = RateLimit.LimitType.IP)
    @GetMapping("/generate")
    @Operation(
        summary = "生成图形验证码", 
        description = "生成图形验证码，返回验证码图片的Base64编码和验证码Key。验证码有效期为5分钟。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "生成成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = CaptchaResponse.class))
        )
    })
    public ApiResponse<CaptchaResponse> generate() {
        return ApiResponse.success(captchaService.generateCaptcha());
    }

    /**
     * 【业务功能】：服务端校验用户输入的图形验证码是否正确
     * 【对应前端页面】：views/Login.vue (/login)
     * 【对应前端API】：api/captcha.ts -> verifyCaptcha()
     * 【可见UI操作】：表单提交前置校验或登录流程联动
     */
    @AuditLog(module = "验证码", action = "验证验证码", description = "验证码 验证验证码")
    @PostMapping("/verify")
    @Operation(
        summary = "验证验证码", 
        description = "校验用户输入的验证码是否正确。验证后验证码立即失效。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "验证完成，返回是否正确"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "请求参数错误"
        )
    })
    public ApiResponse<Boolean> verify(
            @Parameter(description = "验证请求，包含captchaKey和captchaCode", required = true)
            @RequestBody Map<String, String> body) {
        String key = body.get("captchaKey");
        String code = body.get("captchaCode");
        return ApiResponse.success(captchaService.verifyCaptcha(key, code));
    }

    /**
     * 【业务功能】：向指定手机号发送短信动态验证码
     * 【对应前端页面】：views/Login.vue, views/enterprise/UserProfile.vue
     * 【对应前端API】：api/captcha.ts -> sendSmsCode()
     * 【可见UI操作】：点击【获取短信验证码】按钮，触发倒计时
     */
    @AuditLog(module = "验证码", action = "发送短信验证码", description = "验证码 发送短信验证码")
    @RateLimit(key = "captcha:sms:send", limit = 1, period = 60, limitType = RateLimit.LimitType.IP)
    @PostMapping("/sms/send")
    @Operation(
        summary = "发送短信验证码", 
        description = "向指定手机号发送短信验证码。用于手机验证、找回密码等场景。验证码有效期为5分钟。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "发送成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "手机号格式错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429", 
            description = "发送过于频繁，请稍后再试"
        )
    })
    public ApiResponse<Void> sendSmsCode(
            @Parameter(description = "发送短信验证码请求", required = true)
            @Valid @RequestBody CaptchaSendRequest request) {
        captchaService.sendSmsCode(request.getTarget(), request.getType());
        return ApiResponse.success(null, "验证码已发送");
    }

    /**
     * 【业务功能】：向指定邮箱发送动态验证码
     * 【对应前端页面】：views/Login.vue, views/enterprise/UserProfile.vue
     * 【对应前端API】：api/captcha.ts -> sendEmailCode()
     * 【可见UI操作】：点击【发送邮箱验证码】按钮
     */
    @AuditLog(module = "验证码", action = "发送邮箱验证码", description = "验证码 发送邮箱验证码")
    @RateLimit(key = "captcha:email:send", limit = 1, period = 60, limitType = RateLimit.LimitType.IP)
    @PostMapping("/email/send")
    @Operation(
        summary = "发送邮箱验证码", 
        description = "向指定邮箱发送验证码。用于邮箱验证、找回密码等场景。验证码有效期为10分钟。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "发送成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "邮箱格式错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429", 
            description = "发送过于频繁"
        )
    })
    public ApiResponse<Void> sendEmailCode(
            @Parameter(description = "发送邮箱验证码请求", required = true)
            @Valid @RequestBody CaptchaSendRequest request) {
        captchaService.sendEmailCode(request.getTarget(), request.getType());
        return ApiResponse.success(null, "验证码已发送至邮箱");
    }
}
