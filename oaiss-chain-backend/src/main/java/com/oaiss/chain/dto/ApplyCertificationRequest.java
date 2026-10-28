package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyCertificationRequest {

    @NotBlank(message = "认证机构不能为空")
    private String certOrg;
}
