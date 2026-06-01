package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.order.service.SetExpireService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.PaymentCompletedEvent;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
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

    private final StringRedisTemplate redisTemplate;
    private final PaymentRepository paymentRepository;
    private final SetExpireService setExpireService;
    private final OrderQueryService orderQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;
    private final PaymentQueryService paymentQueryService;

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
    public void completePayment(Payment payment, Order order, String paymentMethod, LocalDateTime paidAt) {
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
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handelFailed(String paymentUId, Long orderId){
        Payment payment = paymentQueryService.findPaymentByUidWithLock(paymentUId);
        Order order = orderQueryService.findByOrderIdWithLock(orderId);
        payment.fail();
        order.failPayment();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handelCancel(String paymentUId, Long orderId){
        Order order = orderQueryService.findByOrderIdWithLock(orderId);
        Payment payment = paymentQueryService.findPaymentByUidWithLock(paymentUId);
        payment.cancel();
        order.failPayment();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelFailPayment(String paymentUid) {
        Payment payment = paymentQueryService.findPaymentByUid(paymentUid);
        payment.cancelFailed();
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
}
