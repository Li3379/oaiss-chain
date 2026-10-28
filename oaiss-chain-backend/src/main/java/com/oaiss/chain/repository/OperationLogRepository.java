package com.oaiss.chain.repository;

import com.oaiss.chain.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志Repository
 * 
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    /**
     * 根据用户ID查询操作日志
     */
    Page<OperationLog> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    /**
     * 根据模块查询操作日志
     */
    Page<OperationLog> findByModuleAndDeletedFalse(String module, Pageable pageable);

    /**
     * 根据时间范围查询操作日志
     */
    @Query("SELECT o FROM OperationLog o WHERE o.createdAt BETWEEN :startTime AND :endTime AND o.deleted = false")
    Page<OperationLog> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, 
                                               @Param("endTime") LocalDateTime endTime, 
                                               Pageable pageable);

    /**
     * 根据用户ID和时间范围查询
     */
    @Query("SELECT o FROM OperationLog o WHERE o.userId = :userId AND o.createdAt BETWEEN :startTime AND :endTime AND o.deleted = false")
    Page<OperationLog> findByUserIdAndCreatedAtBetween(@Param("userId") Long userId,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime,
                                                        Pageable pageable);

    /**
     * 根据状态查询
     */
    Page<OperationLog> findByStatusAndDeletedFalse(Integer status, Pageable pageable);

    /**
     * 统计指定用户在时间范围内的操作次数
     */
    @Query("SELECT COUNT(o) FROM OperationLog o WHERE o.userId = :userId AND o.createdAt >= :startTime AND o.deleted = false")
    Long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    /**
     * 查询最近的操作日志
     */
    List<OperationLog> findTop100ByDeletedFalseOrderByCreatedAtDesc();

    /**
     * 删除指定时间之前的日志
     */
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
