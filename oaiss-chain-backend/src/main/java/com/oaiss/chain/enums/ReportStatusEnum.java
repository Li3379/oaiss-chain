package com.oaiss.chain.enums;

import lombok.Getter;

/**
 * 碳报告状态枚举
 * Carbon Report Status Enumeration
 * 
 * @author OAISS Team
 */
@Getter
public enum ReportStatusEnum {

    DRAFT(0, "草稿"),
    SUBMITTED(1, "已提交"),
    UNDER_REVIEW(2, "审核中"),
    APPROVED(3, "审核通过"),
    REJECTED(4, "审核拒绝"),
    ON_CHAIN(5, "已上链");

    private final Integer code;
    private final String description;

    ReportStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ReportStatusEnum fromCode(Integer code) {
        for (ReportStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown report status code: " + code);
    }

    /**
     * 判断是否可以编辑
     */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * 判断是否可以提交
     */
    public boolean isSubmittable() {
        return this == DRAFT || this == REJECTED;
    }

    /**
     * 判断是否可以审核
     */
    public boolean isReviewable() {
        return this == SUBMITTED || this == UNDER_REVIEW;
    }
}
