package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 碳中和项目创建/更新请求
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarbonNeutralProjectRequest {

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称不能超过200字符")
    private String projectName;

    /**
     * 项目类型（1-碳汇, 2-CCUS, 3-可再生能源, 4-节能改造, 5-其他）
     */
    @NotNull(message = "项目类型不能为空")
    private Integer projectType;

    /**
     * 项目描述
     */
    @Size(max = 2000, message = "项目描述不能超过2000字符")
    private String description;

    /**
     * 项目地点
     */
    @Size(max = 200, message = "项目地点不能超过200字符")
    private String location;

    /**
     * 预计减排量（吨CO2当量/年）
     */
    private BigDecimal expectedReduction;

    /**
     * 项目投资金额（元）
     */
    private BigDecimal investmentAmount;

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

    /**
     * 方法学
     */
    private String methodology;

    /**
     * 核算周期（年）
     */
    private Integer accountingPeriod;

    /**
     * 申请资料（JSON）
     */
    private String applicationData;

    /**
     * 附件文件列表（JSON）
     */
    private String attachments;
}
