package com.oaiss.chain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求DTO
 * 
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度3-50个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度6-100个字符")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 用户类型（1-企业, 2-审核员, 3-第三方监管）
     */
    @NotNull(message = "用户类型不能为空")
    private Integer userType;

    /**
     * 企业信用代码（企业用户必填）
     */
    private String creditCode;

    /**
     * 企业名称（企业用户必填）
     */
    private String enterpriseName;

    /**
     * 审核员资质编号（审核员必填）
     */
    private String qualificationNo;

    /**
     * 机构编码（第三方机构必填）
     */
    private String orgCode;

    /**
     * 机构名称（第三方机构必填）
     */
    private String orgName;
}
