package com.rocketcrew.pocat.domain.refund.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.refund.event.RefundApprovedEvent;
import com.rocketcrew.pocat.domain.refund.event.RefundRejectedEvent;
import com.rocketcrew.pocat.domain.refund.event.RefundRequestedEvent;
import com.rocketcrew.pocat.global.event.BaseEventProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RefundEventProducer extends BaseEventProducer {

    private static final String TOPIC = "refund";

    public RefundEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        super(kafkaTemplate, objectMapper);
    }

    // 환불 요철
    public void sendRefundRequested(RefundRequestedEvent event) {
        send(TOPIC, event.getOrderUid(), event);
    }

    // 환불 승인
    public void sendRefundApproved(RefundApprovedEvent event) {
        send(TOPIC, event.getOrderUid(), event);
    }

    // 환불 거절
    public void sendRefundRejected(RefundRejectedEvent event) {
        send(TOPIC, event.getOrderUid(), event);
    }
}
