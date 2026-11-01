package com.oaiss.chain.repository;

import com.oaiss.chain.entity.Enterprise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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
     * 根据用户ID集合批量查找未删除企业（撮合排序批量解析信誉分，避免 N+1 查询）
     */
    List<Enterprise> findByUserIdInAndDeletedFalse(Collection<Long> userIds);

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

    /**
     * 交易对手搜索：按企业名称或统一社会信用代码模糊匹配。
     *
     * <p><b>selfId 必须 NULL-safe</b>：写成 e.id &lt;&gt; :selfId 时，若传入 null，
     * SQL 中 id &lt;&gt; NULL 恒为 UNKNOWN，会静默返回 0 行。管理员账号没有关联企业
     * （JwtUserDetails.enterpriseId 为 null），正是该分支的触发者，故必须写成
     * :selfId IS NULL OR e.id &lt;&gt; :selfId。范式参见 CarbonReportRepository。
     *
     * <p>关键词为空时由调用方（Service）直接返回空页，不依赖此查询做全量兜底。
     *
     * @param kw     关键词（企业名称或信用代码片段）
     * @param selfId 需排除的企业ID；null 表示不排除
     */
    @Query("SELECT e FROM Enterprise e WHERE e.deleted = false "
            + "AND (:selfId IS NULL OR e.id <> :selfId) "
            + "AND (LOWER(e.enterpriseName) LIKE LOWER(CONCAT('%', :kw, '%')) "
            + "  OR LOWER(e.creditCode) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Enterprise> searchForCounterparty(@Param("kw") String kw,
                                           @Param("selfId") Long selfId,
                                           Pageable pageable);
}
