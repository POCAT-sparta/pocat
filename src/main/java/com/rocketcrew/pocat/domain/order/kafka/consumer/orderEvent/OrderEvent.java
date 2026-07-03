package com.rocketcrew.pocat.domain.order.kafka.consumer.orderEvent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "order" 토픽에서 수신하는 모든 이벤트의 공통 역직렬화 DTO.
 * eventType 값:
 *   "order.created"   - orderUid, buyerId, sellerId, finalPrice
 *   "order.cancelled" - orderUid, buyerId, sellerId
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {
    private String eventType;
    private String orderUid;
    private Long buyerId;
    private Long sellerId;
    private Long finalPrice;
}
