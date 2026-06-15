package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCreatedEvent;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSettlementEventHandler")
class NotificationSettlementEventHandlerTest {

    @InjectMocks
    private NotificationSettlementEventHandler handler;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Test
    @DisplayName("성공: 정산 생성 이벤트 수신 시 판매자에게 SETTLEMENT_CREATED 알림을 보낸다")
    void handle_success() {
        SettlementCreatedEvent event = new SettlementCreatedEvent("STL-001", 2L, 10000L);

        handler.handle(event);

        verify(notificationCommandService).send(
                2L,
                NotificationType.SETTLEMENT_CREATED,
                "결제가 완료되어 정산이 시작되었습니다. 정산 예정 금액: 10,000원",
                Map.of("settlementUid", "STL-001", "sellerAmount", 10000L)
        );
    }

    @Test
    @DisplayName("실패: 알림 전송 중 예외가 발생해도 전파되지 않는다")
    void handle_notificationFailureDoesNotPropagate() {
        SettlementCreatedEvent event = new SettlementCreatedEvent("STL-001", 2L, 10000L);
        willThrow(new RuntimeException("kafka down"))
                .given(notificationCommandService).send(any(), any(), any(), any());

        handler.handle(event);
    }
}
