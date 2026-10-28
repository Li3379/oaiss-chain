package com.oaiss.chain.repository;

import com.oaiss.chain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 * 
 * @author OAISS Team
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据手机号查找用户
     */
    Optional<User> findByPhone(String phone);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查手机号是否存在
     */
    boolean existsByPhone(String phone);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 根据ID查找未删除用户
     */
    Optional<User> findByIdAndDeletedFalse(Long id);

    /**
     * 根据用户类型分页查询
     */
    Page<User> findByUserTypeAndDeletedFalse(Integer userType, Pageable pageable);

    /**
     * 根据状态查询
     */
    Page<User> findByStatusAndDeletedFalse(Integer status, Pageable pageable);

    /**
     * 查询未删除的用户（分页）
     */
    Page<User> findByDeletedFalse(Pageable pageable);

    /**
     * 模糊搜索用户名
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false AND u.username LIKE %:keyword%")
    Page<User> searchByUsername(@Param("keyword") String keyword, Pageable pageable);
}
