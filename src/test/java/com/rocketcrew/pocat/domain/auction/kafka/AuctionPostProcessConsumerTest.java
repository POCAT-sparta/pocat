 package com.rocketcrew.pocat.domain.auction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.redis.AuctionExpirationRedisService;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.domain.auction.service.AuctionLifecycleService;
import com.rocketcrew.pocat.domain.auction.snapshot.service.AuctionSnapshotCommandService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.InvalidAuctionEventPayloadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuctionPostProcessConsumerTest {

    @Mock
    AuctionExpirationRedisService auctionExpirationRedisService;

    @Mock
    AuctionSnapshotCommandService auctionSnapshotCommandService;

    @Mock
    AuctionLifecycleService auctionLifecycleService;

    @Mock
    AuctionEsIndexService auctionEsIndexService;

    AuctionPostProcessConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new AuctionPostProcessConsumer(
                objectMapper,
                auctionExpirationRedisService,
                auctionSnapshotCommandService,
                auctionLifecycleService,
                auctionEsIndexService
        );
    }

    @Test
    @DisplayName("auction.activated 이벤트는 경매 종료 TTL 키를 등록한다")
    void consumeActivated_setsExpirationKeys() {
        LocalDateTime endedAt = LocalDateTime.of(2026, 6, 1, 19, 0);
        String message = """
                {
                  "eventType": "auction.activated",
                  "auctionId": 1,
                  "sellerId": 10,
                  "endedAt": "2026-06-01T19:00:00"
                }
                """;

        consumer.consume(message);

        verify(auctionExpirationRedisService).setExpirationKeys(1L, endedAt);
        verifyNoInteractions(auctionSnapshotCommandService);
    }

    @Test
    @DisplayName("auction.activated 이벤트의 endedAt 누락은 실패 처리한다")
    void consumeActivated_withoutEndedAt_throwsInvalidPayloadException() {
        String message = """
                {
                  "eventType": "auction.activated",
                  "auctionId": 1
                }
                """;

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(InvalidAuctionEventPayloadException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        verifyNoInteractions(auctionExpirationRedisService, auctionSnapshotCommandService);
    }

    @Test
    @DisplayName("파싱할 수 없는 auction 이벤트 payload는 실패 처리한다")
    void consumeMalformedJson_throwsInvalidPayloadException() {
        String message = "{";

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(InvalidAuctionEventPayloadException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        verifyNoInteractions(auctionExpirationRedisService, auctionSnapshotCommandService);
    }

    @Test
    @DisplayName("auction.ended 이벤트는 finalPrice가 없어도 Redis 키를 삭제하고 스냅샷을 생성한다")
    void consumeEnded_withoutFinalPrice_createsSnapshotWithNullFinalPrice() {
        String message = """
                {
                  "eventType": "auction.ended",
                  "auctionId": 1
                }
                """;

        consumer.consume(message);

        verify(auctionExpirationRedisService).deleteExpirationKeys(1L);
        verify(auctionSnapshotCommandService).createSnapshot(1L, null);
    }

    @Test
    @DisplayName("auction.buyout.completed 이벤트는 Redis 키를 삭제하고 스냅샷을 생성한다")
    void consumeBuyoutCompleted_deletesKeysAndCreatesSnapshot() {
        String message = """
                {
                  "eventType": "auction.buyout.completed",
                  "auctionId": 1,
                  "finalPrice": 10000
                }
                """;

        consumer.consume(message);

        verify(auctionExpirationRedisService).deleteExpirationKeys(1L);
        verify(auctionSnapshotCommandService).createSnapshot(1L, 10000L);
        verify(auctionEsIndexService).updateHighestPrice(1L, 10000L);
        verify(auctionEsIndexService).updateStatus(1L, AuctionStatus.ENDED);
    }

    @Test
    @DisplayName("auction.buyout.completed 이벤트의 finalPrice 누락은 실패 처리한다")
    void consumeBuyoutCompleted_withoutFinalPrice_throwsInvalidPayloadException() {
        String message = """
                {
                  "eventType": "auction.buyout.completed",
                  "auctionId": 1
                }
                """;

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(InvalidAuctionEventPayloadException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        verifyNoInteractions(auctionExpirationRedisService, auctionSnapshotCommandService);
    }

    @Test
    @DisplayName("auction.cancelled 이벤트는 Redis 키만 삭제한다")
    void consumeCancelled_deletesKeysOnly() {
        String message = """
                {
                  "eventType": "auction.cancelled",
                  "auctionId": 1,
                  "sellerId": 10,
                  "reason": "seller requested",
                  "bidderIds": [20, 30]
                }
                """;

        consumer.consume(message);

        verify(auctionExpirationRedisService).deleteExpirationKeys(1L);
        verifyNoInteractions(auctionSnapshotCommandService);
    }

    @Test
    @DisplayName("auction.inspection.passed 이벤트는 경매를 ACTIVE 상태로 전환한다")
    void consumeInspectionPassedEvent_activatesAuction() {
        String message = """
                {
                  "eventType": "auction.inspection.passed",
                  "auctionId": 1,
                  "sellerId": 10
                }
                """;

        consumer.consume(message);

        verify(auctionLifecycleService).activateApprovedAuction(1L);
        verifyNoInteractions(auctionExpirationRedisService, auctionSnapshotCommandService);
    }

    @Test
    @DisplayName("auction.inspection.failed 이벤트는 후처리 대상이 아니므로 명시적으로 스킵한다")
    void consumeInspectionFailedEvent_skipsPostProcess() {
        String message = """
                {
                  "eventType": "auction.inspection.failed",
                  "auctionId": 1,
                  "sellerId": 10,
                  "failedReason": "invalid condition"
                }
                """;

        consumer.consume(message);

        verifyNoInteractions(auctionExpirationRedisService, auctionSnapshotCommandService, auctionLifecycleService, auctionEsIndexService);
    }

    @Test
    @DisplayName("알 수 없는 eventType은 후처리 대상이 아니므로 스킵한다")
    void consumeUnknownEventType_skipsPostProcess() {
        String message = """
                {
                  "eventType": "auction.unknown",
                  "auctionId": 1
                }
                """;

        consumer.consume(message);

        verifyNoInteractions(auctionExpirationRedisService, auctionSnapshotCommandService);
    }

    @Test
    @DisplayName("auctionId가 필요한 이벤트에서 auctionId가 없으면 실패 처리한다")
    void consumeEventWithoutAuctionId_throwsInvalidPayloadException() {
        String message = """
                {
                  "eventType": "auction.ended"
                }
                """;

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(InvalidAuctionEventPayloadException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        verifyNoInteractions(auctionExpirationRedisService, auctionSnapshotCommandService);
    }
}
