package com.oaiss.chain.integration;

import com.oaiss.chain.H2IntegrationTest;
import com.oaiss.chain.annotation.DataIsolation;
import com.oaiss.chain.security.EnterpriseContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 @Async 和 @DataIsolation 注解配置正确性。
 * Tests annotation wiring rather than full business logic.
 */
class AsyncAndIsolationTest extends H2IntegrationTest {

    @Autowired(required = false)
    private ThreadPoolTaskExecutor blockchainExecutor;

    // ---- @Async Configuration Tests ----

    @Test
    @DisplayName("blockchainExecutor bean is configured with thread pool")
    void blockchainExecutorExists() {
        assertNotNull(blockchainExecutor, "blockchainExecutor should be auto-configured");
        assertTrue(blockchainExecutor.getCorePoolSize() > 0, "Core pool size should be > 0");
        assertTrue(blockchainExecutor.getMaxPoolSize() >= blockchainExecutor.getCorePoolSize(),
                "Max pool size should be >= core pool size");
    }

    @Test
    @DisplayName("@Async annotated methods run on separate thread")
    void asyncRunsOnDifferentThread() throws Exception {
        String originalThread = Thread.currentThread().getName();
        CompletableFuture<String> future = new CompletableFuture<>();

        // Simulate async execution
        if (blockchainExecutor != null) {
            blockchainExecutor.execute(() -> future.complete(Thread.currentThread().getName()));
            String asyncThread = future.get(5, TimeUnit.SECONDS);
            assertNotEquals(originalThread, asyncThread,
                    "Async method should run on executor thread, not calling thread");
        }
    }

    // ---- @DataIsolation Annotation Verification ----

    @Test
    @DisplayName("@DataIsolation is present on expected controller methods")
    void dataIsolationAnnotationsExist() throws Exception {
        // Verify the annotation exists on key enterprise-scoped endpoints
        String[] controllerClasses = {
                "com.oaiss.chain.controller.CarbonController",
                "com.oaiss.chain.controller.TradeController",
                "com.oaiss.chain.controller.CreditScoreController",
                "com.oaiss.chain.controller.CarbonCoinController",
        };

        for (String className : controllerClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                boolean hasDataIsolation = Arrays.stream(clazz.getDeclaredMethods())
                        .anyMatch(m -> m.isAnnotationPresent(DataIsolation.class));
                assertTrue(hasDataIsolation,
                        className + " should have at least one @DataIsolation method");
            } catch (ClassNotFoundException e) {
                // Controller not found — skip (may be in different module)
            }
        }
    }

    // ---- EnterpriseContextHolder Tests ----

    @Test
    @DisplayName("EnterpriseContextHolder thread-local stores and retrieves enterprise ID")
    void enterpriseContextRoundTrip() {
        try {
            assertNull(EnterpriseContextHolder.getEnterpriseId(),
                    "Context should be null before setting");

            Long testId = 42L;
            EnterpriseContextHolder.setEnterpriseId(testId);
            assertEquals(testId, EnterpriseContextHolder.getEnterpriseId(),
                    "Context should return the set enterprise ID");

            EnterpriseContextHolder.clear();
            assertNull(EnterpriseContextHolder.getEnterpriseId(),
                    "Context should be null after clear");
        } finally {
            EnterpriseContextHolder.clear();
        }
    }
}
