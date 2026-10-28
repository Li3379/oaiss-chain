package com.oaiss.chain.repository;

import com.oaiss.chain.entity.OutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 区块链发件箱仓储接口
 *
 * @author OAISS Team
 */
@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * 根据消息唯一ID查找
     */
    Optional<OutboxMessage> findByMessageIdAndDeletedFalse(String messageId);

    /**
     * 查询待处理或达到重试时隙的发件箱消息
     */
    @Query("SELECT m FROM OutboxMessage m WHERE m.deleted = false AND " +
           "(m.status = 'PENDING' OR (m.status = 'FAILED' AND (m.nextRetryAt IS NULL OR m.nextRetryAt <= :now))) " +
           "ORDER BY m.createdAt ASC")
    List<OutboxMessage> findPendingOrRetryableMessages(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * 根据业务聚合查询消息
     */
    List<OutboxMessage> findByAggregateTypeAndAggregateIdAndDeletedFalse(String aggregateType, Long aggregateId);
}
