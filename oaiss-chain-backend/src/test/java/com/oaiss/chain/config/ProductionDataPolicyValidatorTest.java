package com.oaiss.chain.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionDataPolicyValidatorTest {

    private Environment environment;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        jdbcTemplate = mock(JdbcTemplate.class);
    }

    @Test
    @DisplayName("生产环境存在演示账号时应阻止启动")
    void validateOnStartup_productionWithDemoUsers_shouldThrow() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(Object[].class)))
                .thenReturn(3);

        ProductionDataPolicyValidator validator = new ProductionDataPolicyValidator(environment, jdbcTemplate);

        assertThrows(SecurityException.class, validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境无演示账号时应通过")
    void validateOnStartup_productionWithoutDemoUsers_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        ProductionDataPolicyValidator validator = new ProductionDataPolicyValidator(environment, jdbcTemplate);

        assertDoesNotThrow(validator::validateOnStartup);
        verify(jdbcTemplate).queryForObject(any(String.class), eq(Integer.class), any(Object[].class));
    }

    @Test
    @DisplayName("本地环境不检查演示账号")
    void validateOnStartup_localProfile_shouldSkip() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local", "fabric"});

        ProductionDataPolicyValidator validator = new ProductionDataPolicyValidator(environment, jdbcTemplate);

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("staging 环境允许保留演练种子数据")
    void validateOnStartup_stagingProfile_shouldSkipDemoUserBlock() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"staging"});

        ProductionDataPolicyValidator validator = new ProductionDataPolicyValidator(environment, jdbcTemplate);

        assertDoesNotThrow(validator::validateOnStartup);
    }

    @Test
    @DisplayName("生产环境查询返回null时应通过")
    void validateOnStartup_productionWithNullCount_shouldPass() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), any(Object[].class)))
                .thenReturn(null);

        ProductionDataPolicyValidator validator = new ProductionDataPolicyValidator(environment, jdbcTemplate);

        assertDoesNotThrow(validator::validateOnStartup);
    }
}
