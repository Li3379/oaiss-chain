package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 碳报告提交请求DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarbonReportRequest {

    /**
     * 核算周期
     */
    @NotBlank(message = "核算周期不能为空")
    @Size(max = 20, message = "核算周期格式不正确")
    private String accountingPeriod;

    /**
     * 报告标题
     */
    @NotBlank(message = "报告标题不能为空")
    @Size(max = 200, message = "标题不能超过200字符")
    @Pattern(regexp = "^[^<>]*$", message = "标题不能包含特殊字符<>")
    private String title;

    /**
     * 报告类型（1-季度, 2-年度）
     */
    @NotNull(message = "报告类型不能为空")
    private Integer reportType;

    /**
     * 碳排放数据（JSON格式）
     */
    @NotBlank(message = "碳排放数据不能为空")
    private String emissionData;

    /**
     * 核算方法
     */
    private String calculationMethod;

    /**
     * 附件URL（JSON数组）
     */
    private String attachments;

    /**
     * RSA签名数据
     */
    private String signatureData;
}
