package com.oaiss.chain.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionProfileContractTest {

    @Test
    void productionProfileDisablesSwaggerAndOpenApi() {
        Properties properties = loadYaml("application-prod.yml");

        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void productionProfileUsesStrictPersistenceSettings() {
        Properties properties = loadYaml("application-prod.yml");

        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.validate-on-migrate")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.repair-on-migrate")).isEqualTo("false");
    }

    @Test
    void productionProfileExposesExpectedActuatorEndpoints() {
        Properties properties = loadYaml("application-prod.yml");

        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,metrics,prometheus");
        assertThat(properties.getProperty("management.endpoint.health.probes.enabled"))
                .isEqualTo("true");
    }

    @Test
    void productionProfileRequiresExplicitInfrastructureBindings() {
        Properties properties = loadYaml("application-prod.yml");

        assertThat(properties.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(properties.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(properties.getProperty("ml.service.secret")).isEqualTo("${ML_SERVICE_SECRET:}");
        assertThat(properties.getProperty("app.cors.allowed-origins")).isEqualTo("${CORS_ALLOWED_ORIGINS}");
    }

    private Properties loadYaml(String resourceName) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resourceName));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
