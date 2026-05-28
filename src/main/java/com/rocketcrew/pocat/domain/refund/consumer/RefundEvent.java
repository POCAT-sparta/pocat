package com.rocketcrew.pocat.domain.refund.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "refund" 토픽에서 수신하는 모든 이벤트의 공통 역직렬화 DTO.
 *
 * eventType 값:
 *   "refund.requested" - refundId, orderUid, buyerId
 *   "refund.approved"  - refundId, orderUid, buyerId, sellerId
 *   "refund.rejected"  - refundId, orderUid, buyerId, reason
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefundEvent {
    private String eventType;
    private Long refundId;
    private String orderUid;
    private Long buyerId;
    private Long sellerId;
    private String reason;
}
