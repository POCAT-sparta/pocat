package com.rocketcrew.pocat.domain.settlement.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettlementEvent {
    private String eventType;
    private String settlementUid;
    private Long sellerId;
    private Long sellerAmount;
}
