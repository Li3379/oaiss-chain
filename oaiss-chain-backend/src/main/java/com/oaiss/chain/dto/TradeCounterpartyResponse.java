package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * P2P trade counterparty option DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeCounterpartyResponse {

    private Long userId;
    private String username;
    private String realName;
    private String company;
}
