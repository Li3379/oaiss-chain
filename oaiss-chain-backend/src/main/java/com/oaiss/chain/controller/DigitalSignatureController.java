package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.DataIsolation;
import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.dto.RsaKeyPairResponse;
import com.oaiss.chain.dto.SignatureResult;
import com.oaiss.chain.dto.SignatureVerifyRequest;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.DigitalSignatureService;
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

/**
 * ==============================================================================
 * 【业务模块】：08. 密码学信任基石与防篡改数字签名 (Digital Signature & Cryptography)
 * 【服务角色】：企业用户 (ENTERPRISE)、审核员 (REVIEWER)、系统管理员 (ADMIN)、第三方监管 (THIRD_PARTY)
 * 【对应前端页面与路由】：
 *   - 个人中心证书管理: views/enterprise/UserProfile.vue -> 路由: /enterprise/user/profile
 *   - 碳核算填报自动签名: views/enterprise/CarbonUpload.vue -> 路由: /enterprise/carbon/upload
 *   - 核查员在线验签: views/auditor/AuditList.vue -> 路由: /auditor/audit/list
 * 【调用前端 API 模块】：src/api/signature.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/signature")
@RequiredArgsConstructor
@Tag(name = "08. 数字签名", description = "RSA密钥管理、碳报告签名验签、数据加解密等数字签名相关接口")
public class DigitalSignatureController {

    private final DigitalSignatureService digitalSignatureService;

    // ==================== 密钥管理 ====================

    /**
     * 【业务功能】：为当前用户生成专属 RSA-2048 非对称密钥对（私钥受AES保护存储，公钥指纹展示）
     * 【对应前端页面】：views/enterprise/UserProfile.vue (/enterprise/user/profile)
     * 【对应前端API】：api/signature.ts -> generateKeyPair()
     * 【可见UI操作】：在“数字签名证书”卡片点击【重新生成密钥对】按钮
     */
    @AuditLog(module = "数字签名", action = "生成RSA密钥对", description = "数字签名 生成RSA密钥对")
    @PostMapping("/keypair/generate")
    @Operation(
        summary = "生成RSA密钥对",
        description = "为当前用户生成新的RSA密钥对。如果用户已有有效密钥对，将自动撤销旧密钥。密钥有效期为2年。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "生成成功",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RsaKeyPairResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未登录"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5001",
            description = "密钥生成失败"
        )
    })
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'REVIEWER', 'THIRD_PARTY')")
    public ApiResponse<RsaKeyPairResponse> generateKeyPair(
            @Parameter(hidden = true)
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        return ApiResponse.success(
            digitalSignatureService.generateKeyPair(userDetails.getUserId()),
            "密钥对生成成功"
        );
    }

    /**
     * 【业务功能】：获取当前用户的 RSA 密钥对元数据、公钥与就绪状态（不返回私钥明文）
     * 【对应前端页面】：views/enterprise/UserProfile.vue, views/enterprise/CarbonUpload.vue
     * 【对应前端API】：api/signature.ts -> getKeyPair()
     * 【可见UI操作】：填报页面加载时探测企业证书就绪状态（显示就绪绿标）
     */
    @AuditLog(module = "数字签名", action = "获取当前用户密钥对信息", description = "数字签名 获取当前用户密钥对信息")
    @GetMapping("/keypair")
    @Operation(
        summary = "获取当前用户密钥对信息",
        description = "获取当前用户的RSA密钥对信息，包括公钥、状态、有效期等。注意：不返回私钥。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = RsaKeyPairResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未登录"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5002",
            description = "密钥对不存在"
        )
    })
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'REVIEWER', 'THIRD_PARTY')")
    public ApiResponse<RsaKeyPairResponse> getKeyPair(
            @Parameter(hidden = true)
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        return ApiResponse.success(digitalSignatureService.getKeyPair(userDetails.getUserId()));
    }

    /**
     * 【业务功能】：撤销并销毁当前用户的密钥对（密钥注销/轮换）
     * 【对应前端页面】：views/enterprise/UserProfile.vue (/enterprise/user/profile)
     * 【对应前端API】：api/signature.ts -> deleteKeyPair()
     * 【可见UI操作】：在证书管理卡片点击【吊销/作废密钥】按钮并确认
     */
    @AuditLog(module = "数字签名", action = "撤销当前用户密钥对", description = "数字签名 撤销当前用户密钥对")
    @DeleteMapping("/keypair")
    @Operation(
        summary = "撤销当前用户密钥对",
        description = "撤销当前用户的RSA密钥对。撤销后需要重新生成密钥对才能继续使用签名功能。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "撤销成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未登录"
        )
    })
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'REVIEWER', 'THIRD_PARTY')")
    public ApiResponse<Void> revokeKeyPair(
            @Parameter(hidden = true)
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        digitalSignatureService.revokeKeyPair(userDetails.getUserId());
        return ApiResponse.success(null, "密钥对已撤销");
    }

    // ==================== 签名与验签 ====================

    /**
     * 【业务功能】：使用企业私钥对碳报告原始摘要数据生成 SHA256withRSA 数字签名
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload)
     * 【对应前端API】：api/signature.ts -> signData()
     * 【可见UI操作】：点击【提交审核】时系统静默调用私钥对报告进行电子防伪签名
     */
    @AuditLog(module = "数字签名", action = "对碳报告数据进行签名", description = "数字签名 对碳报告数据进行签名")
    @PostMapping("/sign")
    @Operation(
        summary = "对碳报告数据进行签名",
        description = "使用当前用户的私钥对碳报告数据进行数字签名。签名算法为SHA256withRSA。需要用户已有有效的RSA密钥对。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "签名成功",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = SignatureResult.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未登录"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5002",
            description = "密钥对不存在"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5003",
            description = "密钥已失效或过期"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5004",
            description = "签名失败"
        )
    })
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<SignatureResult> signReport(
            @Parameter(description = "报告数据（JSON字符串）", required = true)
            @RequestBody String reportData,
            @Parameter(hidden = true)
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        return ApiResponse.success(
            digitalSignatureService.signReport(userDetails.getUserId(), reportData),
            "报告签名成功"
        );
    }

    /**
     * 【业务功能】：使用申报企业公钥校验碳报告签名真实性（防篡改检测）
     * 【对应前端页面】：views/auditor/AuditList.vue (/auditor/audit/list)
     * 【对应前端API】：api/signature.ts -> verifySignature()
     * 【可见UI操作】：核查员打开报告审核抽屉，点击【验证数字签名】按钮，展示验签通过状态
     */
    @AuditLog(module = "数字签名", action = "验证碳报告签名", description = "数字签名 验证碳报告签名")
    @PostMapping("/verify")
    @Operation(
        summary = "验证碳报告签名",
        description = "验证碳报告的数字签名是否有效。可指定签名者用户ID，或由系统自动识别。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "验签完成",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = SignatureResult.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未登录"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5002",
            description = "签名者密钥对不存在"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5006",
            description = "验签过程出错"
        )
    })
    @PreAuthorize("hasAnyRole('REVIEWER', 'THIRD_PARTY', 'ADMIN')")
    public ApiResponse<SignatureResult> verifySignature(
            @Parameter(description = "验签请求参数", required = true)
            @Valid @RequestBody SignatureVerifyRequest request) {
        
        Long signerId = request.getSignerId() != null ? 
            request.getSignerId() : request.getReportId();
        
        boolean isValid = digitalSignatureService.verifySignature(
            signerId,
            request.getReportData(),
            request.getSignatureData()
        );

        return ApiResponse.success(
            SignatureResult.builder()
                .valid(isValid)
                .signerId(signerId)
                .build(),
            isValid ? "签名验证通过" : "签名验证失败"
        );
    }

    // ==================== 加密与解密 ====================

    /**
     * 【业务功能】：使用审核员公钥加密企业商业机密数据（非对称加密传输）
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue
     * 【对应前端API】：api/signature.ts -> encryptData()
     * 【可见UI操作】：上传敏感凭证时加密封包
     */
    @AuditLog(module = "数字签名", action = "为审核员加密数据", description = "数字签名 为审核员加密数据")
    @PostMapping("/encrypt")
    @Operation(
        summary = "为审核员加密数据",
        description = "使用审核员的公钥加密数据，确保数据传输安全。只有对应的审核员私钥才能解密。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "加密成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未登录"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5002",
            description = "审核员密钥对不存在"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5007",
            description = "加密失败"
        )
    })
    @PreAuthorize("hasRole('ENTERPRISE')")
    @DataIsolation
    public ApiResponse<String> encryptForReviewer(
            @Parameter(description = "待加密的数据", required = true)
            @RequestBody String data,
            @Parameter(description = "审核员用户ID", required = true)
            @RequestParam Long reviewerId) {
        return ApiResponse.success(
            digitalSignatureService.encryptForReviewer(data, reviewerId),
            "数据加密成功"
        );
    }

    /**
     * 【业务功能】：审核员/企业使用自身私钥解密受保护的数据明文
     * 【对应前端页面】：views/auditor/AuditList.vue
     * 【对应前端API】：api/signature.ts -> decryptData()
     * 【可见UI操作】：核查员查阅敏感凭据时调用解密
     */
    @AuditLog(module = "数字签名", action = "解密来自企业的数据", description = "数字签名 解密来自企业的数据")
    @PostMapping("/decrypt")
    @Operation(
        summary = "解密来自企业的数据",
        description = "使用当前用户的私钥解密数据。适用于审核员或企业解密对方发送的加密数据。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "解密成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "请求参数错误"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未登录"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5002",
            description = "密钥对不存在"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "5008",
            description = "解密失败"
        )
    })
    @PreAuthorize("hasAnyRole('REVIEWER', 'ENTERPRISE')")
    @DataIsolation
    public ApiResponse<String> decryptData(
            @Parameter(description = "加密的数据（Base64编码）", required = true)
            @RequestBody String encryptedData,
            @Parameter(hidden = true)
            @AuthenticationPrincipal JwtUserDetails userDetails) {
        
        String decryptedData;
        if (userDetails.getUserType() == 2) { // REVIEWER
            decryptedData = digitalSignatureService.decryptForReviewer(encryptedData, userDetails.getUserId());
        } else { // ENTERPRISE
            decryptedData = digitalSignatureService.decryptForEnterprise(encryptedData, userDetails.getUserId());
        }
        
        return ApiResponse.success(decryptedData, "数据解密成功");
    }
}
