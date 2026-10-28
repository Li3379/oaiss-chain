package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 信誉扣分请求DTO
 *
 * @author OAISS Team
 */
@Data
public class CreditDeductionRequest {

    /**
     * 企业ID
     */
    @NotNull(message = "企业ID不能为空")
    private Long enterpriseId;

    /**
     * 事件类型（1-数据造假, 2-迟交报告, 3-轻微违规, 4-严重违规）
     */
    @NotNull(message = "事件类型不能为空")
    private Integer eventType;

    /**
     * 事件描述
     */
    @Size(max = 500, message = "描述不能超过500字符")
    private String description;

    /**
     * 关联碳报告ID（可选）
     */
    private Long relatedReportId;
}
