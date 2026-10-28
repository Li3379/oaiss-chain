package com.oaiss.chain.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

/**
 * 用户表
 * 存储系统所有用户的认证信息
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "`user`")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    /**
     * 用户名（登录账号）
     */
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /**
     * 密码（BCrypt加密）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /**
     * 手机号
     */
    @Column(name = "phone", unique = true, length = 20)
    private String phone;

    /**
     * 邮箱
     */
    @Column(name = "email", length = 100)
    private String email;

    /**
     * 真实姓名
     */
    @Column(name = "real_name", length = 50)
    private String realName;

    /**
     * 用户类型（1-企业, 2-审核员, 3-第三方监管, 4-管理员）
     */
    @Column(name = "user_type", nullable = false)
    private Integer userType;

    /**
     * 账号状态（0-禁用, 1-启用）
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 1;

    /**
     * 允许登录的IP地址（JSON数组，为空表示不限制）
     */
    @Column(name = "allowed_ips", columnDefinition = "TEXT")
    private String allowedIps;

    /**
     * 最后登录时间
     */
    @Column(name = "last_login_time")
    private java.time.LocalDateTime lastLoginAt;

    /**
     * 最后登录IP
     */
    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;

    /**
     * 用户头像
     */
    @Column(name = "avatar", length = 500)
    private String avatar;

    /**
     * 公司名称
     */
    @Column(name = "company", length = 200)
    private String company;

    /**
     * 地址
     */
    @Column(name = "address", length = 500)
    private String address;
}
