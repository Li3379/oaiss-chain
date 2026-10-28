package com.oaiss.chain.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event fired after a carbon trade is completed, triggering async blockchain commit.
 * R114 - Trade blockchain attestation via event-driven async pattern.
 *
 * @author OAISS Team
 */
public class TradeCompletedEvent extends ApplicationEvent {

    private final Long tradeId;
    private final String tradeData;

    public TradeCompletedEvent(Object source, Long tradeId, String tradeData) {
        super(source);
        this.tradeId = tradeId;
        this.tradeData = tradeData;
    }

    public Long getTradeId() { return tradeId; }
    public String getTradeData() { return tradeData; }
}
