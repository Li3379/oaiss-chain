package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 搜索查询响应DTO
 *
 * @author OAISS Team
 */
public class SearchResponse {

    /**
     * 碳报告摘要
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CarbonReportSummary {
        private Long id;
        private String reportNo;
        private Long enterpriseId;
        private String title;
        private String accountingPeriod;
        private BigDecimal totalEmission;
        private Integer status;
        private LocalDateTime createdAt;
    }

    /**
     * 交易摘要
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeSummary {
        private Long id;
        private String tradeNo;
        private Integer tradeType;
        private Long sellerId;
        private Long buyerId;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalAmount;
        private Integer status;
        private LocalDateTime createdAt;
    }

    /**
     * 市场概览数据（官方首页与大盘总览宏观指标，纯宏观统计，绝无多租户机密数据）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketOverview {
        /** 已入驻企业数 */
        private Long totalEnterprises;
        /** 核证/备案碳报告数 */
        private Long totalCarbonReports;
        /** 链上/撮合交易总数 */
        private Long totalTransactions;
        /** 碳交易流转总量（吨 CO2 当量） */
        private BigDecimal totalTradingVolume;
        /** 碳交易累计成交额（元） */
        private BigDecimal totalTradeAmount;
        /** 累计核证配额/减排总量（吨 CO2 当量） */
        private BigDecimal totalEmissionQuota;
        /** 活跃区块链节点数 */
        private Integer activeNodes;
        /** 全网节点数量（兼容大盘展示） */
        private Long nodeCount;
        /** 网络运行状态（ONLINE / NORMAL） */
        private String networkStatus;
        /** 分布式高可用率（如 "99.96%" / "99.98%"） */
        private String networkAvailability;
        /** 分布式可用率别名 */
        private String distributedAvailability;
        /** 集中式合规评级（如 "A+"） */
        private String complianceGrade;
        /** 平均延迟（毫秒） */
        private Integer avgLatencyMs;
        /** 平均延迟别名（毫秒） */
        private Integer averageLatencyMs;
        /** 最大延时/P99延迟（毫秒） */
        private Integer maxLatencyMs;
        /** 联盟链连通状态 */
        private Boolean chainOnline;
        /** 联盟链运行模式（FABRIC / MOCK） */
        private String chainMode;
        /** 联盟链通道名称 */
        private String channelName;
        /** 最新区块高度 */
        private Long blockHeight;
        /** 存证智能合约名称 */
        private String chaincode;
        /** 采样时间戳 */
        private LocalDateTime queryTime;
    }
}
