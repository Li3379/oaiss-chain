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
}
