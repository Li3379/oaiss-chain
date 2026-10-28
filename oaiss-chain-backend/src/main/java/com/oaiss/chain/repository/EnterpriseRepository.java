package com.oaiss.chain.repository;

import com.oaiss.chain.entity.Enterprise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 企业数据访问层
 * 
 * @author OAISS Team
 */
@Repository
public interface EnterpriseRepository extends JpaRepository<Enterprise, Long> {

    /**
     * 根据用户ID查找企业
     */
    Optional<Enterprise> findByUserId(Long userId);

    /**
     * 根据用户ID查找企业（未删除）
     */
    Optional<Enterprise> findByUserIdAndDeletedFalse(Long userId);

    /**
     * 根据信用代码查找企业
     */
    Optional<Enterprise> findByCreditCode(String creditCode);

    /**
     * 检查信用代码是否存在
     */
    boolean existsByCreditCode(String creditCode);

    /**
     * 根据认证状态分页查询
     */
    Page<Enterprise> findByCertStatusAndDeletedFalse(Integer certStatus, Pageable pageable);

    /**
     * 查询所有未删除的企业
     */
    Page<Enterprise> findByDeletedFalse(Pageable pageable);
}
