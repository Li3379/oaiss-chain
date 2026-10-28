package com.oaiss.chain.repository;

import com.oaiss.chain.entity.ThirdPartyOrg;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ThirdPartyOrgRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ThirdPartyOrgRepository repository;

    private ThirdPartyOrg org1;
    private ThirdPartyOrg deletedOrg;

    @BeforeEach
    void setUp() {
        org1 = createOrg(1L, "环保局", "ORG-001", 1);
        deletedOrg = createOrg(2L, "已删除机构", "ORG-002", 1);
        deletedOrg.setDeleted(true);

        entityManager.persist(org1);
        entityManager.persist(deletedOrg);
        entityManager.flush();
    }

    private ThirdPartyOrg createOrg(Long userId, String orgName, String orgCode, Integer orgType) {
        ThirdPartyOrg org = ThirdPartyOrg.builder()
                .userId(userId)
                .orgName(orgName)
                .orgCode(orgCode)
                .orgType(orgType)
                .contactPerson("张三")
                .contactPhone("13800138000")
                .accessLevel(1)
                .status(1)
                .build();
        org.setCreatedAt(LocalDateTime.now());
        org.setUpdatedAt(LocalDateTime.now());
        return org;
    }

    @Test
    @DisplayName("findByUserIdAndDeletedFalse should return org for user")
    void findByUserId_shouldReturnOrg() {
        Optional<ThirdPartyOrg> result = repository.findByUserIdAndDeletedFalse(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getOrgName()).isEqualTo("环保局");
    }

    @Test
    @DisplayName("findByUserIdAndDeletedFalse should return empty for deleted")
    void findByUserId_shouldReturnEmptyForDeleted() {
        Optional<ThirdPartyOrg> result = repository.findByUserIdAndDeletedFalse(2L);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByOrgCode should return matching org")
    void findByOrgCode_shouldReturnMatching() {
        Optional<ThirdPartyOrg> result = repository.findByOrgCode("ORG-001");
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findByOrgCode should return empty for non-existing")
    void findByOrgCode_shouldReturnEmptyForNonExisting() {
        Optional<ThirdPartyOrg> result = repository.findByOrgCode("NON-EXIST");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByDeletedFalse should return all undeleted orgs")
    void findByDeletedFalse_shouldReturnAllUndeleted() {
        Page<ThirdPartyOrg> result = repository.findByDeletedFalse(PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
    }
}
