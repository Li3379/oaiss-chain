package com.oaiss.chain.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO对象存储配置类
 * 
 * <p>用于配置MinIO客户端连接，支持文件上传、下载、删除等操作</p>
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /**
     * MinIO服务地址
     */
    private String endpoint = "http://localhost:9000";

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 私有密钥
     */
    private String secretKey;

    /**
     * 存储桶名称
     */
    private String bucketName = "oaiss-chain";

    /**
     * 预签名URL过期时间（秒）
     */
    private int presignedUrlExpiry = 3600;

    /**
     * 最大文件大小（字节），默认100MB
     */
    private long maxFileSize = 100 * 1024 * 1024;

    /**
     * 创建MinIO客户端Bean
     *
     * @return MinioClient实例
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
