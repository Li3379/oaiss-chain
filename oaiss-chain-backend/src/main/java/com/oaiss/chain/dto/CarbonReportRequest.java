package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


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

    // ==================== 配额发放依据（组件 1） ====================
    //
    // 这五个字段前端自 35f4d28 起就在发，本 DTO 直到 2026-11-02 复核才补上。
    // 在此之前它们被 Jackson 静默丢弃（Spring Boot 默认 FAIL_ON_UNKNOWN_PROPERTIES=false），
    // 请求照样返回 200，库里的 output_quantity / unit_type / period_year 永远 NULL。
    // 症状要到打开 app.quota.allocation.enabled 才显形：每一份报告审核都因
    // 「发电量缺失」失败——没有任何报告能通过。
    //
    // 教训：给 DTO 加字段时，不能只看「前端在校验」，要看后端是否真的接收。

    /**
     * 核算年份。
     *
     * <p>与 {@link #accountingPeriod} 并存而非二选一：后者是无格式约束的自由文本，
     * 存量里有 {@code 2024-01-15} 这类形态，无法承载「同企业同周期唯一」。
     */
    private Integer periodYear;

    /**
     * 核算季度 1-4；年度报告不传（null）
     */
    private Integer periodQuarter;

    /**
     * 产量（发电量）。配额公式 {@code 配额 = 产量 × 基准值} 的乘数因子。
     */
    @Positive(message = "发电量必须为正数")
    private BigDecimal outputQuantity;

    /**
     * 产量单位。本轮固定 {@code MWh}，缺省时后端补默认值。
     */
    private String outputUnit;

    /**
     * 机组类别 I/II/III/IV，决定查哪一行行业基准值。
     */
    private String unitType;
}
