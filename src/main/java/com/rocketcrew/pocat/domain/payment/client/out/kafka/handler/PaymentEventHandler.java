package com.rocketcrew.pocat.domain.payment.client.out.kafka.handler;

import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.PaymentBaseEvent;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.producer.PaymentEventProducer;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PaymentBaseEvent 는 모든 결제 이벤트의 추상 상위 타입이라, 이 핸들러 하나가
 * 모든 결제 이벤트 하위 타입을 수신한다. 트랜잭션 경계(REQUIRES_NEW)는 베이스에 있다.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventHandler extends AbstractOutboxEventHandler<PaymentBaseEvent> {

    private final PaymentEventProducer paymentEventProducer;

    @Override
    protected void publish(PaymentBaseEvent event) {
        paymentEventProducer.publish(event);
    }
}
