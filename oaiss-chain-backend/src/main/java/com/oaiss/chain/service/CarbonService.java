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
import com.oaiss.chain.enums.AllocationStateEnum;
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
import java.util.ArrayList;
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

    /** 从核算周期里提取四位年份；范围限定 2000–2100，越界视为录入错误 */
    private static final java.util.regex.Pattern YEAR_PATTERN =
            java.util.regex.Pattern.compile("(?<!\\d)(20\\d{2}|19\\d{2})(?!\\d)");

    private final CarbonReportRepository carbonReportRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CreditScoreService creditScoreService;
    private final EmissionRatingService emissionRatingService;
    private final BlockchainServicePort blockchainService;
    private final DigitalSignatureService digitalSignatureService;
    /** 证书门禁：企业无有效准入证书不得提交/认证，审核员无有效资格证不得审核 */
    private final CertificateGateService certificateGateService;
    private final QuotaAllocationService quotaAllocationService;
    private final com.oaiss.chain.repository.IndustryBenchmarkRepository industryBenchmarkRepository;

    /**
     * 配额发放总开关。
     *
     * <p><b>默认关闭</b>：存量报告没有产量/机组类型/核算年度字段（本轮才加），
     * 若强开会让所有历史报告审核一律失败。上线时先回填数据，再把开关打开。
     *
     * <p>关闭时不校验也不发放，审核流程与改造前完全一致——保证改造可灰度、
     * 可回退，不会因为新功能让整个审核停摆。
     */
    @org.springframework.beans.factory.annotation.Value(
            "${app.quota.allocation.enabled:false}")
    private boolean quotaAllocationEnabled;

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

        // 配额发放依据：不落这五项，审核通过时配额算不出来（validatePrerequisites 必抛）。
        // 这些值不参与 DRAFT 阶段的任何计算，只是随报告一起进审核。
        applyQuotaBasis(report, request);

        assertPeriodNotDuplicated(enterprise.getId(), report);

        report = carbonReportRepository.save(report);
        log.info("Carbon report created: {} by user {}", report.getReportNo(), currentUser.getUsername());
        return toResponse(report);
    }

    /**
     * 同企业同核算周期只允许一份报告。
     *
     * <p>唯一键 {@code uk_report_period} 是最终防线，但它抛的是
     * {@code DataIntegrityViolationException}，前端只会看到「系统异常」——
     * 用户不知道是自己填重复了，还是会去找运维。所以在这里先查一次，给出
     * 「2026-Q1 已有报告」这种能直接照着改的提示。
     *
     * <p>为什么不担心并发：两个请求同时通过这里的检查时，仍然由唯一键兜底，
     * 结果是一方成功、另一方 500。这个窗口极窄（毫秒级），且最坏结果只是
     * 提示退化成系统异常，不会产生重复数据——比不加约束安全得多。
     *
     * <p>周期字段为空（历史报告、年度汇总未选季度）时跳过检查：唯一键对 NULL
     * 不做约束，这里也不该拦。
     */
    private void assertPeriodNotDuplicated(Long enterpriseId, CarbonReport report) {
        Integer year = report.getPeriodYear();
        if (year == null) {
            return;
        }
        Integer quarter = report.getPeriodQuarter();
        carbonReportRepository
                .findByEnterpriseIdAndPeriodYearAndPeriodQuarterAndDeletedFalse(enterpriseId, year, quarter)
                .ifPresent(existing -> {
                    throw CarbonException.submitFailed(String.format(
                            "该企业 %s 已存在一份碳报告（编号 %s，状态 %s），同一核算周期不能重复填报。"
                                    + "如需修改请编辑原报告，或改用其他核算周期。",
                            describePeriod(year, quarter), existing.getReportNo(),
                            ReportStatusEnum.fromCode(existing.getStatus()).getDescription()));
                });
    }

    /** 周期的人类可读形式：2026-Q1 / 2026 */
    private static String describePeriod(Integer year, Integer quarter) {
        return quarter == null ? String.valueOf(year) : year + "-Q" + quarter;
    }

    /**
     * 修改报告时的周期查重：命中他人记录才拒绝，命中自己则放行。
     *
     * <p>不能复用 {@link #assertPeriodNotDuplicated}——那份实现的语义是「本周期已有任何记录就拒绝」，
     * 用在修改路径会把「只改标题」误判成重复填报。
     */
    private void assertPeriodNotDuplicatedExcluding(CarbonReport report, Long selfId) {
        Integer year = report.getPeriodYear();
        if (year == null) {
            return;
        }
        carbonReportRepository
                .findByEnterpriseIdAndPeriodYearAndPeriodQuarterAndDeletedFalse(
                        report.getEnterpriseId(), year, report.getPeriodQuarter())
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw CarbonException.submitFailed(String.format(
                            "该企业 %s 已存在另一份碳报告（编号 %s，状态 %s），同一核算周期不能重复。",
                            describePeriod(year, report.getPeriodQuarter()), existing.getReportNo(),
                            ReportStatusEnum.fromCode(existing.getStatus()).getDescription()));
                });
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

        // 提交即「企业为这份数据背书」：无有效准入证书的企业不具备背书资格，
        // 否则吊销后仍可源源不断产出待审报告，审核员无从分辨谁在合规主体之列。
        certificateGateService.assertEnterpriseAdmitted(enterprise.getId());

        calculateEmissions(report);
        // 可信签名链：提交时用企业私钥对排放数据签名（文档契约"企业确认签名后发审核员"）。
        //
        // 两种情形，处置不同：
        //   1. 报告没有签名（从未签过）：跳过签名放行（存量兼容，审核端显示「未签名」）；
        //   2. 报告**带着一份与当前内容不符的签名**（曾被编辑，旧签名已失效）：
        //      必须重新签名成功才允许提交。否则「编辑一次」就能把一份带签名的报告
        //      悄悄变成无签名报告，而审核侧对「无签名」是放行的——这就是绕过通道。
        //
        // 注意判据不能用「状态是驳回」：被驳回后原样重提的无密钥存量企业也会被误拦，
        // 而他们并没有引入任何新内容，不构成签名绕过（BusinessScenarioTest 覆盖该路径）。
        boolean signed;
        if (isSignatureStale(report)) {
            signed = signReportStrict(report, currentUser.getUserId());
            if (!signed) {
                throw CarbonException.submitFailed(
                        "报告已被修改，需重新数字签名后才能提交；请在【个人中心-数字签名】确认密钥有效");
            }
        } else {
            signed = signReportIfPossible(report, currentUser.getUserId());
            if (!signed) {
                log.info("Report {} submitted without signature (no usable key pair)", report.getReportNo());
            }
        }
        report.setStatus(ReportStatusEnum.SUBMITTED.getCode());
        report = carbonReportRepository.save(report);

        log.info("Carbon report submitted: {}", report.getReportNo());
        return toResponse(report);
    }

    /**
     * 构造签名/验签载荷。
     *
     * <p><b>为什么不能只签 {@code emissionData}</b>：配额发放依赖发电量与机组类型
     * （配额 = 发电量 × 基准值，机组类型决定查哪一行基准值）。它们不在签名范围内，
     * 意味着「签名有效」只证明排放明细没被改，不证明配额依据没被改——
     * 篡改产量的人只要不碰 emissionData，验签照样通过。
     *
     * <p><b>为什么要有旧格式回退</b>：改成新载荷会让全部存量签名失效。
     * 存量报告在本轮之前没有 {@code output_quantity} 与 {@code unit_type}
     * （V25 才加这两列），因此按「有没有配额依据」分流：
     * <ul>
     *   <li>有产量或机组类型 → 新载荷（JSON，字段有序，确定性强）</li>
     *   <li>两者都没有 → 旧载荷（就是 emissionData 原文），存量签名继续有效</li>
     * </ul>
     *
     * <p>签名与验签必须调同一个方法，否则两条路径一分开就会出现
     * 「签的时候带产量、验的时候不带」这类永远失败的签名。
     */
    String signaturePayload(CarbonReport report) {
        String emissionData = report.getEmissionData();
        BigDecimal outputQuantity = report.getOutputQuantity();
        String unitType = report.getUnitType();

        if (outputQuantity == null && (unitType == null || unitType.isBlank())) {
            return emissionData;
        }

        // 手写 JSON 而不是用 ObjectMapper 序列化一个 Map：
        // Map 的键顺序不保证稳定，序列化结果一变签名就失效，且这种失效难以复现。
        StringBuilder sb = new StringBuilder(emissionData == null ? 0 : emissionData.length() + 80);
        sb.append("{\"emissionData\":");
        // emissionData 本身已是 JSON 对象，作为值嵌入时直接原样拼接，不二次转义。
        sb.append(emissionData == null ? "null" : emissionData);
        sb.append(",\"outputQuantity\":");
        sb.append(outputQuantity == null ? "null" : outputQuantity.stripTrailingZeros().toPlainString());
        sb.append(",\"unitType\":");
        sb.append(unitType == null || unitType.isBlank() ? "null" : "\"" + unitType + "\"");
        sb.append('}');
        return sb.toString();
    }

    /**
     * 尝试签名；失败不抛异常、仅返回 false（存量兼容：无密钥企业仍可提交）。
     *
     * @return true 表示已成功写入签名
     */
    private boolean signReportIfPossible(CarbonReport report, Long userId) {
        try {
            com.oaiss.chain.dto.SignatureResult result =
                    digitalSignatureService.signReport(userId, signaturePayload(report));
            report.setSignatureData(result.getSignature());
            log.info("Report {} auto-signed by user {}", report.getReportNo(), userId);
            return true;
        } catch (Exception e) {
            log.warn("Auto-sign skipped for report {} by user {}: {}", report.getReportNo(), userId, e.getMessage());
            return false;
        }
    }

    /**
     * 强制签名：必须成功，否则返回 false 由调用方拒绝提交。
     *
     * <p>用于「报告被编辑过」的情形——编辑必然让旧签名失效，
     * 若此时还允许无签名提交，就等于开了一条绕过数字签名的通道。</p>
     */
    private boolean signReportStrict(CarbonReport report, Long userId) {
        try {
            com.oaiss.chain.dto.SignatureResult result =
                    digitalSignatureService.signReport(userId, signaturePayload(report));
            report.setSignatureData(result.getSignature());
            log.info("Report {} re-signed after edit by user {}", report.getReportNo(), userId);
            return true;
        } catch (Exception e) {
            log.warn("Strict re-sign failed for edited report {}: {}", report.getReportNo(), e.getMessage());
            return false;
        }
    }

    /**
     * 判断报告是否带着**一份与当前内容不符的旧签名**。
     *
     * <p>这才是「被编辑过」的可靠判据，而不是状态：</p>
     * <ul>
     *   <li>没有任何签名 → false。企业从未生成密钥（存量兼容），或全新草稿，都属正常路径；</li>
     *   <li>有签名但验签失败/异常 → true。说明内容在签名之后被改过，必须重签；</li>
     *   <li>有签名且验签通过 → false。内容未变，无需重签。</li>
     * </ul>
     *
     * <p>反例（曾踩过的坑）：用「状态 == REJECTED」做判据，会把「被驳回后原样重提」的
     * 无密钥存量企业一并拦下——他们并没有引入新内容，不构成签名绕过。</p>
     */
    private boolean isSignatureStale(CarbonReport report) {
        if (report.getSignatureData() == null || report.getSignatureData().isBlank()) {
            return false;
        }
        Boolean valid = verifyReportSignature(report);
        return !Boolean.TRUE.equals(valid);
    }

    /**
     * 审核裁决（通过 → 自动上链 → 发放配额；驳回 → 冲正配额 → 扣信誉分）。
     *
     * <p><b>为什么需要按报告粒度的分布式锁</b>：本方法是一条「读状态 → 判状态 →
     * 写状态 + 写外部链」的长事务。两位审核员同时点同一份报告时，两者都会读到
     * SUBMITTED 并通过 {@code isReviewable()} 检查，随后各自上链、各自发放配额。
     * {@code allocation_key} 唯一索引只能挡住第二次配额发放，挡不住
     * 「链上写了两条记录」——而链上写入不可回滚。
     *
     * <p>锁键用 {@code #request.reportId} 而不是 {@code #reportId}：
     * 本方法**没有** {@code reportId} 形参，写成 {@code #reportId} 时 SpEL 求值为 null，
     * 锁键退化成常量 {@code report:review:null}，所有审核串成一把全局锁，
     * 并发度掉到 1，而且不会有任何测试报错。
     */
    @com.oaiss.chain.annotation.DistributedLock(
            key = "'report:review:' + #request.reportId", expireTime = 30)
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
            // 审核资格门禁：资格证被吊销/过期的审核员不得再对报告作出裁决。
            // 管理员是兜底角色，不受资格证约束（与既有的「管理员豁免回避」口径一致）。
            certificateGateService.assertReviewerQualifiedByUserId(currentUser.getUserId());
            Enterprise reviewerEnterprise = enterpriseRepository.findByUserId(currentUser.getUserId()).orElse(null);
            if (reviewerEnterprise != null && reviewerEnterprise.getId().equals(report.getEnterpriseId())) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "审核回避原则：禁止审核本企业提交的碳报告");
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
            // ===== 配额发放前置校验（必须在任何写入之前，尤其在上链之前）=====
            //
            // 为什么必须在最前面：上链是不可逆的外部写入。若校验放在上链之后失败，
            // 事务回滚会把 DB 的 txHash 清掉，但链上记录仍在；之后重试会被链码
            // "report already exists" 硬拒，报告永久卡在 APPROVED，配额永远发不出去。
            // Mock 模式下上链恒成功，这个缺陷在测试里看不见，只会在真实 Fabric 暴露。
            if (quotaAllocationEnabled) {
                quotaAllocationService.validatePrerequisites(report);
                assertBenchmarkAvailable(report);
            }

            // 方案 A（依据 docs/raw 原始需求）：「当数据审核员点击提交碳排放报告后，
            // 系统后台自动将该报告进行上链」——审核通过即自动上链，无第二道人工核准。
            report.setStatus(ReportStatusEnum.APPROVED.getCode());
            boolean chained = autoCommitToChain(report, currentUser.getUserId());
            if (chained) {
                report.setStatus(ReportStatusEnum.ON_CHAIN.getCode());
                // ===== 配额发放（上链成功后）=====
                // 放在这里：链上有存证才认可这批减排；且此时所有会失败的校验都已提前做过，
                // 留下的只有「不该失败」的 DB 写。失败不阻断审核（报告已是 ON_CHAIN）。
                if (quotaAllocationEnabled) {
                    allocateQuotaQuietly(report, currentUser.getUserId());
                }
            }
        }

        report = carbonReportRepository.save(report);
        log.info("Carbon report reviewed: {} -> status={}, signatureValid={}, autoChained={}",
                report.getReportNo(), report.getStatus(), signatureValid,
                Objects.equals(report.getStatus(), ReportStatusEnum.ON_CHAIN.getCode()));
        return toResponseWithSignature(report, signatureValid);
    }

    /**
     * 把请求里的配额发放依据写入报告实体。
     *
     * <p><b>为什么单独抽一个方法</b>：创建与修改两条路径必须写同一批字段，
     * 否则「先建后改」会让改过的产量丢失。而这类「两条路径映射不一致」的缺陷
     * 不会被「保存被调用了一次」这种断言发现——必须断言字段级的值。
     *
     * <p>缺省不填 0：{@code null} 表示「企业没填」，{@code 0} 表示「填了一个非法的 0」。
     * 两者在 {@code validatePrerequisites} 里都失败，但在给用户看错误信息时不同，
     * 且 0 会在库里留下一个看起来像真实数据的脏值。
     */
    private void applyQuotaBasis(CarbonReport report, CarbonReportRequest request) {
        report.setPeriodYear(request.getPeriodYear());
        report.setPeriodQuarter(request.getPeriodQuarter());
        report.setOutputQuantity(request.getOutputQuantity());
        report.setUnitType(request.getUnitType());

        String unit = request.getOutputUnit();
        // 单位缺省补 MWh：公式的量纲就是 MWh，前端也固定这个值，
        // 留 null 会让「有产量但无单位」成为一种可能的库内状态。
        if (unit == null || unit.isBlank()) {
            unit = request.getOutputQuantity() == null ? null : "MWh";
        }
        report.setOutputUnit(unit);
    }

    /**
     * 基准值就绪校验——在**上链之前**确认该报告所属行业/机组类型/年度有基准值。
     *
     * <p>不在这里查的话，缺失会等到上链之后才发现，而那时链上已写入、DB 要回滚，
     * 报告就会永久卡死（链码拒绝重复 reportId）。
     */
    private void assertBenchmarkAvailable(CarbonReport report) {
        String unitType = report.getUnitType();
        Integer year = report.getPeriodYear();
        boolean present = industryBenchmarkRepository
                .findByIndustryCodeAndUnitTypeAndBenchmarkYearAndBenchmarkKindAndDeletedFalse(
                        com.oaiss.chain.entity.IndustryBenchmark.INDUSTRY_POWER,
                        unitType, year,
                        com.oaiss.chain.entity.IndustryBenchmark.KIND_GENERATION)
                .isPresent();
        if (!present) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("缺少 %d 年度 %s 类机组的行业基准值，无法发放配额。"
                            + "请管理员先在系统配置中录入该年度基准值。", year, unitType));
        }
    }

    /**
     * 发放配额，失败不阻断审核。
     *
     * <p>此时报告已是 ON_CHAIN（链上存证成功），若让配额失败回滚整个事务，
     * 就会出现「链上有、DB 无」的分歧且无法自愈。故失败只留痕 + 置
     * {@code PENDING_RETRY}，由管理员通过重试端点补发。
     */
    private void allocateQuotaQuietly(CarbonReport report, Long operatorUserId) {
        try {
            quotaAllocationService.allocateForReport(report.getId(), operatorUserId);
        } catch (Exception ex) {
            log.error("[QUOTA] 报告 {} 配额发放失败，已置为待重试: {}",
                    report.getReportNo(), ex.getMessage(), ex);
            report.setAllocationState(
                    com.oaiss.chain.enums.AllocationStateEnum.PENDING_RETRY.getCode());
            try {
                carbonReportRepository.save(report);
            } catch (Exception saveEx) {
                // 连标记都写不进去也只能记日志——不能让审核整体失败
                log.error("[QUOTA] 标记待重试状态失败: {}", saveEx.getMessage());
            }
        }
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
        // 先收回配额，再扣信誉分。
        //
        // 为什么驳回必须冲正：报告一旦被否决，它当初换来的那批配额就没有了合法依据。
        // 不收回的话，企业可以把一份「已被否决的报告」对应的配额拿去挂牌出售——
        // 这是资产错账，不是少做一步流程。冲正失败不阻断驳回（见 revokeQuotaQuietly）。
        revokeQuotaQuietly(report, operatorUserId);

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

    /**
     * 驳回时收回已发放的配额，失败不阻断驳回。
     *
     * <p><b>为什么是「尽力而为」而非强校验</b>：冲正有两道保护——企业已动用配额、
     * 或存在冻结配额（在途挂单）。这两种情况下冲正会拒绝。如果让这个拒绝抛出、
     * 回滚整个驳回事务，审核员就会陷入死局：他必须驳回这份有问题的报告，
     * 但企业只要挂着一笔卖单，他就永远驳不掉。业务上显然应该「驳回优先，
     * 收回配额另行处理」。
     *
     * <p>失败时置 {@code REVOKE_FAILED} 并记错误日志，让这笔该收回的配额
     * 有据可查、可由管理员重试，而不是静默消失。
     */
    private void revokeQuotaQuietly(CarbonReport report, Long operatorUserId) {
        if (!quotaAllocationEnabled) {
            return;
        }
        // 未发放过就没什么可收回的；这里提前判掉，避免每次都触发一次注定失败的调用
        if (report.getQuotaAllocatedAmount() == null
                || !AllocationStateEnum.ALLOCATED.getCode().equals(report.getAllocationState())) {
            return;
        }
        try {
            quotaAllocationService.revokeForReport(report.getId(), operatorUserId);
            // 冲正在独立事务里提交，这里手里这份实体还是冲正**之前**的值。
            // 若不同步，随后 carbonReportRepository.save(report) 会把
            // allocation_state / quota_allocated_amount 写回成 ALLOCATED / 原金额，
            // 冲正等于白做——而且账面看起来完全正常，最难查的那种。
            CarbonReport refreshed = carbonReportRepository.findByIdAndDeletedFalse(report.getId())
                    .orElse(report);
            report.setAllocationState(refreshed.getAllocationState());
            report.setQuotaAllocatedAmount(refreshed.getQuotaAllocatedAmount());
            report.setQuotaUsedSnapshot(refreshed.getQuotaUsedSnapshot());
        } catch (Exception ex) {
            log.error("[QUOTA] 报告 {} 驳回时冲正失败，已置为待人工处理: {}",
                    report.getReportNo(), ex.getMessage(), ex);
            report.setAllocationState(AllocationStateEnum.REVOKE_FAILED.getCode());
        }
    }

    private Boolean verifyReportSignature(CarbonReport report) {
        if (report.getSignatureData() == null || report.getSignatureData().isBlank()) {
            return null; // 无签名（存量/无钥放行）
        }
        try {
            // submitterId 即签名企业用户；用其最新公钥验签。
            // 载荷必须与签名时同源（signaturePayload），否则「签时带产量、验时不带」的
            // 不对称会让每一份新报告都验签失败。
            return digitalSignatureService.verifySignature(
                    report.getSubmitterId(),
                    signaturePayload(report),
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
                // 但配额可能因上次发放失败而缺席——此时补发，而不是空退。
                // 这正是「上链失败→certify 补救」这条链的最后一环：不补这一步，
                // 计划里承诺的"补救后配额会发"就是空头支票。
                if (quotaAllocationEnabled) {
                    allocateQuotaQuietly(report, currentUser.getUserId());
                }
                return toResponse(report);
            }
            // 与 reviewReport 一致：配额前置校验必须在副作用（含上链）之前
            if (quotaAllocationEnabled) {
                quotaAllocationService.validatePrerequisites(report);
                assertBenchmarkAvailable(report);
            }
            applyApprovedReportSideEffects(report, currentUser.getUserId());
            report.setStatus(ReportStatusEnum.ON_CHAIN.getCode());
            // 补救上链成功后才发放配额（与 reviewReport 的主路径同源）
            if (quotaAllocationEnabled) {
                allocateQuotaQuietly(report, currentUser.getUserId());
            }
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
        List<Long> successIds = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

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
        // 与 createReport 走同一份映射：漏掉这里会让「改过的发电量/机组类型」不生效，
        // 审核时仍按旧值算配额——而签名已经覆盖了新内容，看不出任何异常。
        applyQuotaBasis(report, request);

        // 改周期同样要查重：把 2026-Q1 改成 2026-Q2 而后者已有一份报告时，
        // 唯一键会在 save 时抛出 500。这里提前给可读提示。
        // 注意要排除自己——否则「只改标题、不动周期」会被自己的记录判为重复。
        assertPeriodNotDuplicatedExcluding(report, report.getId());

        // 内容变了，四列必须重算，旧签名必然失效（它覆盖的是旧 emissionData）。
        //
        // 安全要点（D13 引入的风险）：
        // 不能简单置 null 了事——审核侧的 verifyReportSignature 对「无签名」返回 null
        // 并放行（为兼容存量无密钥企业），于是「编辑 → 签名丢失 → 无签名过审 → 上链」
        // 会成为一条绕过数字签名的通道。
        //
        // 这里改为：清空旧签名，同时在提交时必须重新签名（见 submitReport 的
        // requireFreshSignature 判断）。企业若已无可用密钥，提交会被明确拒绝，
        // 而不是静默产出一份无签名报告。
        calculateEmissions(report);

        // 旧签名覆盖的是旧 emissionData，内容一变必然失效，先清空。
        report.setSignatureData(null);

        // 立刻用企业私钥重签——**而不是**把「签名」这件事留到 submit。
        // 理由：留到 submit 的话，一份从未签名的新草稿与一份「编辑后签名丢失」的草稿
        // 在库里看起来完全一样，无法区分，只能靠状态猜（REJECTED 才严格）。
        // 在编辑点就重签，能让「被编辑过的报告一定带新签名」成为库内可见事实。
        boolean resigned = signReportStrict(report, currentUser.getUserId());
        if (!resigned) {
            throw CarbonException.submitFailed(
                    "保存修改需重新数字签名，但当前账号没有可用密钥。"
                            + "请先在【个人中心-数字签名】生成或恢复密钥后再修改报告");
        }

        report = carbonReportRepository.save(report);
        log.info("Carbon report updated and re-signed: {} by user {}",
                report.getReportNo(), currentUser.getUsername());
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
        // 过程排放从原文明细里取出并单独上链：总量含这三项，只存三范围会让链上对不上账。
        BigDecimal[] process = EmissionDataValidator.parseProcessEmissions(
                report.getEmissionData(), objectMapper);
        CarbonReportSnapshot snapshot = CarbonReportSnapshot.builder()
                .reportId(report.getId())
                .enterpriseId(report.getEnterpriseId())
                .reportNo(report.getReportNo())
                .accountingPeriod(report.getAccountingPeriod())
                .totalEmission(stringify(report.getTotalEmission()))
                .scope1Emission(stringify(report.getScope1Emission()))
                .scope2Emission(stringify(report.getScope2Emission()))
                .scope3Emission(stringify(report.getScope3Emission()))
                .desulfEmission(stringify(process[0]))
                .transmissionLossEmission(stringify(process[1]))
                .sf6Emission(stringify(process[2]))
                .signature(report.getSignatureData())
                .data(report.getEmissionData())
                // 配额依据一并留痕：事后要能回答「这份报告为什么发了这么多吨」。
                // 注意这几个字段**不进入链上参数**（链码签名固定），
                // 只是让 DB 侧快照可解释。见 CarbonReportSnapshot 的说明。
                //
                // 不放 quotaAllocatedAmount：快照在上链**之前**构建，那一刻配额还没发，
                // 该字段必然恒为 null——放一个永远取不到值的字段比不放更误导。
                .outputQuantity(stringify(report.getOutputQuantity()))
                .outputUnit(report.getOutputUnit())
                .unitType(report.getUnitType())
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
        String ratingYear = extractYear(report.getAccountingPeriod());
        // D10：按**年度汇总**重算，而不是用「这一份报告的排放量」覆盖写。
        // 历史实现在同年多份报告时只有最后一份生效，「年度评级」名不副实。
        emissionRatingService.recalculateYearlyRating(enterpriseId, ratingYear, operatorUserId);
    }

    /**
     * 从核算周期取四位年份（AC-22）。
     *
     * <p><b>为什么不直接 {@code substring(0, 4)}</b>：那样对 {@code Q4-2026}
     * 会得到 {@code Q4-2}，随后被写进年度评级表——不报错、不告警，
     * 只是那一年的评级从此再也算不进去。静默算错比明确报错危险得多。
     *
     * <p>从字符串中找**第一个**形如 19xx/20xx 的四位数字：这样
     * {@code 2026-Q1}、{@code 2026}、{@code 2024-01-15} 都能取到正确年度；
     * {@code Q4-2026} 也能靠搜索而非截断取到 2026。
     * 解析不出来时退回当前年度并留日志，不让审核因为周期格式而失败。
     */
    static String extractYear(String accountingPeriod) {
        if (accountingPeriod == null || accountingPeriod.isBlank()) {
            return String.valueOf(LocalDateTime.now().getYear());
        }
        java.util.regex.Matcher m = YEAR_PATTERN.matcher(accountingPeriod);
        if (m.find()) {
            int year = Integer.parseInt(m.group());
            // 越界值多半是数据录入错误，采纳会让评级表里出现 9999 年这种行
            if (year >= 2000 && year <= 2100) {
                return String.valueOf(year);
            }
        }
        log.warn("核算周期无法解析出年度，退回当前年度: '{}'", accountingPeriod);
        return String.valueOf(LocalDateTime.now().getYear());
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

        // 配额发放结果（只读透出）。判定是纯计算，不走查库——
        // toResponse 在列表页会被调用 N 次，任何额外查询都是 N+1。
        BigDecimal quotaAmount = report.getQuotaAllocatedAmount();
        BigDecimal quotaSurplusOrGap = null;
        String quotaJudgement = null;
        if (quotaAmount != null) {
            QuotaAllocationService.AllocationResult judged =
                    QuotaAllocationService.judge(quotaAmount, report.getScope1Emission(),
                            report.getId(), true);
            quotaSurplusOrGap = judged.getSurplusOrGap();
            quotaJudgement = judged.getJudgement().name();
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
                .chainMark(report.getChainMark())
                .chainMarkReason(report.getChainMarkReason())
                .chainMarkedAt(report.getChainMarkedAt())
                .attachments(report.getAttachments())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .quotaAllocatedAmount(quotaAmount)
                .allocationState(report.getAllocationState())
                .quotaSurplusOrGap(quotaSurplusOrGap)
                .quotaJudgement(quotaJudgement)
                .build();
    }
}
