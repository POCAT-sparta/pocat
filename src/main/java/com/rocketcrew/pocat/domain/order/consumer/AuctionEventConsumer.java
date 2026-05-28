package com.rocketcrew.pocat.domain.order.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventConsumer {

    private final OrderCommandService orderCommandService;
    private final NotificationCommandService notificationCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "auction",
            groupId = "order-auction-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        try {
            AuctionEvent event = objectMapper.readValue(message, AuctionEvent.class);
            switch (event.getEventType()) {
                case "auction.ended" -> handleAuctionEnded(event);
                default -> log.warn("알 수 없는 auction 이벤트: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("auction 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    // 경매 종료 → 낙찰 주문 생성 + 패찰자 알림
    private void handleAuctionEnded(AuctionEvent event) {
        if (event.getWinnerId() != null) {
            try {
                orderCommandService.createOrderFromAuction(
                        event.getAuctionId(),
                        event.getCardId(),
                        event.getSellerId(),
                        event.getWinnerId(),
                        event.getFinalPrice()
                );
            } catch (Exception e) {
                log.error("낙찰 주문 생성 실패: auctionId={}", event.getAuctionId(), e);
                throw new RuntimeException(e);
            }
        }

        List<Long> loserIds = event.getLoserIds();
        if (loserIds == null || loserIds.isEmpty()) return;

        for (Long loserId : loserIds) {
            try {
                notificationCommandService.send(
                        loserId,
                        NotificationType.AUCTION_LOST,
                        "패찰하셨습니다.",
                        Map.of("auctionId", event.getAuctionId())
                );
            } catch (Exception e) {
                log.error("패찰 알림 실패: auctionId={}, userId={}", event.getAuctionId(), loserId, e);
            }
        }
    }
}
