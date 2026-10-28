package com.oaiss.chain.integration;

import com.oaiss.chain.BaseIntegrationTest;
import com.oaiss.chain.H2IntegrationTest;
import com.oaiss.chain.dto.LoginRequest;
import com.oaiss.chain.dto.RegisterRequest;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户认证集成测试
 * Integration tests for user authentication
 *
 * Uses H2 in-memory database for fast execution without Docker.
 * For full integration tests with real MySQL/Redis, use BaseIntegrationTest.
 */
class UserIntegrationTest extends H2IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnterpriseRepository enterpriseRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        enterpriseRepository.deleteAll();
    }

    @Nested
    @DisplayName("用户注册集成测试")
    class RegisterIntegrationTests {

        @Test
        @DisplayName("企业用户注册成功")
        void registerEnterpriseUser_Success() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .username("test_enterprise")
                    .password("Test123456")
                    .confirmPassword("Test123456")
                    .email("test@example.com")
                    .phone("13800138000")
                    .realName("测试企业")
                    .userType(1) // 企业用户
                    .build();

            // Act
            var response = authService.register(request);

            // Assert
            assertNotNull(response);
            assertEquals("test_enterprise", response.getUsername());
            assertEquals(1, response.getUserType());

            User savedUser = userRepository.findByUsername("test_enterprise").orElse(null);
            assertNotNull(savedUser);
            assertEquals("test@example.com", savedUser.getEmail());
            assertTrue(passwordEncoder.matches("Test123456", savedUser.getPassword()));
        }

        @Test
        @DisplayName("重复用户名注册失败")
        void registerDuplicateUsername_Fail() {
            // Arrange - 创建第一个用户
            RegisterRequest request1 = RegisterRequest.builder()
                    .username("duplicate_user")
                    .password("Test123456")
                    .confirmPassword("Test123456")
                    .email("user1@example.com")
                    .phone("13800138001")
                    .userType(1)
                    .build();
            authService.register(request1);

            // Act - 尝试用相同用户名注册
            RegisterRequest request2 = RegisterRequest.builder()
                    .username("duplicate_user")
                    .password("Test123456")
                    .confirmPassword("Test123456")
                    .email("user2@example.com")
                    .phone("13800138002")
                    .userType(1)
                    .build();

            // Assert
            assertThrows(RuntimeException.class, () -> authService.register(request2));
        }
    }

    @Nested
    @DisplayName("用户登录集成测试")
    class LoginIntegrationTests {

        @BeforeEach
        void setUpUser() {
            RegisterRequest request = RegisterRequest.builder()
                    .username("login_test_user")
                    .password("Test123456")
                    .confirmPassword("Test123456")
                    .email("login@example.com")
                    .phone("13800138003")
                    .realName("登录测试用户")
                    .userType(1)
                    .build();
            authService.register(request);
        }

        @Test
        @DisplayName("用户登录成功")
        void login_Success() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .username("login_test_user")
                    .password("Test123456")
                    .captchaKey("test-key")
                    .captcha("1234")
                    .build();

            // 注意：实际测试中需要mock验证码服务
            // For actual testing, mock the captcha service

            // Act & Assert
            // 实际集成测试需要mock验证码验证
            // Actual integration test needs to mock captcha verification
        }

        @Test
        @DisplayName("错误密码登录失败")
        void loginWithWrongPassword_Fail() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .username("login_test_user")
                    .password("WrongPassword")
                    .captchaKey("test-key")
                    .captcha("1234")
                    .build();

            // Act & Assert
            assertThrows(RuntimeException.class, () -> authService.login(request));
        }
    }

    @Nested
    @DisplayName("Redis缓存集成测试")
    @Disabled("Requires a real authenticated Redis instance or Testcontainers-based BaseIntegrationTest")
    class RedisCacheIntegrationTests {

        @Test
        @DisplayName("Redis连接测试")
        void redisConnection_Success() {
            // Arrange
            String key = "test:redis:connection";
            String value = "test-value";

            // Act
            redisTemplate.opsForValue().set(key, value);
            String retrievedValue = redisTemplate.opsForValue().get(key);

            // Assert
            assertEquals(value, retrievedValue);

            // Cleanup
            redisTemplate.delete(key);
        }

        @Test
        @DisplayName("Redis缓存操作测试")
        void redisCacheOperations_Success() {
            // Arrange
            String key = "test:cache:user:123";
            String value = "cached-data";

            // Act
            redisTemplate.opsForValue().set(key, value, 300, TimeUnit.SECONDS);
            String retrievedValue = redisTemplate.opsForValue().get(key);

            // Assert
            assertEquals(value, retrievedValue);

            // Cleanup
            redisTemplate.delete(key);
        }
    }

    @Nested
    @DisplayName("企业数据集成测试")
    class EnterpriseIntegrationTests {

        @Test
        @DisplayName("创建企业并关联用户")
        void createEnterpriseWithUser_Success() {
            // Arrange
            User user = User.builder()
                    .username("enterprise_user")
                    .password(passwordEncoder.encode("Test123456"))
                    .email("enterprise@example.com")
                    .phone("13800138004")
                    .realName("企业管理员")
                    .userType(1)
                    .status(1)
                    .build();
            User savedUser = userRepository.save(user);

            Enterprise enterprise = Enterprise.builder()
                    .userId(savedUser.getId())
                    .enterpriseName("测试企业有限公司")
                    .creditCode("91110000MA00ABCD12")
                    .address("北京市朝阳区测试路1号")
                    .contactPerson("张三")
                    .build();

            // Act
            Enterprise savedEnterprise = enterpriseRepository.save(enterprise);

            // Assert
            assertNotNull(savedEnterprise.getId());
            assertEquals("测试企业有限公司", savedEnterprise.getEnterpriseName());
            assertEquals(savedUser.getId(), savedEnterprise.getUserId());
        }
    }
}
