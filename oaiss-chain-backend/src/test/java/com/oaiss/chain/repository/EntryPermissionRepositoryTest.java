package com.oaiss.chain.repository;

import com.oaiss.chain.entity.EntryPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class EntryPermissionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntryPermissionRepository repository;

    private EntryPermission perm1;
    private EntryPermission perm2;
    private EntryPermission deletedPerm;

    @BeforeEach
    void setUp() {
        perm1 = createPermission(1, "/api/v1/carbon/**", "GET", true);
        perm2 = createPermission(1, "/api/v1/carbon/**", "POST", true);
        deletedPerm = createPermission(2, "/api/v1/admin/**", "GET", true);
        deletedPerm.setDeleted(true);

        entityManager.persist(perm1);
        entityManager.persist(perm2);
        entityManager.persist(deletedPerm);
        entityManager.flush();
    }

    private EntryPermission createPermission(Integer userType, String apiPath,
                                              String httpMethod, Boolean allowed) {
        EntryPermission perm = EntryPermission.builder()
                .userType(userType)
                .apiPath(apiPath)
                .httpMethod(httpMethod)
                .allowed(allowed)
                .build();
        perm.setCreatedAt(LocalDateTime.now());
        perm.setUpdatedAt(LocalDateTime.now());
        return perm;
    }

    @Test
    @DisplayName("findByUserTypeAndDeletedFalse should return permissions for user type")
    void findByUserTypeAndDeletedFalse_shouldReturnPermissions() {
        List<EntryPermission> result = repository.findByUserTypeAndDeletedFalse(1);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByUserTypeAndDeletedFalse should exclude deleted")
    void findByUserTypeAndDeletedFalse_shouldExcludeDeleted() {
        List<EntryPermission> result = repository.findByUserTypeAndDeletedFalse(2);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByUserTypeAndApiPathAndDeletedFalse should filter by type and path")
    void findByUserTypeAndApiPath_shouldFilterCorrectly() {
        List<EntryPermission> result = repository.findByUserTypeAndApiPathAndDeletedFalse(
                1, "/api/v1/carbon/**");
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByApiPathAndHttpMethodAndDeletedFalse should return matching permission")
    void findByApiPathAndHttpMethod_shouldReturnMatching() {
        Optional<EntryPermission> result = repository.findByApiPathAndHttpMethodAndDeletedFalse(
                "/api/v1/carbon/**", "GET");
        assertThat(result).isPresent();
        assertThat(result.get().getUserType()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByApiPathAndHttpMethodAndDeletedFalse should return empty for non-existing")
    void findByApiPathAndHttpMethod_shouldReturnEmptyForNonExisting() {
        Optional<EntryPermission> result = repository.findByApiPathAndHttpMethodAndDeletedFalse(
                "/api/v1/nonexist/**", "GET");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByApiPathAndDeletedFalse should return all permissions for path")
    void findByApiPathAndDeletedFalse_shouldReturnAllForPath() {
        List<EntryPermission> result = repository.findByApiPathAndDeletedFalse("/api/v1/carbon/**");
        assertThat(result).hasSize(2);
    }
}
