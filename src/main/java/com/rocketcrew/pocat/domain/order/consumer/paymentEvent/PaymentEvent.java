package com.rocketcrew.pocat.domain.order.consumer.paymentEvent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "payment" 토픽에서 수신하는 모든 이벤트의 공통 역직렬화 DTO.
 *
 * eventType 값:
 *   "payment.billing.requested" - orderUid, buyerId, finalPrice
 *   "payment.completed"         - orderUid, buyerId, sellerId, finalPrice
 *   "payment.failed"            - orderUid, buyerId, reason
 *   "payment.window.expired"    - orderUid, buyerId, sellerId, bidderRank
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
    private String failureType; // "AUTO" | "DIRECT"
    private Integer bidderRank;
}
