package com.oaiss.chain.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * JWT用户详情
 * JWT User Details
 *
 * @author OAISS Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 用户类型
     */
    private Integer userType;

    /**
     * 角色列表
     */
    private List<String> roles;

    /**
     * 企业ID（仅企业用户有值）
     */
    private Long enterpriseId;

    /**
     * 账号是否启用
     */
    private Boolean enabled;

    /**
     * 账号是否未过期
     */
    private Boolean accountNonExpired;

    /**
     * 账号是否未锁定
     */
    private Boolean accountNonLocked;

    /**
     * 凭证是否未过期
     */
    private Boolean credentialsNonExpired;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        // Spring Security requires ROLE_ prefix for hasRole() checks
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired != null ? accountNonExpired : true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked != null ? accountNonLocked : true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired != null ? credentialsNonExpired : true;
    }

    @Override
    public boolean isEnabled() {
        return enabled != null ? enabled : true;
    }

    /**
     * 判断是否拥有指定角色
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * 判断是否为管理员
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * 判断是否为企业用户
     */
    public boolean isEnterprise() {
        return hasRole("ENTERPRISE");
    }

    /**
     * 判断是否为审核员
     */
    public boolean isReviewer() {
        return hasRole("REVIEWER");
    }

}
