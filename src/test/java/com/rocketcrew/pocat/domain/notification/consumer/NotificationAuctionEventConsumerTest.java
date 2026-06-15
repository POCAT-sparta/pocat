package com.rocketcrew.pocat.domain.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationAuctionEventConsumerTest {

    @Mock
    NotificationCommandService notificationCommandService;

    NotificationAuctionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new NotificationAuctionEventConsumer(objectMapper, notificationCommandService);
    }

    @Test
    @DisplayName("auction.activated 이벤트는 판매자에게 AUCTION_ACTIVATED 알림을 보낸다")
    void consumeActivated_notifiesSeller() {
        String message = """
                {
                  "eventType": "auction.activated",
                  "auctionId": 1,
                  "sellerId": 10
                }
                """;

        consumer.consume(message);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.AUCTION_ACTIVATED),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
    }

    @Test
    @DisplayName("auction.cancelled 이벤트는 판매자와 입찰자 전원에게 AUCTION_CANCELLED 알림을 보낸다")
    void consumeCancelled_notifiesSellerAndBidders() {
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

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.AUCTION_CANCELLED),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
        verify(notificationCommandService).send(
                eq(20L),
                eq(NotificationType.AUCTION_CANCELLED),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
        verify(notificationCommandService).send(
                eq(30L),
                eq(NotificationType.AUCTION_CANCELLED),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
    }

    @Test
    @DisplayName("auction.inspection.passed 이벤트는 판매자에게 INSPECTION_PASSED 알림을 보낸다")
    void consumeInspectionPassed_notifiesSeller() {
        String message = """
                {
                  "eventType": "auction.inspection.passed",
                  "auctionId": 1,
                  "sellerId": 10
                }
                """;

        consumer.consume(message);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.INSPECTION_PASSED),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
    }

    @Test
    @DisplayName("auction.inspection.failed 이벤트는 판매자에게 INSPECTION_FAILED 알림을 보낸다")
    void consumeInspectionFailed_notifiesSeller() {
        String message = """
                {
                  "eventType": "auction.inspection.failed",
                  "auctionId": 1,
                  "sellerId": 10,
                  "failedReason": "invalid condition"
                }
                """;

        consumer.consume(message);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.INSPECTION_FAILED),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
    }

    @Test
    @DisplayName("auction.ended 이벤트는 낙찰자에게 AUCTION_WON, 판매자에게 AUCTION_SOLD, 패찰자에게 AUCTION_LOST 알림을 보낸다")
    void consumeEnded_withWinner_notifiesWinnerSellerAndLosers() {
        String message = """
                {
                  "eventType": "auction.ended",
                  "auctionId": 1,
                  "sellerId": 10,
                  "winnerId": 20,
                  "finalPrice": 10000,
                  "loserIds": [30, 40]
                }
                """;

        consumer.consume(message);

        verify(notificationCommandService).send(
                eq(20L),
                eq(NotificationType.AUCTION_WON),
                anyString(),
                eq(Map.of("auctionId", 1L, "finalPrice", 10000L))
        );
        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.AUCTION_SOLD),
                anyString(),
                eq(Map.of("auctionId", 1L, "finalPrice", 10000L))
        );
        verify(notificationCommandService).send(
                eq(30L),
                eq(NotificationType.AUCTION_LOST),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
        verify(notificationCommandService).send(
                eq(40L),
                eq(NotificationType.AUCTION_LOST),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
    }

    @Test
    @DisplayName("auction.ended 이벤트는 낙찰자가 없으면 패찰자에게만 AUCTION_LOST 알림을 보낸다")
    void consumeEnded_withoutWinner_notifiesLosersOnly() {
        String message = """
                {
                  "eventType": "auction.ended",
                  "auctionId": 1,
                  "sellerId": 10,
                  "loserIds": [30]
                }
                """;

        consumer.consume(message);

        verify(notificationCommandService).send(
                eq(30L),
                eq(NotificationType.AUCTION_LOST),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
        verify(notificationCommandService, org.mockito.Mockito.never())
                .send(eq(10L), eq(NotificationType.AUCTION_SOLD), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("auction.buyout.completed 이벤트는 이전 최고입찰자가 있으면 구매자/판매자/이전 최고입찰자에게 알림을 보낸다")
    void consumeBuyoutCompleted_withPreviousHighestBidder_notifiesAll() {
        String message = """
                {
                  "eventType": "auction.buyout.completed",
                  "auctionId": 1,
                  "sellerId": 10,
                  "buyerId": 20,
                  "orderUid": "ORD-001",
                  "finalPrice": 10000,
                  "previousHighestBidderId": 30
                }
                """;

        consumer.consume(message);

        verify(notificationCommandService).send(
                eq(20L),
                eq(NotificationType.PAYMENT_COMPLETED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001", "finalPrice", 10000L))
        );
        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.PAYMENT_COMPLETED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001", "finalPrice", 10000L))
        );
        verify(notificationCommandService).send(
                eq(30L),
                eq(NotificationType.AUCTION_LOST),
                anyString(),
                eq(Map.of("auctionId", 1L))
        );
    }

    @Test
    @DisplayName("auction.buyout.completed 이벤트는 이전 최고입찰자가 없으면 구매자/판매자에게만 알림을 보낸다")
    void consumeBuyoutCompleted_withoutPreviousHighestBidder_notifiesBuyerAndSellerOnly() {
        String message = """
                {
                  "eventType": "auction.buyout.completed",
                  "auctionId": 1,
                  "sellerId": 10,
                  "buyerId": 20,
                  "orderUid": "ORD-001",
                  "finalPrice": 10000
                }
                """;

        consumer.consume(message);

        verify(notificationCommandService).send(
                eq(20L),
                eq(NotificationType.PAYMENT_COMPLETED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001", "finalPrice", 10000L))
        );
        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.PAYMENT_COMPLETED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001", "finalPrice", 10000L))
        );
        verify(notificationCommandService, org.mockito.Mockito.never())
                .send(org.mockito.ArgumentMatchers.any(), eq(NotificationType.AUCTION_LOST), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("처리 대상이 아닌 eventType은 알림을 보내지 않는다")
    void consumeUnknownEventType_doesNothing() {
        String message = """
                {
                  "eventType": "auction.unknown",
                  "auctionId": 1
                }
                """;

        consumer.consume(message);

        verifyNoInteractions(notificationCommandService);
    }
}
