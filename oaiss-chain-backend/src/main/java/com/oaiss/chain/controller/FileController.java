package com.oaiss.chain.controller;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：07. MinIO 对象存储证据附件上传与生命周期 (File & Object Storage)
 * 【服务角色】：企业用户 (ENTERPRISE)、系统管理员 (ADMIN)、审核员 (REVIEWER)
 * 【对应前端页面与路由】：
 *   - 碳核算填报附件上传: views/enterprise/CarbonUpload.vue -> 路由: /enterprise/carbon/upload
 *   - 审核员查阅佐证原件: views/auditor/AuditList.vue -> 路由: /auditor/audit/list
 *   - 碳中和项目材料提交: views/enterprise/CarbonNeutral.vue -> 路由: /enterprise/carbon-neutral/projects
 * 【调用前端 API 模块】：src/api/file.ts
 * ==============================================================================
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "07. 文件管理", description = "文件上传、下载、删除、预签名等文件操作接口")
@Validated
@PreAuthorize("isAuthenticated()")
public class FileController {

    private final MinioService minioService;

    /**
     * 【业务功能】：上传单个碳排放监测报告、化验单凭据或证书文件至 MinIO 对象存储
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue (/enterprise/carbon/upload), CarbonNeutral.vue
     * 【对应前端API】：api/file.ts -> uploadFile()
     * 【可见UI操作】：点击或拖拽文件到“上传凭证材料”区域触发上传
     */
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    @AuditLog(module = "文件管理", action = "上传单个文件", description = "文件管理 上传单个文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "上传单个文件", 
        description = "上传文件到MinIO对象存储。支持常见文件格式，单文件最大100MB。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "上传成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = MinioService.UploadResult.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "文件为空或格式不支持"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "413", 
            description = "文件大小超过限制"
        )
    })
    public ApiResponse<MinioService.UploadResult> uploadFile(
            @Parameter(description = "要上传的文件", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "存储文件夹，如 reports/, certificates/", example = "reports/")
            @RequestParam(value = "folder", required = false) String folder,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {

        MinioService.UploadResult result;
        if (currentUser != null) {
            Long userId = resolveUserId(currentUser);
            Long enterpriseId = resolveEnterpriseId(currentUser);
            if (enterpriseId != null) {
                result = minioService.uploadFile(file, folder, userId, enterpriseId);
            } else {
                result = minioService.uploadFile(file, folder, userId);
            }
        } else {
            result = minioService.uploadFile(file, folder);
        }
        return ApiResponse.success(result, "文件上传成功");
    }

    /**
     * 【业务功能】：批量上传多份核查佐证附件或图片凭据
     * 【对应前端页面】：CarbonUpload.vue, CarbonNeutralDetail.vue
     * 【对应前端API】：api/file.ts -> batchUploadFiles()
     * 【可见UI操作】：多选文件上传时批量并发执行
     */
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    @AuditLog(module = "文件管理", action = "批量上传文件", description = "文件管理 批量上传文件")
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "批量上传文件", 
        description = "批量上传多个文件到MinIO。单次最多上传10个文件。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "批量上传成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "文件列表为空"
        )
    })
    public ApiResponse<List<MinioService.UploadResult>> uploadFiles(
            @Parameter(description = "文件列表", required = true)
            @RequestPart("files") List<MultipartFile> files,
            @Parameter(description = "存储文件夹", example = "batch/")
            @RequestParam(value = "folder", required = false) String folder,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {

        List<MinioService.UploadResult> results = files.stream()
                .map(file -> {
                    if (currentUser != null) {
                        Long userId = resolveUserId(currentUser);
                        Long enterpriseId = resolveEnterpriseId(currentUser);
                        if (enterpriseId != null) {
                            return minioService.uploadFile(file, folder, userId, enterpriseId);
                        } else {
                            return minioService.uploadFile(file, folder, userId);
                        }
                    } else {
                        return minioService.uploadFile(file, folder);
                    }
                })
                .toList();
        
        return ApiResponse.success(results, "批量上传成功，共 " + results.size() + " 个文件");
    }

    /**
     * 【业务功能】：安全下载已上传至 MinIO 的证据原件二进制流
     * 【对应前端页面】：views/auditor/AuditList.vue, CarbonUpload.vue
     * 【对应前端API】：api/file.ts -> downloadFile()
     * 【可见UI操作】：点击附件列表中的【下载原件】图标
     * 【权限说明】：审核员(REVIEWER, userType=2)与第三方监管(THIRD_PARTY, userType=3)
     *   承担核查与监管职责，必须能查阅待审报告所附的佐证原件，否则无法在「看到凭据」的前提下
     *   作出审核结论，因此注解层显式放行这两类角色。
     *   真正的属主/企业归属校验不在注解层，而是由 checkDownloadPermission() 统一裁决
     *   （企业用户仅可读自己或本企业文件，元数据缺失一律 Fail-Closed）。
     *   此前注解只写了 ENTERPRISE/ADMIN，会先于业务层拦截 REVIEWER/THIRD_PARTY，
     *   使 checkDownloadPermission() 内的 userType==2/3 放行分支永远不可达 —— 此为注解与业务语义
     *   不一致的实现缺陷，不是权限需求。
     */
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'REVIEWER', 'THIRD_PARTY', 'ADMIN')")
    @AuditLog(module = "文件管理", action = "下载文件", description = "文件管理 下载文件")
    @GetMapping("/download")
    @Operation(
        summary = "下载文件", 
        description = "从MinIO下载指定文件。返回文件流。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "下载成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "文件不存在"
        )
    })
    public void downloadFile(
            @Parameter(description = "对象名称（文件路径）", required = true, example = "reports/2024/report.pdf")
            @RequestParam @NotBlank String objectName,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser,
            HttpServletResponse response) {
        
        validateObjectName(objectName);
        checkDownloadPermission(objectName, currentUser);

        try (InputStream inputStream = minioService.downloadFile(objectName)) {
            MinioService.FileInfo fileInfo = minioService.getFileInfo(objectName);
            
            response.setContentType(fileInfo.contentType());
            response.setContentLengthLong(fileInfo.size());
            
            // 安全响应头：杜绝 MIME 嗅探与浏览器执行存储型 XSS 脚本
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");

            String filename = objectName.contains("/")
                    ? objectName.substring(objectName.lastIndexOf("/") + 1)
                    : objectName;
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + encodedFilename + "\"");
            
            inputStream.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("File download failed: {}", objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件下载失败", e);
        }
    }

    /**
     * 【业务功能】：从对象存储中删除指定的作废文件
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue
     * 【对应前端API】：api/file.ts -> deleteFile()
     * 【可见UI操作】：上传列表中点击单个文件右侧的【删除】图标
     */
    @AuditLog(module = "文件管理", action = "删除文件", description = "文件管理 删除文件")
    @DeleteMapping
    @Operation(
        summary = "删除文件",
        description = "删除MinIO中的指定文件。删除后无法恢复。仅管理员或文件所有者可删除。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "删除成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "文件不存在"
        )
    })
    public ApiResponse<Void> deleteFile(
            @Parameter(description = "对象名称", required = true, example = "reports/2024/report.pdf")
            @RequestParam @NotBlank String objectName,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {

        validateObjectName(objectName);
        checkDeletePermission(objectName, currentUser);
        minioService.deleteFile(objectName);
        return ApiResponse.success(null, "文件删除成功");
    }

    /**
     * 【业务功能】：批量删除多个作废的凭证文件
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue, views/enterprise/CarbonNeutral.vue
     * 【对应前端API】：api/file.ts -> batchDeleteFiles()
     * 【可见UI操作】：重置表单或批量清空上传附件列表
     */
    @AuditLog(module = "文件管理", action = "DELETEFILES", description = "文件管理 DELETEFILES")
    @DeleteMapping("/batch")
    @Operation(
        summary = "批量删除文件",
        description = "批量删除MinIO中的多个文件。仅管理员或文件所有者可删除。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "批量删除成功"
        )
    })
    public ApiResponse<Void> deleteFiles(
            @Parameter(description = "对象名称列表", required = true)
            @RequestBody @NotEmpty @Valid List<@NotBlank String> objectNames,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {

        // Check permission for each file
        for (String objectName : objectNames) {
            validateObjectName(objectName);
            checkDeletePermission(objectName, currentUser);
        }
        minioService.deleteFiles(objectNames);
        return ApiResponse.success(null, "批量删除成功，共 " + objectNames.size() + " 个文件");
    }

    /**
     * 【业务功能】：获取文件元数据（大小、哈希校验码、MIME 类型）
     * 【对应前端页面】：views/auditor/AuditList.vue
     * 【对应前端API】：api/file.ts -> getFileInfo()
     * 【可见UI操作】：审核员审查附件完整性时校验文件指纹
     */
    @AuditLog(module = "文件管理", action = "获取文件信息", description = "文件管理 获取文件信息")
    @GetMapping("/info")
    @Operation(
        summary = "获取文件信息", 
        description = "获取文件的元数据信息，包括大小、类型、修改时间等。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功",
            content = @Content(mediaType = "application/json", 
                schema = @Schema(implementation = MinioService.FileInfo.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "文件不存在"
        )
    })
    public ApiResponse<MinioService.FileInfo> getFileInfo(
            @Parameter(description = "对象名称", required = true, example = "reports/2024/report.pdf")
            @RequestParam @NotBlank String objectName,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        
        validateObjectName(objectName);
        checkDownloadPermission(objectName, currentUser);
        MinioService.FileInfo fileInfo = minioService.getFileInfo(objectName);
        return ApiResponse.success(fileInfo);
    }

    /**
     * 【业务功能】：检查指定对象文件是否存在于 MinIO 存储桶中
     * 【对应前端页面】：views/enterprise/CarbonUpload.vue, views/auditor/AuditList.vue
     * 【对应前端API】：api/file.ts -> fileExists()
     * 【可见UI操作】：预览前探测文件有效性
     */
    @AuditLog(module = "文件管理", action = "FILEEXISTS", description = "文件管理 FILEEXISTS")
    @GetMapping("/exists")
    @Operation(
        summary = "检查文件是否存在", 
        description = "检查指定文件是否存在于MinIO存储中。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "检查完成"
        )
    })
    public ApiResponse<Boolean> fileExists(
            @Parameter(description = "对象名称", required = true, example = "reports/2024/report.pdf")
            @RequestParam @NotBlank String objectName) {
        
        validateObjectName(objectName);
        boolean exists = minioService.fileExists(objectName);
        return ApiResponse.success(exists);
    }

    /**
     * 【业务功能】：获取带时效安全保护的直接下载/在线预览预签名 URL
     * 【对应前端页面】：views/auditor/AuditList.vue, views/enterprise/CarbonUpload.vue
     * 【对应前端API】：api/file.ts -> getPresignedUrl()
     * 【可见UI操作】：点击附件进行在线预览（图片/PDF）
     * 【权限说明】：与 GET /file/download 同源放行 REVIEWER/THIRD_PARTY —— 审核员需要临时签名链接
     *   才能在前端直接打开/下载佐证附件（私有桶的裸 URL 无签名必 403）。
     *   归属校验同样由 checkDownloadPermission() 负责，注解层只做角色初筛。
     */
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'REVIEWER', 'THIRD_PARTY', 'ADMIN')")
    @AuditLog(module = "文件管理", action = "获取文件预签名URL", description = "文件管理 获取文件预签名URL")
    @GetMapping("/presigned-url")
    @Operation(
        summary = "获取文件预签名URL", 
        description = "获取文件的临时访问URL，有效期1小时。适用于前端直接展示文件。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "文件不存在"
        )
    })
    public ApiResponse<String> getPresignedUrl(
            @Parameter(description = "对象名称", required = true, example = "reports/2024/report.pdf")
            @RequestParam @NotBlank String objectName,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {
        
        validateObjectName(objectName);
        checkDownloadPermission(objectName, currentUser);
        String url = minioService.getPresignedUrl(objectName);
        return ApiResponse.success(url);
    }

    /**
     * 【业务功能】：获取前端直传 MinIO 的预签名安全 PUT URL
     * 【对应前端页面】：大文件分片直传场景
     * 【对应前端API】：api/file.ts -> getPresignedUploadUrl()
     * 【可见UI操作】：直传通道初始化
     */
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    @AuditLog(module = "文件管理", action = "GETPRESIGNEDUPLOADURL", description = "文件管理 GETPRESIGNEDUPLOADURL")
    @GetMapping("/presigned-upload-url")
    @Operation(
        summary = "获取上传预签名URL", 
        description = "获取文件直传的预签名URL，用于前端直接上传文件到MinIO。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "获取成功"
        )
    })
    public ApiResponse<String> getPresignedUploadUrl(
            @Parameter(description = "目标对象名称", required = true, example = "uploads/new_file.pdf")
            @RequestParam @NotBlank String objectName) {
        
        validateObjectNameForUpload(objectName);
        String url = minioService.getPresignedUploadUrl(objectName);
        return ApiResponse.success(url);
    }

    /**
     * 【业务功能】：分页列出指定目录前缀下的所有归档文件
     * 【对应前端页面】：文件归档列表
     * 【对应前端API】：api/file.ts -> listFiles()
     * 【可见UI操作】：浏览凭证库
     */
    @PreAuthorize("hasAnyRole('ENTERPRISE', 'ADMIN')")
    @AuditLog(module = "文件管理", action = "LISTFILES", description = "文件管理 LISTFILES")
    @GetMapping("/list")
    @Operation(
        summary = "列出文件", 
        description = "列出指定文件夹下的所有文件。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "查询成功"
        )
    })
    public ApiResponse<MinioService.FileListResult> listFiles(
            @Parameter(description = "文件夹前缀", example = "reports/2024/")
            @RequestParam(value = "prefix", required = false) String prefix,
            @Parameter(description = "页码（从1开始）", example = "1")
            @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(value = "size", required = false) Integer size,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtUserDetails currentUser) {

        if (prefix != null && !prefix.isBlank()) {
            if (prefix.contains("..") || prefix.startsWith("/") || prefix.startsWith("\\") || prefix.contains("\\")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "非法文件夹前缀");
            }
        }

        MinioService.FileListResult result = currentUser != null
                ? minioService.listFiles(prefix, page, size, resolveUserId(currentUser), isAdmin(currentUser))
                : minioService.listFiles(prefix, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 【业务功能】：在对象存储桶内部复制或归档文件
     * 【对应前端页面】：报告存证归档
     * 【对应前端API】：api/file.ts -> copyFile()
     * 【可见UI操作】：归档触发
     */
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "文件管理", action = "复制文件", description = "文件管理 复制文件")
    @PostMapping("/copy")
    @Operation(
        summary = "复制文件", 
        description = "复制文件到新位置。",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "复制成功"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "源文件不存在"
        )
    })
    public ApiResponse<Void> copyFile(
            @Parameter(description = "源对象名称", required = true, example = "reports/original.pdf")
            @RequestParam @NotBlank String sourceObjectName,
            @Parameter(description = "目标对象名称", required = true, example = "reports/copy.pdf")
            @RequestParam @NotBlank String targetObjectName) {
        
        validateObjectName(sourceObjectName);
        validateObjectName(targetObjectName);
        minioService.copyFile(sourceObjectName, targetObjectName);
        return ApiResponse.success(null, "文件复制成功");
    }

    // ==================== 权限与路径安全检查 ====================

    private void validateObjectName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "对象名称不能为空");
        }
        if (objectName.contains("..") || objectName.startsWith("/") || objectName.startsWith("\\")
                || objectName.contains("\\") || objectName.contains("\0")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法对象路径");
        }
    }

    private void validateObjectNameForUpload(String objectName) {
        validateObjectName(objectName);
        int dotIdx = objectName.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx == objectName.length() - 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标对象必须包含合法文件扩展名");
        }
        String ext = objectName.substring(dotIdx).toLowerCase();
        if (!MinioService.ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不允许直传此类型的文件: " + ext);
        }
        String lowerName = objectName.toLowerCase();
        for (String dangerous : MinioService.DANGEROUS_SUBSTRINGS) {
            if (lowerName.contains(dangerous)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "目标路径包含非法扩展名或脚本特征: " + dangerous);
            }
        }
        if (minioService.fileExists(objectName)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标文件已存在，禁止覆盖");
        }
    }

    private void checkDownloadPermission(String objectName, JwtUserDetails currentUser) {
        if (currentUser == null) {
            return;
        }
        Integer userType = resolveUserType(currentUser);
        // 管理员(4)、审核员(2)、第三方监管(3) 具备核查与监管权，允许查阅全平台证据附件
        if (userType != null && (userType == 4 || userType == 2 || userType == 3)) {
            return;
        }

        Long userId = resolveUserId(currentUser);
        Long enterpriseId = resolveEnterpriseId(currentUser);
        Long fileOwner = minioService.getFileOwner(objectName);
        Long fileEnterpriseId = minioService.getFileEnterpriseId(objectName);

        // Fail-Closed: 无法证明文件属主或企业归属时，普通企业用户拒绝访问
        if (fileOwner == null && fileEnterpriseId == null) {
            log.warn("Download denied: file {} has no owner metadata, user {} denied (Fail-Closed)",
                    objectName, userId);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权访问此文件");
        }

        boolean isOwner = fileOwner != null && fileOwner.equals(userId);
        boolean isSameEnterprise = enterpriseId != null && fileEnterpriseId != null && enterpriseId.equals(fileEnterpriseId);

        if (!isOwner && !isSameEnterprise) {
            log.warn("Download denied: user {} (enterprise {}) attempted to access file {} owned by user {} (enterprise {})",
                    userId, enterpriseId, objectName, fileOwner, fileEnterpriseId);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权访问此文件");
        }
    }

    /**
     * 检查删除权限：管理员可删除任意文件，普通用户只能删除自己上传的文件（Fail-Closed）
     */
    private void checkDeletePermission(String objectName, JwtUserDetails currentUser) {
        if (currentUser == null) {
            return;
        }
        Long userId = resolveUserId(currentUser);
        Integer userType = resolveUserType(currentUser);

        // 管理员（userType=4）可删除任意文件
        if (userType != null && userType == 4) {
            return;
        }

        // 非管理员：检查是否为文件所有者（Fail-Closed: 属主为空一律拒绝）
        Long fileOwner = minioService.getFileOwner(objectName);
        if (fileOwner == null) {
            log.warn("Delete denied: file {} has no owner metadata, user {} denied (Fail-Closed)",
                    objectName, userId);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权删除此文件");
        }
        if (!fileOwner.equals(userId)) {
            log.warn("Delete denied: user {} (type {}) attempted to delete file {} owned by {}",
                    userId, userType, objectName, fileOwner);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权删除此文件");
        }
    }

    private Long resolveUserId(JwtUserDetails currentUser) {
        return currentUser != null ? currentUser.getUserId() : null;
    }

    private Long resolveEnterpriseId(JwtUserDetails currentUser) {
        return currentUser != null ? currentUser.getEnterpriseId() : null;
    }

    private Integer resolveUserType(JwtUserDetails currentUser) {
        return currentUser != null ? currentUser.getUserType() : null;
    }

    private boolean isAdmin(JwtUserDetails currentUser) {
        return currentUser != null && resolveUserType(currentUser) != null && resolveUserType(currentUser) == 4;
    }
}
