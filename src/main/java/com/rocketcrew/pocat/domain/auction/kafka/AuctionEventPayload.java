package com.rocketcrew.pocat.domain.auction.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuctionEventPayload {

    private String eventType;
    private Long auctionId;
    private Long cardId;
    private Long sellerId;
    private Long winnerId;
    private Long buyerId;
    private Long finalPrice;
    private Long cancelledBy;
    private String reason;
    private String orderUid;
    private List<Long> loserIds;
    private List<Long> bidderIds;
    private LocalDateTime endedAt;
}
