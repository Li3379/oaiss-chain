package com.oaiss.chain.service;

import com.oaiss.chain.config.MinioConfig;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.exception.BusinessException;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO对象存储服务
 * 
 * <p>提供文件上传、下载、删除、预览等功能</p>
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 文件上传结果
     */
    public record UploadResult(String objectName, String url, long size, String contentType) {}

    /**
     * 文件信息
     */
    public record FileInfo(String objectName, long size, String contentType, String etag) {}

    /**
     * 初始化存储桶（如果不存在则创建）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .build());

            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .build());
                log.info("Created MinIO bucket: {}", minioConfig.getBucketName());
            }
        } catch (Exception e) {
            log.warn("MinIO bucket initialization failed — service will be unavailable for file operations: {}", e.getMessage());
        }
    }

    /**
     * 上传文件
     *
     * @param file   文件
     * @param folder 文件夹路径（如：reports/2024）
     * @return 上传结果
     */
    public UploadResult uploadFile(MultipartFile file, String folder) {
        return uploadFile(file, folder, null, null);
    }

    /**
     * 上传文件（携带上传者ID）
     *
     * @param file   文件
     * @param folder 文件夹路径（如：reports/2024）
     * @param uploaderId 上传者用户ID
     * @return 上传结果
     */
    public UploadResult uploadFile(MultipartFile file, String folder, Long uploaderId) {
        return uploadFile(file, folder, uploaderId, null);
    }

    /**
     * 上传文件（携带上传者ID与企业ID）
     *
     * @param file   文件
     * @param folder 文件夹路径（如：reports/2024）
     * @param uploaderId 上传者用户ID
     * @param enterpriseId 上传者企业ID
     * @return 上传结果
     */
    public UploadResult uploadFile(MultipartFile file, String folder, Long uploaderId, Long enterpriseId) {
        validateFile(file);

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String objectName = generateObjectName(folder, extension);

            var putObjectBuilder = PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType());

            // 存储上传者ID与企业ID到自定义元数据（同时兼容标准键名与AWS S3元数据键）
            Map<String, String> userMetadata = new java.util.HashMap<>();
            if (uploaderId != null) {
                userMetadata.put("uploader-id", String.valueOf(uploaderId));
                userMetadata.put("x-amz-meta-uploader-id", String.valueOf(uploaderId));
            }
            if (enterpriseId != null) {
                userMetadata.put("enterprise-id", String.valueOf(enterpriseId));
                userMetadata.put("x-amz-meta-enterprise-id", String.valueOf(enterpriseId));
            }
            if (!userMetadata.isEmpty()) {
                putObjectBuilder.userMetadata(userMetadata);
            }

            minioClient.putObject(putObjectBuilder.build());

            String url = getFileUrl(objectName);
            log.info("File uploaded successfully: {} (uploader: {}, enterprise: {})", objectName, uploaderId, enterpriseId);

            return new UploadResult(objectName, url, file.getSize(), file.getContentType());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件上传者ID
     *
     * @param objectName 对象名称
     * @return 上传者用户ID，无元数据则返回null
     */
    public Long getFileOwner(String objectName) {
        return getMetadataLong(objectName, "uploader-id");
    }

    /**
     * 获取文件所属企业ID
     *
     * @param objectName 对象名称
     * @return 所属企业ID，无元数据则返回null
     */
    public Long getFileEnterpriseId(String objectName) {
        return getMetadataLong(objectName, "enterprise-id");
    }

    private Long getMetadataLong(String objectName, String targetKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());

            Map<String, String> metadata = stat.userMetadata();
            if (metadata != null) {
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    String k = entry.getKey().toLowerCase();
                    if (k.equals(targetKey) || k.equals("x-amz-meta-" + targetKey) || k.endsWith(targetKey)) {
                        try {
                            return Long.parseLong(entry.getValue());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 上传文件（使用InputStream）
     *
     * @param inputStream   输入流
     * @param objectName    对象名称
     * @param contentType   内容类型
     * @param size          文件大小
     * @return 上传结果
     */
    public UploadResult uploadStream(InputStream inputStream, String objectName, 
                                     String contentType, long size) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            
            String url = getFileUrl(objectName);
            log.info("Stream uploaded successfully: {}", objectName);
            
            return new UploadResult(objectName, url, size, contentType);
        } catch (Exception e) {
            log.error("Failed to upload stream", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     *
     * @param objectName 对象名称
     * @return 文件输入流
     */
    public InputStream downloadFile(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("Failed to download file: {}", objectName, e);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在或下载失败");
        }
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     */
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
            log.info("File deleted successfully: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除文件
     *
     * @param objectNames 对象名称列表
     */
    public void deleteFiles(List<String> objectNames) {
        try {
            List<DeleteObject> objects = objectNames.stream()
                    .map(DeleteObject::new)
                    .toList();
            
            minioClient.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .objects(objects)
                    .build());
            
            log.info("Files deleted successfully: {}", objectNames);
        } catch (Exception e) {
            log.error("Failed to delete files", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "批量删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名称
     * @return 是否存在
     */
    public boolean fileExists(String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取文件信息
     *
     * @param objectName 对象名称
     * @return 文件信息
     */
    public FileInfo getFileInfo(String objectName) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
            
            return new FileInfo(
                    objectName,
                    stat.size(),
                    stat.contentType(),
                    stat.etag()
            );
        } catch (Exception e) {
            log.error("Failed to get file info: {}", objectName, e);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
    }

    /**
     * 获取文件预签名URL（用于临时访问）
     *
     * @param objectName 对象名称
     * @return 预签名URL
     */
    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .expiry(minioConfig.getPresignedUrlExpiry(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("Failed to get presigned URL: {}", objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取文件URL失败");
        }
    }

    /**
     * 获取文件上传预签名URL（用于前端直传）
     *
     * @param objectName 对象名称
     * @return 预签名上传URL
     */
    public String getPresignedUploadUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .expiry(minioConfig.getPresignedUrlExpiry(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("Failed to get presigned upload URL: {}", objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取上传URL失败");
        }
    }

    /**
     * 列出文件夹下的文件（分页）
     *
     * @param prefix 文件夹前缀
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 文件列表（带分页信息）
     */
    public FileListResult listFiles(String prefix, Integer page, Integer size) {
        return listFiles(prefix, page, size, null, true);
    }

    /**
     * 列出文件夹下的文件（按用户权限过滤）
     *
     * @param prefix 文件夹前缀
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @param requesterUserId 请求用户ID
     * @param adminView 是否管理员视图
     * @return 文件列表（带分页信息）
     */
    public FileListResult listFiles(String prefix, Integer page, Integer size, Long requesterUserId, boolean adminView) {
        try {
            if (page == null || page < 1) page = 1;
            if (size == null || size < 1) size = 20;
            size = Math.min(size, 1000);

            List<FileInfo> allFiles = new ArrayList<>();
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .prefix(prefix)
                    .recursive(true)
                    .build());

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!adminView) {
                    Long fileOwner = getFileOwner(item.objectName());
                    if (requesterUserId == null || !requesterUserId.equals(fileOwner)) {
                        continue;
                    }
                }
                allFiles.add(new FileInfo(
                        item.objectName(),
                        item.size(),
                        null,
                        item.etag()
                ));
            }

            int total = allFiles.size();
            int fromIndex = Math.min((page - 1) * size, total);
            int toIndex = Math.min(fromIndex + size, total);
            List<FileInfo> pageFiles = allFiles.subList(fromIndex, toIndex);

            return new FileListResult(pageFiles, total, page, size);
        } catch (Exception e) {
            log.error("Failed to list files: {}", prefix, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取文件列表失败");
        }
    }

    /**
     * 文件列表分页结果
     */
    public record FileListResult(List<FileInfo> files, long total, int page, int size) {}

    /**
     * 复制文件
     *
     * @param sourceObjectName 源对象名称
     * @param targetObjectName 目标对象名称
     */
    public void copyFile(String sourceObjectName, String targetObjectName) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(targetObjectName)
                    .source(CopySource.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(sourceObjectName)
                            .build())
                    .build());
            
            log.info("File copied from {} to {}", sourceObjectName, targetObjectName);
        } catch (Exception e) {
            log.error("Failed to copy file from {} to {}", sourceObjectName, targetObjectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件复制失败");
        }
    }

    /**
     * 允许上传的安全文件扩展名白名单（文档、表格、图片、纯文本）
     */
    public static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".csv",
            ".png", ".jpg", ".jpeg", ".txt"
    );

    /**
     * 允许上传的安全MIME媒体类型白名单
     */
    public static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "text/plain",
            "image/png",
            "image/jpeg",
            "application/octet-stream"
    );

    /**
     * 明确禁止的危险扩展名特征或脚本片段（防止双扩展名绕过，如 test.svg.pdf, test.html.txt, shell.php.png）
     */
    public static final java.util.Set<String> DANGEROUS_SUBSTRINGS = java.util.Set.of(
            ".jsp", ".jspx", ".exe", ".bat", ".cmd", ".sh", ".php", ".py",
            ".dll", ".so", ".vbs", ".wsf", ".msi", ".com", ".scr", ".jar", ".war",
            ".svg", ".html", ".htm", ".xhtml", ".shtml", ".xml", ".js", ".mjs"
    );

    /**
     * 保持向后兼容历史引用
     */
    public static final java.util.Set<String> DANGEROUS_EXTENSIONS = DANGEROUS_SUBSTRINGS;

    /**
     * 禁止的危险MIME媒体类型（禁止HTML、SVG矢量XML、脚本与可执行载荷）
     */
    public static final java.util.Set<String> DANGEROUS_CONTENT_TYPES = java.util.Set.of(
            "image/svg+xml", "text/html", "text/xml", "application/xml",
            "application/javascript", "text/javascript", "application/x-javascript",
            "application/x-sh", "application/x-msdownload", "application/x-httpd-php"
    );

    /**
     * 验证文件安全合规性（严格白名单、防双扩展名、防无扩展名、防路径穿越与危险 MIME）
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件不能为空");
        }

        if (file.getSize() > minioConfig.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "文件大小超过限制，最大允许 " + (minioConfig.getMaxFileSize() / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }

        if (originalFilename.contains("\0")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名包含非法空字符");
        }

        // 1. 强制扩展名白名单校验（禁止无扩展名）
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx == originalFilename.length() - 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上传文件必须包含合法扩展名");
        }

        String extension = originalFilename.substring(dotIdx).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不允许上传此类型的文件: " + extension);
        }

        // 2. 双扩展名与危险特征阻断（例如 evil.html.txt, test.svg.pdf, shell.php.png）
        String baseName = originalFilename.substring(0, dotIdx).toLowerCase();
        for (String dangerous : DANGEROUS_SUBSTRINGS) {
            if (baseName.contains(dangerous)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名包含非法扩展名或脚本特征: " + dangerous);
            }
        }

        // 3. MIME 媒体类型白名单与黑名单强阻断
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String primaryType = contentType.toLowerCase().trim();
            int semi = primaryType.indexOf(';');
            if (semi != -1) {
                primaryType = primaryType.substring(0, semi).trim();
            }
            if (DANGEROUS_CONTENT_TYPES.contains(primaryType)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不允许上传此媒体类型的文件: " + contentType);
            }
            if (!ALLOWED_CONTENT_TYPES.contains(primaryType)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的媒体类型: " + contentType);
            }
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    /**
     * 生成对象名称
     */
    private String generateObjectName(String folder, String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());

        String normalizedFolder = normalizeFolder(folder);
        if (normalizedFolder != null && !normalizedFolder.isEmpty()) {
            return normalizedFolder + "/" + timestamp + "_" + uuid + extension;
        }
        return timestamp + "_" + uuid + extension;
    }

    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return null;
        }

        String normalized = folder.trim();
        if (normalized.contains("..") || normalized.startsWith("/") || normalized.startsWith("\\")
                || normalized.contains("\\") || normalized.contains("\0")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法文件夹路径");
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 获取文件URL
     */
    private String getFileUrl(String objectName) {
        return minioConfig.getEndpoint() + "/" + 
               minioConfig.getBucketName() + "/" + objectName;
    }
}
