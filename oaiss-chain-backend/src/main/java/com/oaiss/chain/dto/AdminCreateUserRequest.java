package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员创建用户请求 DTO
 * userType 允许 1-企业 / 2-审核员 / 3-第三方监管；4-管理员禁止经本端点创建
 */
public class AdminCreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度3-50个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度6-100个字符")
    private String password;

    @NotNull(message = "用户类型不能为空")
    private Integer userType;

    private String phone;
    private String email;
    private String realName;

    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
    public Integer getUserType() { return userType; }
    public void setUserType(Integer v) { this.userType = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getRealName() { return realName; }
    public void setRealName(String v) { this.realName = v; }
}
