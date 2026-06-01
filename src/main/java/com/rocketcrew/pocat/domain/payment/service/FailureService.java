package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.AutoPaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.DirectPaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import static com.rocketcrew.pocat.domain.payment.client.out.kafka.producer.PaymentEventProducer.PAYMENT_TOPIC;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailureService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBillingKeyFailure(Long paymentId, Long orderId) {
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
        payment.fail();
        if (order.getOrderType() == OrderType.BUYOUT) {
            order.cancel("즉시구매 자동결제 실패");
            return;
        }
        order.failPayment();
        AutoPaymentFailedEvent event = new AutoPaymentFailedEvent(
                order.getOrderUid(),
                order.getBuyerId(),
                order.getSellerId()
        );
        outboxEventWriter.write(PAYMENT_TOPIC, order.getOrderUid(), event);
        eventPublisher.publishEvent(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long orderId, PaymentErrorReason reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        // 스케줄러, ttl웹훅 어디서 호출해도 멱등하게 상태 처리를 해야함.
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.failPayment();
            log.info("[OrderFailure] orderId={} reason={} → FAILED", orderId, reason);
            DirectPaymentFailedEvent event = new DirectPaymentFailedEvent(
                            order.getOrderUid(),
                            order.getBuyerId(),
                            order.getSellerId()

            );
            outboxEventWriter.write(PAYMENT_TOPIC, order.getOrderUid(), event);
            eventPublisher.publishEvent(event);
        }
    }

}
