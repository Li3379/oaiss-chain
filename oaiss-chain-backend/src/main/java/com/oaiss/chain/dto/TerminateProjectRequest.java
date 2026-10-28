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
public class TerminateProjectRequest {

    @NotBlank(message = "终止原因不能为空")
    private String reason;
}
