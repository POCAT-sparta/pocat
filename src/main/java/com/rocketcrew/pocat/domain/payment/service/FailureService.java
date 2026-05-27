package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 결제 실패 상태를 독립 트랜잭션으로 저장하는 서비스.
 * REQUIRES_NEW로 커밋하면 호출부 트랜잭션 롤백과 무관하게 실패 상태가 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailureService {

    static final String PAYMENT_EXPIRY_KEY_PREFIX = "order:expire";

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;

    // TODO : 실패 시 왜 실패했는지 받을 수 있어야 함 PORTONE API 확인 필요.
    // TODO : LocalDateTime.now().plusHours(24) -> order.getExpireAt 으로 변경해야 함: 승현님 작업 완료후 진행
    // TODO : 실패 이벤트 발행
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBillingKeyFailure(Payment payment, Order order, Long orderId) {
        payment.fail();
        order.failPayment();
        scheduleExpiry(orderId, LocalDateTime.now().plusHours(24));
    }

    /**
     * PENDING 결제를 Redis에 등록한다. expireAt까지 TTL이 지나면
     * PaymentExpiryEventHandler가 keyspace expired 이벤트를 수신해 markFailed를 호출한다.
     */
    public void scheduleExpiry(Long orderId, LocalDateTime expireAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), expireAt);
        if (ttl.isNegative() || ttl.isZero()) return;
        redisTemplate.opsForValue().setIfAbsent(
                PAYMENT_EXPIRY_KEY_PREFIX + orderId,
                String.valueOf(orderId),
                ttl
        );
    }

    /**
     * order FAILED 상태로 전환한다. PENDING이 아닌 경우 멱등성 보장을 위해 무시한다.
     * REQUIRES_NEW: 외부 트랜잭션 롤백과 무관하게 커밋되어야 하는 경우(즉시 실패 처리)와
     *               트랜잭션 없는 컨텍스트(TTL 만료 이벤트)에서 모두 사용된다.
     */
    // TODO : 만료시간이 실제로 지났는지 검사를 해야함. expireAt 이 생기면 진행
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long orderId) {
        orderRepository.findById(orderId)
                .filter(o -> o.getStatus() == OrderStatus.PAYMENT_PENDING)
                .ifPresent(p -> {
                    p.failPayment();
                    log.info("[OrderFailure] orderId={} → FAILED", orderId);
                });
    }

    /**
     * 결제가 성공적으로 완료되거나 이미 즉시 실패 처리될 때 Redis 만료 키를 정리한다.
     * Redis 오류는 결제 완료 처리와 무관하므로 예외를 흡수하고 로그만 남긴다.
     */
    public void cancelExpiry(Long orderId) {
        try {
            redisTemplate.delete(PAYMENT_EXPIRY_KEY_PREFIX + orderId);
        } catch (Exception e) {
            log.warn("[PaymentExpiry] 만료 키 삭제 실패 orderId={}", orderId, e);
        }
    }

}
