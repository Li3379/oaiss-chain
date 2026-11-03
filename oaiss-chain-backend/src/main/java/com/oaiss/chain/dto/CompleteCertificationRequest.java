package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完成认证请求体。
 *
 * <p>证书编号由服务端生成（格式 {@code CNE-{年份}-{六位序号}}），
 * 本 DTO 不再由 {@code POST /{id}/certify} 接收，仅为兼容保留
 * （{@code DtoAllClassesTest} 仍引用该类）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteCertificationRequest {

    private String certNo;
}
