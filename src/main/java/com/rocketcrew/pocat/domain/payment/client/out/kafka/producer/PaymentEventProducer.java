package com.rocketcrew.pocat.domain.payment.client.out.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.PaymentBaseEvent;
import com.rocketcrew.pocat.global.event.BaseEventProducer;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer extends BaseEventProducer {

    public static final String PAYMENT_TOPIC = "payment";

    public PaymentEventProducer(
            @Qualifier("paymentKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            OutboxRepository outboxRepository) {
        super(kafkaTemplate, objectMapper, outboxRepository);
    }

    public void publish(PaymentBaseEvent event) {
        send(PAYMENT_TOPIC, event.getOrderUid(), event.getOutboxId(), event);
    }
}
