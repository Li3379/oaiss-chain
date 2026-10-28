package com.oaiss.chain.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CorsAllowedOriginsConfigTest {

    @Test
    void baseConfigAllowsLocal127Frontend() {
        assertAllowedOrigin("application.yml", "http://127.0.0.1:5173");
    }

    @Test
    void baseConfigAllowsAlternateLocalFrontendPort() {
        assertAllowedOrigin("application.yml", "http://127.0.0.1:5174");
    }

    @Test
    void devConfigAllowsLocal127Frontend() {
        assertAllowedOrigin("application-dev.yml", "http://127.0.0.1:5173");
    }

    @Test
    void devConfigAllowsAlternateLocalFrontendPort() {
        assertAllowedOrigin("application-dev.yml", "http://127.0.0.1:5174");
    }

    private void assertAllowedOrigin(String resourceName, String expectedOrigin) {
        // 用 main 源文件绝对路径读取：src/test/resources/application.yml 为 @WebMvcTest
        // ratelimit 覆盖件（有意遮蔽同名 main 文件），classpath 读取会命中错误目标
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/" + resourceName));

        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        String originsValue = properties.getProperty("app.cors.allowed-origins");
        assertThat(originsValue)
                .as("%s should include %s", resourceName, expectedOrigin)
                .isNotNull();

        // The value may contain Spring env var placeholders like ${VAR:default}
        // Just check that the expected origin appears somewhere in the value
        assertThat(originsValue).contains(expectedOrigin);
    }
}
