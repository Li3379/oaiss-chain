package com.oaiss.chain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.CarbonNeutralProjectRequest;
import com.oaiss.chain.dto.CarbonNeutralProjectResponse;
import com.oaiss.chain.dto.ProjectVerificationRequest;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.CarbonNeutralProject;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Reviewer;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonNeutralProjectRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.ReviewerRepository;
import com.oaiss.chain.repository.UserRepository;
import com.oaiss.chain.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 碳中和项目服务
 * 管理碳减排、碳汇、CCUS、可再生能源等项目
 * <p>
 * 项目生命周期：
 * 1. 创建申请 → 2. 审核通过 → 3. 项目实施 → 4. 减排核证 → 5. 碳信用签发 → 6. 持续监测
 * <p>
 * 项目类型：
 * - 1: 碳汇项目（林业、草原、海洋等）
 * - 2: CCUS项目（碳捕集、利用与封存）
 * - 3: 可再生能源项目（光伏、风电、水电等）
 * - 4: 节能改造项目
 * - 5: 其他减排项目
 * <p>
 * 设计文档要求（doc01/doc03）：
 * - 碳中和项目管理
 * - 支持碳汇、CCUS、可再生能源等类型项目
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonNeutralProjectService {

    private final CarbonNeutralProjectRepository projectRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final ReviewerRepository reviewerRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    /** 碳信用抵扣缺口时要写报告的 shortfall_offset，故需报告仓库 */
    private final com.oaiss.chain.repository.CarbonReportRepository carbonReportRepository;

    // ==================== 项目状态常量 ====================
    public static final int STATUS_DRAFT = 0;          // 筹备/草稿
    public static final int STATUS_PENDING = 1;        // 待审核
    public static final int STATUS_APPROVED = 2;       // 审核通过
    public static final int STATUS_IMPLEMENTING = 3;   // 实施中
    public static final int STATUS_COMPLETED = 4;      // 已完成
    public static final int STATUS_TERMINATED = 5;     // 已终止
    public static final int STATUS_REJECTED = 6;       // 审核拒绝

    // ==================== 认证状态常量 ====================
    public static final int CERT_STATUS_NONE = 0;      // 未认证
    public static final int CERT_STATUS_PENDING = 1;   // 认证中
    public static final int CERT_STATUS_CERTIFIED = 2; // 已认证
    public static final int CERT_STATUS_FAILED = 3;    // 认证失败

    // ==================== 核证状态常量 ====================
    public static final int VERIFY_STATUS_NONE = 0;    // 未核证
    public static final int VERIFY_STATUS_PENDING = 1; // 核证中
    public static final int VERIFY_STATUS_VERIFIED = 2;// 已核证
    public static final int VERIFY_STATUS_FAILED = 3;  // 核证失败

    /**
     * 创建项目（草稿）
     */
    @Transactional
    public CarbonNeutralProjectResponse createProject(JwtUserDetails currentUser,
                                                       CarbonNeutralProjectRequest request) {
        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(currentUser.getUserId())
                .orElseThrow(() -> new BusinessException(3001, "未找到关联企业信息"));

        CarbonNeutralProject project = CarbonNeutralProject.builder()
                .projectNo(generateProjectNo())
                .projectName(request.getProjectName())
                .projectType(request.getProjectType())
                .ownerId(enterprise.getId())
                .description(request.getDescription())
                .location(request.getLocation())
                .expectedReduction(request.getExpectedReduction())
                .investmentAmount(request.getInvestmentAmount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .methodology(request.getMethodology())
                .accountingPeriod(request.getAccountingPeriod())
                .applicationData(request.getApplicationData())
                .attachments(request.getAttachments())
                .status(STATUS_DRAFT)
                .certStatus(CERT_STATUS_NONE)
                .verificationStatus(VERIFY_STATUS_NONE)
                .issuedCredits(BigDecimal.ZERO)
                .usedCredits(BigDecimal.ZERO)
                .build();

        project = projectRepository.save(project);
        log.info("Carbon neutral project created: {} by user {}", project.getProjectNo(), currentUser.getUsername());

        return toResponse(project);
    }

    /**
     * 更新项目信息
     */
    @Transactional
    public CarbonNeutralProjectResponse updateProject(JwtUserDetails currentUser, Long projectId,
                                                       CarbonNeutralProjectRequest request) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        validateOwner(currentUser, project);

        // 只有草稿和被拒绝的项目可以修改
        if (project.getStatus() != STATUS_DRAFT && project.getStatus() != STATUS_REJECTED) {
            throw new BusinessException(3003, "当前项目状态不允许修改");
        }

        project.setProjectName(request.getProjectName());
        project.setDescription(request.getDescription());
        project.setLocation(request.getLocation());
        project.setExpectedReduction(request.getExpectedReduction());
        project.setInvestmentAmount(request.getInvestmentAmount());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        project.setMethodology(request.getMethodology());
        project.setAccountingPeriod(request.getAccountingPeriod());
        project.setApplicationData(request.getApplicationData());
        project.setAttachments(request.getAttachments());

        project = projectRepository.save(project);
        log.info("Project updated: {}", project.getProjectNo());

        return toResponse(project);
    }

    /**
     * 提交审核
     */
    @Transactional
    public CarbonNeutralProjectResponse submitForReview(JwtUserDetails currentUser, Long projectId) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        validateOwner(currentUser, project);

        if (project.getStatus() != STATUS_DRAFT && project.getStatus() != STATUS_REJECTED) {
            throw new BusinessException(3003, "当前项目状态不允许提交审核");
        }

        validateProjectForSubmission(project);

        project.setStatus(STATUS_PENDING);
        project = projectRepository.save(project);

        log.info("Project submitted for review: {}", project.getProjectNo());
        return toResponse(project);
    }

    /**
     * 审核项目（管理员）
     */
    @Transactional
    public CarbonNeutralProjectResponse reviewProject(JwtUserDetails reviewer, Long projectId,
                                                       boolean approved, String comment) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        if (project.getStatus() != STATUS_PENDING) {
            throw new BusinessException(3003, "项目不在待审核状态");
        }

        project.setReviewerId(reviewer.getUserId());
        project.setReviewComment(comment);
        project.setReviewedAt(LocalDateTime.now());

        if (approved) {
            project.setStatus(STATUS_APPROVED);
            log.info("Project approved: {} by {}", project.getProjectNo(), reviewer.getUsername());
        } else {
            project.setStatus(STATUS_REJECTED);
            log.info("Project rejected: {} by {}", project.getProjectNo(), reviewer.getUsername());
        }

        project = projectRepository.save(project);
        return toResponse(project);
    }

    /**
     * 启动项目实施
     */
    @Transactional
    public CarbonNeutralProjectResponse startImplementation(JwtUserDetails currentUser, Long projectId) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        validateOwner(currentUser, project);

        if (project.getStatus() != STATUS_APPROVED) {
            throw new BusinessException(3003, "只有审核通过的项目可以启动实施");
        }

        project.setStatus(STATUS_IMPLEMENTING);
        project = projectRepository.save(project);

        log.info("Project implementation started: {}", project.getProjectNo());
        return toResponse(project);
    }

    /**
     * 提交核证申请
     */
    @Transactional
    public CarbonNeutralProjectResponse submitForVerification(JwtUserDetails currentUser,
                                                                Long projectId, Long verifierId) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        validateOwner(currentUser, project);

        if (project.getStatus() != STATUS_IMPLEMENTING && project.getStatus() != STATUS_COMPLETED) {
            throw new BusinessException(3003, "项目不在可核证状态");
        }

        project.setVerifierId(resolveVerifierUserId(verifierId));
        project.setVerificationStatus(VERIFY_STATUS_PENDING);
        project = projectRepository.save(project);

        log.info("Project submitted for verification: {}, verifier={}", project.getProjectNo(), verifierId);
        return toResponse(project);
    }

    /**
     * 核证项目（第三方核证机构）
     */
    @Transactional
    public CarbonNeutralProjectResponse verifyProject(JwtUserDetails verifier,
                                                       ProjectVerificationRequest request) {
        CarbonNeutralProject project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        if (project.getVerificationStatus() != VERIFY_STATUS_PENDING) {
            throw new BusinessException(3003, "项目不在待核证状态");
        }

        project.setActualReduction(request.getVerifiedReduction());
        project.setVerificationReport(request.getVerificationReport());
        project.setMonitoringData(request.getMonitoringData());
        project.setLastMonitoringDate(LocalDate.now());
        project.setVerificationStatus(VERIFY_STATUS_VERIFIED);

        // 自动签发碳信用（核证减排量的100%）
        BigDecimal creditsToIssue = request.getVerifiedReduction();
        project.setIssuedCredits(project.getIssuedCredits().add(creditsToIssue));

        if (project.getStatus() == STATUS_IMPLEMENTING) {
            project.setStatus(STATUS_COMPLETED);
        }

        project = projectRepository.save(project);

        log.info("Project verified: {}, verifiedReduction={}, issuedCredits={}",
                project.getProjectNo(), request.getVerifiedReduction(), creditsToIssue);

        return toResponse(project);
    }

    /**
     * 消耗碳信用
     */
    @Transactional
    public CarbonNeutralProjectResponse useCredits(JwtUserDetails currentUser, Long projectId, BigDecimal amount) {
        return useCredits(currentUser, projectId, amount, null);
    }

    /**
     * 消耗碳信用，可选择抵扣某份报告的超排缺口。
     *
     * <p><b>为什么需要 relatedReportId</b>：碳信用原来只改项目自己的
     * {@code usedCredits}，不关联任何业务对象——于是「用碳信用抵消了排放」
     * 这句话在数据上无从验证：不知道抵消的是哪份报告的哪个缺口、抵消了多少。
     *
     * <p>接上之后，抵扣会写入报告的 {@code shortfallOffset}，
     * 「这份报告还差多少吨」因此可以算出来：
     * <pre>
     * 剩余缺口 = scope1Emission − quotaAllocatedAmount − shortfallOffset
     * </pre>
     *
     * <p>抵扣上限是当前缺口：超过缺口会让「已抵消」大于「该抵消」，
     * 多出的部分凭空消失，账面上看不出异常。
     *
     * @param relatedReportId 要抵扣缺口的报告；null 表示仅消耗信用、不关联报告
     */
    @Transactional
    public CarbonNeutralProjectResponse useCredits(JwtUserDetails currentUser, Long projectId,
                                                   BigDecimal amount, Long relatedReportId) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        validateOwner(currentUser, project);

        BigDecimal available = project.getIssuedCredits().subtract(project.getUsedCredits());
        if (available.compareTo(amount) < 0) {
            throw new BusinessException(3004, "可用碳信用不足");
        }

        // 关联报告时校验抵扣上限：不允许抵扣超过该报告的实际缺口
        if (relatedReportId != null) {
            CarbonReport report = carbonReportRepository.findByIdAndDeletedFalse(relatedReportId)
                    .orElseThrow(() -> new BusinessException(3002, "碳报告不存在: " + relatedReportId));
            BigDecimal gap = currentShortfall(report);
            if (gap.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(3005, "该报告没有超排缺口，无需抵扣");
            }
            if (amount.compareTo(gap) > 0) {
                throw new BusinessException(3005,
                        String.format("抵扣量超过缺口：当前缺口 %s 吨，本次抵扣 %s 吨",
                                gap.toPlainString(), amount.toPlainString()));
            }
            report.setShortfallOffset(nz(report.getShortfallOffset()).add(amount));
            carbonReportRepository.save(report);
            log.info("Shortfall offset: report={}, amount={}, remaining={}",
                    relatedReportId, amount.toPlainString(),
                    currentShortfall(report).toPlainString());
        }

        project.setUsedCredits(project.getUsedCredits().add(amount));
        project = projectRepository.save(project);

        log.info("Project credits used: {} -> {}", project.getProjectNo(), amount);
        return toResponse(project);
    }

    /**
     * 更新监测数据
     */
    @Transactional
    public CarbonNeutralProjectResponse updateMonitoring(JwtUserDetails currentUser,
                                                          Long projectId, String monitoringData) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        validateOwner(currentUser, project);

        project.setMonitoringData(monitoringData);
        project.setLastMonitoringDate(LocalDate.now());
        project = projectRepository.save(project);

        log.info("Project monitoring updated: {}", project.getProjectNo());
        return toResponse(project);
    }

    /**
     * 申请认证
     */
    @Transactional
    public CarbonNeutralProjectResponse applyForCertification(JwtUserDetails currentUser,
                                                                Long projectId, String certOrg) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        validateOwner(currentUser, project);

        if (project.getVerificationStatus() != VERIFY_STATUS_VERIFIED) {
            throw new BusinessException(3003, "项目需先完成核证");
        }

        project.setCertOrg(certOrg);
        project.setCertStatus(CERT_STATUS_PENDING);
        project = projectRepository.save(project);

        log.info("Project applied for certification: {} -> {}", project.getProjectNo(), certOrg);
        return toResponse(project);
    }

    /**
     * 完成认证。
     *
     * <p>证书编号由服务端生成（D-05/D-06），客户端不提供输入面；
     * 唯一索引冲突说明并发请求算出了同一序号，转 3007 让调用方重试。
     *
     * @param projectId  项目ID
     * @param operatorId 操作人用户ID（认证动作的 REVIEWER/ADMIN）
     */
    @Transactional
    public CarbonNeutralProjectResponse completeCertification(Long projectId, Long operatorId) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        if (project.getCertStatus() != CERT_STATUS_PENDING) {
            throw new BusinessException(3003, "项目不在待认证状态");
        }

        String certNo = project.getCertNo();
        if (certNo == null || certNo.isBlank()) {
            certNo = generateCertNo();
        }

        project.setCertStatus(CERT_STATUS_CERTIFIED);
        project.setCertNo(certNo);
        project.setCertDate(LocalDate.now());
        try {
            project = projectRepository.save(project);
        } catch (DataIntegrityViolationException e) {
            // uk_cnp_cert_no 兜底：并发请求算出了同一序号
            throw new BusinessException(3007, "证书编号生成冲突，请重试");
        }

        log.info("Project certified: {} -> {}", project.getProjectNo(), certNo);
        return toResponse(project);
    }

    /**
     * 生成证书编号 {@code CNE-{四位年份}-{六位序号}}，同一年的序号连续递增。
     */
    private String generateCertNo() {
        String prefix = "CNE-" + LocalDate.now().getYear() + "-";
        int next = 1;

        Optional<CarbonNeutralProject> latest =
                projectRepository.findTopByCertNoStartingWithAndDeletedFalseOrderByCertNoDesc(prefix);
        if (latest.isPresent()) {
            String latestCertNo = latest.get().getCertNo();
            try {
                next = Integer.parseInt(latestCertNo.substring(prefix.length())) + 1;
            } catch (RuntimeException e) {
                // 历史数据格式非预期：不阻塞发证，从头计数并留痕
                log.warn("Unexpected cert no format, restarting sequence at 1: {}", latestCertNo);
                next = 1;
            }
        }

        return String.format("%s%06d", prefix, next);
    }

    /**
     * 终止项目
     */
    @Transactional
    public CarbonNeutralProjectResponse terminateProject(JwtUserDetails currentUser, Long projectId,
                                                          String reason) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));

        validateOwner(currentUser, project);

        if (project.getStatus() != STATUS_IMPLEMENTING && project.getStatus() != STATUS_APPROVED) {
            throw new BusinessException(3003, "只有实施中或审核通过的项目可以终止");
        }

        project.setStatus(STATUS_TERMINATED);
        project.setReviewComment("项目终止: " + reason);
        project = projectRepository.save(project);

        log.info("Project terminated: {} - {}", project.getProjectNo(), reason);
        return toResponse(project);
    }

    /**
     * 获取项目详情
     */
    public CarbonNeutralProjectResponse getProject(Long projectId) {
        CarbonNeutralProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(3002, "项目不存在"));
        return toResponse(project);
    }

    /**
     * 搜索项目
     */
    public Page<CarbonNeutralProjectResponse> searchProjects(Integer projectType, Integer status,
                                                              String keyword, Integer page, Integer size) {
        String escapedKeyword = keyword;
        if (keyword != null) {
            escapedKeyword = keyword.replace("%", "\\%").replace("_", "\\_");
        }
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CarbonNeutralProject> projects = projectRepository.search(projectType, status, escapedKeyword, pageable);
        return toResponsePage(projects);
    }

    /**
     * 获取企业项目列表
     */
    public Page<CarbonNeutralProjectResponse> getMyProjects(JwtUserDetails currentUser,
                                                             Integer status, Integer page, Integer size) {
        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(currentUser.getUserId())
                .orElseThrow(() -> new BusinessException(3001, "未找到关联企业信息"));

        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CarbonNeutralProject> projects;
        if (status != null) {
            projects = projectRepository.findByOwnerIdAndStatusAndDeletedFalse(
                    enterprise.getId(), status, pageable);
        } else {
            projects = projectRepository.findByOwnerIdAndDeletedFalse(enterprise.getId(), pageable);
        }
        return toResponsePage(projects);
    }

    /**
     * 获取待核证项目列表（第三方核证机构）
     */
    public Page<CarbonNeutralProjectResponse> getPendingVerificationProjects(Long verifierId,
                                                                              Integer page, Integer size) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Set<Long> verifierLookupIds = buildVerifierLookupIds(verifierId);
        // 工单池 = 指派给我的待核证工单 + 尚未指派但已进入可核证阶段的公共工单。
        // 仅按 verifierId 过滤会让未指派工单对所有审核员不可见（历史页面恒空白的根因）。
        Page<CarbonNeutralProject> projects = projectRepository
                .findVerificationWorkPool(
                        verifierLookupIds,
                        List.of(STATUS_IMPLEMENTING, STATUS_COMPLETED),
                        VERIFY_STATUS_PENDING,
                        pageable);
        return toResponsePage(projects);
    }

    /**
     * 管理员视角的核证工单池：不受「指派给我」限制，覆盖全部待核证工单。
     * <p>
     * {@code /pending-verification} 同时允许 REVIEWER 与 ADMIN 访问，
     * 若 ADMIN 复用个人工单池查询，会因 userId 不在 verifier_id 集合内而恒为空——
     * 掩盖了「全局还有多少待核证工单」这一监督职责。
     */
    public Page<CarbonNeutralProjectResponse> getAllPendingVerificationProjects(Integer page, Integer size) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CarbonNeutralProject> projects = projectRepository
                .findByVerificationStatusAndDeletedFalse(VERIFY_STATUS_PENDING, pageable);
        return toResponsePage(projects);
    }

    // ==================== 私有方法 ====================

    private void validateOwner(JwtUserDetails currentUser, CarbonNeutralProject project) {
        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(currentUser.getUserId())
                .orElseThrow(() -> new BusinessException(3001, "未找到关联企业信息"));
        if (!project.getOwnerId().equals(enterprise.getId())) {
            throw new BusinessException(3005, "无权操作此项目");
        }
    }

    private void validateProjectForSubmission(CarbonNeutralProject project) {
        if (project.getProjectName() == null || project.getProjectName().isBlank()) {
            throw new BusinessException(3006, "项目名称不能为空");
        }
        if (project.getProjectType() == null) {
            throw new BusinessException(3006, "项目类型不能为空");
        }
        if (project.getExpectedReduction() == null || project.getExpectedReduction().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(3006, "预计减排量必须大于0");
        }
    }

    private String generateProjectNo() {
        return "CNP" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private CarbonNeutralProjectResponse toResponse(CarbonNeutralProject p) {
        return toResponse(p, buildDisplayContext(List.of(p)));
    }

    private Page<CarbonNeutralProjectResponse> toResponsePage(Page<CarbonNeutralProject> projects) {
        ProjectDisplayContext context = buildDisplayContext(projects.getContent());
        return projects.map(project -> toResponse(project, context));
    }

    /**
     * Batch-load the display names needed by project list/detail responses so list endpoints
     * do not issue per-row lookups for enterprises, reviewers, and verifiers.
     */
    private ProjectDisplayContext buildDisplayContext(Collection<CarbonNeutralProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return new ProjectDisplayContext(Map.of(), Map.of(), Set.of(), Map.of());
        }

        Set<Long> ownerIds = new LinkedHashSet<>();
        Set<Long> reviewerIds = new LinkedHashSet<>();
        Set<Long> verifierIds = new LinkedHashSet<>();

        for (CarbonNeutralProject project : projects) {
            if (project.getOwnerId() != null) {
                ownerIds.add(project.getOwnerId());
            }
            if (project.getReviewerId() != null) {
                reviewerIds.add(project.getReviewerId());
            }
            if (project.getVerifierId() != null) {
                verifierIds.add(project.getVerifierId());
            }
        }

        Map<Long, String> ownerNamesById = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (Enterprise enterprise : enterpriseRepository.findAllById(ownerIds)) {
                if (enterprise.getEnterpriseName() != null) {
                    ownerNamesById.put(enterprise.getId(), enterprise.getEnterpriseName());
                }
            }
        }

        Map<Long, Long> reviewerUserIdsByReviewerId = new HashMap<>();
        if (!verifierIds.isEmpty()) {
            for (Reviewer reviewer : reviewerRepository.findAllById(verifierIds)) {
                reviewerUserIdsByReviewerId.put(reviewer.getId(), reviewer.getUserId());
            }
        }

        Set<Long> reviewerUserIds = new LinkedHashSet<>();
        if (!verifierIds.isEmpty()) {
            for (Reviewer reviewer : reviewerRepository.findByUserIdInAndDeletedFalse(verifierIds)) {
                reviewerUserIds.add(reviewer.getUserId());
            }
        }

        Set<Long> userIds = new LinkedHashSet<>(reviewerIds);
        userIds.addAll(verifierIds);
        userIds.addAll(reviewerUserIds);
        userIds.addAll(reviewerUserIdsByReviewerId.values());

        Map<Long, String> userRealNamesById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllById(userIds)) {
                userRealNamesById.put(user.getId(), user.getRealName());
            }
        }

        return new ProjectDisplayContext(ownerNamesById, userRealNamesById, reviewerUserIds, reviewerUserIdsByReviewerId);
    }

    private CarbonNeutralProjectResponse toResponse(CarbonNeutralProject p, ProjectDisplayContext context) {
        String ownerName = context.ownerNamesById().getOrDefault(p.getOwnerId(), "未知企业");
        String reviewerName = p.getReviewerId() != null ? context.userRealNamesById().get(p.getReviewerId()) : null;
        String verifierName = resolveVerifierName(p.getVerifierId(), context);

        BigDecimal availableCredits = p.getIssuedCredits().subtract(
                p.getUsedCredits() != null ? p.getUsedCredits() : BigDecimal.ZERO);

        return CarbonNeutralProjectResponse.builder()
                .id(p.getId())
                .projectNo(p.getProjectNo())
                .projectName(p.getProjectName())
                .projectType(p.getProjectType())
                .projectTypeName(getProjectTypeName(p.getProjectType()))
                .ownerId(p.getOwnerId())
                .ownerName(ownerName)
                .description(p.getDescription())
                .location(p.getLocation())
                .expectedReduction(p.getExpectedReduction())
                .actualReduction(p.getActualReduction())
                .investmentAmount(p.getInvestmentAmount())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .status(p.getStatus())
                .statusText(getStatusText(p.getStatus()))
                .certStatus(p.getCertStatus())
                .certStatusText(getCertStatusText(p.getCertStatus()))
                .certOrg(p.getCertOrg())
                .certDate(p.getCertDate())
                .certNo(p.getCertNo())
                .methodology(p.getMethodology())
                .accountingPeriod(p.getAccountingPeriod())
                .issuedCredits(p.getIssuedCredits())
                .usedCredits(p.getUsedCredits())
                .availableCredits(availableCredits)
                .applicationData(p.getApplicationData())
                .verificationReport(p.getVerificationReport())
                .attachments(p.getAttachments())
                .reviewComment(p.getReviewComment())
                .reviewerId(p.getReviewerId())
                .reviewerName(reviewerName)
                .reviewedAt(p.getReviewedAt())
                .monitoringData(p.getMonitoringData())
                .lastMonitoringDate(p.getLastMonitoringDate())
                .verifierId(p.getVerifierId())
                .verifierName(verifierName)
                .verificationStatus(p.getVerificationStatus())
                .verificationStatusText(getVerificationStatusText(p.getVerificationStatus()))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private record ProjectDisplayContext(
            Map<Long, String> ownerNamesById,
            Map<Long, String> userRealNamesById,
            Set<Long> reviewerUserIds,
            Map<Long, Long> reviewerUserIdsByReviewerId
    ) {
    }

    private String getProjectTypeName(Integer type) {
        if (type == null) return "";
        return switch (type) {
            case 1 -> "碳汇项目";
            case 2 -> "CCUS项目";
            case 3 -> "可再生能源项目";
            case 4 -> "节能改造项目";
            case 5 -> "其他减排项目";
            default -> "未知类型";
        };
    }

    private String getStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case STATUS_DRAFT -> "筹备中";
            case STATUS_PENDING -> "待审核";
            case STATUS_APPROVED -> "审核通过";
            case STATUS_IMPLEMENTING -> "实施中";
            case STATUS_COMPLETED -> "已完成";
            case STATUS_TERMINATED -> "已终止";
            case STATUS_REJECTED -> "审核拒绝";
            default -> "未知状态";
        };
    }

    private String getCertStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case CERT_STATUS_NONE -> "未认证";
            case CERT_STATUS_PENDING -> "认证中";
            case CERT_STATUS_CERTIFIED -> "已认证";
            case CERT_STATUS_FAILED -> "认证失败";
            default -> "未知状态";
        };
    }

    private String getVerificationStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case VERIFY_STATUS_NONE -> "未核证";
            case VERIFY_STATUS_PENDING -> "核证中";
            case VERIFY_STATUS_VERIFIED -> "已核证";
            case VERIFY_STATUS_FAILED -> "核证失败";
            default -> "未知状态";
        };
    }
    private Long resolveVerifierUserId(Long verifierId) {
        if (verifierId == null) {
            throw new BusinessException(3006, "Verifier ID is required");
        }

        return reviewerRepository.findByUserIdAndDeletedFalse(verifierId)
                .map(Reviewer::getUserId)
                .or(() -> reviewerRepository.findById(verifierId).map(Reviewer::getUserId))
                .or(() -> userRepository.findById(verifierId).map(User::getId))
                .orElseThrow(() -> new BusinessException(3002, "Verifier not found"));
    }

    private Set<Long> buildVerifierLookupIds(Long verifierId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (verifierId != null) {
            ids.add(verifierId);
            reviewerRepository.findByUserIdAndDeletedFalse(verifierId)
                    .map(Reviewer::getId)
                    .ifPresent(ids::add);
        }
        return ids;
    }

    private String resolveVerifierName(Long verifierId, ProjectDisplayContext context) {
        if (verifierId == null) {
            return null;
        }

        Long verifierUserId;
        if (context.reviewerUserIds().contains(verifierId)) {
            verifierUserId = verifierId;
        } else {
            verifierUserId = context.reviewerUserIdsByReviewerId().getOrDefault(verifierId, verifierId);
        }

        return context.userRealNamesById().get(verifierUserId);
    }

    /**
     * 某报告当前剩余的超排缺口。
     *
     * <pre>
     * 缺口 = scope1Emission − quotaAllocatedAmount − shortfallOffset
     * </pre>
     * 未结算（配额为空）或已抵扣完时返回 0。
     */
    public BigDecimal currentShortfall(CarbonReport report) {
        BigDecimal allocated = report.getQuotaAllocatedAmount();
        if (allocated == null) {
            return BigDecimal.ZERO; // 未结算的报告判不了缺口
        }
        BigDecimal gap = nz(report.getScope1Emission())
                .subtract(allocated)
                .subtract(nz(report.getShortfallOffset()));
        return gap.compareTo(BigDecimal.ZERO) > 0 ? gap : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
