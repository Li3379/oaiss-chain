package com.oaiss.chain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 拍卖挂单状态枚举
 *
 * @author OAISS Team
 */
@Getter
@AllArgsConstructor
public enum AuctionOrderStatusEnum {

    PENDING(0, "待匹配"),
    PARTIALLY_MATCHED(1, "部分匹配"),
    FULLY_MATCHED(2, "完全匹配"),
    CANCELLED(3, "已取消");

    private final Integer code;
    private final String description;

    /**
     * 根据code获取枚举
     */
    public static AuctionOrderStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (AuctionOrderStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    /**
     * 是否可取消
     */
    public boolean isCancellable() {
        return this == PENDING || this == PARTIALLY_MATCHED;
    }
}
