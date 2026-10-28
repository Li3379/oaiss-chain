package com.oaiss.chain.repository;

import com.oaiss.chain.entity.UserTypeList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户类型列表 Repository
 *
 * @author OAISS Team
 */
@Repository
public interface UserTypeListRepository extends JpaRepository<UserTypeList, Long> {

    Optional<UserTypeList> findByTypeCodeAndDeletedFalse(String typeCode);

    Optional<UserTypeList> findByTypeNameAndDeletedFalse(String typeName);
}
