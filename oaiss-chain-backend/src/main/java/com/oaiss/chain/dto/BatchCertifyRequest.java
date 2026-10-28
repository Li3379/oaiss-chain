package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量认证/上链请求DTO
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCertifyRequest {

    /**
     * 待认证报告ID列表
     */
    @NotEmpty(message = "报告ID列表不能为空")
    private List<Long> reportIds;

    /**
     * 认证结果（5-认证通过上链, 4-驳回认证）
     */
    @NotNull(message = "认证结果不能为空")
    private Integer reviewResult;

    /**
     * 审核/认证批注
     */
    private String reviewComment;
}
