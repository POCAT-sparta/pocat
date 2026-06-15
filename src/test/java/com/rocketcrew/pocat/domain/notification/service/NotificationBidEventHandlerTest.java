package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.bid.event.BidOutbidEvent;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
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
@DisplayName("NotificationBidEventHandler")
class NotificationBidEventHandlerTest {

    @InjectMocks
    private NotificationBidEventHandler handler;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Test
    @DisplayName("성공: outbid 이벤트 수신 시 이전 최고가 입찰자에게 BID_OUTBID 알림을 보낸다")
    void handle_success() {
        BidOutbidEvent event = new BidOutbidEvent(1L, 5L, 20000L);

        handler.handle(event);

        verify(notificationCommandService).send(
                5L,
                NotificationType.BID_OUTBID,
                "더 높은 입찰가가 등록되었습니다.",
                Map.of("auctionId", 1L, "currentHighestPrice", 20000L)
        );
    }

    @Test
    @DisplayName("실패: 알림 전송 중 예외가 발생해도 전파되지 않는다")
    void handle_notificationFailureDoesNotPropagate() {
        BidOutbidEvent event = new BidOutbidEvent(1L, 5L, 20000L);
        willThrow(new RuntimeException("kafka down"))
                .given(notificationCommandService).send(any(), any(), any(), any());

        handler.handle(event);
    }
}
