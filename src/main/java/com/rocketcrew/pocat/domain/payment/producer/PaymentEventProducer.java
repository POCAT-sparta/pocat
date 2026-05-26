package com.rocketcrew.pocat.domain.payment.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.payment.event.PaymentBillingRequestedEvent;
import com.rocketcrew.pocat.domain.payment.event.PaymentCompletedEvent;
import com.rocketcrew.pocat.domain.payment.event.PaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.event.PaymentWindowExpiredEvent;
import com.rocketcrew.pocat.global.event.BaseEventProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer extends BaseEventProducer {


    private static final String TOPIC = "payment";

    public PaymentEventProducer(
            @Qualifier("paymentKafkaTemplate")
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        super(kafkaTemplate, objectMapper);
    }

    // 자동결제 요청
    public void sendBillingRequested(PaymentBillingRequestedEvent event) {
        send(TOPIC, event.getOrderUid(), event);
    }

    // 결제 완료
    public void sendPaymentCompleted(PaymentCompletedEvent event) {
        send(TOPIC, event.getOrderUid(), event);
    }

    // 결제 실패
    public void sendPaymentFailed(PaymentFailedEvent event) {
        send(TOPIC, event.getOrderUid(), event);
    }

    // 직접 결제 가능 시간 만료
    public void sendWindowExpired(PaymentWindowExpiredEvent event) {
        send(TOPIC, event.getOrderUid(), event);
    }
}
