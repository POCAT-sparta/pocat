package com.rocketcrew.pocat.domain.order.consumer.paymentEvent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "payment" 토픽에서 수신하는 모든 이벤트의 공통 역직렬화 DTO.
 *
 * eventType 값:
 *   "payment.completed"      - orderUid, buyerId, sellerId, finalPrice
 *   "payment.auto.failed"    - orderUid, buyerId, sellerId
 *   "payment.direct.failed"  - orderUid, buyerId, sellerId
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEvent {
    private String eventType;
    private String orderUid;
    private Long buyerId;
    private Long sellerId;
    private Long finalPrice;
    private String reason;
}
