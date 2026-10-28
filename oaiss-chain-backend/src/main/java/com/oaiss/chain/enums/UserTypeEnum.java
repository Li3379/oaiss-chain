package com.oaiss.chain.enums;

import lombok.Getter;

/**
 * 用户类型枚举
 * User Type Enumeration
 * 
 * @author OAISS Team
 */
@Getter
public enum UserTypeEnum {

    ENTERPRISE(1, "企业用户"),
    REVIEWER(2, "审核员"),
    THIRD_PARTY(3, "第三方监管"), // Reserved for future use — not yet used in @PreAuthorize annotations
    ADMIN(4, "系统管理员");

    private final Integer code;
    private final String description;

    UserTypeEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static UserTypeEnum fromCode(Integer code) {
        for (UserTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown user type code: " + code);
    }
}
