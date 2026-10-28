package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CarbonNeutralProject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;

/**
 * 碳中和项目 Repository
 *
 * @author OAISS Team
 */
@Repository
public interface CarbonNeutralProjectRepository extends JpaRepository<CarbonNeutralProject, Long> {

    Optional<CarbonNeutralProject> findByProjectNo(String projectNo);

    Page<CarbonNeutralProject> findByOwnerIdAndDeletedFalse(Long ownerId, Pageable pageable);

    Page<CarbonNeutralProject> findByProjectTypeAndDeletedFalse(Integer projectType, Pageable pageable);

    @Query("SELECT p FROM CarbonNeutralProject p WHERE p.deleted = false " +
            "AND (:projectType IS NULL OR p.projectType = :projectType) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:keyword IS NULL OR p.projectName LIKE %:keyword% OR p.projectNo LIKE %:keyword%)")
    Page<CarbonNeutralProject> search(
            @Param("projectType") Integer projectType,
            @Param("status") Integer status,
            @Param("keyword") String keyword,
            Pageable pageable);

    Page<CarbonNeutralProject> findByOwnerIdAndStatusAndDeletedFalse(Long ownerId, Integer status, Pageable pageable);

    Page<CarbonNeutralProject> findByVerifierIdAndVerificationStatusAndDeletedFalse(Long verifierId, Integer verificationStatus, Pageable pageable);

    /** 管理员视角：全部待核证工单（不限指派人）。 */
    Page<CarbonNeutralProject> findByVerificationStatusAndDeletedFalse(Integer verificationStatus, Pageable pageable);

    Page<CarbonNeutralProject> findByVerifierIdInAndVerificationStatusAndDeletedFalse(Collection<Long> verifierIds,
                                                                                      Integer verificationStatus,
                                                                                      Pageable pageable);

    /**
     * 核证工单池：已指派给当前核证人的待核证项目 + 尚未指派但已进入可核证阶段的公共工单。
     * <p>
     * 历史缺陷：仅按 verifierId 过滤，导致「未指派」的公共工单对审核员完全不可见（页面恒空白），
     * 而工单池的语义（见 Controller 注释）本就是「待第三方核证机构处理的工单池」。
     */
    @Query("SELECT p FROM CarbonNeutralProject p WHERE p.deleted = false " +
            "AND p.verificationStatus = :verificationStatus " +
            "AND (p.verifierId IN :verifierIds " +
            "     OR (p.verifierId IS NULL AND p.status IN :eligibleStatuses))")
    Page<CarbonNeutralProject> findVerificationWorkPool(
            @Param("verifierIds") Collection<Long> verifierIds,
            @Param("eligibleStatuses") Collection<Integer> eligibleStatuses,
            @Param("verificationStatus") Integer verificationStatus,
            Pageable pageable);
}
