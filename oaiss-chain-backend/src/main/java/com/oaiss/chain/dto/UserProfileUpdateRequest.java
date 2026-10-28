package com.oaiss.chain.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 用户资料更新请求
 *
 * @author OAISS Team
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileUpdateRequest {

    @Size(max = 50, message = "真实姓名不能超过50字符")
    private String realName;

    @Size(max = 20, message = "手机号不能超过20字符")
    private String phone;

    @Size(max = 100, message = "邮箱不能超过100字符")
    private String email;

    @Size(max = 500, message = "头像URL不能超过500字符")
    private String avatar;

    @Size(max = 200, message = "公司名称不能超过200字符")
    private String company;

    @Size(max = 500, message = "地址不能超过500字符")
    private String address;
}
