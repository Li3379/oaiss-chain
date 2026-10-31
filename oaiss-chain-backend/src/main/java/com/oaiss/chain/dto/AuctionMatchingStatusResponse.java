package com.oaiss.chain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 撮合控制台状态响应 DTO
 *
 * <p>供管理员端「撮合控制台」展示当前自动撮合运行态与市场待撮合盘口规模，
 * 使「平台会自动撮合」这一行为在 UI 上可被观察，而不是只能靠等待成交结果。</p>
 *
 * @author OAISS Team
 */
@Data
@Builder
public class AuctionMatchingStatusResponse {

    /** 自动撮合是否已启用 */
    private Boolean autoMatchingEnabled;

    /** 自动撮合间隔（秒） */
    private Long intervalSeconds;

    /** 待撮合买盘挂单数 */
    private Long pendingBuyOrders;

    /** 待撮合卖盘挂单数 */
    private Long pendingSellOrders;

    /** 最近一次撮合成交时间；从未成交则为 null */
    private LocalDateTime lastMatchAt;
}
