package com.oaiss.chain.service;

import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.enums.QualificationStatusEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.EnterpriseAdmissionRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 企业准入证书 Service
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseAdmissionService {

    private final EnterpriseAdmissionRepository enterpriseAdmissionRepository;
    private final EnterpriseRepository enterpriseRepository;

    private static final int MAX_CERT_NO_RETRIES = 3;
    /** 与 V19 迁移的 revoke_reason 列宽保持一致 */
    private static final int REVOKE_REASON_MAX_LENGTH = 200;
    /** 证书默认有效期（年），与 V19 迁移的存量回填口径一致 */
    private static final int CERT_VALID_YEARS = 1;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 签发准入证书
     *
     * @param enterpriseId 企业ID
     * @return 签发的准入证书
     */
    @DistributedLock(key = "'cert:enterprise:' + #enterpriseId")
    @Transactional
    public EnterpriseAdmission issueCertificate(Long enterpriseId) {
        // 1. Verify enterprise exists
        enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> BusinessException.notFound("error.enterprise.notFound"));

        // 2. Check duplicate ACTIVE (D-07)
        enterpriseAdmissionRepository.findFirstByEnterpriseIdAndStatusAndDeletedFalse(enterpriseId, QualificationStatusEnum.ACTIVE.getCode())
                .ifPresent(existing -> {
                    throw BusinessException.of(ErrorCode.PARAM_ERROR, "error.admission.alreadyActive");
                });

        // 3. Generate certificateNo with collision retry (D-04)
        String certNo = generateUniqueCertificateNo();

        // 4. Build and save
        EnterpriseAdmission admission = EnterpriseAdmission.builder()
                .enterpriseId(enterpriseId)
                .certificateNo(certNo)
                .issuedDate(LocalDate.now())
                // 有效期与 V19 存量回填口径一致：签发日 + 1 年。
                // 该列此前从不赋值，「到期」语义形同虚设——门禁判过期时永远判不出来。
                .expiryDate(LocalDate.now().plusYears(CERT_VALID_YEARS))
                .status(QualificationStatusEnum.ACTIVE.getCode())
                .build();
        admission = enterpriseAdmissionRepository.save(admission);
        log.info("Enterprise admission issued: {} for enterprise {}", certNo, enterpriseId);
        return admission;
    }

    /**
     * 吊销准入证书（不记录原因）。
     *
     * @param enterpriseId 企业ID
     */
    @Transactional
    public void revokeCertificate(Long enterpriseId) {
        revokeCertificate(enterpriseId, null);
    }

    /**
     * 吊销准入证书，并留下「何时、为何」的审计痕迹。
     *
     * <p>改造前这里只写 {@code status=2}，既无时间也无原因，事后无法回答
     * 「这张证书是什么时候因为什么被吊销的」。吊销本身已由
     * {@link CertificateGateService} 变成真正生效的业务约束，留痕是配套动作。</p>
     *
     * @param enterpriseId 企业ID
     * @param reason       吊销原因，可为空；超过 200 字符时按列宽截断
     */
    @Transactional
    public void revokeCertificate(Long enterpriseId, String reason) {
        // Find ACTIVE certificate (D-08)
        EnterpriseAdmission admission = enterpriseAdmissionRepository
                .findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
                        enterpriseId, QualificationStatusEnum.ACTIVE.getCode())
                .orElseThrow(() -> BusinessException.notFound("error.admission.notFound"));

        admission.setStatus(QualificationStatusEnum.REVOKED.getCode());
        admission.setRevokedAt(LocalDateTime.now());
        admission.setRevokeReason(normalizeReason(reason));
        enterpriseAdmissionRepository.save(admission);
        log.info("Enterprise admission revoked for enterprise {}: reason={}", enterpriseId, admission.getRevokeReason());
    }

    /** 原因列宽 200；超长直接截断而不是抛错——吊销动作本身不应因备注失败而回滚。 */
    private static String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > REVOKE_REASON_MAX_LENGTH
                ? trimmed.substring(0, REVOKE_REASON_MAX_LENGTH)
                : trimmed;
    }

    /**
     * 查询准入证书列表（分页，可选状态筛选）
     *
     * @param status 状态筛选（可选）
     * @param page   页码（从1开始）
     * @param size   每页数量
     * @return 分页结果
     */
    public Page<EnterpriseAdmission> listCertificates(Integer status, Integer page, Integer size) {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 10;
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null) {
            return enterpriseAdmissionRepository.findByStatusAndDeletedFalse(status, pageable);
        }
        return enterpriseAdmissionRepository.findByDeletedFalse(pageable);
    }

    /**
     * 查询企业自身的准入证书（按创建时间倒序，最新在前）。
     *
     * <p>调用方普遍取 {@code list[0]} 判断「当前证书状态」。此前底层查询无排序，
     * 吊销后重新签发时下标 0 可能仍是已吊销的旧记录，页面会误显示「已吊销」。</p>
     *
     * @param enterpriseId 企业ID
     * @return 该企业的所有准入证书，最新在前
     */
    public List<EnterpriseAdmission> getMyCertificate(Long enterpriseId) {
        return enterpriseAdmissionRepository
                .findByEnterpriseIdAndDeletedFalseOrderByCreatedAtDesc(enterpriseId);
    }

    /**
     * 生成唯一证书编号: EA-{yyyyMMdd}-{6位随机数字}
     */
    private String generateUniqueCertificateNo() {
        for (int i = 0; i < MAX_CERT_NO_RETRIES; i++) {
            String certNo = String.format("EA-%s-%06d",
                    DATE_FORMAT.format(LocalDate.now()),
                    SECURE_RANDOM.nextInt(1000000));
            if (!enterpriseAdmissionRepository.existsByCertificateNoAndDeletedFalse(certNo)) {
                return certNo;
            }
        }
        throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "error.admission.certNoGenerationFailed");
    }
}
