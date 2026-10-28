package com.oaiss.chain.repository;

import com.oaiss.chain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserRepository 数据访问层测试
 * 
 * @author OAISS Team
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private User deletedUser;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        
        // 创建测试用户1 - 企业用户，启用状态
        testUser1 = User.builder()
                .username("testuser1")
                .password("$2a$10$encryptedPassword1")
                .phone("13800138001")
                .email("user1@test.com")
                .realName("测试用户1")
                .userType(1)
                .status(1)
                .build();
        testUser1.setDeleted(false);
        testUser1.setCreatedAt(now);
        testUser1.setUpdatedAt(now);
        entityManager.persistAndFlush(testUser1);

        // 创建测试用户2 - 审核员，启用状态
        testUser2 = User.builder()
                .username("testuser2")
                .password("$2a$10$encryptedPassword2")
                .phone("13800138002")
                .email("user2@test.com")
                .realName("测试用户2")
                .userType(2)
                .status(1)
                .build();
        testUser2.setDeleted(false);
        testUser2.setCreatedAt(now);
        testUser2.setUpdatedAt(now);
        entityManager.persistAndFlush(testUser2);

        // 创建已删除用户
        deletedUser = User.builder()
                .username("deleteduser")
                .password("$2a$10$encryptedPassword3")
                .phone("13800138003")
                .email("deleted@test.com")
                .realName("已删除用户")
                .userType(1)
                .status(1)
                .build();
        deletedUser.setDeleted(true);
        deletedUser.setCreatedAt(now);
        deletedUser.setUpdatedAt(now);
        entityManager.persistAndFlush(deletedUser);
    }

    // ==================== findByUsername 测试 ====================

    @Test
    @DisplayName("findByUsername - 根据用户名查找用户 - 用户存在时返回用户")
    void findByUsername_WhenUserExists_ShouldReturnUser() {
        // When
        Optional<User> result = userRepository.findByUsername("testuser1");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser1");
        assertThat(result.get().getEmail()).isEqualTo("user1@test.com");
        assertThat(result.get().getUserType()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByUsername - 根据用户名查找用户 - 用户不存在时返回空")
    void findByUsername_WhenUserNotExists_ShouldReturnEmpty() {
        // When
        Optional<User> result = userRepository.findByUsername("nonexistent");

        // Then
        assertThat(result).isEmpty();
    }

    // ==================== findByPhone 测试 ====================

    @Test
    @DisplayName("findByPhone - 根据手机号查找用户 - 手机号存在时返回用户")
    void findByPhone_WhenPhoneExists_ShouldReturnUser() {
        // When
        Optional<User> result = userRepository.findByPhone("13800138001");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPhone()).isEqualTo("13800138001");
        assertThat(result.get().getUsername()).isEqualTo("testuser1");
    }

    @Test
    @DisplayName("findByPhone - 根据手机号查找用户 - 手机号不存在时返回空")
    void findByPhone_WhenPhoneNotExists_ShouldReturnEmpty() {
        // When
        Optional<User> result = userRepository.findByPhone("19999999999");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByPhone - 根据手机号查找用户 - 手机号为null时返回空")
    void findByPhone_WhenPhoneIsNull_ShouldReturnEmpty() {
        // When
        Optional<User> result = userRepository.findByPhone(null);

        // Then
        assertThat(result).isEmpty();
    }

    // ==================== existsByUsername 测试 ====================

    @Test
    @DisplayName("existsByUsername - 检查用户名是否存在 - 用户名存在时返回true")
    void existsByUsername_WhenUsernameExists_ShouldReturnTrue() {
        // When
        boolean result = userRepository.existsByUsername("testuser1");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsByUsername - 检查用户名是否存在 - 用户名不存在时返回false")
    void existsByUsername_WhenUsernameNotExists_ShouldReturnFalse() {
        // When
        boolean result = userRepository.existsByUsername("nonexistent");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("existsByUsername - 检查用户名是否存在 - 已删除用户的用户名仍然存在")
    void existsByUsername_WhenUserIsDeleted_ShouldStillReturnTrue() {
        // When
        boolean result = userRepository.existsByUsername("deleteduser");

        // Then
        assertThat(result).isTrue();
    }

    // ==================== existsByPhone 测试 ====================

    @Test
    @DisplayName("existsByPhone - 检查手机号是否存在 - 手机号存在时返回true")
    void existsByPhone_WhenPhoneExists_ShouldReturnTrue() {
        // When
        boolean result = userRepository.existsByPhone("13800138001");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsByPhone - 检查手机号是否存在 - 手机号不存在时返回false")
    void existsByPhone_WhenPhoneNotExists_ShouldReturnFalse() {
        // When
        boolean result = userRepository.existsByPhone("19999999999");

        // Then
        assertThat(result).isFalse();
    }

    // ==================== existsByEmail 测试 ====================

    @Test
    @DisplayName("existsByEmail - 检查邮箱是否存在 - 邮箱存在时返回true")
    void existsByEmail_WhenEmailExists_ShouldReturnTrue() {
        // When
        boolean result = userRepository.existsByEmail("user1@test.com");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existsByEmail - 检查邮箱是否存在 - 邮箱不存在时返回false")
    void existsByEmail_WhenEmailNotExists_ShouldReturnFalse() {
        // When
        boolean result = userRepository.existsByEmail("nonexistent@test.com");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("existsByEmail - 检查邮箱是否存在 - 邮箱为null时返回false")
    void existsByEmail_WhenEmailIsNull_ShouldReturnFalse() {
        // When
        boolean result = userRepository.existsByEmail(null);

        // Then
        assertThat(result).isFalse();
    }

    // ==================== findByIdAndDeletedFalse 测试 ====================

    @Test
    @DisplayName("findByIdAndDeletedFalse - 根据ID查找未删除用户 - 用户存在且未删除时返回用户")
    void findByIdAndDeletedFalse_WhenUserExistsAndNotDeleted_ShouldReturnUser() {
        // When
        Optional<User> result = userRepository.findByIdAndDeletedFalse(testUser1.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(testUser1.getId());
        assertThat(result.get().getDeleted()).isFalse();
    }

    @Test
    @DisplayName("findByIdAndDeletedFalse - 根据ID查找未删除用户 - 用户已删除时返回空")
    void findByIdAndDeletedFalse_WhenUserIsDeleted_ShouldReturnEmpty() {
        // When
        Optional<User> result = userRepository.findByIdAndDeletedFalse(deletedUser.getId());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndDeletedFalse - 根据ID查找未删除用户 - 用户不存在时返回空")
    void findByIdAndDeletedFalse_WhenUserNotExists_ShouldReturnEmpty() {
        // When
        Optional<User> result = userRepository.findByIdAndDeletedFalse(99999L);

        // Then
        assertThat(result).isEmpty();
    }

    // ==================== findByUserTypeAndDeletedFalse 测试 ====================

    @Test
    @DisplayName("findByUserTypeAndDeletedFalse - 根据用户类型分页查询 - 返回指定类型的未删除用户")
    void findByUserTypeAndDeletedFalse_WhenUsersExist_ShouldReturnFilteredUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.findByUserTypeAndDeletedFalse(1, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserType()).isEqualTo(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("testuser1");
    }

    @Test
    @DisplayName("findByUserTypeAndDeletedFalse - 根据用户类型分页查询 - 不包含已删除用户")
    void findByUserTypeAndDeletedFalse_ShouldNotIncludeDeletedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - deletedUser 也是 userType=1 但已被删除
        Page<User> result = userRepository.findByUserTypeAndDeletedFalse(1, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent()).noneMatch(u -> u.getUsername().equals("deleteduser"));
    }

    @Test
    @DisplayName("findByUserTypeAndDeletedFalse - 根据用户类型分页查询 - 类型不存在时返回空页")
    void findByUserTypeAndDeletedFalse_WhenTypeNotExists_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 查询不存在的用户类型
        Page<User> result = userRepository.findByUserTypeAndDeletedFalse(99, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("findByUserTypeAndDeletedFalse - 根据用户类型分页查询 - 支持分页")
    void findByUserTypeAndDeletedFalse_ShouldSupportPagination() {
        // Given - 添加更多企业用户
        LocalDateTime now = LocalDateTime.now();
        for (int i = 3; i <= 5; i++) {
            User user = User.builder()
                    .username("enterprise" + i)
                    .password("$2a$10$encryptedPassword")
                    .phone("1380013800" + (10 + i))
                    .email("enterprise" + i + "@test.com")
                    .userType(1)
                    .status(1)
                    .build();
            user.setDeleted(false);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            entityManager.persist(user);
        }
        entityManager.flush();

        Pageable firstPage = PageRequest.of(0, 2);
        Pageable secondPage = PageRequest.of(1, 2);

        // When
        Page<User> page1 = userRepository.findByUserTypeAndDeletedFalse(1, firstPage);
        Page<User> page2 = userRepository.findByUserTypeAndDeletedFalse(1, secondPage);

        // Then
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(4);
        assertThat(page1.getTotalPages()).isEqualTo(2);
        assertThat(page2.getContent()).hasSize(2);
    }

    // ==================== findByStatusAndDeletedFalse 测试 ====================

    @Test
    @DisplayName("findByStatusAndDeletedFalse - 根据状态分页查询 - 返回指定状态的未删除用户")
    void findByStatusAndDeletedFalse_WhenUsersExist_ShouldReturnFilteredUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.findByStatusAndDeletedFalse(1, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(u -> u.getStatus() == 1);
        assertThat(result.getContent()).allMatch(u -> !u.getDeleted());
    }

    @Test
    @DisplayName("findByStatusAndDeletedFalse - 根据状态分页查询 - 不包含已删除用户")
    void findByStatusAndDeletedFalse_ShouldNotIncludeDeletedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.findByStatusAndDeletedFalse(1, pageable);

        // Then
        List<String> usernames = result.getContent().stream().map(User::getUsername).toList();
        assertThat(usernames).doesNotContain("deleteduser");
    }

    @Test
    @DisplayName("findByStatusAndDeletedFalse - 根据状态分页查询 - 状态不存在时返回空页")
    void findByStatusAndDeletedFalse_WhenStatusNotExists_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 查询禁用状态的用户（当前没有）
        Page<User> result = userRepository.findByStatusAndDeletedFalse(0, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    // ==================== findByDeletedFalse 测试 ====================

    @Test
    @DisplayName("findByDeletedFalse - 查询未删除用户 - 返回所有未删除用户")
    void findByDeletedFalse_ShouldReturnAllNotDeletedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.findByDeletedFalse(pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(u -> !u.getDeleted());
    }

    @Test
    @DisplayName("findByDeletedFalse - 查询未删除用户 - 不包含已删除用户")
    void findByDeletedFalse_ShouldNotIncludeDeletedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.findByDeletedFalse(pageable);

        // Then
        List<String> usernames = result.getContent().stream().map(User::getUsername).toList();
        assertThat(usernames).containsExactlyInAnyOrder("testuser1", "testuser2");
        assertThat(usernames).doesNotContain("deleteduser");
    }

    @Test
    @DisplayName("findByDeletedFalse - 查询未删除用户 - 支持分页排序")
    void findByDeletedFalse_ShouldSupportPaginationAndSorting() {
        // Given - 添加更多用户
        LocalDateTime now = LocalDateTime.now();
        for (int i = 3; i <= 7; i++) {
            User user = User.builder()
                    .username("user" + i)
                    .password("$2a$10$encryptedPassword")
                    .phone("1380013800" + (20 + i))
                    .email("user" + i + "@test.com")
                    .userType(1)
                    .status(1)
                    .build();
            user.setDeleted(false);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            entityManager.persist(user);
        }
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 3);

        // When
        Page<User> result = userRepository.findByDeletedFalse(pageable);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(7);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    // ==================== searchByUsername 测试 ====================

    @Test
    @DisplayName("searchByUsername - 模糊搜索用户名 - 返回匹配的未删除用户")
    void searchByUsername_WhenMatchesFound_ShouldReturnMatchingUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 搜索包含"test"的用户名
        Page<User> result = userRepository.searchByUsername("test", pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(u -> u.getUsername().contains("test"));
    }

    @Test
    @DisplayName("searchByUsername - 模糊搜索用户名 - 精确匹配用户名")
    void searchByUsername_WhenExactMatch_ShouldReturnUser() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.searchByUsername("testuser1", pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("testuser1");
    }

    @Test
    @DisplayName("searchByUsername - 模糊搜索用户名 - 部分匹配")
    void searchByUsername_WhenPartialMatch_ShouldReturnMatchingUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 搜索"ser1"
        Page<User> result = userRepository.searchByUsername("ser1", pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("testuser1");
    }

    @Test
    @DisplayName("searchByUsername - 模糊搜索用户名 - 不包含已删除用户")
    void searchByUsername_ShouldNotIncludeDeletedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 搜索"deleted"（deletedUser 的用户名）
        Page<User> result = userRepository.searchByUsername("deleted", pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("searchByUsername - 模糊搜索用户名 - 无匹配时返回空页")
    void searchByUsername_WhenNoMatch_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.searchByUsername("nonexistent", pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("searchByUsername - 模糊搜索用户名 - 支持分页")
    void searchByUsername_ShouldSupportPagination() {
        // Given - 添加更多测试用户
        LocalDateTime now = LocalDateTime.now();
        for (int i = 3; i <= 5; i++) {
            User user = User.builder()
                    .username("testuser" + i)
                    .password("$2a$10$encryptedPassword")
                    .phone("1380013800" + (30 + i))
                    .email("testuser" + i + "@test.com")
                    .userType(1)
                    .status(1)
                    .build();
            user.setDeleted(false);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            entityManager.persist(user);
        }
        entityManager.flush();

        Pageable firstPage = PageRequest.of(0, 2);

        // When
        Page<User> result = userRepository.searchByUsername("test", firstPage);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
    }

    // ==================== 边界场景测试 ====================

    @Test
    @DisplayName("边界场景 - 空数据库时查询返回空结果")
    void boundaryTest_WhenDatabaseIsEmpty_ShouldReturnEmpty() {
        // Given - 清空数据库
        entityManager.getEntityManager().createQuery("DELETE FROM User").executeUpdate();
        entityManager.flush();

        Pageable pageable = PageRequest.of(0, 10);

        // When & Then
        assertThat(userRepository.findByUsername("anyone")).isEmpty();
        assertThat(userRepository.existsByUsername("anyone")).isFalse();
        assertThat(userRepository.findByDeletedFalse(pageable).getContent()).isEmpty();
        assertThat(userRepository.searchByUsername("any", pageable).getContent()).isEmpty();
    }

    @Test
    @DisplayName("边界场景 - 特殊字符用户名搜索")
    void boundaryTest_SearchWithSpecialCharacters() {
        // Given - 清空数据库避免干扰
        entityManager.getEntityManager().createQuery("DELETE FROM User").executeUpdate();
        entityManager.flush();
        
        LocalDateTime now = LocalDateTime.now();
        
        // 创建特殊字符用户名
        User specialUser = User.builder()
                .username("test_user")
                .password("$2a$10$encryptedPassword")
                .phone("13800138999")
                .email("special@test.com")
                .userType(1)
                .status(1)
                .build();
        specialUser.setDeleted(false);
        specialUser.setCreatedAt(now);
        specialUser.setUpdatedAt(now);
        entityManager.persistAndFlush(specialUser);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.searchByUsername("test_", pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("test_user");
    }

    @Test
    @DisplayName("边界场景 - 用户类型边界值测试")
    void boundaryTest_UserTypeValues() {
        // Given - 创建各种用户类型的用户
        LocalDateTime now = LocalDateTime.now();
        User adminUser = User.builder()
                .username("admin")
                .password("$2a$10$encryptedPassword")
                .phone("13800138100")
                .email("admin@test.com")
                .userType(4) // 管理员
                .status(1)
                .build();
        adminUser.setDeleted(false);
        adminUser.setCreatedAt(now);
        adminUser.setUpdatedAt(now);
        entityManager.persistAndFlush(adminUser);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.findByUserTypeAndDeletedFalse(4, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserType()).isEqualTo(4);
    }

    @Test
    @DisplayName("边界场景 - 状态边界值测试")
    void boundaryTest_StatusValues() {
        // Given - 创建禁用状态用户
        LocalDateTime now = LocalDateTime.now();
        User disabledUser = User.builder()
                .username("disabled")
                .password("$2a$10$encryptedPassword")
                .phone("13800138200")
                .email("disabled@test.com")
                .userType(1)
                .status(0) // 禁用
                .build();
        disabledUser.setDeleted(false);
        disabledUser.setCreatedAt(now);
        disabledUser.setUpdatedAt(now);
        entityManager.persistAndFlush(disabledUser);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<User> result = userRepository.findByStatusAndDeletedFalse(0, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("边界场景 - 空手机号用户")
    void boundaryTest_UserWithNullPhone() {
        // Given - 创建无手机号用户
        LocalDateTime now = LocalDateTime.now();
        User noPhoneUser = User.builder()
                .username("nophone")
                .password("$2a$10$encryptedPassword")
                .phone(null)
                .email("nophone@test.com")
                .userType(1)
                .status(1)
                .build();
        noPhoneUser.setDeleted(false);
        noPhoneUser.setCreatedAt(now);
        noPhoneUser.setUpdatedAt(now);
        entityManager.persistAndFlush(noPhoneUser);

        // When - findByPhone(null) 会匹配phone为null的记录
        Optional<User> result = userRepository.findByPhone(null);

        // Then - JPA会找到phone为null的记录
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("nophone");
    }

    @Test
    @DisplayName("边界场景 - 空邮箱用户")
    void boundaryTest_UserWithNullEmail() {
        // Given - 清空数据库避免干扰
        entityManager.getEntityManager().createQuery("DELETE FROM User").executeUpdate();
        entityManager.flush();
        
        LocalDateTime now = LocalDateTime.now();
        
        // 创建无邮箱用户
        User noEmailUser = User.builder()
                .username("noemail")
                .password("$2a$10$encryptedPassword")
                .phone("13800138300")
                .email(null)
                .userType(1)
                .status(1)
                .build();
        noEmailUser.setDeleted(false);
        noEmailUser.setCreatedAt(now);
        noEmailUser.setUpdatedAt(now);
        entityManager.persistAndFlush(noEmailUser);

        // When - existsByEmail(null) 会匹配email为null的记录
        boolean exists = userRepository.existsByEmail(null);

        // Then - JPA会找到email为null的记录
        assertThat(exists).isTrue();
    }
}
