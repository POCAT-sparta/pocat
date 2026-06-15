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
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationPaymentEventConsumerTest {

    @Mock
    NotificationCommandService notificationCommandService;

    @Mock
    Acknowledgment acknowledgment;

    NotificationPaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new NotificationPaymentEventConsumer(notificationCommandService, objectMapper);
    }

    @Test
    @DisplayName("payment.completed 이벤트는 finalPrice 포함 시 구매자/판매자에게 알림을 보낸다")
    void consumeCompleted_withFinalPrice_notifiesBuyerAndSeller() {
        String message = """
                {
                  "eventType": "payment.completed",
                  "orderUid": "ORD-001",
                  "buyerId": 10,
                  "sellerId": 20,
                  "finalPrice": 50000
                }
                """;

        consumer.consume(message, acknowledgment);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.PAYMENT_COMPLETED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001", "finalPrice", 50000L))
        );
        verify(notificationCommandService).send(
                eq(20L),
                eq(NotificationType.PAYMENT_COMPLETED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001", "finalPrice", 50000L))
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("payment.completed 이벤트는 finalPrice 누락 시 orderUid만으로 알림을 보낸다")
    void consumeCompleted_withoutFinalPrice_notifiesWithOrderUidOnly() {
        String message = """
                {
                  "eventType": "payment.completed",
                  "orderUid": "ORD-001",
                  "buyerId": 10,
                  "sellerId": 20
                }
                """;

        consumer.consume(message, acknowledgment);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.PAYMENT_COMPLETED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001"))
        );
        verify(notificationCommandService).send(
                eq(20L),
                eq(NotificationType.PAYMENT_COMPLETED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001"))
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("payment.auto.failed 이벤트는 구매자에게 AUTO_PAYMENT_FAILED 알림을 보낸다")
    void consumeAutoFailed_notifiesBuyer() {
        String message = """
                {
                  "eventType": "payment.auto.failed",
                  "orderUid": "ORD-001",
                  "buyerId": 10,
                  "sellerId": 20
                }
                """;

        consumer.consume(message, acknowledgment);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.AUTO_PAYMENT_FAILED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001"))
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("payment.direct.failed 이벤트는 구매자에게 DIRECT_PAYMENT_FAILED 알림을 보낸다")
    void consumeDirectFailed_notifiesBuyer() {
        String message = """
                {
                  "eventType": "payment.direct.failed",
                  "orderUid": "ORD-001",
                  "buyerId": 10,
                  "sellerId": 20
                }
                """;

        consumer.consume(message, acknowledgment);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.DIRECT_PAYMENT_FAILED),
                anyString(),
                eq(Map.of("orderUid", "ORD-001"))
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("처리 대상이 아닌 eventType은 알림 없이 acknowledge만 한다")
    void consumeUnknownEventType_acknowledgesWithoutNotification() {
        String message = """
                {
                  "eventType": "payment.unknown",
                  "orderUid": "ORD-001"
                }
                """;

        consumer.consume(message, acknowledgment);

        verifyNoInteractions(notificationCommandService);
        verify(acknowledgment).acknowledge();
    }
}
