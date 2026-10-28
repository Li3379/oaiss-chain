package com.oaiss.chain.service;

import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.repository.EnterpriseRepository;
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
}
