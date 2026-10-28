package com.oaiss.chain.repository;

import com.oaiss.chain.entity.ThirdPartyOrg;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 第三方机构数据访问层
 * 
 * @author OAISS Team
 */
@Repository
public interface ThirdPartyOrgRepository extends JpaRepository<ThirdPartyOrg, Long> {

    /**
     * 根据用户ID查找
     */
    Optional<ThirdPartyOrg> findByUserIdAndDeletedFalse(Long userId);

    /**
     * 根据机构编码查找
     */
    Optional<ThirdPartyOrg> findByOrgCode(String orgCode);

    /**
     * 查询所有未删除的机构
     */
    Page<ThirdPartyOrg> findByDeletedFalse(Pageable pageable);
}
