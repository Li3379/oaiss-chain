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
public class CaptchaSendRequest {

    @NotBlank(message = "手机号/邮箱不能为空")
    private String target;

    @Builder.Default
    private Integer type = 1;
}
