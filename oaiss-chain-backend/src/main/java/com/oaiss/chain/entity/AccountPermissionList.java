package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 账户权限列表
 * 定义各角色的功能权限
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "account_permission_list")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPermissionList extends BaseEntity {

    /**
     * 权限名称
     */
    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    /**
     * 权限代码
     */
    @Column(name = "permission_code", nullable = false, unique = true, length = 100)
    private String permissionCode;

    /**
     * 权限描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 所属模块
     */
    @Column(name = "module", length = 50)
    private String module;

    /**
     * 排序号
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
