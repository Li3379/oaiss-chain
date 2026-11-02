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

    /** 脱硫过程排放量（发电企业） */
    private String desulfEmission;

    /** 输配电线损排放量（电网企业） */
    private String transmissionLossEmission;

    /** SF6 绝缘设备泄漏排放量（电网企业） */
    private String sf6Emission;

    /** 企业数字签名（RSA，覆盖 emissionData 原文） */
    private String signature;

    /** 排放明细原文（JSON） */
    private String data;

    /** 报告编号（人可读，便于链上核对） */
    private String reportNo;

    // ==================== 配额发放依据（组件 1，DB 侧留痕）====================
    //
    // ⚠️ 本轮**不写入链上**。链上写入是位置参数（FabricBlockchainService 传 14 个
    // 定长参数，chaincode.go 的 CreateCarbonReportV2 签名固定），只加 DTO 字段对
    // 链上零影响。要让链上真的记录产量，必须同时改链码参数 + 客户端 + schemaVersion
    // 升级——那是独立的一轮工作，本轮明确不做。
    //
    // 所以这三个字段的实际作用是：报告快照落库时留下「当时按什么产量、哪类机组、
    // 算出多少配额」的可回溯依据。缺了它，事后无法解释某份报告为什么发了这么多吨。

    /** 产量（发电量，MWh） */
    private String outputQuantity;

    /** 产量单位 */
    private String outputUnit;

    /** 机组类别 I/II/III/IV */
    private String unitType;
}