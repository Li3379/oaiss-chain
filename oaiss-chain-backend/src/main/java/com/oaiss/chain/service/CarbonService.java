package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.CarbonReportSnapshot;
import com.oaiss.chain.dto.BatchCertifyRequest;
import com.oaiss.chain.dto.BatchCertifyResponse;
import com.oaiss.chain.dto.CarbonReportRequest;
import com.oaiss.chain.dto.CarbonReportResponse;
import com.oaiss.chain.dto.ReviewRequest;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.CreditEventTypeEnum;
import com.oaiss.chain.enums.ReportStatusEnum;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.exception.AuthorizationException;
import com.oaiss.chain.exception.CarbonException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.util.CommonUtils;
import com.oaiss.chain.util.EmissionDataValidator;
import com.oaiss.chain.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonService {

    private static final String ENTERPRISE_NOT_FOUND = "Enterprise info not found";
    private static final String NO_PERMISSION = "No permission to operate this report";
    private static final String REVIEW_STATUS_INVALID = "Report status does not allow review";
    private static final String REVIEW_DECISION_INVALID = "Invalid reviewer decision";
    private static final String CERTIFY_STATUS_INVALID = "Only approved reports can be certified";
    private static final String CERTIFY_DECISION_INVALID = "Invalid certification decision";
    private static final String CERTIFICATION_COMMENT_PREFIX = "Certification Comment: ";

    private final CarbonReportRepository carbonReportRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CreditScoreService creditScoreService;
    private final EmissionRatingService emissionRatingService;
    private final BlockchainServicePort blockchainService;
    private final DigitalSignatureService digitalSignatureService;

    @Transactional
    public CarbonReportResponse createReport(JwtUserDetails currentUser, CarbonReportRequest request) {
        Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> CarbonException.submitFailed(ENTERPRISE_NOT_FOUND));

        BigDecimal[] totals = parseEmissionTotals(request.getEmissionData());
        BigDecimal scope1 = totals[0];
        BigDecimal scope2 = totals[1];
        BigDecimal scope3 = totals[2];
        BigDecimal totalEmission = totals[3];

        CarbonReport report = CarbonReport.builder()
                .reportNo(CommonUtils.generateReportId())
                .enterpriseId(enterprise.getId())
                .submitterId(currentUser.getUserId())
                .accountingPeriod(request.getAccountingPeriod())
                .title(CommonUtils.sanitizeHtml(CommonUtils.sanitizeInput(request.getTitle())))
                .reportType(request.getReportType())
                .emissionData(request.getEmissionData())
                .totalEmission(totalEmission)
                .scope1Emission(scope1)
                .scope2Emission(scope2)
                .scope3Emission(scope3)
                .calculationMethod(request.getCalculationMethod())
                .status(ReportStatusEnum.DRAFT.getCode())
                .signatureData(request.getSignatureData())
                .attachments(request.getAttachments())
                .build();

        report = carbonReportRepository.save(report);
        log.info("Carbon report created: {} by user {}", report.getReportNo(), currentUser.getUsername());
        return toResponse(report);
    }

    @Transactional
    public CarbonReportResponse submitReport(JwtUserDetails currentUser, Long reportId) {
        CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> CarbonException.reportNotFound(reportId));

        Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> CarbonException.submitFailed(ENTERPRISE_NOT_FOUND));
        if (!report.getEnterpriseId().equals(enterprise.getId())) {
            throw CarbonException.submitFailed(NO_PERMISSION);
        }

        ReportStatusEnum status = ReportStatusEnum.fromCode(report.getStatus());
        if (!status.isSubmittable()) {
            throw CarbonException.reportAlreadySubmitted(reportId);
        }

        calculateEmissions(report);
        // 可信签名链：提交时用企业私钥对排放数据签名（文档契约"企业确认签名后发审核员"）。
        // 无有效密钥对时跳过签名并放行（引导去个人中心生成），存量流程不受阻。
        signReportIfPossible(report, currentUser.getUserId());
        report.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        report = carbonReportRepository.save(report);

        log.info("Carbon report submitted: {}", report.getReportNo());
        return toResponse(report);
    }

    private void signReportIfPossible(CarbonReport report, Long userId) {
        try {
            com.oaiss.chain.dto.SignatureResult result = digitalSignatureService.signReport(userId, report.getEmissionData());
            report.setSignatureData(result.getSignature());
            log.info("Report {} auto-signed by user {}", report.getReportNo(), userId);
        } catch (Exception e) {
            log.warn("Auto-sign skipped for report {} by user {}: {}", report.getReportNo(), userId, e.getMessage());
        }
    }

    @Transactional
    public CarbonReportResponse reviewReport(JwtUserDetails currentUser, ReviewRequest request) {
        CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(request.getReportId())
                .orElseThrow(() -> CarbonException.reportNotFound(request.getReportId()));

        ReportStatusEnum status = ReportStatusEnum.fromCode(report.getStatus());
        if (!status.isReviewable()) {
            throw CarbonException.submitFailed(REVIEW_STATUS_INVALID);
        }
        if (!isReviewerDecision(request.getReviewResult())) {
            throw CarbonException.submitFailed(REVIEW_DECISION_INVALID);
        }

        // 利益冲突回避原则：审核员不得审核所属企业或本人的报告
        if (currentUser != null && !currentUser.isAdmin()) {
            Enterprise reviewerEnterprise = enterpriseRepository.findByUserId(currentUser.getUserId()).orElse(null);
            if (reviewerEnterprise != null && reviewerEnterprise.getId().equals(report.getEnterpriseId())) {
                throw new com.oaiss.chain.exception.BusinessException(com.oaiss.chain.constant.ErrorCode.PERMISSION_DENIED, "审核回避原则：禁止审核本企业提交的碳报告");
            }
        }

        // 可信签名链：审核时用企业公钥验签，验证数据来源与完整性
        Boolean signatureValid = verifyReportSignature(report);

        // 如果存在签名数据，但验签结果为 false (签名与内容不符，存在篡改伪造风险)，严禁通过审核！
        if (Boolean.FALSE.equals(signatureValid) && Objects.equals(request.getReviewResult(), ReportStatusEnum.APPROVED.getCode())) {
            throw CarbonException.submitFailed("碳报告数字签名校验失败，数据存在篡改或伪造风险，禁止审核通过！");
        }

        report.setReviewerId(currentUser.getUserId());
        report.setReviewComment(CommonUtils.sanitizeHtml(CommonUtils.sanitizeInput(request.getReviewComment())));
        report.setReviewedAt(LocalDateTime.now());

        if (Objects.equals(request.getReviewResult(), ReportStatusEnum.REJECTED.getCode())) {
            report.setStatus(ReportStatusEnum.REJECTED.getCode());
            // 原生需求 03-项目详细方案 D3.2 规定：审核不通过退回，系统自动扣除该企业一定信誉分
            applyRejectedReportSideEffects(report, currentUser.getUserId(), signatureValid);
        } else {
            // 方案 A（依据 docs/raw 原始需求）：「当数据审核员点击提交碳排放报告后，
            // 系统后台自动将该报告进行上链」——审核通过即自动上链，无第二道人工核准。
            report.setStatus(ReportStatusEnum.APPROVED.getCode());
            boolean chained = autoCommitToChain(report, currentUser.getUserId());
            if (chained) {
                report.setStatus(ReportStatusEnum.ON_CHAIN.getCode());
            }
        }

        report = carbonReportRepository.save(report);
        log.info("Carbon report reviewed: {} -> status={}, signatureValid={}, autoChained={}",
                report.getReportNo(), report.getStatus(), signatureValid,
                Objects.equals(report.getStatus(), ReportStatusEnum.ON_CHAIN.getCode()));
        return toResponseWithSignature(report, signatureValid);
    }

    /**
     * 审核通过后的自动上链（方案 A 主路径）。
     * <p>
     * 依据 docs/raw 原始需求：「当数据审核员点击提交碳排放报告后，系统后台自动将该报告进行上链」。
     * <p>
     * 原子性约束：上链属外部系统调用，失败时**不得回滚审核结论**，
     * 否则审核员工作会因链端抖动而丢失。失败时保持 APPROVED，
     * 由 {@code /carbon/certify} 幂等补救入口重试。
     *
     * @return true 表示已成功上链并写入 TxHash
     */
    private boolean autoCommitToChain(CarbonReport report, Long operatorUserId) {
        try {
            applyApprovedReportSideEffects(report, operatorUserId);
            return report.getBlockchainTxHash() != null;
        } catch (Exception e) {
            log.error("Auto on-chain failed for report {}, queued for manual remediation: {}",
                    report.getReportNo(), e.getMessage(), e);
            report.setBlockchainTxHash(null);
            report.setOnChainAt(null);
            return false;
        }
    }

    private void applyRejectedReportSideEffects(CarbonReport report, Long operatorUserId, Boolean signatureValid) {
        Long enterpriseId = report.getEnterpriseId();
        if (enterpriseId == null) {
            return;
        }
        Integer eventType = Boolean.FALSE.equals(signatureValid)
                ? CreditEventTypeEnum.DATA_FALSIFICATION.getCode()
                : CreditEventTypeEnum.MINOR_VIOLATION.getCode();
        // i18n：扣分原因写入数据库时即本地化，避免前端「原因」列直出英文原文
        String description = Boolean.FALSE.equals(signatureValid)
                ? MessageUtils.getMessage("credit.event.report.signature.invalid")
                : MessageUtils.getMessage("credit.event.report.rejected", report.getReportNo());
        try {
            creditScoreService.deductPoints(enterpriseId, eventType, description, operatorUserId, report.getId());
        } catch (Exception e) {
            log.warn("Credit deduction on report rejection failed for enterprise {}: {}", enterpriseId, e.getMessage());
        }
    }

    private Boolean verifyReportSignature(CarbonReport report) {
        if (report.getSignatureData() == null || report.getSignatureData().isBlank()) {
            return null; // 无签名（存量/无钥放行）
        }
        try {
            // submitterId 即签名企业用户；用其最新公钥验签
            return digitalSignatureService.verifySignature(
                    report.getSubmitterId(),
                    report.getEmissionData(),
                    report.getSignatureData());
        } catch (Exception e) {
            log.warn("Signature verification failed for report {}: {}", report.getReportNo(), e.getMessage());
            return Boolean.FALSE;
        }
    }

    private CarbonReportResponse toResponseWithSignature(CarbonReport report, Boolean signatureValid) {
        CarbonReportResponse response = toResponse(report);
        response.setSignatureValid(signatureValid);
        return response;
    }

    @Transactional
    public CarbonReportResponse certifyReport(JwtUserDetails currentUser, ReviewRequest request) {
        CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(request.getReportId())
                .orElseThrow(() -> CarbonException.reportNotFound(request.getReportId()));

        ReportStatusEnum status = ReportStatusEnum.fromCode(report.getStatus());
        // 方案 A 后本端点的定位：审核通过已自动上链，此处仅承担
        // ① 幂等补救（自动上链失败时重试）② 审核后驳回的合规追偿。
        if (status != ReportStatusEnum.APPROVED && status != ReportStatusEnum.ON_CHAIN) {
            throw CarbonException.submitFailed(CERTIFY_STATUS_INVALID);
        }
        if (!isCertificationDecision(request.getReviewResult())) {
            throw CarbonException.submitFailed(CERTIFY_DECISION_INVALID);
        }

        if (Objects.equals(request.getReviewResult(), ReportStatusEnum.ON_CHAIN.getCode())) {
            if (status == ReportStatusEnum.ON_CHAIN) {
                // 幂等：已上链的报告重复调用不应重复加分/重复提交链上交易
                log.info("Report {} already on-chain, idempotent skip", report.getReportNo());
                return toResponse(report);
            }
            applyApprovedReportSideEffects(report, currentUser.getUserId());
            report.setStatus(ReportStatusEnum.ON_CHAIN.getCode());
        } else {
            if (request.getReviewComment() != null && !request.getReviewComment().isBlank()) {
                String sanitizedComment = CommonUtils.sanitizeHtml(CommonUtils.sanitizeInput(request.getReviewComment()));
                report.setReviewComment(mergeCertificationComment(report.getReviewComment(), sanitizedComment));
            }
            report.setStatus(ReportStatusEnum.REJECTED.getCode());
            report.setBlockchainTxHash(null);
            report.setOnChainAt(null);
            // 原生需求 03-项目详细方案 D3.2 规定：认证不通过退回，系统自动扣除该企业一定信誉分
            applyRejectedReportSideEffects(report, currentUser.getUserId(), Boolean.TRUE);
        }

        report = carbonReportRepository.save(report);
        log.info("Carbon report certified: {} -> status={}", report.getReportNo(), report.getStatus());
        return toResponse(report);
    }

    /**
     * 批量认证通过并上链或批量驳回
     */
    @Transactional
    public BatchCertifyResponse batchCertifyReports(JwtUserDetails currentUser, BatchCertifyRequest request) {
        if (request.getReportIds() == null || request.getReportIds().isEmpty()) {
            return BatchCertifyResponse.builder()
                    .totalCount(0)
                    .successCount(0)
                    .failureCount(0)
                    .successReportIds(java.util.Collections.emptyList())
                    .errorMessages(java.util.Collections.emptyList())
                    .build();
        }

        int successCount = 0;
        int failureCount = 0;
        java.util.List<Long> successIds = new java.util.ArrayList<>();
        java.util.List<String> errorMessages = new java.util.ArrayList<>();

        for (Long reportId : request.getReportIds()) {
            try {
                ReviewRequest singleReq = ReviewRequest.builder()
                        .reportId(reportId)
                        .reviewResult(request.getReviewResult())
                        .reviewComment(request.getReviewComment())
                        .build();
                certifyReport(currentUser, singleReq);
                successCount++;
                successIds.add(reportId);
            } catch (Exception e) {
                failureCount++;
                String err = "报告ID " + reportId + " 认证失败: " + e.getMessage();
                log.warn(err, e);
                errorMessages.add(err);
            }
        }

        log.info("Batch certify finished by user {}: total={}, success={}, failed={}",
                currentUser.getUserId(), request.getReportIds().size(), successCount, failureCount);

        return BatchCertifyResponse.builder()
                .totalCount(request.getReportIds().size())
                .successCount(successCount)
                .failureCount(failureCount)
                .successReportIds(successIds)
                .errorMessages(errorMessages)
                .build();
    }

    @Transactional(readOnly = true)
    public CarbonReportResponse getReport(JwtUserDetails currentUser, Long reportId) {
        CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> CarbonException.reportNotFound(reportId));
        assertEnterpriseOwnsReport(currentUser, report, "无权查看其他企业的碳报告");
        // 与接口文档契约一致：详情响应携带验签结果（无签名 → null）
        return toResponseWithSignature(report, verifyReportSignature(report));
    }

    /**
     * 企业用户只能操作本企业报告；核查员/管理员/监管等审阅角色可跨企业访问（业务需要）。
     *
     * <p>此前详情与删除端点缺少归属校验，任何企业都能按 ID 读取甚至删除他人报告（水平越权）。</p>
     */
    private void assertEnterpriseOwnsReport(JwtUserDetails currentUser, CarbonReport report, String denyMessage) {
        List<String> roles = currentUser == null ? null : currentUser.getRoles();
        boolean enterpriseOnly = roles != null
                && roles.contains("ENTERPRISE")
                && !roles.contains("ADMIN");
        if (!enterpriseOnly) {
            return;
        }
        Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> AuthorizationException.permissionDenied("当前用户未关联企业"));
        if (!enterprise.getId().equals(report.getEnterpriseId())) {
            throw AuthorizationException.permissionDenied(denyMessage);
        }
    }

    public Page<CarbonReportResponse> listReports(JwtUserDetails currentUser, Long enterpriseId, Integer status, String keyword, Integer page, Integer size) {
        // 多租户隔离防护：全平台报告列表接口仅供管理端、审核员与监管机构访问，企业用户不可越权检索全网报告
        if (currentUser != null && currentUser.isEnterprise() && !currentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限查询所有报告");
        }

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CarbonReport> reports = carbonReportRepository.search(enterpriseId, status, keyword, pageable);
        return reports.map(this::toResponse);
    }

    public Page<CarbonReportResponse> listReports(Long enterpriseId, Integer status, String keyword, Integer page, Integer size) {
        return listReports(null, enterpriseId, status, keyword, page, size);
    }

    public Page<CarbonReportResponse> listMyReports(
            JwtUserDetails currentUser,
            Integer status,
            String title,
            String accountingPeriod,
            Integer page,
            Integer size) {
        Enterprise enterprise = enterpriseRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> CarbonException.submitFailed(ENTERPRISE_NOT_FOUND));

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedTitle = normalizeFilter(title);
        String normalizedAccountingPeriod = normalizeFilter(accountingPeriod);
        Page<CarbonReport> reports = carbonReportRepository.searchMyReports(
                enterprise.getId(),
                status,
                normalizedTitle,
                normalizedAccountingPeriod,
                pageable);
        return reports.map(this::toResponse);
    }

    @Transactional
    public void deleteReport(JwtUserDetails currentUser, Long reportId) {
        CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> CarbonException.reportNotFound(reportId));

        assertEnterpriseOwnsReport(currentUser, report, "无权删除其他企业的碳报告");

        if (ReportStatusEnum.fromCode(report.getStatus()) != ReportStatusEnum.DRAFT) {
            throw CarbonException.reportAlreadySubmitted(reportId);
        }

        report.setDeleted(true);
        carbonReportRepository.save(report);
        log.info("Carbon report deleted: {}", report.getReportNo());
    }

    /**
     * 修改报告（D13）：被驳回或草稿状态的报告可由所属企业编辑后重新提交。
     *
     * <p>历史缺陷：后端 `isSubmittable()` 允许 REJECTED 重提，但没有任何编辑端点，
     * 界面也没有入口——被驳回的报告在客户端是死胡同（库内曾有 10 份卡住）。
     * 用户唯一出路是删除重建，附件、编号、审计链全部丢失。</p>
     *
     * <p>安全边界：仅 ENTERPRISE 本人、仅 DRAFT/REJECTED、必须归属校验。
     * 修改后重算四个结果列，签名会因内容变化而失效——提交时会重新签名。</p>
     */
    @Transactional
    public CarbonReportResponse updateReport(JwtUserDetails currentUser, Long reportId,
                                             CarbonReportRequest request) {
        CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> CarbonException.reportNotFound(reportId));

        assertEnterpriseOwnsReport(currentUser, report, "无权修改其他企业的碳报告");

        ReportStatusEnum status = ReportStatusEnum.fromCode(report.getStatus());
        if (!status.isEditable()) {
            throw CarbonException.reportAlreadySubmitted(reportId);
        }

        report.setTitle(CommonUtils.sanitizeHtml(CommonUtils.sanitizeInput(request.getTitle())));
        report.setAccountingPeriod(request.getAccountingPeriod());
        report.setReportType(request.getReportType());
        report.setEmissionData(request.getEmissionData());
        report.setCalculationMethod(request.getCalculationMethod());
        if (request.getAttachments() != null) {
            report.setAttachments(request.getAttachments());
        }

        // 内容变了，四列必须重算；签名缓存也一并清空，提交时重新签。
        calculateEmissions(report);
        report.setSignatureData(null);

        report = carbonReportRepository.save(report);
        log.info("Carbon report updated: {} by user {}", report.getReportNo(), currentUser.getUsername());
        return toResponse(report);
    }

    /** BigDecimal 转字符串，null 保持 null（链码侧会再转空串）。 */
    private static String stringify(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private void calculateEmissions(CarbonReport report) {
        BigDecimal[] totals = parseEmissionTotals(report.getEmissionData());
        report.setScope1Emission(totals[0]);
        report.setScope2Emission(totals[1]);
        report.setScope3Emission(totals[2]);
        report.setTotalEmission(totals[3]);
    }

    /**
     * 审核通过后的副作用：上链 → 加信用分 → 评级。
     *
     * <p><b>顺序至关重要（D17）</b>：历史实现先加分与评级、最后才上链。
     * 上链抛异常时分数已经发出，而 {@code /carbon/certify} 补救成功后会再执行一遍，
     * 导致同一份报告被加两次分（+10 而非 +5）。</p>
     *
     * <p>现在的约束是：<b>只有真正上链成功（txHash 非空）才发放奖励</b>。
     * 未存证的报告不该获得信用分与评级——这是「数据受限」与「可信存证」的底线。</p>
     *
     * @throws RuntimeException 上链失败时抛出，调用方据此保持 APPROVED 状态等待补救
     */
    private void applyApprovedReportSideEffects(CarbonReport report, Long operatorUserId) {
        Long enterpriseId = report.getEnterpriseId();

        // 第一步：先上链。失败则直接抛出，后续奖励一概不发。
        // D15：存证**完整快照**（企业归属 / 周期 / 总量 / 三范围 / 签名 / 原文明细）。
        // v1 只传 emissionData 原文且链码 EnterpriseID 从未赋值，
        // 链上无法回答「这份报告属于哪家企业」——存证不能独立验证主体。
        CarbonReportSnapshot snapshot = CarbonReportSnapshot.builder()
                .reportId(report.getId())
                .enterpriseId(report.getEnterpriseId())
                .reportNo(report.getReportNo())
                .accountingPeriod(report.getAccountingPeriod())
                .totalEmission(stringify(report.getTotalEmission()))
                .scope1Emission(stringify(report.getScope1Emission()))
                .scope2Emission(stringify(report.getScope2Emission()))
                .scope3Emission(stringify(report.getScope3Emission()))
                .signature(report.getSignatureData())
                .data(report.getEmissionData())
                .build();
        String txHash = blockchainService.commitReportSnapshot(snapshot);
        if (txHash == null || txHash.isBlank()) {
            throw new IllegalStateException("上链未返回有效交易哈希，报告 " + report.getReportNo() + " 暂不发放奖励");
        }
        report.setBlockchainTxHash(txHash);
        report.setOnChainAt(LocalDateTime.now());

        // 第二步：上链确认后才加信用分
        creditScoreService.addBonusPoints(
                enterpriseId,
                5,
                MessageUtils.getMessage("credit.event.report.certified"),
                operatorUserId);

        // 第三步：评级
        String ratingYear = report.getAccountingPeriod();
        if (ratingYear == null || ratingYear.isEmpty()) {
            ratingYear = String.valueOf(LocalDateTime.now().getYear());
        } else if (ratingYear.length() > 4) {
            ratingYear = ratingYear.substring(0, 4);
        }
        // D10：按**年度汇总**重算，而不是用「这一份报告的排放量」覆盖写。
        // 历史实现在同年多份报告时只有最后一份生效，「年度评级」名不副实。
        emissionRatingService.recalculateYearlyRating(enterpriseId, ratingYear, operatorUserId);
    }

    private boolean isReviewerDecision(Integer decision) {
        return decision != null
                && (decision.equals(ReportStatusEnum.APPROVED.getCode())
                || decision.equals(ReportStatusEnum.REJECTED.getCode()));
    }

    private boolean isCertificationDecision(Integer decision) {
        return decision != null
                && (decision.equals(ReportStatusEnum.ON_CHAIN.getCode())
                || decision.equals(ReportStatusEnum.REJECTED.getCode()));
    }

    private String mergeCertificationComment(String existingComment, String certificationComment) {
        if (existingComment == null || existingComment.isBlank()) {
            return CERTIFICATION_COMMENT_PREFIX + certificationComment;
        }
        return existingComment + System.lineSeparator() + CERTIFICATION_COMMENT_PREFIX + certificationComment;
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal[] parseEmissionTotals(String emissionData) {
        return EmissionDataValidator.validateAndParse(emissionData, objectMapper);
    }

    private CarbonReportResponse toResponse(CarbonReport report) {
        String enterpriseName = null;
        String reviewerName = null;

        if (report.getEnterpriseId() != null) {
            enterpriseName = enterpriseRepository.findById(report.getEnterpriseId())
                    .map(Enterprise::getEnterpriseName)
                    .orElse(null);
        }
        if (report.getReviewerId() != null) {
            reviewerName = userRepository.findById(report.getReviewerId())
                    .map(User::getRealName)
                    .orElse(null);
        }

        String statusText = null;
        if (report.getStatus() != null) {
            statusText = ReportStatusEnum.fromCode(report.getStatus()).getDescription();
        }

        return CarbonReportResponse.builder()
                .id(report.getId())
                .reportNo(report.getReportNo())
                .enterpriseId(report.getEnterpriseId())
                .enterpriseName(enterpriseName)
                .accountingPeriod(report.getAccountingPeriod())
                .title(report.getTitle())
                .reportType(report.getReportType())
                .emissionData(report.getEmissionData())
                .totalEmission(report.getTotalEmission())
                .scope1Emission(report.getScope1Emission())
                .scope2Emission(report.getScope2Emission())
                .scope3Emission(report.getScope3Emission())
                .calculationMethod(report.getCalculationMethod())
                .status(report.getStatus())
                .statusText(statusText)
                .reviewerId(report.getReviewerId())
                .reviewerName(reviewerName)
                .reviewComment(report.getReviewComment())
                .reviewedAt(report.getReviewedAt())
                .signatureData(report.getSignatureData())
                .blockchainTxHash(report.getBlockchainTxHash())
                .onChainAt(report.getOnChainAt())
                .attachments(report.getAttachments())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
