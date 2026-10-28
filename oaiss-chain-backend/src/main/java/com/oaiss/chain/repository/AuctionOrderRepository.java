package com.oaiss.chain.repository;

import com.oaiss.chain.entity.AuctionOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 拍卖挂单 Repository
 *
 * @author OAISS Team
 */
public interface AuctionOrderRepository extends JpaRepository<AuctionOrder, Long> {

    /**
     * 查询用户的挂单
     */
    Page<AuctionOrder> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    /**
     * 查询买入挂单（按价格降序——最高优先）
     */
    List<AuctionOrder> findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(Integer direction, List<Integer> statuses);

    /**
     * 查询卖出挂单（按价格升序——最低优先）
     */
    List<AuctionOrder> findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(Integer direction, List<Integer> statuses);

    /**
     * 分页查询挂单
     */
    Page<AuctionOrder> findByDirectionAndStatusAndDeletedFalse(Integer direction, Integer status, Pageable pageable);

    /**
     * 分页查询所有挂单
     */
    Page<AuctionOrder> findByDeletedFalse(Pageable pageable);

    /**
     * 查询用户指定方向和状态的挂单
     */
    Page<AuctionOrder> findByUserIdAndDirectionAndStatusAndDeletedFalse(Long userId, Integer direction, Integer status, Pageable pageable);

    /**
     * 查询用户指定方向的挂单
     */
    Page<AuctionOrder> findByUserIdAndDirectionAndDeletedFalse(Long userId, Integer direction, Pageable pageable);

    /**
     * 查询最近的60条挂单（按创建时间降序）
     */
    List<AuctionOrder> findTop60ByDeletedFalseOrderByCreatedAtDesc();

    /**
     * 检查用户在指定方向和状态下是否存在有效挂单（防范自成交/洗售交易）
     */
    boolean existsByUserIdAndDirectionAndStatusInAndDeletedFalse(Long userId, Integer direction, List<Integer> statuses);
}
