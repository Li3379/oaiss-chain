package com.oaiss.chain.repository;

import com.oaiss.chain.entity.Reviewer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 审核员数据访问层
 * 
 * @author OAISS Team
 */
@Repository
public interface ReviewerRepository extends JpaRepository<Reviewer, Long> {

    /**
     * 根据用户ID查找审核员
     */
    Optional<Reviewer> findByUserIdAndDeletedFalse(Long userId);

    /**
     * 根据用户ID批量查找审核员
     */
    List<Reviewer> findByUserIdInAndDeletedFalse(Collection<Long> userIds);

    /**
     * 根据资质编号查找
     */
    Optional<Reviewer> findByQualificationNo(String qualificationNo);

    /**
     * 根据状态分页查询
     */
    Page<Reviewer> findByStatusAndDeletedFalse(Integer status, Pageable pageable);

    /**
     * 根据级别查询
     */
    Page<Reviewer> findByLevelAndDeletedFalse(Integer level, Pageable pageable);

    /**
     * 查询所有未删除的审核员
     */
    Page<Reviewer> findByDeletedFalse(Pageable pageable);
}
