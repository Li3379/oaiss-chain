package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminConfigDTO {
    private String systemName;
    private Integer maxUploadSize;
    private Integer sessionTimeout;
    private Boolean enableCaptcha;
    private Boolean enableBlockChain;
    private String version;
}
