package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 碳排放评级请求
 *
 * @author OAISS Team
 */
@Data
public class EmissionRatingRequest {

    @NotNull(message = "企业ID不能为空")
    private Long enterpriseId;

    @NotBlank(message = "评级年度不能为空")
    private String year;

    @NotNull(message = "碳排放总量不能为空")
    private BigDecimal totalEmission;

    /**
     * 企业年产值（万元，用于计算排放强度）
     */
    private BigDecimal revenue;

    private Long ratedBy;
}
