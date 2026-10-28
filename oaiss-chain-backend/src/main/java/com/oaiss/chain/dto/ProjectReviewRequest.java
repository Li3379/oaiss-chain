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
public class ProjectReviewRequest {

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    private String comment;
}
