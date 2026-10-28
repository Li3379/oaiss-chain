package com.oaiss.chain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 信誉事件类型枚举
 *
 * @author OAISS Team
 */
@Getter
@AllArgsConstructor
public enum CreditEventTypeEnum {

    DATA_FALSIFICATION(1, "数据造假", -20),
    LATE_SUBMISSION(2, "迟交报告", -5),
    MINOR_VIOLATION(3, "轻微违规", -10),
    MAJOR_VIOLATION(4, "严重违规", -30),
    BONUS_GOOD_BEHAVIOR(5, "良好行为奖励", 5);

    private final Integer code;
    private final String description;
    private final Integer defaultPoints;

    /**
     * 根据code获取枚举
     */
    public static CreditEventTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (CreditEventTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
