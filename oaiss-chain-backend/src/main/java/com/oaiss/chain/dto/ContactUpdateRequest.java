package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactUpdateRequest {
    @NotBlank(message = "联系人不能为空")
    @Size(max = 100, message = "联系人姓名不能超过100个字符")
    private String contactPerson;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话不能超过20个字符")
    private String contactPhone;
}
