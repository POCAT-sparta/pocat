package com.rocketcrew.pocat.domain.settlement.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCompletedEvent;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCreatedEvent;
import com.rocketcrew.pocat.global.event.BaseEventProducer;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SettlementEventProducer extends BaseEventProducer {

    private static final String TOPIC = "settlement";

    public SettlementEventProducer(
            @Qualifier("settlementKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            OutboxRepository outboxRepository) {
        super(kafkaTemplate, objectMapper, outboxRepository);
    }

    // 정산 생성
    public void sendSettlementCreated(SettlementCreatedEvent event) {
        send(TOPIC, event.getSettlementUid(), event);
    }

    // 정산 완료
    public void sendSettlementCompleted(SettlementCompletedEvent event) {
        send(TOPIC, event.getSettlementUid(), event);
    }
}
