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

    /**
     * 「权威口径」的状态码集合：已通过 (3) 与已上链 (5)。
     *
     * <p>统计、AI 推理、企业年度评级、第三方核查都只应采信这两种状态的报告。
     * 该集合此前在 4 个 service 里各写一份 {@code List.of(3, 5)}，
     * 其中一处还是裸数字 {@code Arrays.asList(3, 5)}——任何状态语义调整都会漏改其中几处。
     * 现在收敛到枚举，让「权威」有唯一定义点。</p>
     */
    public static java.util.List<Integer> authoritativeCodes() {
        return java.util.List.of(APPROVED.getCode(), ON_CHAIN.getCode());
    }
}
