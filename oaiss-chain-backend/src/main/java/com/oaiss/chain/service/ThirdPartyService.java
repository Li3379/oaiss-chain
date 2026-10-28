package com.oaiss.chain.service;

import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.ThirdPartyOrg;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.ThirdPartyOrgRepository;
import com.oaiss.chain.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方监管服务
 * 提供第三方监管机构的数据查询能力
 *
 * @author OAISS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyService {

    private final ThirdPartyOrgRepository thirdPartyOrgRepository;
    private final CarbonReportRepository carbonReportRepository;
    private final EnterpriseRepository enterpriseRepository;

    /**
     * 获取当前第三方机构信息
     */
    @Transactional(readOnly = true)
    public ThirdPartyOrg getCurrentOrg(JwtUserDetails currentUser) {
        return thirdPartyOrgRepository.findByUserIdAndDeletedFalse(currentUser.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "第三方机构信息不存在"));
    }

    /**
     * 查询企业碳报告（监管视角）
     * 根据权限级别控制可见数据
     */
    public ApiResponse<Page<CarbonReport>> queryCarbonReports(
            JwtUserDetails currentUser,
            Long enterpriseId, Integer status, String keyword,
            Integer page, Integer size) {

        ThirdPartyOrg org = getCurrentOrg(currentUser);

        if (org.getStatus() == 0) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "机构已被禁用");
        }

        Pageable pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<CarbonReport> reports;
        if ((enterpriseId != null || status != null || keyword != null)) {
            reports = carbonReportRepository.search(enterpriseId, status, keyword, pageable);
        } else {
            reports = carbonReportRepository.findByDeletedFalse(pageable);
        }

        // 基于权限级别过滤数据（简化处理，实际应根据accessLevel做字段级控制）
        log.info("ThirdPartyOrg {} queried carbon reports (accessLevel={})",
                org.getOrgName(), org.getAccessLevel());

        // 填充企业名称
        reports.forEach(report ->
                enterpriseRepository.findById(report.getEnterpriseId())
                        .ifPresent(ent -> report.setEnterpriseName(ent.getEnterpriseName()))
        );

        return ApiResponse.success(reports);
    }

    /**
     * 获取统计数据概览（监管视角）
     */
    public ApiResponse<Map<String, Object>> getStatistics(JwtUserDetails currentUser) {
        ThirdPartyOrg org = getCurrentOrg(currentUser);

        Map<String, Object> stats = new HashMap<>();
        stats.put("orgName", org.getOrgName());
        stats.put("accessLevel", org.getAccessLevel());

        // 全局报告统计
        long totalReports = carbonReportRepository.findByDeletedFalse(
                PageRequest.of(0, 1)).getTotalElements();
        long pendingReports = carbonReportRepository.findByStatusAndDeletedFalse(
                1, PageRequest.of(0, 1)).getTotalElements();
        long approvedReports = carbonReportRepository.findByStatusInAndDeletedFalse(
                java.util.Arrays.asList(3, 5), PageRequest.of(0, 1)).getTotalElements();
        long rejectedReports = carbonReportRepository.findByStatusAndDeletedFalse(
                4, PageRequest.of(0, 1)).getTotalElements();

        stats.put("totalReports", totalReports);
        stats.put("pendingReports", pendingReports);
        stats.put("approvedReports", approvedReports);
        stats.put("rejectedReports", rejectedReports);

        return ApiResponse.success(stats);
    }

    /**
     * 更新机构联系方式
     */
    @Transactional
    public ApiResponse<Void> updateContact(JwtUserDetails currentUser,
            String contactPerson, String contactPhone) {

        ThirdPartyOrg org = getCurrentOrg(currentUser);

        if (contactPerson != null) {
            org.setContactPerson(contactPerson);
        }
        if (contactPhone != null) {
            org.setContactPhone(contactPhone);
        }

        thirdPartyOrgRepository.save(org);
        log.info("ThirdPartyOrg contact updated: {}", org.getOrgName());

        return ApiResponse.success();
    }
}
