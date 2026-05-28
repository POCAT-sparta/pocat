package com.rocketcrew.pocat.domain.order.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {
    private String eventType;
    private String orderUid;
    private Long buyerId;
    private Long sellerId;
    private Long finalPrice;
    private String reason;
}
