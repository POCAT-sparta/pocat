package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.AutoPaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.DirectPaymentFailedEvent;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import static com.rocketcrew.pocat.domain.payment.client.out.kafka.producer.PaymentEventProducer.PAYMENT_TOPIC;

/**
 * 결제 실패 상태를 독립 트랜잭션으로 저장하는 서비스.
 * REQUIRES_NEW로 커밋하면 호출부 트랜잭션 롤백과 무관하게 실패 상태가 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailureService {

    public static final String PAYMENT_EXPIRY_KEY_PREFIX = "order:expire";
    public static final String PAYMENT_SHADOW_KEY_PREFIX = "order:shadow";

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBillingKeyFailure(Payment payment, Order order,Boolean evnetFlag) {
        payment.fail();
        order.failPayment();
        // 경매 종료 후 자동 결제 일때만 redis저장
        if(evnetFlag){
            Duration ttl = Duration.between(LocalDateTime.now(), order.getPaymentDeadline());
            scheduleExpiry(order.getId(), ttl);
        }
        AutoPaymentFailedEvent event = new AutoPaymentFailedEvent(
                order.getOrderUid(),
                order.getBuyerId(),
                order.getSellerId()
        );
        outboxEventWriter.write(PAYMENT_TOPIC, order.getOrderUid(), event);
        eventPublisher.publishEvent(event);
    }

    /**
     * PENDING 결제를 Redis에 등록한다. expireAt까지 TTL이 지나면
     * PaymentExpiryEventHandler가 keyspace expired 이벤트를 수신해 markFailed를 호출한다.
     */
    public void scheduleExpiry(Long orderId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) return;

        redisTemplate.opsForValue().setIfAbsent(
                PAYMENT_EXPIRY_KEY_PREFIX + orderId,
                String.valueOf(orderId),
                ttl
        );
        markAsProcessing(orderId);
    }

    private void markAsProcessing(Long orderId) {
        redisTemplate.opsForValue().setIfAbsent(PAYMENT_SHADOW_KEY_PREFIX + orderId, String.valueOf(orderId));
    }

    /**
     * order FAILED 상태로 전환한다. PENDING이 아닌 경우 멱등성 보장을 위해 무시한다.
     * REQUIRES_NEW: 외부 트랜잭션 롤백과 무관하게 커밋되어야 하는 경우(즉시 실패 처리)와
     *               트랜잭션 없는 컨텍스트(TTL 만료 이벤트)에서 모두 사용된다.
     */
    // TODO : 만료시간이 실제로 지났는지 검사를 해야함. expireAt 이 생기면 진행
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

    /**
     * 결제가 성공적으로 완료되거나 이미 즉시 실패 처리될 때 Redis 키를 정리한다.
     * TTL 만료 경로(onMessage)에서는 TTL키가 이미 사라진 상태이므로 shadow키만 추가 삭제한다.
     */
    public void cancelExpiry(Long orderId) {
        redisTemplate.delete(PAYMENT_EXPIRY_KEY_PREFIX + orderId);
        redisTemplate.delete(PAYMENT_SHADOW_KEY_PREFIX + orderId);
    }

}
