package com.oaiss.chain.repository;

import com.oaiss.chain.entity.MatchingResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 撮合结果 Repository
 *
 * @author OAISS Team
 */
public interface MatchingResultRepository extends JpaRepository<MatchingResult, Long> {

    /**
     * 查询与指定用户相关的撮合结果（作为买方或卖方）
     */
    @Query("SELECT m FROM MatchingResult m WHERE m.deleted = false AND (m.buyerId = :userId OR m.sellerId = :userId)")
    Page<MatchingResult> findByUserIdRelated(@Param("userId") Long userId, Pageable pageable);

    /**
     * 分页查询所有撮合结果
     */
    Page<MatchingResult> findByDeletedFalse(Pageable pageable);
}
