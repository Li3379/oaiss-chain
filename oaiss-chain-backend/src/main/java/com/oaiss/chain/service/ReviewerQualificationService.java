package com.oaiss.chain.service;

import com.oaiss.chain.annotation.DistributedLock;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.Reviewer;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.QualificationStatusEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.ReviewerQualificationRepository;
import com.oaiss.chain.repository.ReviewerRepository;
import com.oaiss.chain.repository.UserRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reviewer qualification certificate service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewerQualificationService {

    private final ReviewerQualificationRepository reviewerQualificationRepository;
    private final ReviewerRepository reviewerRepository;
    private final UserRepository userRepository;

    private static final int MAX_CERT_NO_RETRIES = 3;
    /** 与 V19 迁移的 revoke_reason 列宽保持一致 */
    private static final int REVOKE_REASON_MAX_LENGTH = 200;
    /** 资格证默认有效期（年） */
    private static final int CERT_VALID_YEARS = 1;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DEFAULT_QUALIFICATION_TYPE = "碳排放审核资质";
    private static final String DEFAULT_ISSUING_AUTHORITY = "OAISS管理中心";

    /**
     * Issue a reviewer qualification certificate.
     */
    @DistributedLock(key = "'cert:reviewer:' + #reviewerId")
    @Transactional
    public ReviewerQualification issueCertificate(Long reviewerId) {
        Reviewer reviewer = resolveReviewer(reviewerId);
        Long normalizedReviewerId = reviewer.getId();

        List<ReviewerQualification> active = reviewerQualificationRepository
                .findByReviewerIdAndStatusAndDeletedFalse(normalizedReviewerId, QualificationStatusEnum.ACTIVE.getCode());
        if (!active.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_ERROR, "error.qualification.alreadyActive");
        }

        ReviewerQualification qualification = ReviewerQualification.builder()
                .reviewerId(normalizedReviewerId)
                .qualificationType(DEFAULT_QUALIFICATION_TYPE)
                .certificateNo(generateUniqueCertificateNo())
                .issuingAuthority(DEFAULT_ISSUING_AUTHORITY)
                .issuedDate(LocalDate.now())
                // 同企业准入证书：有效期默认签发日 + 1 年
                .expiryDate(LocalDate.now().plusYears(CERT_VALID_YEARS))
                .status(QualificationStatusEnum.ACTIVE.getCode())
                .build();

        qualification = reviewerQualificationRepository.save(qualification);
        log.info("ReviewerQualification issued: {} for reviewer {}", qualification.getCertificateNo(), normalizedReviewerId);
        return qualification;
    }

    /**
     * Revoke the active certificate for a reviewer.
     */
    @Transactional
    public void revokeCertificate(Long reviewerId) {
        revokeCertificate(reviewerId, null);
    }

    /**
     * 吊销审核员资格证，并留下「何时、为何」的审计痕迹。
     *
     * @param reviewerId 审核员ID
     * @param reason     吊销原因，可为空；超过 200 字符时按列宽截断
     */
    @Transactional
    public void revokeCertificate(Long reviewerId, String reason) {
        Reviewer reviewer = resolveReviewer(reviewerId);
        Long normalizedReviewerId = reviewer.getId();

        ReviewerQualification qualification = findBestActiveCertificate(normalizedReviewerId)
                .orElseThrow(() -> BusinessException.notFound("error.qualification.notFound"));

        qualification.setStatus(QualificationStatusEnum.REVOKED.getCode());
        qualification.setRevokedAt(LocalDateTime.now());
        qualification.setRevokeReason(normalizeReason(reason));
        reviewerQualificationRepository.save(qualification);
        log.info("ReviewerQualification revoked for reviewer {}: reason={}",
                normalizedReviewerId, qualification.getRevokeReason());
    }

    /** 原因列宽 200；超长截断而非抛错——吊销动作不应因备注而回滚。 */
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
     * List qualification certificates with optional status filter.
     */
    public Page<ReviewerQualification> listCertificates(Integer status, Integer page, Integer size) {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 10;

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null) {
            return reviewerQualificationRepository.findByStatusAndDeletedFalse(status, pageable);
        }
        return reviewerQualificationRepository.findByDeletedFalse(pageable);
    }

    /**
     * Get all certificates for a reviewer, with active/latest certificates first.
     */
    @Transactional
    public List<ReviewerQualification> getMyCertificate(Long reviewerId) {
        Reviewer reviewer = reviewerRepository.findById(reviewerId)
                .orElseThrow(() -> BusinessException.notFound("error.reviewer.notFound"));
        return getCertificatesForReviewer(reviewer);
    }

    private Reviewer resolveReviewer(Long reviewerIdOrUserId) {
        Optional<Reviewer> reviewerById = reviewerRepository.findById(reviewerIdOrUserId);
        if (reviewerById.isPresent()) {
            migrateLegacyCertificates(reviewerById.get());
            return reviewerById.get();
        }

        User user = userRepository.findById(reviewerIdOrUserId)
                .orElseThrow(() -> BusinessException.notFound("error.user.notFound"));
        Reviewer reviewer = reviewerRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseThrow(() -> BusinessException.notFound("error.reviewer.notFound"));
        migrateLegacyCertificates(reviewer);
        return reviewer;
    }

    private List<ReviewerQualification> getCertificatesForReviewer(Reviewer reviewer) {
        migrateLegacyCertificates(reviewer);

        List<ReviewerQualification> combined = new ArrayList<>(
                reviewerQualificationRepository.findByReviewerIdAndDeletedFalse(reviewer.getId()));

        if (reviewer.getUserId() != null && !reviewer.getUserId().equals(reviewer.getId())) {
            combined.addAll(reviewerQualificationRepository.findByReviewerIdAndDeletedFalse(reviewer.getUserId()));
        }

        return combined.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                .sorted(Comparator
                        .comparing((ReviewerQualification item) ->
                                item.getStatus() != null && item.getStatus().equals(QualificationStatusEnum.ACTIVE.getCode()) ? 0 : 1)
                        .thenComparing(ReviewerQualification::getIssuedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ReviewerQualification::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Optional<ReviewerQualification> findBestActiveCertificate(Long reviewerId) {
        return reviewerQualificationRepository
                .findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc(
                        reviewerId, QualificationStatusEnum.ACTIVE.getCode());
    }

    private void migrateLegacyCertificates(Reviewer reviewer) {
        Long reviewerId = reviewer.getId();
        Long userId = reviewer.getUserId();
        if (reviewerId == null || userId == null || reviewerId.equals(userId)) {
            return;
        }

        List<ReviewerQualification> legacyCertificates = reviewerQualificationRepository.findByReviewerIdAndDeletedFalse(userId);
        if (legacyCertificates.isEmpty()) {
            return;
        }

        for (ReviewerQualification certificate : legacyCertificates) {
            certificate.setReviewerId(reviewerId);
        }
        reviewerQualificationRepository.saveAll(legacyCertificates);
        log.info("Migrated {} legacy reviewer qualifications from userId {} to reviewerId {}",
                legacyCertificates.size(), userId, reviewerId);
    }

    /**
     * Generate a unique certificate number: RQ-{yyyyMMdd}-{6 random digits}.
     */
    private String generateUniqueCertificateNo() {
        for (int i = 0; i < MAX_CERT_NO_RETRIES; i++) {
            String certNo = String.format("RQ-%s-%06d",
                    DATE_FORMAT.format(LocalDate.now()),
                    SECURE_RANDOM.nextInt(1_000_000));
            if (!reviewerQualificationRepository.existsByCertificateNoAndDeletedFalse(certNo)) {
                return certNo;
            }
        }
        throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "error.qualification.certNoGenerationFailed");
    }
}
