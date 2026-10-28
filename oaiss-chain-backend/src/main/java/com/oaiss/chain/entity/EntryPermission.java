package com.oaiss.chain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 入口权限表
 * 控制各角色对API入口的访问权限
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "entry_permission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntryPermission extends BaseEntity {

    /**
     * 角色类型（关联用户类型）
     */
    @Column(name = "user_type", nullable = false)
    private Integer userType;

    /**
     * API路径
     */
    @Column(name = "api_path", nullable = false, length = 255)
    private String apiPath;

    /**
     * HTTP方法（GET/POST/PUT/DELETE）
     */
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    /**
     * 是否允许访问（0-拒绝, 1-允许）
     */
    @Column(name = "is_allowed", nullable = false)
    @Builder.Default
    private Boolean allowed = true;
}
