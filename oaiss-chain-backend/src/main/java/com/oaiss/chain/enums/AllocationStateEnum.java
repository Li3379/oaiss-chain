package com.oaiss.chain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 配额发放状态机。
 *
 * <p>为什么需要显式的状态机，而不是靠 {@code quota_allocated_amount} 是否非空来判断：
 *
 * <p>配额发放支持「冲正后重发」——报告被驳回、修改产量、重新提交、再次通过时，
 * 系统要先收回上次发的配额，再按新产量重发。若只用「金额非空」当幂等依据：
 * <ul>
 *   <li>冲正后不置空 → 第二次审核命中幂等短路，新数据永远发不出去</li>
 *   <li>冲正后置空 → 幂等失效，重复调用会重复发放</li>
 * </ul>
 * 两种做法都有洞。引入状态机后，「已发放」与「已冲正待重发」是两个明确状态，
 * 各有各的处理分支。
 *
 * @author OAISS Team
 */
@Getter
@AllArgsConstructor
public enum AllocationStateEnum {

    /** 无需发放或尚未发放（历史报告、首次进入） */
    NONE("NONE", "未发放"),

    /** 已发放，幂等短路：重复调用返回既有结果 */
    ALLOCATED("ALLOCATED", "已发放"),

    /** 已冲正，允许重新发放（重发时序号递增，避免撞唯一索引） */
    REVOKED("REVOKED", "已冲正"),

    /** 发放失败待重试（如 DB 写失败），由管理员通过重试端点补发 */
    PENDING_RETRY("PENDING_RETRY", "待重试"),

    /**
     * 冲正失败。
     *
     * <p>用于 certify 驳回场景：此时必须保证「驳回」本身不被阻断
     * （否则企业有在途挂单时审核员连驳回都做不了），故冲正失败只留痕，
     * 由管理员后续处理。
     */
    REVOKE_FAILED("REVOKE_FAILED", "冲正失败");

    private final String code;
    private final String description;

    /**
     * 根据 code 获取枚举；未知值返回 {@code null}（不抛异常，便于处理历史脏数据）
     */
    public static AllocationStateEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AllocationStateEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 是否允许发放（首次发放或冲正后重发）
     */
    public boolean allowsAllocation() {
        return this == NONE || this == REVOKED || this == PENDING_RETRY || this == REVOKE_FAILED;
    }

    /**
     * 是否已发放（幂等短路）
     */
    public boolean isAllocated() {
        return this == ALLOCATED;
    }
}