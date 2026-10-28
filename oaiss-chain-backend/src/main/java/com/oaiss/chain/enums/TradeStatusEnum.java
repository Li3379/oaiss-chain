package com.oaiss.chain.enums;

import lombok.Getter;

/**
 * 交易状态枚举
 * Trade Status Enumeration
 * 
 * @author OAISS Team
 */
@Getter
public enum TradeStatusEnum {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成"),
    CANCELLED(3, "已取消"),
    FAILED(4, "失败");

    private final Integer code;
    private final String description;

    TradeStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static TradeStatusEnum fromCode(Integer code) {
        for (TradeStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown trade status code: " + code);
    }

    /**
     * 判断是否可以取消
     */
    public boolean isCancellable() {
        return this == PENDING || this == PROCESSING;
    }

    /**
     * 判断是否已完成
     */
    public boolean isFinalized() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
