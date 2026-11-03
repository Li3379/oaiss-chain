package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 碳交易请求DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeRequest {

    /**
     * 交易类型（1-拍卖, 2-P2P）
     */
    @NotNull(message = "交易类型不能为空")
    private Integer tradeType;

    /**
     * 买方ID（P2P交易时必填）
     */
    private Long buyerId;

    /**
     * 卖方ID（P2P交易时必填）
     */
    private Long sellerId;

    /**
     * 数量。
     *
     * <p>语义随交易类型变化：
     * <ul>
     *   <li><b>P2P</b>：碳币枚数（1 枚 = 1 吨排放权）</li>
     *   <li><b>拍卖</b>：配额吨数</li>
     * </ul>
     */
    @NotNull(message = "交易数量不能为空")
    @Positive(message = "交易数量必须为正数")
    private BigDecimal quantity;

    /**
     * 单价（碳币/吨）。
     *
     * <p><b>P2P 已不使用</b>：P2P 改为「碳币换碳币」，1 枚换 1 枚，无价格维度。
     * 后端在 P2P 路径忽略此字段（按 1 处理），前端也不再展示单价输入。
     * 保留字段是为了不改 `TradeRequest` 契约——拍卖仍需单价，
     * 且 60 个既有测试依赖它。
     */
    @Positive(message = "单价必须为正数")
    private BigDecimal unitPrice;

    /**
     * 关联碳报告ID（可选）
     */
    private Long reportId;

    /**
     * 交易备注
     */
    private String remark;
}
