package com.rocketcrew.pocat.domain.notification.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "auction" 토픽에서 수신하는 모든 이벤트의 공통 역직렬화 DTO.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuctionNotificationEventPayload {

    private String eventType;
    private Long auctionId;
    private Long cardId;
    private Long sellerId;
    private Long winnerId;
    private Long buyerId;
    private Long finalPrice;
    private Long cancelledBy;
    private String reason;
    private String failedReason;
    private String orderUid;
    private List<Long> loserIds;
    private List<Long> bidderIds;
    private LocalDateTime endedAt;
    private Long previousHighestBidderId;
}
