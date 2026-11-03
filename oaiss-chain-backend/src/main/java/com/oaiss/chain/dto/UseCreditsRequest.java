package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UseCreditsRequest {

    @NotNull(message = "使用数量不能为空")
    @Positive(message = "使用数量必须大于0")
    private BigDecimal amount;

    /**
     * 可选：要抵扣超排缺口的碳报告ID。
     *
     * <p>不传表示「仅消耗碳信用，不关联任何报告」——保留既有调用语义。
     * 传了则抵扣会写入该报告的 {@code shortfallOffset}，
     * 从而「这份报告还差多少吨」可算出来。
     */
    private Long relatedReportId;
}
