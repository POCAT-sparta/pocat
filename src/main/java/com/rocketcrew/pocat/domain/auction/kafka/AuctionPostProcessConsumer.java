package com.rocketcrew.pocat.domain.auction.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.event.AuctionEventType;
import com.rocketcrew.pocat.domain.auction.redis.AuctionExpirationRedisService;
import com.rocketcrew.pocat.domain.auction.snapshot.service.AuctionSnapshotCommandService;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.InvalidAuctionEventPayloadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionPostProcessConsumer {

    private final ObjectMapper objectMapper;
    private final AuctionExpirationRedisService auctionExpirationRedisService;
    private final AuctionSnapshotCommandService auctionSnapshotCommandService;
    private final NotificationCommandService notificationCommandService;

    @KafkaListener(
            topics = "auction",
            groupId = "auction-postprocess-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        try {
            AuctionEventPayload event = objectMapper.readValue(message, AuctionEventPayload.class);
            requireEventType(event);
            switch (event.getEventType()) {
                case AuctionEventType.ACTIVATED -> handleActivated(event);
                case AuctionEventType.ENDED -> handleEnded(event);
                case AuctionEventType.BUYOUT_COMPLETED -> handleBuyoutCompleted(event);
                case AuctionEventType.CANCELLED -> handleCancelled(event);
                case AuctionEventType.INSPECTION_PASSED -> handleInspectionPassed(event);
                case AuctionEventType.INSPECTION_FAILED -> handleInspectionFailed(event);
                default -> log.debug("Auction postprocess skipped unknown event. eventType={}, auctionId={}",
                        event.getEventType(), event.getAuctionId());
            }
        } catch (JsonProcessingException e) {
            log.error("Auction postprocess invalid payload. message={}", message, e);
            throw new InvalidAuctionEventPayloadException(e);
        } catch (AuctionException e) {
            log.error("Auction postprocess failed. message={}", message, e);
            throw e;
        } catch (Exception e) {
            log.error("Auction postprocess failed. message={}", message, e);
            throw new RuntimeException(e);
        }
    }

    private void handleActivated(AuctionEventPayload event) {
        requireAuctionId(event);
        requireEndedAt(event);
        auctionExpirationRedisService.setExpirationKeys(event.getAuctionId(), event.getEndedAt());
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

    private void handleEnded(AuctionEventPayload event) {
        requireAuctionId(event);
        auctionExpirationRedisService.deleteExpirationKeys(event.getAuctionId());
        auctionSnapshotCommandService.createSnapshot(event.getAuctionId(), event.getFinalPrice());
    }

    private void handleBuyoutCompleted(AuctionEventPayload event) {
        requireAuctionId(event);
        requireFinalPrice(event);
        auctionExpirationRedisService.deleteExpirationKeys(event.getAuctionId());
        auctionSnapshotCommandService.createSnapshot(event.getAuctionId(), event.getFinalPrice());
    }

    private void handleCancelled(AuctionEventPayload event) {
        requireAuctionId(event);
        auctionExpirationRedisService.deleteExpirationKeys(event.getAuctionId());
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

    private void handleInspectionPassed(AuctionEventPayload event) {
        requireAuctionId(event);
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

    private void handleInspectionFailed(AuctionEventPayload event) {
        requireAuctionId(event);
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

    private void requireEventType(AuctionEventPayload event) {
        if (event == null) {
            throw new InvalidAuctionEventPayloadException();
        }
        if (event.getEventType() == null || event.getEventType().isBlank()) {
            throw new InvalidAuctionEventPayloadException();
        }
    }

    private void requireAuctionId(AuctionEventPayload event) {
        if (event.getAuctionId() == null) {
            throw new InvalidAuctionEventPayloadException();
        }
    }

    private void requireFinalPrice(AuctionEventPayload event) {
        if (event.getFinalPrice() == null) {
            throw new InvalidAuctionEventPayloadException();
        }
    }

    private void requireEndedAt(AuctionEventPayload event) {
        if (event.getEndedAt() == null) {
            throw new InvalidAuctionEventPayloadException();
        }
    }
}
