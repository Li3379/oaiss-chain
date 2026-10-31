package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 碳报告链上快照（v2）。
 *
 * <p>存在的理由：v1 上链只传 {@code emissionData} 原文，链码的 {@code EnterpriseID}
 * 字段从未赋值——链上无法回答「这份报告属于哪家企业」，存证因此不能独立验证主体。</p>
 *
 * <p>v2 补齐主体、周期、总量、三范围与签名，使链上记录可独立回答：
 * 谁、什么时候、报了多少、内容是否被改过。</p>
 */
@Data
@Builder
public class CarbonReportSnapshot {

    /** 报告 ID（链上主键） */
    private Long reportId;

    /** 所属企业 ID —— v1 缺失的关键字段 */
    private Long enterpriseId;

    /** 核算周期，如 2026-Q1 */
    private String accountingPeriod;

    /** 总排放量 (tCO2e) */
    private String totalEmission;

    /** 范围一排放量 */
    private String scope1Emission;

    /** 范围二排放量 */
    private String scope2Emission;

    /** 范围三排放量 */
    private String scope3Emission;

    /** 企业数字签名（RSA，覆盖 emissionData 原文） */
    private String signature;

    /** 排放明细原文（JSON） */
    private String data;

    /** 报告编号（人可读，便于链上核对） */
    private String reportNo;
}