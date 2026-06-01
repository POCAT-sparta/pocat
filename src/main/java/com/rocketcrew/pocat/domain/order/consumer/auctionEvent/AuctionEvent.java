package com.rocketcrew.pocat.domain.order.consumer.auctionEvent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * "auction" 토픽에서 수신하는 모든 이벤트의 공통 역직렬화 DTO.
 *
 * eventType 값:
 *   "auction.ended"            - auctionId, cardId, sellerId, winnerId(nullable), loserIds, finalPrice(nullable)
 *   "auction.buyout.completed" - auctionId, orderUid, buyerId, sellerId, finalPrice, previousHighestBidderId(nullable)
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuctionEvent {
    private String eventType;
    private Long auctionId;
    private Long cardId;
    private Long sellerId;
    private Long winnerId;
    private List<Long> loserIds;
    private Long finalPrice;

    // auction.buyout.completed 전용
    private String orderUid;
    private Long buyerId;
    private Long previousHighestBidderId;
}
