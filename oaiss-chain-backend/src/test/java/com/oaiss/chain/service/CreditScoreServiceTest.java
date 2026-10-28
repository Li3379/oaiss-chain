package com.oaiss.chain.service;

import com.oaiss.chain.dto.CreditEventResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 信誉评分服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditScoreServiceTest {

    @Mock
    private CreditScoreRepository creditScoreRepository;
    @Mock
    private CreditEventRepository creditEventRepository;
    @Mock
    private EnterpriseRepository enterpriseRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CreditScoreService creditScoreService;

    private CreditScore testCreditScore;
    private Enterprise testEnterprise;
    private User testUser;

    @BeforeEach
    void setUp() {
        testCreditScore = CreditScore.builder()
                .enterpriseId(1L).score(100).level("EXCELLENT")
                .tradeRestricted(false).accountFrozen(false)
                .lastEvaluatedAt(LocalDateTime.now()).build();
        testCreditScore.setId(1L);
        testCreditScore.setCreatedAt(LocalDateTime.now());
        testCreditScore.setUpdatedAt(LocalDateTime.now());

        testEnterprise = new Enterprise();
        testEnterprise.setId(1L);
        testEnterprise.setEnterpriseName("测试企业");

        testUser = new User();
        testUser.setId(1L);
        testUser.setRealName("测试用户");

        when(enterpriseRepository.findById(anyLong())).thenReturn(Optional.of(testEnterprise));
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));
    }

    // ==================== initializeScore ====================

    @Test
    @DisplayName("初始化信誉分 - 已存在时返回已有记录")
    void initializeScore_WhenAlreadyExists_ShouldReturnExisting() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        CreditScoreResponse response = creditScoreService.initializeScore(1L);
        assertNotNull(response);
        assertEquals(100, response.getScore());
        verify(creditScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("初始化信誉分 - 不存在时创建新记录")
    void initializeScore_WhenNotExists_ShouldCreateNew() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> { CreditScore cs = inv.getArgument(0); cs.setId(1L); return cs; });
        CreditScoreResponse response = creditScoreService.initializeScore(1L);
        assertNotNull(response);
        assertEquals(100, response.getScore());
        verify(creditScoreRepository, times(1)).save(any(CreditScore.class));
    }

    // ==================== getScore ====================

    @Test
    @DisplayName("获取信誉分 - 已存在")
    void getScore_WhenExists_ShouldReturnScore() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        CreditScoreResponse response = creditScoreService.getScore(1L);
        assertNotNull(response);
        assertEquals(1L, response.getEnterpriseId());
        assertEquals("测试企业", response.getEnterpriseName());
    }

    @Test
    @DisplayName("获取信誉分 - 不存在时自动创建")
    void getScore_WhenNotExists_ShouldAutoCreate() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> { CreditScore cs = inv.getArgument(0); cs.setId(2L); return cs; });
        CreditScoreResponse response = creditScoreService.getScore(1L);
        assertNotNull(response);
        assertEquals(100, response.getScore());
    }

    @Test
    @DisplayName("获取信誉分 - 企业不存在时显示未知企业")
    void getScore_WhenEnterpriseNotFound_ShouldShowUnknown() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(enterpriseRepository.findById(1L)).thenReturn(Optional.empty());
        CreditScoreResponse response = creditScoreService.getScore(1L);
        assertEquals("未知企业", response.getEnterpriseName());
    }

    // ==================== deductPoints ====================

    @Test
    @DisplayName("扣分 - 数据造假扣20分")
    void deductPoints_DataFalsification_ShouldDeduct20() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.deductPoints(1L, CreditEventTypeEnum.DATA_FALSIFICATION.getCode(), "数据造假", 1L, null);
        assertEquals(80, response.getScore());
    }

    @Test
    @DisplayName("扣分 - 迟交报告扣5分")
    void deductPoints_LateSubmission_ShouldDeduct5() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.deductPoints(1L, CreditEventTypeEnum.LATE_SUBMISSION.getCode(), null, 1L, 100L);
        assertEquals(95, response.getScore());
    }

    @Test
    @DisplayName("扣分 - 轻微违规扣10分")
    void deductPoints_MinorViolation_ShouldDeduct10() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.deductPoints(1L, CreditEventTypeEnum.MINOR_VIOLATION.getCode(), "违规", 1L, null);
        assertEquals(90, response.getScore());
    }

    @Test
    @DisplayName("扣分 - 严重违规扣30分")
    void deductPoints_MajorViolation_ShouldDeduct30() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.deductPoints(1L, CreditEventTypeEnum.MAJOR_VIOLATION.getCode(), "严重违规", 1L, null);
        assertEquals(70, response.getScore());
    }

    @Test
    @DisplayName("扣分 - 良好行为奖励类型被扣分接口拒绝")
    void deductPoints_GoodBehavior_ShouldReject() {
        testCreditScore.setScore(50);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        assertThrows(IllegalArgumentException.class,
            () -> creditScoreService.deductPoints(1L, CreditEventTypeEnum.BONUS_GOOD_BEHAVIOR.getCode(), "奖励", 1L, null));
    }

    @Test
    @DisplayName("扣分 - 无效事件类型抛出异常")
    void deductPoints_InvalidEventType_ShouldThrow() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        assertThrows(IllegalArgumentException.class, () -> creditScoreService.deductPoints(1L, 999, "无效", 1L, null));
    }

    @Test
    @DisplayName("扣分 - 分数不低于0")
    void deductPoints_ScoreFloorAtZero() {
        testCreditScore.setScore(5);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.deductPoints(1L, CreditEventTypeEnum.MAJOR_VIOLATION.getCode(), "严重", 1L, null);
        assertEquals(0, response.getScore());
        assertTrue(response.getAccountFrozen());
    }

    @Test
    @DisplayName("扣分 - 低于20分冻结账户")
    void deductPoints_Below20_ShouldFreeze() {
        testCreditScore.setScore(25);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.deductPoints(1L, CreditEventTypeEnum.MINOR_VIOLATION.getCode(), "违规", 1L, null);
        assertEquals(15, response.getScore());
        assertTrue(response.getAccountFrozen());
        assertTrue(response.getTradeRestricted());
        assertEquals("FROZEN", response.getLevel());
    }

    @Test
    @DisplayName("扣分 - 低于40分限制交易但不冻结")
    void deductPoints_Below40_ShouldRestrictNotFreeze() {
        testCreditScore.setScore(45);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.deductPoints(1L, CreditEventTypeEnum.MINOR_VIOLATION.getCode(), "违规", 1L, null);
        assertEquals(35, response.getScore());
        assertTrue(response.getTradeRestricted());
        assertFalse(response.getAccountFrozen());
        assertEquals("DANGER", response.getLevel());
    }

    @Test
    @DisplayName("扣分 - 不存在时自动创建")
    void deductPoints_WhenNotExists_ShouldAutoCreate() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> { CreditScore cs = inv.getArgument(0); cs.setId(1L); return cs; });
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.deductPoints(1L, CreditEventTypeEnum.MINOR_VIOLATION.getCode(), "违规", 1L, null);
        assertEquals(90, response.getScore());
    }

    // ==================== addBonusPoints ====================

    @Test
    @DisplayName("加分 - 添加奖励分")
    void addBonusPoints_ShouldAddPoints() {
        testCreditScore.setScore(90);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.addBonusPoints(1L, 5, "良好行为", 1L);
        assertEquals(95, response.getScore());
    }

    @Test
    @DisplayName("加分 - 上限为100分")
    void addBonusPoints_ShouldNotExceed100() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.addBonusPoints(1L, 20, "大量加分", 1L);
        assertEquals(100, response.getScore());
    }

    @Test
    @DisplayName("加分 - 从冻结状态恢复到正常")
    void addBonusPoints_RecoverFromFrozen_ShouldUnfreeze() {
        testCreditScore.setScore(15);
        testCreditScore.setAccountFrozen(true);
        testCreditScore.setTradeRestricted(true);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.addBonusPoints(1L, 30, "大幅恢复", 1L);
        assertEquals(45, response.getScore());
        assertFalse(response.getAccountFrozen());
        assertFalse(response.getTradeRestricted());
    }

    @Test
    @DisplayName("加分 - 不存在时自动创建")
    void addBonusPoints_WhenNotExists_ShouldAutoCreate() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> { CreditScore cs = inv.getArgument(0); cs.setId(1L); return cs; });
        when(creditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.addBonusPoints(1L, 5, "奖励", 1L);
        assertEquals(100, response.getScore());
    }

    @Test
    @DisplayName("加分 - 负数分数抛出异常")
    void addBonusPoints_NegativePoints_ShouldThrow() {
        assertThrows(IllegalArgumentException.class,
            () -> creditScoreService.addBonusPoints(1L, -10, "无效", 1L));
    }

    @Test
    @DisplayName("加分 - null分数抛出异常")
    void addBonusPoints_NullPoints_ShouldThrow() {
        assertThrows(IllegalArgumentException.class,
            () -> creditScoreService.addBonusPoints(1L, null, "无效", 1L));
    }

    @Test
    @DisplayName("扣分 - 奖励类型不可用于扣分接口")
    void deductPoints_BonusEventType_ShouldThrow() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L))
            .thenReturn(Optional.of(testCreditScore));
        assertThrows(IllegalArgumentException.class,
            () -> creditScoreService.deductPoints(1L,
                CreditEventTypeEnum.BONUS_GOOD_BEHAVIOR.getCode(), "无效", 1L, null));
    }

    // ==================== evaluateLevel ====================

    @Test
    @DisplayName("评估等级 - 已存在时更新")
    void evaluateLevel_WhenExists_ShouldUpdate() {
        testCreditScore.setScore(50);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        when(creditScoreRepository.save(any(CreditScore.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditScoreResponse response = creditScoreService.evaluateLevel(1L);
        assertEquals("WARNING", response.getLevel());
    }

    @Test
    @DisplayName("评估等级 - 不存在时抛出异常")
    void evaluateLevel_WhenNotExists_ShouldThrow() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> creditScoreService.evaluateLevel(999L));
    }

    // ==================== getCreditHistory ====================

    @Test
    @DisplayName("查询信誉事件历史 - 带事件类型过滤")
    void getCreditHistory_WithEventType() {
        CreditEvent event = buildCreditEvent(1L, 1, -20, 100, 80, 1L);
        when(creditEventRepository.findByEnterpriseIdAndEventTypeAndDeletedFalse(eq(1L), eq(1), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        var response = creditScoreService.getCreditHistory(1L, 1, 1, 10);
        assertEquals(1, response.getContent().size());
    }

    @Test
    @DisplayName("查询信誉事件历史 - 不带事件类型过滤")
    void getCreditHistory_WithoutEventType() {
        CreditEvent event = buildCreditEvent(2L, 3, -10, 80, 70, 1L);
        when(creditEventRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        var response = creditScoreService.getCreditHistory(1L, null, 1, 10);
        assertEquals(1, response.getContent().size());
    }

    @Test
    @DisplayName("查询信誉事件历史 - 触发者为空显示系统")
    void getCreditHistory_NullTriggeredBy_ShowsSystem() {
        CreditEvent event = buildCreditEvent(1L, 1, -20, 100, 80, null);
        when(creditEventRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        var response = creditScoreService.getCreditHistory(1L, null, 1, 10);
        assertEquals("系统", response.getContent().get(0).getTriggeredByName());
    }

    @Test
    @DisplayName("查询信誉事件历史 - 无效事件类型显示未知")
    void getCreditHistory_InvalidEventType_ShowsUnknown() {
        CreditEvent event = buildCreditEvent(1L, 999, -5, 80, 75, 1L);
        when(creditEventRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        var response = creditScoreService.getCreditHistory(1L, null, 1, 10);
        assertEquals("未知", response.getContent().get(0).getEventTypeName());
    }

    // ==================== getRestrictedEnterprises ====================

    @Test
    @DisplayName("获取被限制交易的企业列表")
    void getRestrictedEnterprises_ShouldReturnList() {
        CreditScore restricted = CreditScore.builder().enterpriseId(2L).score(35).tradeRestricted(true).accountFrozen(false).build();
        restricted.setId(2L);
        when(creditScoreRepository.findByTradeRestrictedAndDeletedFalse(true)).thenReturn(List.of(restricted));
        List<CreditScoreResponse> result = creditScoreService.getRestrictedEnterprises();
        assertEquals(1, result.size());
        assertTrue(result.get(0).getTradeRestricted());
    }

    @Test
    @DisplayName("获取被限制交易的企业列表 - 空")
    void getRestrictedEnterprises_ShouldReturnEmpty() {
        when(creditScoreRepository.findByTradeRestrictedAndDeletedFalse(true)).thenReturn(Collections.emptyList());
        List<CreditScoreResponse> result = creditScoreService.getRestrictedEnterprises();
        assertTrue(result.isEmpty());
    }

    // ==================== getFrozenEnterprises ====================

    @Test
    @DisplayName("获取被冻结的企业列表")
    void getFrozenEnterprises_ShouldReturnList() {
        CreditScore frozen = CreditScore.builder().enterpriseId(3L).score(10).accountFrozen(true).tradeRestricted(true).build();
        frozen.setId(3L);
        when(creditScoreRepository.findByAccountFrozenAndDeletedFalse(true)).thenReturn(List.of(frozen));
        List<CreditScoreResponse> result = creditScoreService.getFrozenEnterprises();
        assertEquals(1, result.size());
        assertTrue(result.get(0).getAccountFrozen());
    }

    // ==================== checkTradePermission ====================

    @Test
    @DisplayName("检查交易权限 - 无记录时允许")
    void checkTradePermission_NoRecord_ShouldAllow() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        assertTrue(creditScoreService.checkTradePermission(1L));
    }

    @Test
    @DisplayName("检查交易权限 - 正常状态允许")
    void checkTradePermission_Normal_ShouldAllow() {
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        assertTrue(creditScoreService.checkTradePermission(1L));
    }

    @Test
    @DisplayName("检查交易权限 - 被限制交易时禁止")
    void checkTradePermission_Restricted_ShouldDeny() {
        testCreditScore.setTradeRestricted(true);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        assertFalse(creditScoreService.checkTradePermission(1L));
    }

    @Test
    @DisplayName("检查交易权限 - 被冻结时禁止")
    void checkTradePermission_Frozen_ShouldDeny() {
        testCreditScore.setAccountFrozen(true);
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));
        assertFalse(creditScoreService.checkTradePermission(1L));
    }

    // ==================== Enum tests ====================

    @Test
    @DisplayName("信誉事件类型枚举 - 验证事件类型和分值")
    void testCreditEventTypeEnum() {
        assertEquals(-10, CreditEventTypeEnum.MINOR_VIOLATION.getDefaultPoints());
        assertEquals(-5, CreditEventTypeEnum.LATE_SUBMISSION.getDefaultPoints());
        assertEquals(-20, CreditEventTypeEnum.DATA_FALSIFICATION.getDefaultPoints());
        assertEquals(5, CreditEventTypeEnum.BONUS_GOOD_BEHAVIOR.getDefaultPoints());
        assertEquals(-30, CreditEventTypeEnum.MAJOR_VIOLATION.getDefaultPoints());
    }

    @Test
    @DisplayName("fromCode - null返回null")
    void testFromCode_Null() {
        assertNull(CreditEventTypeEnum.fromCode(null));
    }

    @Test
    @DisplayName("fromCode - 无效code返回null")
    void testFromCode_Invalid() {
        assertNull(CreditEventTypeEnum.fromCode(999));
    }

    @Test
    @DisplayName("根据分数判定等级 - 边界值测试")
    void testFromScoreBoundary() {
        assertEquals(CreditLevelEnum.EXCELLENT, CreditLevelEnum.fromScore(100));
        assertEquals(CreditLevelEnum.EXCELLENT, CreditLevelEnum.fromScore(80));
        assertEquals(CreditLevelEnum.GOOD, CreditLevelEnum.fromScore(79));
        assertEquals(CreditLevelEnum.GOOD, CreditLevelEnum.fromScore(60));
        assertEquals(CreditLevelEnum.WARNING, CreditLevelEnum.fromScore(59));
        assertEquals(CreditLevelEnum.WARNING, CreditLevelEnum.fromScore(40));
        assertEquals(CreditLevelEnum.DANGER, CreditLevelEnum.fromScore(39));
        assertEquals(CreditLevelEnum.DANGER, CreditLevelEnum.fromScore(20));
        assertEquals(CreditLevelEnum.FROZEN, CreditLevelEnum.fromScore(19));
        assertEquals(CreditLevelEnum.FROZEN, CreditLevelEnum.fromScore(0));
        assertEquals(CreditLevelEnum.FROZEN, CreditLevelEnum.fromScore(null));
    }

    @Test
    @DisplayName("信誉等级枚举 - 验证等级数量")
    void testCreditLevelEnum() {
        assertEquals(5, CreditLevelEnum.values().length);
    }

    // ==================== getScoreByUserId ====================

    @Test
    @DisplayName("根据用户ID获取信誉分 - 用户不存在")
    void getScoreByUserId_UserNotFound_ShouldThrow() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
            () -> creditScoreService.getScoreByUserId(999L));
    }

    @Test
    @DisplayName("根据用户ID获取信誉分 - 用户未关联企业")
    void getScoreByUserId_EnterpriseNotFound_ShouldThrow() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
            () -> creditScoreService.getScoreByUserId(1L));
    }

    @Test
    @DisplayName("根据用户ID获取信誉分 - 正常返回")
    void getScoreByUserId_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(creditScoreRepository.findByEnterpriseIdAndDeletedFalse(1L)).thenReturn(Optional.of(testCreditScore));

        CreditScoreResponse response = creditScoreService.getScoreByUserId(1L);
        assertNotNull(response);
        assertEquals(100, response.getScore());
    }

    // ==================== getCreditHistoryByUserId ====================

    @Test
    @DisplayName("根据用户ID获取信誉历史 - 用户不存在")
    void getCreditHistoryByUserId_UserNotFound_ShouldThrow() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
            () -> creditScoreService.getCreditHistoryByUserId(999L, null, 1, 10));
    }

    @Test
    @DisplayName("根据用户ID获取信誉历史 - 用户未关联企业")
    void getCreditHistoryByUserId_EnterpriseNotFound_ShouldThrow() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
            () -> creditScoreService.getCreditHistoryByUserId(1L, null, 1, 10));
    }

    @Test
    @DisplayName("根据用户ID获取信誉历史 - 正常返回")
    void getCreditHistoryByUserId_Success() {
        CreditEvent event = buildCreditEvent(1L, 1, -20, 100, 80, 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(enterpriseRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(testEnterprise));
        when(creditEventRepository.findByEnterpriseIdAndDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        var response = creditScoreService.getCreditHistoryByUserId(1L, null, 1, 10);
        assertNotNull(response);
        assertEquals(1, response.getContent().size());
    }

    // ==================== getScoreRanking ====================

    @Test
    @DisplayName("获取信誉排名列表")
    void getScoreRanking_ShouldReturnPaged() {
        when(creditScoreRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testCreditScore)));

        var response = creditScoreService.getScoreRanking(1, 10);
        assertNotNull(response);
        assertEquals(1, response.getContent().size());
    }

    @Test
    @DisplayName("获取信誉排名列表 - 空")
    void getScoreRanking_ShouldReturnEmpty() {
        when(creditScoreRepository.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        var response = creditScoreService.getScoreRanking(1, 10);
        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
    }

    // ==================== Helper ====================

    private CreditEvent buildCreditEvent(Long id, int eventType, int points, int before, int after, Long triggeredBy) {
        CreditEvent event = CreditEvent.builder()
                .enterpriseId(1L).eventType(eventType).eventDescription("测试事件")
                .pointsChanged(points).scoreBefore(before).scoreAfter(after)
                .triggeredBy(triggeredBy).triggeredAt(LocalDateTime.now()).build();
        event.setId(id);
        return event;
    }
}
