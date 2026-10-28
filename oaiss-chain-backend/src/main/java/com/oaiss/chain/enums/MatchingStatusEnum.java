package com.oaiss.chain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 撮合结果状态枚举
 *
 * @author OAISS Team
 */
@Getter
@AllArgsConstructor
public enum MatchingStatusEnum {

    PENDING_SETTLEMENT(0, "待结算"),
    SETTLED(1, "已结算"),
    FAILED(2, "失败");

    private final Integer code;
    private final String description;

    /**
     * 根据code获取枚举
     */
    public static MatchingStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (MatchingStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
