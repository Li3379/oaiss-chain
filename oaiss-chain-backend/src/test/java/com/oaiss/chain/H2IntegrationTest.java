package com.oaiss.chain;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 轻量级集成测试基类
 * Base class for lightweight integration tests using H2 in-memory database
 *
 * Use this when Docker/Testcontainers is not available.
 * For full integration tests with real MySQL/Redis, use BaseIntegrationTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class H2IntegrationTest {
    // Uses application-test.yml which configures H2 and mock Redis
}
