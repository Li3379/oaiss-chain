package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.entity.EnterpriseAdmission;
import com.oaiss.chain.entity.Reviewer;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.enums.QualificationStatusEnum;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.EnterpriseAdmissionRepository;
import com.oaiss.chain.repository.ReviewerQualificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 证书门禁：把「准入证书 / 审核资格证」变成真正生效的业务约束。
 *
 * <h2>为什么需要这一层</h2>
 * 改造前，管理员在证书管理页点「吊销」只会把 {@code status} 置 2，而全仓
 * 没有任何业务服务读取该字段（{@code EnterpriseAdmissionRepository} 仅存活在
 * 签发/吊销 Service 内部）。结果是吊销成了孤岛写入：企业照常登录、挂单、成交、
 * 提交报告，审核员照常审核；同时 UI 与文档却宣称「没有准入证书就不能交易」。
 *
 * <h2>判定口径</h2>
 * <ul>
 *   <li>有效 = {@code status=1} 且（{@code expiry_date} 为空 或 {@code expiry_date >= 今天}）；
 *       到期日含当天，避免「最后一天突然不能交易」的边界意外。</li>
 *   <li><b>无证书即拒绝</b>。这里刻意不复制 {@link CreditScoreService#checkTradePermission}
 *       的 {@code creditScore == null → true} 默认放行——那等于把「缺数据」当成「合规」。
 *       存量企业的有效期已由 V19 迁移回填，不会因此集体失效。</li>
 *   <li>企业ID为空直接拒绝：调用方漏传参数时的默认行为必须是关闭，而不是放行。</li>
 * </ul>
 *
 * <h2>使用约定</h2>
 * 所有「产生资产或证据」的写操作都应经过本服务：提交/审核碳报告、拍卖挂单与撮合结算、
 * P2P 创建与成交确认、碳币充值。只读浏览与登录不拦截——吊销后果应当让人看见
 * （顶部横幅 + 写操作被拒），而不是把人锁在门外看不到原因。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateGateService {

    private final EnterpriseAdmissionRepository enterpriseAdmissionRepository;
    private final ReviewerQualificationRepository reviewerQualificationRepository;
    private final com.oaiss.chain.repository.ReviewerRepository reviewerRepository;
    private final com.oaiss.chain.repository.EnterpriseRepository enterpriseRepository;

    /**
     * 企业是否持有有效的准入证书。
     */
    @Transactional(readOnly = true)
    public boolean isEnterpriseAdmitted(Long enterpriseId) {
        if (enterpriseId == null) {
            return false;
        }
        return enterpriseAdmissionRepository
                .findFirstByEnterpriseIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
                        enterpriseId, QualificationStatusEnum.ACTIVE.getCode())
                .map(this::notExpired)
                .orElse(false);
    }

    /**
     * 企业没有有效准入证书时抛出业务异常。
     *
     * @param enterpriseId 企业ID
     */
    @Transactional(readOnly = true)
    public void assertEnterpriseAdmitted(Long enterpriseId) {
        if (!isEnterpriseAdmitted(enterpriseId)) {
            log.warn("[CERT-GATE] Enterprise admission blocked: enterpriseId={}", enterpriseId);
            throw BusinessException.of(ErrorCode.ADMISSION_REQUIRED, "error.admission.required");
        }
    }

    /**
     * 审核员是否持有有效的审核资格证。
     */
    @Transactional(readOnly = true)
    public boolean isReviewerQualified(Long reviewerId) {
        if (reviewerId == null) {
            return false;
        }
        return reviewerQualificationRepository
                .findFirstByReviewerIdAndStatusAndDeletedFalseOrderByIssuedDateDescCreatedAtDesc(
                        reviewerId, QualificationStatusEnum.ACTIVE.getCode())
                .map(this::notExpired)
                .orElse(false);
    }

    /**
     * 审核员未持有效资格证时抛出业务异常。
     *
     * @param reviewerId 审核员ID
     */
    @Transactional(readOnly = true)
    public void assertReviewerQualified(Long reviewerId) {
        if (!isReviewerQualified(reviewerId)) {
            log.warn("[CERT-GATE] Reviewer qualification blocked: reviewerId={}", reviewerId);
            throw BusinessException.of(ErrorCode.QUALIFICATION_REQUIRED, "error.qualification.required");
        }
    }

    /**
     * 按「登录用户 ID」校验审核员资格证。
     *
     * <p>业务侧拿到的是 JWT 里的 userId，而资格证挂在 reviewer 表上，需要一跳映射。
     * 查不到 reviewer 记录时按未持证处理——审核员身份必须显式存在，不能靠角色名推断。</p>
     *
     * @param userId 登录用户ID
     */
    @Transactional(readOnly = true)
    public void assertReviewerQualifiedByUserId(Long userId) {
        if (userId == null) {
            throw BusinessException.of(ErrorCode.QUALIFICATION_REQUIRED, "error.qualification.required");
        }
        Long reviewerId = reviewerRepository.findByUserIdAndDeletedFalse(userId)
                .map(Reviewer::getId)
                .orElse(null);
        assertReviewerQualified(reviewerId);
    }

    /**
     * 按「登录用户 ID」校验其所属企业的准入证书。
     *
     * <p>碳币账户、挂单等对象挂的是 userId，而准入证书挂 enterpriseId，
     * 因此需要一跳映射。解析不到企业时按不合规处理。</p>
     *
     * @param userId 登录用户ID
     */
    @Transactional(readOnly = true)
    public void assertEnterpriseAdmittedByUserId(Long userId) {
        Long enterpriseId = userId == null ? null
                : enterpriseRepository.findByUserIdAndDeletedFalse(userId)
                        .map(com.oaiss.chain.entity.Enterprise::getId)
                        .orElse(null);
        assertEnterpriseAdmitted(enterpriseId);
    }

    private boolean notExpired(EnterpriseAdmission admission) {
        LocalDate expiry = admission.getExpiryDate();
        return expiry == null || !expiry.isBefore(LocalDate.now());
    }

    private boolean notExpired(ReviewerQualification qualification) {
        LocalDate expiry = qualification.getExpiryDate();
        return expiry == null || !expiry.isBefore(LocalDate.now());
    }
}
