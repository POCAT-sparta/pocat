package com.rocketcrew.pocat.domain.auction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.event.AuctionEventType;
import com.rocketcrew.pocat.domain.auction.redis.AuctionExpirationRedisService;
import com.rocketcrew.pocat.domain.auction.snapshot.service.AuctionSnapshotCommandService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionPostProcessConsumer {

    private final ObjectMapper objectMapper;
    private final AuctionExpirationRedisService auctionExpirationRedisService;
    private final AuctionSnapshotCommandService auctionSnapshotCommandService;

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
                case AuctionEventType.INSPECTION_PASSED, AuctionEventType.INSPECTION_FAILED ->
                        log.debug("Auction postprocess skipped inspection event. eventType={}, auctionId={}",
                                event.getEventType(), event.getAuctionId());
                default -> throw new AuctionException(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
            }
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
    }

    private void requireEventType(AuctionEventPayload event) {
        if (event == null) {
            throw new AuctionException(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        }
        if (event.getEventType() == null || event.getEventType().isBlank()) {
            throw new AuctionException(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        }
    }

    private void requireAuctionId(AuctionEventPayload event) {
        if (event.getAuctionId() == null) {
            throw new AuctionException(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        }
    }

    private void requireFinalPrice(AuctionEventPayload event) {
        if (event.getFinalPrice() == null) {
            throw new AuctionException(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        }
    }

    private void requireEndedAt(AuctionEventPayload event) {
        if (event.getEndedAt() == null) {
            throw new AuctionException(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        }
    }
}
