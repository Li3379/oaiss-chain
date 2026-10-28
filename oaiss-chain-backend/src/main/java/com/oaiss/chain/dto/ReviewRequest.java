package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核请求DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    /**
     * 报告ID
     */
    @NotNull(message = "报告ID不能为空")
    private Long reportId;

    /**
     * 审核结果（3-通过, 4-拒绝）
     */
    @NotNull(message = "审核结果不能为空")
    private Integer reviewResult;

    /**
     * 审核意见
     */
    private String reviewComment;
}
