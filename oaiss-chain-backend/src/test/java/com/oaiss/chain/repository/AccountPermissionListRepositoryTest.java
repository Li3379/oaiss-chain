package com.oaiss.chain.repository;

import com.oaiss.chain.entity.AccountPermissionList;
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
class AccountPermissionListRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountPermissionListRepository repository;

    private AccountPermissionList perm1;
    private AccountPermissionList perm2;
    private AccountPermissionList deletedPerm;

    @BeforeEach
    void setUp() {
        perm1 = AccountPermissionList.builder()
                .permissionName("碳报告查看")
                .permissionCode("carbon_report_view")
                .description("查看碳报告")
                .module("carbon")
                .sortOrder(1)
                .build();
        perm1.setCreatedAt(LocalDateTime.now());
        perm1.setUpdatedAt(LocalDateTime.now());

        perm2 = AccountPermissionList.builder()
                .permissionName("碳报告编辑")
                .permissionCode("carbon_report_edit")
                .description("编辑碳报告")
                .module("carbon")
                .sortOrder(2)
                .build();
        perm2.setCreatedAt(LocalDateTime.now());
        perm2.setUpdatedAt(LocalDateTime.now());

        deletedPerm = AccountPermissionList.builder()
                .permissionName("已删除权限")
                .permissionCode("deleted_perm")
                .description("已删除")
                .module("system")
                .sortOrder(99)
                .build();
        deletedPerm.setDeleted(true);
        deletedPerm.setCreatedAt(LocalDateTime.now());
        deletedPerm.setUpdatedAt(LocalDateTime.now());

        entityManager.persist(perm1);
        entityManager.persist(perm2);
        entityManager.persist(deletedPerm);
        entityManager.flush();
    }

    @Test
    @DisplayName("findByPermissionCodeAndDeletedFalse should return matching permission")
    void findByPermissionCodeAndDeletedFalse_shouldReturnMatching() {
        Optional<AccountPermissionList> result = repository.findByPermissionCodeAndDeletedFalse("carbon_report_view");
        assertThat(result).isPresent();
        assertThat(result.get().getPermissionName()).isEqualTo("碳报告查看");
    }

    @Test
    @DisplayName("findByPermissionCodeAndDeletedFalse should not return deleted")
    void findByPermissionCodeAndDeletedFalse_shouldNotReturnDeleted() {
        Optional<AccountPermissionList> result = repository.findByPermissionCodeAndDeletedFalse("deleted_perm");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByModuleAndDeletedFalse should return permissions in module")
    void findByModuleAndDeletedFalse_shouldReturnPermissionsInModule() {
        List<AccountPermissionList> result = repository.findByModuleAndDeletedFalse("carbon");
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByDeletedFalseOrderBySortOrderAsc should return ordered list")
    void findByDeletedFalseOrderBySortOrderAsc_shouldReturnOrderedList() {
        List<AccountPermissionList> result = repository.findByDeletedFalseOrderBySortOrderAsc();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSortOrder()).isLessThanOrEqualTo(result.get(1).getSortOrder());
    }

    @Test
    @DisplayName("existsByPermissionCodeAndDeletedFalse should return true for existing")
    void existsByPermissionCodeAndDeletedFalse_shouldReturnTrueForExisting() {
        assertThat(repository.existsByPermissionCodeAndDeletedFalse("carbon_report_view")).isTrue();
    }

    @Test
    @DisplayName("existsByPermissionCodeAndDeletedFalse should return false for deleted")
    void existsByPermissionCodeAndDeletedFalse_shouldReturnFalseForDeleted() {
        assertThat(repository.existsByPermissionCodeAndDeletedFalse("deleted_perm")).isFalse();
    }

    @Test
    @DisplayName("existsByPermissionCodeAndDeletedFalse should return false for non-existing")
    void existsByPermissionCodeAndDeletedFalse_shouldReturnFalseForNonExisting() {
        assertThat(repository.existsByPermissionCodeAndDeletedFalse("non_existing")).isFalse();
    }
}
