package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 行业碳排放基准值。
 *
 * <p>用于配额发放：{@code 配额 = 产量 × 基准值}。这是把「排放强度低于行业基准」
 * 转化为「有可交易盈余」的依据。
 *
 * <p>数据来源是生态环境部年度配额分配方案。本轮只入库发电行业（POWER），
 * 因为只有发电行业有公开的固定基准值；钢铁/水泥/铝冶炼用的是「平衡值」，
 * 官方明确需通过全国碳市场管理平台按年度查询，无法预置。
 *
 * <p>两个字段命名有讲究：
 * <ul>
 *   <li>{@code benchmark_year} 而非 {@code year}——{@code year} 是 H2 保留字，
 *       实测建表会报 {@code Syntax error ... expected "identifier"}</li>
 *   <li>{@code unit_type} 非空且默认 {@code 'NA'}——MySQL 唯一索引把 NULL
 *       视为互不相等，若允许 NULL 则同一行业/年度的记录可重复插入</li>
 * </ul>
 *
 * @author OAISS Team
 */
@Entity
@Table(name = "industry_benchmark")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndustryBenchmark extends BaseEntity {

    /** 发电行业代码 */
    public static final String INDUSTRY_POWER = "POWER";

    /** 发电基准值类别 */
    public static final String KIND_GENERATION = "GENERATION_BENCHMARK";

    /** 非分类型行业的 unit_type 占位值 */
    public static final String UNIT_TYPE_NA = "NA";

    /**
     * 行业代码。本轮固定 {@link #INDUSTRY_POWER}
     */
    @Column(name = "industry_code", nullable = false, length = 32)
    private String industryCode;

    /**
     * 机组类别：I / II / III / IV；非发电行业为 {@link #UNIT_TYPE_NA}
     */
    @Column(name = "unit_type", nullable = false, length = 32)
    @Builder.Default
    private String unitType = UNIT_TYPE_NA;

    /**
     * 基准年度（列名避开 H2 保留字 year）
     */
    @Column(name = "benchmark_year", nullable = false)
    private Integer benchmarkYear;

    /**
     * 基准值类别，本轮固定 {@link #KIND_GENERATION}
     */
    @Column(name = "benchmark_kind", nullable = false, length = 32)
    private String benchmarkKind;

    /**
     * 基准值。发电行业单位 tCO2/MWh，精度 6 位以容纳官方原始精度
     */
    @Column(name = "benchmark_value", nullable = false, precision = 15, scale = 6)
    private BigDecimal benchmarkValue;

    /**
     * 基准值单位，本轮固定 tCO2/MWh
     */
    @Column(name = "benchmark_unit", nullable = false, length = 20)
    private String benchmarkUnit;
}