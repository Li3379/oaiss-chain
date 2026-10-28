package com.oaiss.chain.repository;

import com.oaiss.chain.entity.UserTypeList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserTypeListRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserTypeListRepository repository;

    private UserTypeList enterpriseType;
    private UserTypeList deletedType;

    @BeforeEach
    void setUp() {
        enterpriseType = UserTypeList.builder()
                .typeCode("ENTERPRISE")
                .typeName("企业用户")
                .description("企业用户类型")
                .defaultRole("ENTERPRISE")
                .build();
        enterpriseType.setCreatedAt(LocalDateTime.now());
        enterpriseType.setUpdatedAt(LocalDateTime.now());

        deletedType = UserTypeList.builder()
                .typeCode("DELETED_TYPE")
                .typeName("已删除类型")
                .description("已删除")
                .defaultRole("NONE")
                .build();
        deletedType.setDeleted(true);
        deletedType.setCreatedAt(LocalDateTime.now());
        deletedType.setUpdatedAt(LocalDateTime.now());

        entityManager.persist(enterpriseType);
        entityManager.persist(deletedType);
        entityManager.flush();
    }

    @Test
    @DisplayName("findByTypeCodeAndDeletedFalse should return matching type")
    void findByTypeCode_shouldReturnMatching() {
        Optional<UserTypeList> result = repository.findByTypeCodeAndDeletedFalse("ENTERPRISE");
        assertThat(result).isPresent();
        assertThat(result.get().getTypeName()).isEqualTo("企业用户");
    }

    @Test
    @DisplayName("findByTypeCodeAndDeletedFalse should not return deleted")
    void findByTypeCode_shouldNotReturnDeleted() {
        Optional<UserTypeList> result = repository.findByTypeCodeAndDeletedFalse("DELETED_TYPE");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByTypeCodeAndDeletedFalse should return empty for non-existing")
    void findByTypeCode_shouldReturnEmptyForNonExisting() {
        Optional<UserTypeList> result = repository.findByTypeCodeAndDeletedFalse("NON_EXIST");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByTypeNameAndDeletedFalse should return matching type")
    void findByTypeName_shouldReturnMatching() {
        Optional<UserTypeList> result = repository.findByTypeNameAndDeletedFalse("企业用户");
        assertThat(result).isPresent();
        assertThat(result.get().getTypeCode()).isEqualTo("ENTERPRISE");
    }

    @Test
    @DisplayName("findByTypeNameAndDeletedFalse should not return deleted")
    void findByTypeName_shouldNotReturnDeleted() {
        Optional<UserTypeList> result = repository.findByTypeNameAndDeletedFalse("已删除类型");
        assertThat(result).isEmpty();
    }
}
