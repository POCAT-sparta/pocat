package com.rocketcrew.pocat.domain.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.event.AuctionEventType;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAuctionEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationCommandService notificationCommandService;

    @KafkaListener(
            topics = "auction",
            groupId = "notification-auction-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        try {
            AuctionNotificationEventPayload event = objectMapper.readValue(message, AuctionNotificationEventPayload.class);
            switch (event.getEventType()) {
                case AuctionEventType.ACTIVATED -> handleActivated(event);
                case AuctionEventType.CANCELLED -> handleCancelled(event);
                case AuctionEventType.INSPECTION_PASSED -> handleInspectionPassed(event);
                case AuctionEventType.INSPECTION_FAILED -> handleInspectionFailed(event);
                case AuctionEventType.ENDED -> handleEnded(event);
                case AuctionEventType.BUYOUT_COMPLETED -> handleBuyoutCompleted(event);
                default -> log.debug("알림 처리 대상 아닌 auction 이벤트: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("auction 알림 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    private void handleActivated(AuctionNotificationEventPayload event) {
        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.AUCTION_ACTIVATED,
                    "경매가 활성화되었습니다.",
                    Map.of("auctionId", event.getAuctionId())
            );
        } catch (Exception e) {
            log.error("경매 활성화 알림 실패: auctionId={}", event.getAuctionId(), e);
        }
    }

    private void handleCancelled(AuctionNotificationEventPayload event) {
        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.AUCTION_CANCELLED,
                    "경매가 취소되었습니다. 사유: " + event.getReason(),
                    Map.of("auctionId", event.getAuctionId())
            );
        } catch (Exception e) {
            log.error("경매 취소 판매자 알림 실패: auctionId={}", event.getAuctionId(), e);
        }
        List<Long> bidderIds = event.getBidderIds();
        if (bidderIds == null) return;
        for (Long bidderId : bidderIds) {
            try {
                notificationCommandService.send(
                        bidderId,
                        NotificationType.AUCTION_CANCELLED,
                        "경매가 취소되었습니다. 사유: " + event.getReason(),
                        Map.of("auctionId", event.getAuctionId())
                );
            } catch (Exception e) {
                log.error("경매 취소 입찰자 알림 실패: auctionId={}, bidderId={}", event.getAuctionId(), bidderId, e);
            }
        }
    }

    private void handleInspectionPassed(AuctionNotificationEventPayload event) {
        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.INSPECTION_PASSED,
                    "경매 검수가 통과되었습니다.",
                    Map.of("auctionId", event.getAuctionId())
            );
        } catch (Exception e) {
            log.error("검수 통과 알림 실패: auctionId={}", event.getAuctionId(), e);
        }
    }

    private void handleInspectionFailed(AuctionNotificationEventPayload event) {
        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.INSPECTION_FAILED,
                    "경매 검수가 반려되었습니다. 사유: " + event.getFailedReason(),
                    Map.of("auctionId", event.getAuctionId())
            );
        } catch (Exception e) {
            log.error("검수 실패 알림 실패: auctionId={}", event.getAuctionId(), e);
        }
    }

    // 경매 종료 → 낙찰자/판매자 알림 + 패찰자 알림
    private void handleEnded(AuctionNotificationEventPayload event) {
        if (event.getWinnerId() != null) {
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
    private void handleBuyoutCompleted(AuctionNotificationEventPayload event) {
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
