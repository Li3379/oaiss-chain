package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交易对手搜索结果项。
 *
 * <p><b>字段白名单是刻意收窄的</b>：本 DTO 只暴露「选择交易对手所必需」的信息，
 * 不包含 {@code address / contactPerson / contactPhone / licenseUrl /
 * carbonQuota / carbonTradable} 等企业与经营敏感字段。
 *
 * <p>为什么不直接用 {@code Enterprise} 实体：项目里 {@code GET /enterprise/{id}}
 * 直出实体属于已知的字段泄露模式，本端点不得复制该做法。测试用**反向断言**
 * 固定这一约束（断言敏感字段不存在），而非仅断言必需字段存在。
 *
 * <p>{@code userId} 必须返回：后端 {@code POST /trade/p2p} 以 userId 定位买方。
 * 但**前端不得把它渲染给用户看**——展示层只用企业名 + 信用代码后 6 位。
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseSearchResponse {

    /** 企业ID */
    private Long enterpriseId;

    /** 关联用户ID（P2P 建单必需；不可展示给用户） */
    private Long userId;

    /** 企业名称 */
    private String enterpriseName;

    /** 统一社会信用代码 */
    private String creditCode;

    /**
     * 信誉分。缺 {@code credit_score} 记录时回退 100
     * （与 {@code CreditScoreService.getScore} 的 orElseGet 语义一致），
     * 保证下游「按信誉筛选/排序」不会因缺失而失序。
     */
    private Integer creditScore;
}