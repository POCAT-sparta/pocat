package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionActivatedEventTest {

    @Test
    @DisplayName("auction.activated 이벤트는 auctionId가 필수다")
    void constructor_withoutAuctionId_throwsAuctionException() {
        assertThatThrownBy(() -> new AuctionActivatedEvent(
                null,
                2L,
                LocalDateTime.of(2026, 6, 1, 19, 0)
        )).isInstanceOf(AuctionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
    }

    @Test
    @DisplayName("auction.activated 이벤트는 endedAt이 필수다")
    void constructor_withoutEndedAt_throwsAuctionException() {
        assertThatThrownBy(() -> new AuctionActivatedEvent(
                1L,
                2L,
                null
        )).isInstanceOf(AuctionException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
    }
}
