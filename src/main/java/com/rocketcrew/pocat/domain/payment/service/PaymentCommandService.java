package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.order.service.SetExpireService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.PaymentCompletedEvent;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.global.util.TsidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.rocketcrew.pocat.domain.payment.client.out.kafka.producer.PaymentEventProducer.PAYMENT_TOPIC;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private static final String AVG_PRICE_CACHE_PREFIX = "card:avgprice:";

    private final PaymentRepository paymentRepository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;
    private final SetExpireService setExpireService;
    private final OrderQueryService orderQueryService;
    private final OrderRepository orderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createPayment(Long orderId, PaymentType paymentType) {
        Order order = orderQueryService.findByOrderid(orderId);

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .paymentUid(generatePaymentUid())
                .amount(order.getFinalPrice())
                .paymentType(paymentType)
                .status(PaymentStatus.PENDING)
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingKeyPayment createBillingKeyPaymentIfAbsent(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        return paymentRepository.findByOrderIdAndPaymentType(orderId, PaymentType.BILLING_KEY)
                .map(payment -> new BillingKeyPayment(payment, false))
                .orElseGet(() -> new BillingKeyPayment(paymentRepository.save(Payment.builder()
                        .orderId(order.getId())
                        .paymentUid(generatePaymentUid())
                        .amount(order.getFinalPrice())
                        .paymentType(PaymentType.BILLING_KEY)
                        .status(PaymentStatus.PENDING)
                        .build()), true));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment completePayment(Long paymentId, Long orderId, String paymentMethod, LocalDateTime paidAt) {
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        payment.complete(paymentMethod, paidAt);
        order.completePayment();
        evictAvgPriceCache(order.getCardId());

        setExpireService.cancelExpiry(payment.getOrderId());

        PaymentCompletedEvent event = new PaymentCompletedEvent(
                order.getOrderUid(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getFinalPrice()
        );
        outboxEventWriter.write(PAYMENT_TOPIC, order.getOrderUid(), event);
        eventPublisher.publishEvent(event);

        return payment;
    }

    private String generatePaymentUid() {
        return TsidGenerator.generatePaymentUid();
    }

    private void evictAvgPriceCache(Long cardId) {
        try {
            redisTemplate.delete(AVG_PRICE_CACHE_PREFIX + cardId);
        } catch (Exception e) {
            log.warn("[CardCache] 평균가 캐시 삭제 실패 cardId={}", cardId, e);
        }
    }

    public record BillingKeyPayment(Payment payment, boolean created) {
    }
}
