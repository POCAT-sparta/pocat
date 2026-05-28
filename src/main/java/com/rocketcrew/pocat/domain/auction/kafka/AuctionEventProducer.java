package com.rocketcrew.pocat.domain.auction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.event.*;
import com.rocketcrew.pocat.global.event.BaseEventProducer;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuctionEventProducer extends BaseEventProducer {

    private static final String TOPIC = "auction";

    public AuctionEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper,
                                OutboxRepository outboxRepository) {
        super(kafkaTemplate, objectMapper, outboxRepository);
    }

    // 검수 통과
    public void sendInspectionPassed(AuctionInspectionPassedEvent event) {
        send(TOPIC, String.valueOf(event.getAuctionId()), event);
    }

    // 검수 실패
    public void sendInspectionFailed(AuctionInspectionFailedEvent event) {
        send(TOPIC, String.valueOf(event.getAuctionId()), event);
    }

    // 경매 활성화
    public void sendActivated(AuctionActivatedEvent event) {
        send(TOPIC, String.valueOf(event.getAuctionId()), event);
    }

    // 경매 취소
    public void sendCancelled(AuctionCancelledEvent event) {
        send(TOPIC, String.valueOf(event.getAuctionId()), event);
    }

    // 경매 종료
    public void sendEnded(AuctionEndedEvent event) {
        send(TOPIC, String.valueOf(event.getAuctionId()), event);
    }
}
