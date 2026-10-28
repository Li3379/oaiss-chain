package com.oaiss.chain.repository;

import com.oaiss.chain.entity.EntryPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 入口权限数据访问层
 * 
 * @author OAISS Team
 */
@Repository
public interface EntryPermissionRepository extends JpaRepository<EntryPermission, Long> {

    /**
     * 根据用户类型查询所有权限
     */
    List<EntryPermission> findByUserTypeAndDeletedFalse(Integer userType);

    /**
     * 根据用户类型和API路径查询
     */
    List<EntryPermission> findByUserTypeAndApiPathAndDeletedFalse(
            Integer userType, String apiPath);

    /**
     * 根据API路径和HTTP方法查询权限
     */
    Optional<EntryPermission> findByApiPathAndHttpMethodAndDeletedFalse(String apiPath, String httpMethod);

    /**
     * 根据API路径查询所有权限配置
     */
    List<EntryPermission> findByApiPathAndDeletedFalse(String apiPath);
}
