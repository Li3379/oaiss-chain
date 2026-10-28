package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量认证/上链响应DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCertifyResponse {

    /**
     * 总处理数
     */
    private int totalCount;

    /**
     * 成功数
     */
    private int successCount;

    /**
     * 失败数
     */
    private int failureCount;

    /**
     * 成功上链/认证的报告ID
     */
    private List<Long> successReportIds;

    /**
     * 错误信息列表
     */
    private List<String> errorMessages;
}
