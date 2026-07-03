package com.rocketcrew.pocat.domain.order.kafka.consumer.auctionEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.event.AuctionEventType;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventConsumer {

    private final OrderCommandService orderCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "auction",
            groupId = "order-auction-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        try {
            AuctionEvent event = objectMapper.readValue(message, AuctionEvent.class);
            switch (event.getEventType()) {
                case AuctionEventType.ENDED -> handleAuctionEnded(event);
                default -> log.debug("처리 대상 아닌 auction 이벤트: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("auction 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    // 경매 종료 → 낙찰 주문 생성
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
        }
    }
}
