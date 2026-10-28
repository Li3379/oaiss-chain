package com.oaiss.chain.service;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Reviewer;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.ReportStatusEnum;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核员服务
 * 提供审核员信息查询、待审核报告管理等功能
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewerService {

    private static final List<Integer> HISTORY_STATUSES = List.of(
            ReportStatusEnum.APPROVED.getCode(),
            ReportStatusEnum.REJECTED.getCode(),
            ReportStatusEnum.ON_CHAIN.getCode()
    );

    private final ReviewerRepository reviewerRepository;
    private final CarbonReportRepository carbonReportRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;
    private final DigitalSignatureService digitalSignatureService;

    /**
     * 获取当前审核员信息
     */
    @Transactional(readOnly = true)
    public Reviewer getReviewerInfo(Long userId) {
        Reviewer reviewer = reviewerRepository.findByUserIdAndDeletedFalse(userId)
                .filter(r -> !r.getDeleted())
                .orElseThrow(() -> new RuntimeException("审核员信息不存在"));

        userRepository.findByIdAndDeletedFalse(userId)
                .map(User::getRealName)
                .filter(name -> name != null && !name.isBlank())
                .ifPresent(reviewer::setName);
        return reviewer;
    }

    /**
     * 获取待审核报告列表
     */
    @Transactional(readOnly = true)
    public Page<CarbonReport> getPendingReports(Long userId, Integer page, Integer size) {
        getReviewerInfo(userId);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CarbonReport> reports = carbonReportRepository.findByStatusAndDeletedFalse(
                ReportStatusEnum.SUBMITTED.getCode(), pageable);
        // 可信签名链：待审列表即完成验签（有签名才验），审核员可在决策前分流问题报告
        reports.forEach(report -> {
            enrichReportDisplayFields(report);
            report.setSignatureValid(verifySignatureOf(report));
        });
        return reports;
    }

    /**
     * 获取审核历史
     */
    @Transactional(readOnly = true)
    public Page<CarbonReport> getReviewHistory(Long userId, Integer page, Integer size) {
        Reviewer reviewer = getReviewerInfo(userId);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<CarbonReport> history = carbonReportRepository.findByReviewerIdAndStatusInAndDeletedFalse(
                reviewer.getUserId(), HISTORY_STATUSES, pageable);
        history.forEach(this::enrichReportDisplayFields);
        return history;
    }

    /**
     * 获取审核统计数据
     *
     * <p>口径约定：总审核数/通过数/驳回数均按「该审核员实际审结且未删除的报告」统计，
     * 与「审核历史」列表同源，保证 通过数+驳回数=总审核数 恒成立；
     * 通过率由前端用 passed/completed 计算，分子分母同口径。
     * 不再使用 Reviewer.completedReviews 累加计数器——该计数器含历史已删除报告的审核，
     * 会随测试数据清理而漂移，曾出现 156(生涯) vs 17(实存) 的口径错位。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(Long userId) {
        Reviewer reviewer = getReviewerInfo(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("level", reviewer.getLevel());
        stats.put("organization", reviewer.getOrganization());

        long pendingCount = carbonReportRepository.countByStatusAndDeletedFalse(ReportStatusEnum.SUBMITTED.getCode());
        stats.put("pendingCount", pendingCount);

        long passedCount = carbonReportRepository.countByReviewerIdAndStatusInAndDeletedFalse(
                reviewer.getUserId(),
                List.of(ReportStatusEnum.APPROVED.getCode(), ReportStatusEnum.ON_CHAIN.getCode()));
        stats.put("passedCount", passedCount);

        long rejectedCount = carbonReportRepository.countByReviewerIdAndStatusInAndDeletedFalse(
                reviewer.getUserId(),
                List.of(ReportStatusEnum.REJECTED.getCode()));
        stats.put("rejectedCount", rejectedCount);

        stats.put("completedReviews", passedCount + rejectedCount);

        return stats;
    }

    /**
     * 更新审核员状态
     */
    @Transactional
    public void incrementCompletedReviews(Long userId) {
        Reviewer reviewer = getReviewerInfo(userId);
        reviewer.setCompletedReviews(reviewer.getCompletedReviews() + 1);
        reviewerRepository.save(reviewer);
        log.info("审核员完成审核数更新: userId={}, completedReviews={}", userId, reviewer.getCompletedReviews());
    }

    private void enrichReportDisplayFields(CarbonReport report) {
        enterpriseRepository.findById(report.getEnterpriseId())
                .map(Enterprise::getEnterpriseName)
                .ifPresent(report::setEnterpriseName);

        if (report.getStatus() != null) {
            report.setStatusText(ReportStatusEnum.fromCode(report.getStatus()).getDescription());
        }
    }

    /**
     * 与 CarbonService.verifyReportSignature 同语义：
     * 无签名 → null；验签异常/失败 → false；成功 → true
     */
    private Boolean verifySignatureOf(CarbonReport report) {
        if (report.getSignatureData() == null || report.getSignatureData().isBlank()) {
            return null;
        }
        try {
            return digitalSignatureService.verifySignature(
                    report.getSubmitterId(),
                    report.getEmissionData(),
                    report.getSignatureData());
        } catch (Exception e) {
            log.warn("Signature verification failed for pending report {}: {}", report.getReportNo(), e.getMessage());
            return Boolean.FALSE;
        }
    }
}
