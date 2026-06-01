package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.AutoPaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.DirectPaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;

import java.time.LocalDateTime;

import static com.rocketcrew.pocat.domain.payment.client.out.kafka.producer.PaymentEventProducer.PAYMENT_TOPIC;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailureService {

    private final OrderQueryService orderQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;
    private final PaymentQueryService paymentQueryService;

    public void autoPaymentFailEvent(String orderUid, Long buyerId, Long sellerId) {
        AutoPaymentFailedEvent event = new AutoPaymentFailedEvent(
                orderUid,
                buyerId,
                sellerId
        );

        outboxEventWriter.write(PAYMENT_TOPIC, orderUid, event);
        eventPublisher.publishEvent(event);
    }

    public void directPaymentFailEvent(String orderUid, Long buyerId, Long sellerId) {
        DirectPaymentFailedEvent event = new DirectPaymentFailedEvent(
                orderUid,
                buyerId,
                sellerId

        );
        outboxEventWriter.write(PAYMENT_TOPIC, orderUid, event);
        eventPublisher.publishEvent(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String paymentUId, Long orderId, PaymentErrorReason reason) {
        Payment payment = paymentQueryService.findPaymentByUidWithLock(paymentUId);
        Order order = orderQueryService.findByOrderIdWithLock(orderId);
        payment.fail();

        // 시간이 지나고 주문이 아직 pending인 경우
        if(order.getPaymentDeadline().isBefore(LocalDateTime.now()) && order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.failPayment();
            log.info("[OrderFailure] orderId={} reason={} → FAILED", orderId, reason);
            directPaymentFailEvent(order.getOrderUid(), order.getBuyerId(), order.getSellerId());
        }
    }
}
