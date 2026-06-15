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
class NotificationRefundEventConsumerTest {

    @Mock
    NotificationCommandService notificationCommandService;

    @Mock
    Acknowledgment acknowledgment;

    NotificationRefundEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new NotificationRefundEventConsumer(notificationCommandService, objectMapper);
    }

    @Test
    @DisplayName("refund.requested 이벤트는 구매자에게 REFUND_REQUESTED 알림을 보낸다")
    void consumeRefundRequested_notifiesBuyer() {
        String message = """
                {
                  "eventType": "refund.requested",
                  "refundId": 1,
                  "orderUid": "ORD-001",
                  "buyerId": 10
                }
                """;

        consumer.consume(message, acknowledgment);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.REFUND_REQUESTED),
                anyString(),
                eq(Map.of("refundId", 1L, "orderUid", "ORD-001"))
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("refund.approved 이벤트는 구매자와 판매자 모두에게 REFUND_APPROVED 알림을 보낸다")
    void consumeRefundApproved_notifiesBuyerAndSeller() {
        String message = """
                {
                  "eventType": "refund.approved",
                  "refundId": 1,
                  "orderUid": "ORD-001",
                  "buyerId": 10,
                  "sellerId": 20
                }
                """;

        consumer.consume(message, acknowledgment);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.REFUND_APPROVED),
                anyString(),
                eq(Map.of("refundId", 1L, "orderUid", "ORD-001"))
        );
        verify(notificationCommandService).send(
                eq(20L),
                eq(NotificationType.REFUND_APPROVED),
                anyString(),
                eq(Map.of("refundId", 1L, "orderUid", "ORD-001"))
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("refund.rejected 이벤트는 구매자에게 사유를 포함한 REFUND_REJECTED 알림을 보낸다")
    void consumeRefundRejected_notifiesBuyerWithReason() {
        String message = """
                {
                  "eventType": "refund.rejected",
                  "refundId": 1,
                  "orderUid": "ORD-001",
                  "buyerId": 10,
                  "reason": "사진과 상태가 다름"
                }
                """;

        consumer.consume(message, acknowledgment);

        verify(notificationCommandService).send(
                eq(10L),
                eq(NotificationType.REFUND_REJECTED),
                anyString(),
                eq(Map.of("refundId", 1L, "orderUid", "ORD-001", "reason", "사진과 상태가 다름"))
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("알 수 없는 eventType은 알림 없이 acknowledge만 한다")
    void consumeUnknownEventType_acknowledgesWithoutNotification() {
        String message = """
                {
                  "eventType": "refund.unknown",
                  "refundId": 1
                }
                """;

        consumer.consume(message, acknowledgment);

        verifyNoInteractions(notificationCommandService);
        verify(acknowledgment).acknowledge();
    }
}
