package com.oaiss.chain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RSA密钥对表
 * RSA Key Pair Entity
 * 
 * 存储用户（企业/审核员）的RSA密钥对
 * 用于碳报告数字签名和数据加密传输
 * 
 * @author OAISS Team
 */
@Entity
@Table(name = "rsa_key_pair")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RsaKeyPair extends BaseEntity {

    /**
     * 用户ID
     * 关联用户表
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 公钥（Base64编码）
     * 用于验签和加密
     */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    /**
     * 私钥（Base64编码）
     * 用于签名和解密
     * 注意：此字段敏感，不应在API响应中暴露
     */
    @JsonIgnore
    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String privateKey;

    /**
     * 密钥状态
     * 1-有效, 0-已失效, 2-已过期
     */
    @Column(name = "key_status", nullable = false)
    @Builder.Default
    private Integer keyStatus = 1;

    /**
     * 过期时间
     * 密钥有效期，null表示永不过期
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * 密钥版本号
     * 用于密钥轮换管理
     */
    @Column(name = "key_version")
    @Builder.Default
    private Integer keyVersion = 1;

    /**
     * 密钥用途
     * 1-签名验签, 2-加密解密, 3-通用
     */
    @Column(name = "key_usage", nullable = false)
    @Builder.Default
    private Integer keyUsage = 3;

    /**
     * 私钥是否已加密
     * 0-未加密(明文), 1-已加密(AES-256-GCM)
     */
    @Column(name = "encrypted", nullable = false)
    @Builder.Default
    private Boolean encrypted = false;

    /**
     * 备注信息
     */
    @Column(name = "remark", length = 500)
    private String remark;
}
