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
    BONUS_GOOD_BEHAVIOR(5, "良好行为奖励", 5),
    /**
     * 成交审核驳回：成交被人工判定为异常（价格异常 / 洗售嫌疑 / 低信誉等）。
     *
     * <p>扣分刻意轻于「轻微违规(-10)」：驳回时额度与资金已全额回滚，
     * 未造成实际损失，扣分的作用是形成行为记录并驱动连续驳回的监管线索，
     * 而非惩罚已发生的损失。
     */
    TRADE_AUDIT_REJECTED(6, "成交审核驳回", -8);

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
