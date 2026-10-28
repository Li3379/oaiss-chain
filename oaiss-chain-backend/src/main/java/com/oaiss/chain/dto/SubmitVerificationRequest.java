package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitVerificationRequest {

    @NotNull(message = "核证机构ID不能为空")
    private Long verifierId;
}
