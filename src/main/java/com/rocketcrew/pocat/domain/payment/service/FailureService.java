package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.AutoPaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.DirectPaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.global.metrics.PaymentMetrics;
import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final PaymentMetrics paymentMetrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAutoPaymentFailure(Long paymentId, Long orderId) {
        Payment payment = paymentQueryService.findPaymentByIdWithLock(paymentId);
        Order order = findOrderForPaymentWithLock(payment, orderId);

        payment.fail();
        if (order.getOrderType() == OrderType.BUYOUT) {
            order.cancel("즉시구매 자동결제 실패");
            return;
        }

        order.failPayment();
        autoPaymentFailEvent(order.getOrderUid(), order.getBuyerId(), order.getSellerId());
    }

    public void autoPaymentFailEvent(String orderUid, Long buyerId, Long sellerId) {
        paymentMetrics.incrementAutoFail();
        AutoPaymentFailedEvent event = new AutoPaymentFailedEvent(
                orderUid,
                buyerId,
                sellerId
        );

        outboxEventWriter.write(PAYMENT_TOPIC, orderUid, event);
        eventPublisher.publishEvent(event);
    }

    public void directPaymentFailEvent(String orderUid, Long buyerId, Long sellerId) {
        paymentMetrics.incrementDirectFail();
        DirectPaymentFailedEvent event = new DirectPaymentFailedEvent(
                orderUid,
                buyerId,
                sellerId
        );

        outboxEventWriter.write(PAYMENT_TOPIC, orderUid, event);
        eventPublisher.publishEvent(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String paymentUid, Long orderId, PaymentErrorReason reason) {
        Payment payment = paymentQueryService.findPaymentByUidWithLock(paymentUid);
        Order order = findOrderForPaymentWithLock(payment, orderId);
        payment.fail();

        // 결제 기한이 지난 PENDING 주문은 직접결제 실패 이벤트를 발행해 후속 승격 처리를 진행한다.
        if (order.getPaymentDeadline().isBefore(LocalDateTime.now())
                && order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.failPayment();
            log.info("[OrderFailure] orderId={} reason={} -> FAILED", orderId, reason);
            directPaymentFailEvent(order.getOrderUid(), order.getBuyerId(), order.getSellerId());
        }
    }

    private Order findOrderForPaymentWithLock(Payment payment, Long orderId) {
        if (!payment.getOrderId().equals(orderId)) {
            throw new PaymentException(ErrorCode.PAYMENT_ORDER_MISMATCH);
        }
        return orderQueryService.findByOrderIdWithLock(orderId);
    }
}
