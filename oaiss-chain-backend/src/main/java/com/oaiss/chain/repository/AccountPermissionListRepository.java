package com.oaiss.chain.repository;

import com.oaiss.chain.entity.AccountPermissionList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 账户权限列表 Repository
 *
 * @author OAISS Team
 */
@Repository
public interface AccountPermissionListRepository extends JpaRepository<AccountPermissionList, Long> {

    Optional<AccountPermissionList> findByPermissionCodeAndDeletedFalse(String permissionCode);

    List<AccountPermissionList> findByModuleAndDeletedFalse(String module);

    List<AccountPermissionList> findByDeletedFalseOrderBySortOrderAsc();

    boolean existsByPermissionCodeAndDeletedFalse(String permissionCode);
}
