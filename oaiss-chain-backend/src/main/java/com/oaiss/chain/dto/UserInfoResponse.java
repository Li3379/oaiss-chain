package com.oaiss.chain.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户信息响应
 *
 * @author OAISS Team
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoResponse {

    private Long userId;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private String company;
    private String address;
    private Integer userType;
    private String userTypeDesc;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    /**
     * 累计在线停留时长（秒）
     */
    private Long totalStaySeconds;
}
