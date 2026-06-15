package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.order.event.OrderEscalatedEvent;
import com.rocketcrew.pocat.domain.order.service.EscalationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationOrderEscalationEventHandler")
class NotificationOrderEscalationEventHandlerTest {

    @InjectMocks
    private NotificationOrderEscalationEventHandler handler;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Test
    @DisplayName("성공(ESCALATED): 다음 입찰자에게 ESCALATED_PAYMENT_OPPORTUNITY 알림을 보낸다")
    void handle_escalated_notifiesNextBidder() {
        OrderEscalatedEvent event = new OrderEscalatedEvent(
                EscalationResult.Status.ESCALATED, 99L, "ORD-NEXT", 2L, "ORD-001");

        handler.handle(event);

        verify(notificationCommandService).send(
                99L,
                NotificationType.ESCALATED_PAYMENT_OPPORTUNITY,
                "낙찰 기회가 생겼습니다. 1시간 내에 직접 결제를 진행해 주세요.",
                Map.of("orderUid", "ORD-NEXT")
        );
    }

    @Test
    @DisplayName("성공(CANCELLED): 판매자에게 PAYMENT_FINAL_FAILED 알림을 보낸다")
    void handle_cancelled_notifiesSeller() {
        OrderEscalatedEvent event = new OrderEscalatedEvent(
                EscalationResult.Status.CANCELLED, null, null, 2L, "ORD-001");

        handler.handle(event);

        verify(notificationCommandService).send(
                2L,
                NotificationType.PAYMENT_FINAL_FAILED,
                "구매자의 결제가 최종 실패하여 경매가 취소되었습니다.",
                Map.of("orderUid", "ORD-001")
        );
    }

    @Test
    @DisplayName("SKIPPED: 알림을 보내지 않는다")
    void handle_skipped_doesNothing() {
        OrderEscalatedEvent event = new OrderEscalatedEvent(
                EscalationResult.Status.SKIPPED, null, null, 2L, "ORD-001");

        handler.handle(event);

        verifyNoInteractions(notificationCommandService);
    }

    @Test
    @DisplayName("실패: 알림 전송 중 예외가 발생해도 전파되지 않는다")
    void handle_notificationFailureDoesNotPropagate() {
        OrderEscalatedEvent event = new OrderEscalatedEvent(
                EscalationResult.Status.ESCALATED, 99L, "ORD-NEXT", 2L, "ORD-001");
        willThrow(new RuntimeException("kafka down"))
                .given(notificationCommandService).send(any(), any(), any(), any());

        handler.handle(event);
    }
}
