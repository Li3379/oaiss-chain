package com.oaiss.chain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Blocks production startup when demo seed accounts are still present.
 * Staging is intentionally allowed to carry seeded demo data for dress rehearsals.
 */
@Slf4j
@Component
public class ProductionDataPolicyValidator {

    private static final Set<String> PRODUCTION_PROFILES = Set.of(
            "prod", "production"
    );

    private static final List<String> DEMO_USERNAMES = List.of(
            "enterprise001",
            "enterprise002",
            "enterprise003",
            "reviewer001",
            "thirdparty001"
    );

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;

    public ProductionDataPolicyValidator(Environment environment, JdbcTemplate jdbcTemplate) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void validateOnStartup() {
        if (!isProductionProfile()) {
            return;
        }

        Integer demoUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE username IN (?, ?, ?, ?, ?)",
                Integer.class,
                DEMO_USERNAMES.toArray()
        );

        if (demoUserCount != null && demoUserCount > 0) {
            throw new SecurityException("FATAL: PRODUCTION DATA POLICY VIOLATION: demo seed users are present. "
                    + "Remove demo accounts before starting prod.");
        }

        log.info("Production data policy validation PASSED: no demo seed users detected");
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(PRODUCTION_PROFILES::contains);
    }
}
