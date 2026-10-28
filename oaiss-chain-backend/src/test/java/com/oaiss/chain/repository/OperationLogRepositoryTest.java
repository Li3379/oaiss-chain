package com.oaiss.chain.repository;

import com.oaiss.chain.entity.OperationLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OperationLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OperationLogRepository repository;

    private OperationLog log1;
    private OperationLog log2;
    private OperationLog log3;
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        baseTime = LocalDateTime.of(2024, 6, 1, 12, 0, 0);

        log1 = createLog(1L, "admin", 4, "carbon", "CREATE", 1, baseTime.minusHours(2));
        log2 = createLog(1L, "admin", 4, "trade", "UPDATE", 1, baseTime.minusHours(1));
        log3 = createLog(2L, "user1", 1, "carbon", "CREATE", 2, baseTime);

        entityManager.persist(log1);
        entityManager.persist(log2);
        entityManager.persist(log3);
        entityManager.flush();
    }

    private OperationLog createLog(Long userId, String username, Integer userType,
                                    String module, String action, Integer status, LocalDateTime createdAt) {
        OperationLog log = OperationLog.builder()
                .userId(userId)
                .username(username)
                .userType(userType)
                .module(module)
                .action(action)
                .description("Test operation")
                .httpMethod("POST")
                .requestUrl("/api/v1/test")
                .requestIp("127.0.0.1")
                .status(status)
                .executionTime(100L)
                .build();
        log.setCreatedAt(createdAt);
        log.setUpdatedAt(createdAt);
        return log;
    }

    @Test
    @DisplayName("findByUserIdAndDeletedFalse should return user logs")
    void findByUserId_shouldReturnUserLogs() {
        Page<OperationLog> result = repository.findByUserIdAndDeletedFalse(1L, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findByModuleAndDeletedFalse should filter by module")
    void findByModule_shouldFilterByModule() {
        Page<OperationLog> result = repository.findByModuleAndDeletedFalse("carbon", PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findByCreatedAtBetween should return logs in time range")
    void findByCreatedAtBetween_shouldReturnLogsInRange() {
        Page<OperationLog> result = repository.findByCreatedAtBetween(
                baseTime.minusHours(3), baseTime.plusMinutes(1), PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("findByUserIdAndCreatedAtBetween should filter by user and time")
    void findByUserIdAndCreatedAtBetween_shouldFilterByUserAndTime() {
        Page<OperationLog> result = repository.findByUserIdAndCreatedAtBetween(
                1L, baseTime.minusHours(2), baseTime, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findByStatusAndDeletedFalse should filter by status")
    void findByStatus_shouldFilterByStatus() {
        Page<OperationLog> result = repository.findByStatusAndDeletedFalse(1, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("countByUserIdAndCreatedAtAfter should count correctly")
    void countByUserIdAndCreatedAtAfter_shouldCountCorrectly() {
        Long count = repository.countByUserIdAndCreatedAtAfter(1L, baseTime.minusHours(2));
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("findTop100ByDeletedFalseOrderByCreatedAtDesc should return recent logs")
    void findTop100ByDeletedFalseOrderByCreatedAtDesc_shouldReturnRecentLogs() {
        List<OperationLog> result = repository.findTop100ByDeletedFalseOrderByCreatedAtDesc();
        assertThat(result).hasSize(3);
        // Should be ordered by createdAt desc
        assertThat(result.get(0).getCreatedAt()).isAfterOrEqualTo(result.get(1).getCreatedAt());
    }
}
