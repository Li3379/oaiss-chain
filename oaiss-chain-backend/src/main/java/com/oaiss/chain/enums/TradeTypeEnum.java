package com.oaiss.chain.enums;

import lombok.Getter;

/**
 * 交易类型枚举
 * Trade Type Enumeration
 * 
 * @author OAISS Team
 */
@Getter
public enum TradeTypeEnum {

    AUCTION(1, "拍卖交易"),
    P2P(2, "P2P交易"),
    ALLOCATION(3, "配额分配");

    private final Integer code;
    private final String description;

    TradeTypeEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static TradeTypeEnum fromCode(Integer code) {
        for (TradeTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown trade type code: " + code);
    }
}
