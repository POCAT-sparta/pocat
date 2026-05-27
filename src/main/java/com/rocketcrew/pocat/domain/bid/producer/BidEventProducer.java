package com.rocketcrew.pocat.domain.bid.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.bid.event.BidCreatedEvent;
import com.rocketcrew.pocat.domain.bid.event.BidOutbidEvent;
import com.rocketcrew.pocat.global.event.BaseEventProducer;
import com.rocketcrew.pocat.global.event.outbox.OutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BidEventProducer extends BaseEventProducer {

    private static final String TOPIC = "bid";

    public BidEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            OutboxRepository outboxRepository) {
        super(kafkaTemplate, objectMapper, outboxRepository);
    }

    // 입찰 생성
    public void sendBidCreated(BidCreatedEvent event) {
        send(TOPIC, String.valueOf(event.getAuctionId()), event);
    }

    // 밀려난 입찰
    public void sendBidOutbid(BidOutbidEvent event) {
        send(TOPIC, String.valueOf(event.getAuctionId()), event);
    }
}
