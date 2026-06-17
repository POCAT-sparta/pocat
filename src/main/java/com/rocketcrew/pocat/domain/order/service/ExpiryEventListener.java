package com.rocketcrew.pocat.domain.order.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiryEventListener {

    private final OrderRepository orderRepository;
    private final OrderCommandService orderCommandService;
    private final RedissonClient redissonClient;

    private RPatternTopic topic;
    private int listenerId;

    @PostConstruct
    public void subscribe() {
        topic = redissonClient.getPatternTopic("__keyevent@*__:expired", StringCodec.INSTANCE);
        listenerId = topic.addListener(String.class, (pattern, channel, expiredKey) -> {
            if (!expiredKey.startsWith(SetExpireService.PAYMENT_EXPIRY_KEY_PREFIX)) return;

            String orderIdStr = expiredKey.substring(SetExpireService.PAYMENT_EXPIRY_KEY_PREFIX.length());
            Long orderId;
            try {
                orderId = Long.parseLong(orderIdStr);
            } catch (NumberFormatException e) {
                log.warn("[PAYMENT_ESCALATION] 결제 만료 키 파싱 불가 key={}", expiredKey);
                return;
            }

            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                log.warn("[PAYMENT_ESCALATION] 승격 대상 주문 없음 orderId={}", orderId);
                return;
            }

            try {
                EscalationResult result = orderCommandService.escalateToNextRankWithDirectPayment(order.getOrderUid());
                if (result.status() == EscalationResult.Status.SKIPPED) {
                    log.info("[PAYMENT_ESCALATION] 승격 처리 스킵 orderId={}", orderId);
                }
            } catch (Exception e) {
                log.error("[PAYMENT_ESCALATION] 다음 순위 승격 실패 orderId={}: {}", orderId, e.getMessage(), e);
            }
        });
    }

    @PreDestroy
    public void unsubscribe() {
        if (topic != null) {
            topic.removeListener(listenerId);
        }
    }
}
