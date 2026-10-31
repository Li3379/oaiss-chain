package com.oaiss.chain.repository;

import com.oaiss.chain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 交易数据访问层
 * 
 * @author OAISS Team
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * 根据交易编号查找
     */
    Optional<Transaction> findByTradeNo(String tradeNo);

    /**
     * 根据卖方ID分页查询
     */
    Page<Transaction> findBySellerIdAndDeletedFalse(Long sellerId, Pageable pageable);

    /**
     * 根据买方ID分页查询
     */
    Page<Transaction> findByBuyerIdAndDeletedFalse(Long buyerId, Pageable pageable);

    /**
     * 统计与指定用户相关的交易数量（作为买方或卖方）
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.deleted = false AND (t.buyerId = :userId OR t.sellerId = :userId)")
    long countByUserIdRelated(@Param("userId") Long userId);

    /**
     * 根据交易类型分页查询
     */
    Page<Transaction> findByTradeTypeAndDeletedFalse(Integer tradeType, Pageable pageable);

    /**
     * 根据状态分页查询
     */
    Page<Transaction> findByStatusAndDeletedFalse(Integer status, Pageable pageable);

    /**
     * 查询未删除的交易（分页）
     */
    Page<Transaction> findByDeletedFalse(Pageable pageable);

    /**
     * 综合搜索
     */
    @Query("SELECT t FROM Transaction t WHERE t.deleted = false " +
            "AND (:sellerId IS NULL OR t.sellerId = :sellerId) " +
            "AND (:buyerId IS NULL OR t.buyerId = :buyerId) " +
            "AND (:tradeType IS NULL OR t.tradeType = :tradeType) " +
            "AND (:status IS NULL OR t.status = :status)")
    Page<Transaction> search(
            @Param("sellerId") Long sellerId,
            @Param("buyerId") Long buyerId,
            @Param("tradeType") Integer tradeType,
            @Param("status") Integer status,
            Pageable pageable);

    /**
     * 综合搜索（支持用户组织边界隔离）
     */
    @Query("SELECT t FROM Transaction t WHERE t.deleted = false " +
            "AND (:userId IS NULL OR t.sellerId = :userId OR t.buyerId = :userId) " +
            "AND (:sellerId IS NULL OR t.sellerId = :sellerId) " +
            "AND (:buyerId IS NULL OR t.buyerId = :buyerId) " +
            "AND (:tradeType IS NULL OR t.tradeType = :tradeType) " +
            "AND (:status IS NULL OR t.status = :status)")
    Page<Transaction> searchWithUserBound(
            @Param("userId") Long userId,
            @Param("sellerId") Long sellerId,
            @Param("buyerId") Long buyerId,
            @Param("tradeType") Integer tradeType,
            @Param("status") Integer status,
            Pageable pageable);

    /**
     * 带关键词的综合搜索。
     *
     * <p>关键词匹配交易编号，以及买卖双方企业的名称与统一社会信用代码——
     * 信用代码必须覆盖：名称会变、信用代码不变，且它是唯一的法定标识。
     *
     * <p>实现说明：企业名称/信用代码通过子查询命中，避免把 enterprise 表 join 进来
     * 造成分页计数失真（join 后一交易对多企业会重复行）。
     */
    @Query("SELECT t FROM Transaction t WHERE t.deleted = false " +
            "AND (:sellerId IS NULL OR t.sellerId = :sellerId) " +
            "AND (:buyerId IS NULL OR t.buyerId = :buyerId) " +
            "AND (:tradeType IS NULL OR t.tradeType = :tradeType) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (LOWER(t.tradeNo) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR t.sellerId IN (SELECT e.userId FROM Enterprise e WHERE e.deleted = false " +
            "         AND (LOWER(e.enterpriseName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "              OR LOWER(e.creditCode) LIKE LOWER(CONCAT('%', :keyword, '%')))) " +
            "     OR t.buyerId IN (SELECT e.userId FROM Enterprise e WHERE e.deleted = false " +
            "         AND (LOWER(e.enterpriseName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "              OR LOWER(e.creditCode) LIKE LOWER(CONCAT('%', :keyword, '%')))))")
    Page<Transaction> searchWithKeyword(
            @Param("keyword") String keyword,
            @Param("sellerId") Long sellerId,
            @Param("buyerId") Long buyerId,
            @Param("tradeType") Integer tradeType,
            @Param("status") Integer status,
            Pageable pageable);

    /**
     * 带关键词 + 企业租户边界的综合搜索。
     *
     * <p>新增 keyword 参数不得成为绕过租户隔离的通道，故单独提供带边界的版本：
     * 企业用户只能检索到自己参与的成交。
     */
    @Query("SELECT t FROM Transaction t WHERE t.deleted = false " +
            "AND (:userId IS NULL OR t.sellerId = :userId OR t.buyerId = :userId) " +
            "AND (:sellerId IS NULL OR t.sellerId = :sellerId) " +
            "AND (:buyerId IS NULL OR t.buyerId = :buyerId) " +
            "AND (:tradeType IS NULL OR t.tradeType = :tradeType) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (LOWER(t.tradeNo) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "     OR t.sellerId IN (SELECT e.userId FROM Enterprise e WHERE e.deleted = false " +
            "         AND (LOWER(e.enterpriseName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "              OR LOWER(e.creditCode) LIKE LOWER(CONCAT('%', :keyword, '%')))) " +
            "     OR t.buyerId IN (SELECT e.userId FROM Enterprise e WHERE e.deleted = false " +
            "         AND (LOWER(e.enterpriseName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "              OR LOWER(e.creditCode) LIKE LOWER(CONCAT('%', :keyword, '%')))))")
    Page<Transaction> searchWithUserBoundAndKeyword(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("sellerId") Long sellerId,
            @Param("buyerId") Long buyerId,
            @Param("tradeType") Integer tradeType,
            @Param("status") Integer status,
            Pageable pageable);

    /**
     * 查询与指定用户相关的交易（作为买方或卖方）
     */
    @Query("SELECT t FROM Transaction t WHERE t.deleted = false " +
            "AND (t.sellerId = :userId OR t.buyerId = :userId) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:tradeType IS NULL OR t.tradeType = :tradeType) " +
            "AND (:tradeNo IS NULL OR t.tradeNo LIKE CONCAT('%', :tradeNo, '%')) " +
            "AND (:keyword IS NULL OR (" +
            "   (t.sellerId = :userId AND EXISTS (" +
            "       SELECT 1 FROM User buyer " +
            "       WHERE buyer.id = t.buyerId " +
            "       AND LOWER(COALESCE(buyer.realName, '')) LIKE CONCAT('%', LOWER(:keyword), '%')" +
            "   )) OR " +
            "   (t.buyerId = :userId AND EXISTS (" +
            "       SELECT 1 FROM User seller " +
            "       WHERE seller.id = t.sellerId " +
            "       AND LOWER(COALESCE(seller.realName, '')) LIKE CONCAT('%', LOWER(:keyword), '%')" +
            "   ))" +
            ")) " +
            "AND (:identity IS NULL " +
            "   OR (:identity = 'buyer' AND t.buyerId = :userId) " +
            "   OR (:identity = 'seller' AND t.sellerId = :userId)) " +
            "AND (:startTime IS NULL OR t.createdAt >= :startTime) " +
            "AND (:endTime IS NULL OR t.createdAt <= :endTime)")
    Page<Transaction> findByUserIdRelated(
            @Param("userId") Long userId,
            @Param("tradeType") Integer tradeType,
            @Param("status") Integer status,
            @Param("tradeNo") String tradeNo,
            @Param("keyword") String keyword,
            @Param("identity") String identity,
            @Param("startTime") java.time.LocalDateTime startTime,
            @Param("endTime") java.time.LocalDateTime endTime,
            Pageable pageable);

    /**
     * 统计全市场累计成交碳配额总量（吨CO2当量）
     */
    @Query("SELECT COALESCE(SUM(t.quantity), 0) FROM Transaction t WHERE t.deleted = false")
    java.math.BigDecimal sumTotalQuantity();

    /**
     * 统计全市场累计成交金额（元）
     */
    @Query("SELECT COALESCE(SUM(t.totalAmount), 0) FROM Transaction t WHERE t.deleted = false")
    java.math.BigDecimal sumTotalAmount();

    /**
     * 近 N 日成交均价（按成交数量加权）。
     *
     * <p>供成交审核的价格偏离检查使用。只统计**已完成**（status=2）的交易：
     * 待审成交的价格尚未经人工确认，若计入参考价会形成自我强化——
     * 异常价把参考价拉偏，反过来让后续异常价显得正常。
     *
     * @return 无历史成交时返回 null（分流器据此跳过价格检查，避免冷启动全量转人工）
     */
    @Query("SELECT SUM(t.totalAmount) / SUM(t.quantity) FROM Transaction t "
            + "WHERE t.deleted = false AND t.status = 2 AND t.quantity > 0 "
            + "AND t.completedAt >= :since")
    java.math.BigDecimal findAverageUnitPriceSince(
            @Param("since") java.time.LocalDateTime since);
}
