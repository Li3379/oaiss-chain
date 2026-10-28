package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 用户类型列表
 * 定义系统中所有用户类型
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "user_type_list")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTypeList extends BaseEntity {

    /**
     * 类型编码（ENTERPRISE, REVIEWER, THIRD_PARTY, ADMIN）
     */
    @Column(name = "type_code", nullable = false, unique = true, length = 50)
    private String typeCode;

    /**
     * 类型名称
     */
    @Column(name = "type_name", nullable = false, length = 50)
    private String typeName;

    /**
     * 类型描述
     */
    @Column(name = "description", length = 200)
    private String description;

    /**
     * 默认角色（对应Spring Security角色）
     */
    @Column(name = "default_role", length = 50)
    private String defaultRole;
}
