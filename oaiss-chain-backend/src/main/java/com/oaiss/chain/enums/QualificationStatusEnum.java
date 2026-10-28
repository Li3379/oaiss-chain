package com.oaiss.chain.enums;

import lombok.Getter;

/**
 * 证书状态枚举
 * Certificate Status Enumeration
 *
 * @author OAISS Team
 */
@Getter
public enum QualificationStatusEnum {

    ACTIVE(1, "有效"),
    REVOKED(2, "已吊销");

    private final Integer code;
    private final String description;

    QualificationStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static QualificationStatusEnum fromCode(Integer code) {
        for (QualificationStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown qualification status code: " + code);
    }
}