package com.rocketcrew.pocat.domain.order.consumer.auctionEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.event.AuctionEventType;
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
                case AuctionEventType.ENDED            -> handleAuctionEnded(event);
                case AuctionEventType.BUYOUT_COMPLETED -> handleBuyoutCompleted(event);
                default -> log.warn("알 수 없는 auction 이벤트: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("auction 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }
    // 경매 종료 → 낙찰 주문 생성 + 낙찰자 알림 + 패찰자 알림
    private void handleAuctionEnded(AuctionEvent event) {
        if (event.getWinnerId() != null) {
            try {
                orderCommandService.createOrderFromAuction(
                        event.getAuctionId(),
                        event.getCardId(),
                        event.getSellerId(),
                        event.getWinnerId(),
                        event.getFinalPrice(),
                        1
                );
            } catch (Exception e) {
                log.error("낙찰 주문 생성 실패: auctionId={}", event.getAuctionId(), e);
                throw new RuntimeException(e);
            }

            try {
                notificationCommandService.send(
                        event.getWinnerId(),
                        NotificationType.AUCTION_WON,
                        "낙찰되었습니다.",
                        Map.of("auctionId", event.getAuctionId(), "finalPrice", event.getFinalPrice())
                );
            } catch (Exception e) {
                log.error("낙찰 알림 실패: auctionId={}, userId={}", event.getAuctionId(), event.getWinnerId(), e);
            }

            try {
                notificationCommandService.send(
                        event.getSellerId(),
                        NotificationType.AUCTION_SOLD,
                        "카드가 낙찰되었습니다.",
                        Map.of("auctionId", event.getAuctionId(), "finalPrice", event.getFinalPrice())
                );
            } catch (Exception e) {
                log.error("경매 종료 판매자 알림 실패: auctionId={}, sellerId={}", event.getAuctionId(), event.getSellerId(), e);
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

    // 즉시구매 완료 → 구매자/판매자 알림 + 이전 최고입찰자 알림
    private void handleBuyoutCompleted(AuctionEvent event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.PAYMENT_COMPLETED,
                    "즉시구매가 완료되었습니다.",
                    Map.of("orderUid", event.getOrderUid(), "finalPrice", event.getFinalPrice())
            );
        } catch (Exception e) {
            log.error("즉시구매 구매자 알림 실패: auctionId={}", event.getAuctionId(), e);
        }

        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.PAYMENT_COMPLETED,
                    "즉시구매로 카드가 판매되었습니다.",
                    Map.of("orderUid", event.getOrderUid(), "finalPrice", event.getFinalPrice())
            );
        } catch (Exception e) {
            log.error("즉시구매 판매자 알림 실패: auctionId={}", event.getAuctionId(), e);
        }

        if (event.getPreviousHighestBidderId() != null) {
            try {
                notificationCommandService.send(
                        event.getPreviousHighestBidderId(),
                        NotificationType.AUCTION_LOST,
                        "다른 사용자가 즉시구매하여 경매가 종료되었습니다.",
                        Map.of("auctionId", event.getAuctionId())
                );
            } catch (Exception e) {
                log.error("즉시구매 이전 최고입찰자 알림 실패: auctionId={}, bidderId={}",
                        event.getAuctionId(), event.getPreviousHighestBidderId(), e);
            }
        }
    }
}
