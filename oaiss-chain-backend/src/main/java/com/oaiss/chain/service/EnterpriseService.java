package com.oaiss.chain.service;

import com.oaiss.chain.dto.EnterpriseSearchResponse;
import com.oaiss.chain.entity.CreditScore;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.repository.CreditScoreRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业服务
 * 提供企业信息查询、配额管理等功能
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseService {

    private final EnterpriseRepository enterpriseRepository;
    private final CreditScoreRepository creditScoreRepository;

    /** 搜索每页上限，防止 size 被用来拖库 */
    private static final int MAX_SEARCH_PAGE_SIZE = 100;

    /** 未建立信誉记录时的默认分，与 CreditScoreService.getScore 的 orElseGet 语义一致 */
    private static final int DEFAULT_CREDIT_SCORE = 100;

    /**
     * 获取当前登录企业的信息
     */
    @Transactional(readOnly = true)
    public Enterprise getEnterpriseInfo(Long userId) {
        return enterpriseRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("企业信息不存在"));
    }

    /**
     * 获取企业碳配额信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getQuotaInfo(Long userId) {
        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("企业信息不存在"));

        Map<String, Object> quotaInfo = new HashMap<>();
        quotaInfo.put("totalQuota", enterprise.getCarbonQuota());
        quotaInfo.put("usedQuota", enterprise.getCarbonUsed());
        quotaInfo.put("tradableQuota", enterprise.getCarbonTradable());
        quotaInfo.put("enterpriseName", enterprise.getEnterpriseName());

        BigDecimal remaining = enterprise.getCarbonQuota().subtract(enterprise.getCarbonUsed());
        quotaInfo.put("remainingQuota", remaining);

        BigDecimal usageRate = BigDecimal.ZERO;
        if (enterprise.getCarbonQuota().compareTo(BigDecimal.ZERO) > 0) {
            usageRate = enterprise.getCarbonUsed()
                    .multiply(new BigDecimal("100"))
                    .divide(enterprise.getCarbonQuota(), 2, BigDecimal.ROUND_HALF_UP);
        }
        quotaInfo.put("usageRate", usageRate);

        return quotaInfo;
    }

    /**
     * 更新企业联系方式
     */
    @Transactional
    public void updateContact(Long userId, String contactPerson, String contactPhone) {
        Enterprise enterprise = enterpriseRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new RuntimeException("企业信息不存在"));

        if (contactPerson != null && !contactPerson.trim().isEmpty()) {
            enterprise.setContactPerson(contactPerson);
        }
        if (contactPhone != null && !contactPhone.trim().isEmpty()) {
            enterprise.setContactPhone(contactPhone);
        }

        enterpriseRepository.save(enterprise);
        log.info("企业联系方式更新成功: userId={}", userId);
    }

    /**
     * 根据ID获取企业信息
     */
    @Transactional(readOnly = true)
    public Enterprise getEnterpriseById(Long enterpriseId) {
        return enterpriseRepository.findById(enterpriseId)
                .filter(e -> !e.getDeleted())
                .orElseThrow(() -> new RuntimeException("企业不存在"));
    }

    /**
     * 交易对手搜索。
     *
     * <p>供 P2P 建单时选择对手方使用，替代前端原先「取信誉榜前 50 名再逐个查详情」的
     * N+1 实现（该实现在企业超过 50 家时静默截断，且把内部 userId 拼进下拉文案）。
     *
     * <p>三条约束：
     * <ul>
     *   <li>关键词空白直接返回空页——不做全量兜底，避免把企业名录一次性暴露</li>
     *   <li>排除当前登录企业自身（后端 createP2PTrade 也会拒绝自成交，此处提前过滤）</li>
     *   <li>返回 {@link EnterpriseSearchResponse} 而非实体，字段白名单见该 DTO 注释</li>
     * </ul>
     *
     * @param keyword         关键词（企业名或信用代码片段）
     * @param currentUserId   当前登录用户ID
     * @param currentEnterpriseId 当前用户关联的企业ID；管理员为 null
     */
    @Transactional(readOnly = true)
    public Page<EnterpriseSearchResponse> searchForCounterparty(
            String keyword, Long currentEnterpriseId, Integer page, Integer size) {

        String kw = keyword == null ? null : keyword.trim();
        if (kw == null || kw.isEmpty()) {
            // 空关键词不返回全量：企业名录不应被一次拉走
            return Page.empty();
        }

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, MAX_SEARCH_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by("enterpriseName").ascending());

        Page<Enterprise> enterprises = enterpriseRepository.searchForCounterparty(
                kw, currentEnterpriseId, pageable);
        if (enterprises.isEmpty()) {
            return Page.empty(pageable);
        }

        // 批量取信誉分，避免逐个查询；缺失的按默认分补齐
        java.util.Map<Long, Integer> scoreByEnterpriseId = new java.util.HashMap<>();
        creditScoreRepository
                .findByEnterpriseIdInAndDeletedFalse(
                        enterprises.getContent().stream().map(Enterprise::getId).toList())
                .forEach(cs -> scoreByEnterpriseId.put(cs.getEnterpriseId(), cs.getScore()));

        return enterprises.map(e -> EnterpriseSearchResponse.builder()
                .enterpriseId(e.getId())
                .userId(e.getUserId())
                .enterpriseName(e.getEnterpriseName())
                .creditCode(e.getCreditCode())
                .creditScore(scoreByEnterpriseId.getOrDefault(e.getId(), DEFAULT_CREDIT_SCORE))
                .build());
    }
}
