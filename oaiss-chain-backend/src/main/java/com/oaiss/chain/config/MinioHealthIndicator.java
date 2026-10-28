package com.oaiss.chain.config;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MinIO健康检查指示器
 * MinIO Health Check Indicator for Actuator
 *
 * @author OAISS Team
 */
@Component
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioHealthIndicator(MinioClient minioClient,
                                @org.springframework.beans.factory.annotation.Value("${minio.bucket-name:oaiss-chain}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    @Override
    public Health health() {
        try {
            // 检查MinIO连接和bucket是否存在
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());

            if (bucketExists) {
                return Health.up()
                        .withDetail("minio", "connected")
                        .withDetail("bucket", bucketName)
                        .withDetail("bucketExists", true)
                        .build();
            } else {
                return Health.down()
                        .withDetail("minio", "connected")
                        .withDetail("bucket", bucketName)
                        .withDetail("bucketExists", false)
                        .withDetail("error", "Bucket does not exist")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("minio", "disconnected")
                    .withDetail("bucket", bucketName)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
