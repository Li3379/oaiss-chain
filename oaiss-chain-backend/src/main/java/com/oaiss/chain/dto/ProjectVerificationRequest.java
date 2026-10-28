package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 项目减排量核证请求
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectVerificationRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /**
     * 核证减排量（吨CO2当量）
     */
    @NotNull(message = "核证减排量不能为空")
    @Positive(message = "核证减排量必须大于0")
    private BigDecimal verifiedReduction;

    /**
     * 核证报告
     */
    private String verificationReport;

    /**
     * 监测数据
     */
    private String monitoringData;

    /**
     * 备注
     */
    private String remark;
}
