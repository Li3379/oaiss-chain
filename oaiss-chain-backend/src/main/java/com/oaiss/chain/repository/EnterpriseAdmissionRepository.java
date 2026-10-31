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

    /**
     * 按签发时间倒序取该企业的全部证书。
     *
     * <p>调用方（个人资料页 / 企业首页）此前取 {@code list[0]} 判断当前证书状态，
     * 但底层查询无排序——吊销后重新签发时，下标 0 可能仍是那条已吊销的旧记录，
     * 于是页面显示「已吊销」而实际证书有效。</p>
     */
    List<EnterpriseAdmission> findByEnterpriseIdAndDeletedFalseOrderByCreatedAtDesc(Long enterpriseId);

    Optional<EnterpriseAdmission> findFirstByEnterpriseIdAndStatusAndDeletedFalse(Long enterpriseId, Integer status);

    /**
     * 取该企业最新一张指定状态的证书（门禁判定用，避免无排序列表的首元素不确定性）
     */
    Optional<EnterpriseAdmission> findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Long enterpriseId, Integer status);

    Page<EnterpriseAdmission> findByDeletedFalse(Pageable pageable);

    Page<EnterpriseAdmission> findByStatusAndDeletedFalse(Integer status, Pageable pageable);

    boolean existsByCertificateNoAndDeletedFalse(String certificateNo);
}
