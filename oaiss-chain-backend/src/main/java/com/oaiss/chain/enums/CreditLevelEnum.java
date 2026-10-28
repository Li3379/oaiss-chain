package com.oaiss.chain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 信誉等级枚举
 *
 * @author OAISS Team
 */
@Getter
@AllArgsConstructor
public enum CreditLevelEnum {

    EXCELLENT("EXCELLENT", 80, 100, "优秀"),
    GOOD("GOOD", 60, 79, "良好"),
    WARNING("WARNING", 40, 59, "警告"),
    DANGER("DANGER", 20, 39, "危险"),
    FROZEN("FROZEN", 0, 19, "冻结");

    private final String code;
    private final int minScore;
    private final int maxScore;
    private final String description;

    /**
     * 根据分数判定信誉等级
     */
    public static CreditLevelEnum fromScore(Integer score) {
        if (score == null) return FROZEN;
        if (score >= 80) return EXCELLENT;
        if (score >= 60) return GOOD;
        if (score >= 40) return WARNING;
        if (score >= 20) return DANGER;
        return FROZEN;
    }
}
