package com.oaiss.chain.repository;

import com.oaiss.chain.entity.EnterpriseAdmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 企业准入证书 Repository
 *
 * @author OAISS Team
 */
@Repository
public interface EnterpriseAdmissionRepository extends JpaRepository<EnterpriseAdmission, Long> {

    List<EnterpriseAdmission> findByEnterpriseIdAndDeletedFalse(Long enterpriseId);

    Optional<EnterpriseAdmission> findFirstByEnterpriseIdAndStatusAndDeletedFalse(Long enterpriseId, Integer status);

    Page<EnterpriseAdmission> findByDeletedFalse(Pageable pageable);

    Page<EnterpriseAdmission> findByStatusAndDeletedFalse(Integer status, Pageable pageable);

    boolean existsByCertificateNoAndDeletedFalse(String certificateNo);
}
