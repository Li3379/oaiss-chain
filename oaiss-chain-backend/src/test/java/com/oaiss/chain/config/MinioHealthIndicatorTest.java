package com.oaiss.chain.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioHealthIndicator unit tests")
class MinioHealthIndicatorTest {

    @Mock
    private MinioClient minioClient;

    @Test
    @DisplayName("health should be UP when bucket exists")
    void health_shouldBeUpWhenBucketExists() throws Exception {
        MinioHealthIndicator indicator = new MinioHealthIndicator(minioClient, "bucket-a");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("connected", health.getDetails().get("minio"));
        assertEquals("bucket-a", health.getDetails().get("bucket"));
        assertEquals(true, health.getDetails().get("bucketExists"));
    }

    @Test
    @DisplayName("health should be DOWN when bucket missing")
    void health_shouldBeDownWhenBucketMissing() throws Exception {
        MinioHealthIndicator indicator = new MinioHealthIndicator(minioClient, "bucket-b");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("connected", health.getDetails().get("minio"));
        assertEquals(false, health.getDetails().get("bucketExists"));
        assertEquals("Bucket does not exist", health.getDetails().get("error"));
    }

    @Test
    @DisplayName("health should be DOWN when client throws")
    void health_shouldBeDownWhenClientThrows() throws Exception {
        MinioHealthIndicator indicator = new MinioHealthIndicator(minioClient, "bucket-c");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("boom"));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("disconnected", health.getDetails().get("minio"));
        assertTrue(String.valueOf(health.getDetails().get("error")).contains("boom"));
    }
}
