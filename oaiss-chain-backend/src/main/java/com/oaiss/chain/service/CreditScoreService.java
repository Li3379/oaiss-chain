package com.oaiss.chain.service;

import com.oaiss.chain.dto.CreditEventResponse;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.constant.ErrorMessage;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.dto.CreditScoreResponse;
import com.oaiss.chain.entity.CreditEvent;
import com.oaiss.chain.entity.CreditScore;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.enums.CreditEventTypeEnum;
import com.oaiss.chain.enums.CreditLevelEnum;
import com.oaiss.chain.repository.CreditEventRepository;
import com.oaiss.chain.repository.CreditScoreRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 信誉评分服务
 * 管理企业信誉评分的初始化、扣分、加分、等级评估和交易权限控制
 * <p>
 * 规则说明：
 * - 初始分数：100
 * - 数据造假：-20分
 * - 迟交报告：-5分
 * - 轻微违规：-10分
 * - 严重违规：-30分
 * - 良好行为：+5分
 * - 分数低于40：限制交易
 * - 分数低于20：冻结账户
 * - 分数范围：0-100（超出范围自动截断）
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditScoreService {

    private final CreditScoreRepository creditScoreRepository;
    private final CreditEventRepository creditEventRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;

    /**
     * 初始化企业信誉分（新企业注册时调用）
     *
     * @param enterpriseId 企业ID
     * @return 信誉评分响应
     */
    @Transactional
    public CreditScoreResponse initializeScore(Long enterpriseId) {
        if (creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId).isPresent()) {
            return getScore(enterpriseId);
        }

        CreditScore creditScore = CreditScore.builder()
                .enterpriseId(enterpriseId)
                .score(100)
                .level(CreditLevelEnum.EXCELLENT.getCode())
                .tradeRestricted(false)
                .accountFrozen(false)
                .lastEvaluatedAt(LocalDateTime.now())
                .build();

        creditScore = creditScoreRepository.save(creditScore);
        log.info("Credit score initialized for enterprise {}: score={}", enterpriseId, creditScore.getScore());

        return toScoreResponse(creditScore);
    }

    /**
     * 获取企业信誉分（自动初始化）
     *
     * @param enterpriseId 企业ID
     * @return 信誉评分响应
     */
    @Transactional
    public CreditScoreResponse getScore(Long enterpriseId) {
        CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                .orElseGet(() -> {
                    CreditScore cs = CreditScore.builder()
                            .enterpriseId(enterpriseId)
                            .score(100)
                            .level(CreditLevelEnum.EXCELLENT.getCode())
                            .tradeRestricted(false)
                            .accountFrozen(false)
                            .lastEvaluatedAt(LocalDateTime.now())
                            .build();
                    return creditScoreRepository.save(cs);
                });

        return toScoreResponse(creditScore);
    }

    /**
     * 扣除信誉分
     *
     * @param enterpriseId   企业ID
     * @param eventType      事件类型
     * @param description    事件描述
     * @param triggeredBy    触发者用户ID
     * @param relatedReportId 关联报告ID（可选）
     * @return 更新后的信誉评分
     */
    @Transactional
    public CreditScoreResponse deductPoints(Long enterpriseId, Integer eventType,
                                              String description, Long triggeredBy,
                                              Long relatedReportId) {
        CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                .orElseGet(() -> {
                    CreditScore cs = CreditScore.builder()
                            .enterpriseId(enterpriseId)
                            .score(100)
                            .level(CreditLevelEnum.EXCELLENT.getCode())
                            .tradeRestricted(false)
                            .accountFrozen(false)
                            .build();
                    return creditScoreRepository.save(cs);
                });

        CreditEventTypeEnum typeEnum = CreditEventTypeEnum.fromCode(eventType);
        if (typeEnum == null) {
            throw new IllegalArgumentException("无效的事件类型: " + eventType);
        }
        if (typeEnum.getDefaultPoints() > 0) {
            throw new IllegalArgumentException("扣分接口不接受奖励类型事件，请使用 /credit/bonus 接口");
        }

        int pointsToDeduct = typeEnum.getDefaultPoints();
        int scoreBefore = creditScore.getScore();
        int scoreAfter = Math.max(0, scoreBefore + pointsToDeduct); // pointsToDeduct is negative

        CreditEvent event = CreditEvent.builder()
                .enterpriseId(enterpriseId)
                .eventType(eventType)
                .eventDescription(description != null ? description : typeEnum.getDescription())
                .pointsChanged(pointsToDeduct)
                .scoreBefore(scoreBefore)
                .scoreAfter(scoreAfter)
                .relatedReportId(relatedReportId)
                .triggeredBy(triggeredBy)
                .triggeredAt(LocalDateTime.now())
                .build();
        creditEventRepository.save(event);

        creditScore.setScore(scoreAfter);
        creditScore.setLastEvaluatedAt(LocalDateTime.now());
        checkThresholds(creditScore);
        creditScore = creditScoreRepository.save(creditScore);

        log.info("Credit deducted for enterprise {}: {} -> {} ({}), event={}",
                enterpriseId, scoreBefore, scoreAfter, typeEnum.getDescription(), eventType);

        return toScoreResponse(creditScore);
    }

    /**
     * 成交审核驳回扣分：与 {@code deductPoints} 的区别是能**归因到交易**。
     *
     * <p>存在理由：{@code deductPoints} 的签名只有 {@code relatedReportId}，
     * 而 {@code CreditEvent.relatedTradeId} 字段虽早已存在，全仓却从无写入方——
     * 造成「企业为什么被扣分」在交易维度上不可回答。审核驳回必须能指回
     * 那笔交易，否则监管无法把信誉下降与具体成交关联起来。
     *
     * <p>调用方（TradeAuditService）对买卖双方分别调用：一笔异常成交中双方
     * 都参与了价格形成，只罚一方会留下「靠对手方低信誉规避」的口子。
     *
     * @param enterpriseId 被扣分企业ID
     * @param eventType    事件类型（应传 {@code TRADE_AUDIT_REJECTED}）
     * @param description  描述
     * @param triggeredBy  裁决人用户ID
     * @param tradeId      关联交易ID
     */
    @Transactional
    public CreditScoreResponse deductPointsForTrade(Long enterpriseId, Integer eventType,
                                                    String description, Long triggeredBy,
                                                    Long tradeId) {
        CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                .orElseGet(() -> creditScoreRepository.save(CreditScore.builder()
                        .enterpriseId(enterpriseId)
                        .score(100)
                        .level(CreditLevelEnum.EXCELLENT.getCode())
                        .tradeRestricted(false)
                        .accountFrozen(false)
                        .build()));

        CreditEventTypeEnum typeEnum = CreditEventTypeEnum.fromCode(eventType);
        if (typeEnum == null) {
            throw new IllegalArgumentException("无效的事件类型: " + eventType);
        }
        if (typeEnum.getDefaultPoints() > 0) {
            throw new IllegalArgumentException("扣分接口不接受奖励类型事件，请使用 addBonusPoints");
        }

        int pointsToDeduct = typeEnum.getDefaultPoints();
        int scoreBefore = creditScore.getScore();
        int scoreAfter = Math.max(0, scoreBefore + pointsToDeduct);

        CreditEvent event = CreditEvent.builder()
                .enterpriseId(enterpriseId)
                .eventType(eventType)
                .eventDescription(description != null ? description : typeEnum.getDescription())
                .pointsChanged(pointsToDeduct)
                .scoreBefore(scoreBefore)
                .scoreAfter(scoreAfter)
                .relatedTradeId(tradeId)
                .triggeredBy(triggeredBy)
                .triggeredAt(LocalDateTime.now())
                .build();
        creditEventRepository.save(event);

        creditScore.setScore(scoreAfter);
        creditScore.setLastEvaluatedAt(LocalDateTime.now());
        checkThresholds(creditScore);
        creditScore = creditScoreRepository.save(creditScore);

        log.info("成交审核驳回扣分: enterprise={}, trade={}, {} -> {}, event={}",
                enterpriseId, tradeId, scoreBefore, scoreAfter, eventType);

        return toScoreResponse(creditScore);
    }

    /**
     * 添加奖励分（默认记为 BONUS_GOOD_BEHAVIOR）。
     *
     * @param enterpriseId 企业ID
     * @param points       奖励分数（正数）
     * @param description  描述
     * @param triggeredBy  触发者
     * @return 更新后的信誉评分
     */
    @Transactional
    public CreditScoreResponse addBonusPoints(Long enterpriseId, Integer points,
                                                String description, Long triggeredBy) {
        return addBonusPoints(enterpriseId, points, description, triggeredBy,
                CreditEventTypeEnum.BONUS_GOOD_BEHAVIOR);
    }

    /**
     * 添加奖励分，事件类型由调用方决定（如碳中和认证记 {@code CARBON_NEUTRAL_CERTIFIED}）。
     *
     * <p>四参重载保留原签名，避免既有调用方行为变化；事务注解必须留在本方法上——
     * 四参方法内部委派属于自调用，不经过 Spring 代理。
     *
     * @param enterpriseId 企业ID
     * @param points       奖励分数（正数）
     * @param description  描述
     * @param triggeredBy  触发者
     * @param eventType    事件类型
     * @return 更新后的信誉评分
     */
    @Transactional
    public CreditScoreResponse addBonusPoints(Long enterpriseId, Integer points,
                                                String description, Long triggeredBy,
                                                CreditEventTypeEnum eventType) {
        if (points == null || points <= 0) {
            throw new IllegalArgumentException("奖励分数必须为正整数");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("事件类型不能为空");
        }
        CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                .orElseGet(() -> {
                    CreditScore cs = CreditScore.builder()
                            .enterpriseId(enterpriseId)
                            .score(100)
                            .level(CreditLevelEnum.EXCELLENT.getCode())
                            .tradeRestricted(false)
                            .accountFrozen(false)
                            .build();
                    return creditScoreRepository.save(cs);
                });

        int scoreBefore = creditScore.getScore();
        int scoreAfter = Math.min(100, scoreBefore + points); // 上限100

        CreditEvent event = CreditEvent.builder()
                .enterpriseId(enterpriseId)
                .eventType(eventType.getCode())
                .eventDescription(description)
                .pointsChanged(points)
                .scoreBefore(scoreBefore)
                .scoreAfter(scoreAfter)
                .triggeredBy(triggeredBy)
                .triggeredAt(LocalDateTime.now())
                .build();
        creditEventRepository.save(event);

        creditScore.setScore(scoreAfter);
        creditScore.setLastEvaluatedAt(LocalDateTime.now());
        checkThresholds(creditScore);
        creditScore = creditScoreRepository.save(creditScore);

        log.info("Credit bonus for enterprise {}: {} -> {} (+{})", enterpriseId, scoreBefore, scoreAfter, points);

        return toScoreResponse(creditScore);
    }

    /**
     * 重新评估信誉等级
     *
     * @param enterpriseId 企业ID
     * @return 更新后的信誉评分
     */
    @Transactional
    public CreditScoreResponse evaluateLevel(Long enterpriseId) {
        CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                .orElseThrow(() -> new IllegalArgumentException("企业信誉分不存在: " + enterpriseId));

        checkThresholds(creditScore);
        creditScore.setLastEvaluatedAt(LocalDateTime.now());
        creditScore = creditScoreRepository.save(creditScore);

        log.info("Credit level evaluated for enterprise {}: level={}, score={}",
                enterpriseId, creditScore.getLevel(), creditScore.getScore());

        return toScoreResponse(creditScore);
    }

    /**
     * 查询信誉事件历史
     */
    public Page<CreditEventResponse> getCreditHistory(Long enterpriseId, Integer eventType,
                                                        Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "triggeredAt"));
        Page<CreditEvent> events;
        if (eventType != null) {
            events = creditEventRepository.findByEnterpriseIdAndEventTypeAndDeletedFalse(
                    enterpriseId, eventType, pageable);
        } else {
            events = creditEventRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId, pageable);
        }
        return events.map(this::toEventResponse);
    }

    /**
     * 获取被限制交易的企业列表
     */
    @Transactional(readOnly = true)
    public List<CreditScoreResponse> getRestrictedEnterprises() {
        return creditScoreRepository.findByAnyRestrictionAndDeletedFalse().stream()
                .map(this::toScoreResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取被冻结的企业列表
     */
    @Transactional(readOnly = true)
    public List<CreditScoreResponse> getFrozenEnterprises() {
        return creditScoreRepository.findByAccountFrozenAndDeletedFalse(true).stream()
                .map(this::toScoreResponse)
                .collect(Collectors.toList());
    }

    /**
     * 检查企业是否有交易权限
     *
     * @param enterpriseId 企业ID
     * @return true=允许交易，false=禁止
     */
    @Transactional(readOnly = true)
    public boolean checkTradePermission(Long enterpriseId) {
        CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                .orElse(null);
        if (creditScore == null) {
            return true; // 没有记录默认允许
        }
        return !creditScore.getTradeRestricted() && !creditScore.getAccountFrozen()
                && !Boolean.TRUE.equals(creditScore.getDelisted());
    }

    /**
     * 管理员下市处罚（R-03）：delisted 置位 + ADMIN_DELIST 留痕。
     * 语义=禁止碳交易直至恢复上市（raw 03 L1098），非禁登录；与信誉阈值自动启停解耦。
     *
     * @param userId     目标用户的 userId（经企业档案解析到 enterpriseId）
     * @param operatorId 操作管理员 userId（留痕）
     */
    @Transactional
    public CreditScoreResponse delistEnterpriseByUser(Long userId, Long operatorId) {
        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标用户无企业档案"));
        CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterprise.getId())
                .orElseGet(() -> CreditScore.builder()
                        .enterpriseId(enterprise.getId())
                        .score(100)
                        .level(CreditLevelEnum.EXCELLENT.getCode())
                        .tradeRestricted(false)
                        .accountFrozen(false)
                        .build());
        if (Boolean.TRUE.equals(creditScore.getDelisted())) {
            throw new BusinessException(ErrorCode.DELIST_STATE_INVALID, ErrorMessage.DELIST_STATE_INVALID);
        }
        creditScore.setDelisted(true);
        creditScoreRepository.save(creditScore);
        recordCreditEvent(creditScore.getEnterpriseId(), CreditEventTypeEnum.ADMIN_DELIST,
                "管理员下市处罚：暂停碳交易资格", operatorId, null);
        return toScoreResponse(creditScore);
    }

    /**
     * 恢复上市（下市解除）：delisted 复位 + ADMIN_REPEAL 留痕。
     */
    @Transactional
    public CreditScoreResponse restoreEnterpriseByUser(Long userId, Long operatorId) {
        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标用户无企业档案"));
        CreditScore creditScore = creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterprise.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该企业无信誉分记录"));
        if (!Boolean.TRUE.equals(creditScore.getDelisted())) {
            throw new BusinessException(ErrorCode.DELIST_STATE_INVALID, ErrorMessage.DELIST_STATE_INVALID);
        }
        creditScore.setDelisted(false);
        creditScoreRepository.save(creditScore);
        recordCreditEvent(creditScore.getEnterpriseId(), CreditEventTypeEnum.ADMIN_REPEAL,
                "恢复上市：解除下市处罚", operatorId, null);
        return toScoreResponse(creditScore);
    }

    /** 统一留痕：状态事件（0 分）不改分数，仅落 credit_event。 */
    private void recordCreditEvent(Long enterpriseId, CreditEventTypeEnum type,
                                   String description, Long triggeredBy, Long relatedReportId) {
        CreditEvent event = CreditEvent.builder()
                .enterpriseId(enterpriseId)
                .eventType(type.getCode())
                .eventDescription(description)
                .pointsChanged(0)
                .scoreBefore(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                        .map(CreditScore::getScore).orElse(100))
                .scoreAfter(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(enterpriseId)
                        .map(CreditScore::getScore).orElse(100))
                .relatedReportId(relatedReportId)
                .triggeredBy(triggeredBy)
                .triggeredAt(LocalDateTime.now())
                .build();
        creditEventRepository.save(event);
    }

    /**
     * 批量获取用户所属企业的信誉分（撮合同价次级排序用）
     * <p>
     * 查不到用户、企业或信誉分记录的用户不会出现在返回结果中，
     * 由调用方按中性分处理，避免因数据缺失导致排序报错或排到最后。
     *
     * @param userIds 用户ID集合
     * @return userId -> 信誉分；无法解析的用户不在 Map 中
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> getScoresByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 用户 → 企业 → 信誉分，两跳批量查询，避免逐单 N+1
        Map<Long, Long> enterpriseIdByUserId = enterpriseRepository.findByUserIdInAndDeletedFalse(userIds).stream()
                .filter(e -> e.getId() != null && e.getUserId() != null)
                .collect(Collectors.toMap(Enterprise::getUserId, Enterprise::getId, (first, second) -> first));
        if (enterpriseIdByUserId.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Integer> scoreByEnterpriseId = creditScoreRepository
                .findByEnterpriseIdInAndDeletedFalse(new HashSet<>(enterpriseIdByUserId.values())).stream()
                .filter(cs -> cs.getScore() != null)
                .collect(Collectors.toMap(CreditScore::getEnterpriseId, CreditScore::getScore, (first, second) -> first));

        Map<Long, Integer> scoreByUserId = new HashMap<>();
        enterpriseIdByUserId.forEach((userId, enterpriseId) -> {
            Integer score = scoreByEnterpriseId.get(enterpriseId);
            if (score != null) {
                scoreByUserId.put(userId, score);
            }
        });
        return scoreByUserId;
    }

    /**
     * 根据用户ID获取企业信誉分
     *
     * @param userId 用户ID
     * @return 信誉评分响应
     */
    @Transactional(readOnly = true)
    public CreditScoreResponse getScoreByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户未关联企业"));

        return getScore(enterprise.getId());
    }

    /**
     * 根据用户ID获取信誉历史
     *
     * @param userId 用户ID
     * @param eventType 事件类型
     * @param page 页码
     * @param size 每页数量
     * @return 信誉事件分页
     */
    public Page<CreditEventResponse> getCreditHistoryByUserId(Long userId, Integer eventType,
                                                                Integer page, Integer size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户未关联企业"));

        return getCreditHistory(enterprise.getId(), eventType, page, size);
    }

    /**
     * 获取信誉排名列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 信誉评分分页
     */
    public Page<CreditScoreResponse> getScoreRanking(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "score"));
        Page<CreditScore> scores = creditScoreRepository.findByDeletedFalse(pageable);
        return scores.map(this::toScoreResponse);
    }

    // ==================== 私有方法 ====================

    /**
     * 检查并应用阈值规则
     * - 分数低于40：限制交易
     * - 分数低于20：冻结账户
     * - 分数恢复到40以上：解除交易限制
     * - 分数恢复到20以上：解除冻结（但仍可能被限制交易）
     */
    private void checkThresholds(CreditScore creditScore) {
        CreditLevelEnum level = CreditLevelEnum.fromScore(creditScore.getScore());
        creditScore.setLevel(level.getCode());

        // 冻结检查（最严格，优先判断）
        if (creditScore.getScore() < 20) {
            creditScore.setAccountFrozen(true);
            creditScore.setTradeRestricted(true);
        }
        // 交易限制检查
        else if (creditScore.getScore() < 40) {
            creditScore.setTradeRestricted(true);
            creditScore.setAccountFrozen(false);
        }
        // 正常范围：解除所有限制
        else {
            creditScore.setTradeRestricted(false);
            creditScore.setAccountFrozen(false);
        }
    }

    /**
     * CreditScore → CreditScoreResponse
     */
    private CreditScoreResponse toScoreResponse(CreditScore cs) {
        Enterprise ent = enterpriseRepository.findById(cs.getEnterpriseId()).orElse(null);
        String enterpriseName = ent != null ? ent.getEnterpriseName() : "未知企业";

        return CreditScoreResponse.builder()
                .id(cs.getId())
                .enterpriseId(cs.getEnterpriseId())
                .enterpriseName(enterpriseName)
                .score(cs.getScore())
                .level(cs.getLevel())
                .tradeRestricted(cs.getTradeRestricted())
                .accountFrozen(cs.getAccountFrozen())
                .delisted(cs.getDelisted())
                .userId(ent != null ? ent.getUserId() : null)
                .lastEvaluatedAt(cs.getLastEvaluatedAt())
                .createdAt(cs.getCreatedAt())
                .build();
    }

    /**
     * CreditEvent → CreditEventResponse
     */
    private CreditEventResponse toEventResponse(CreditEvent event) {
        CreditEventTypeEnum typeEnum = CreditEventTypeEnum.fromCode(event.getEventType());
        String typeName = typeEnum != null ? typeEnum.getDescription() : "未知";

        String triggeredByName = event.getTriggeredBy() != null
                ? userRepository.findById(event.getTriggeredBy()).map(User::getRealName).orElse("系统")
                : "系统";

        return CreditEventResponse.builder()
                .id(event.getId())
                .enterpriseId(event.getEnterpriseId())
                .eventType(event.getEventType())
                .eventTypeName(typeName)
                .eventDescription(event.getEventDescription())
                .pointsChanged(event.getPointsChanged())
                .scoreBefore(event.getScoreBefore())
                .scoreAfter(event.getScoreAfter())
                .relatedReportId(event.getRelatedReportId())
                .relatedTradeId(event.getRelatedTradeId())
                .triggeredBy(event.getTriggeredBy())
                .triggeredByName(triggeredByName)
                .triggeredAt(event.getTriggeredAt())
                .build();
    }
}
