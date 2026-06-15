package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis keyspace expired 이벤트를 수신하여 1시간 결제 창이 만료된 주문을 다음 순위 입찰자에게 넘긴다.
 * Redis에 "notify-keyspace-events Ex" 설정이 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiryEventListener implements MessageListener {

    private final OrderRepository orderRepository;
    private final OrderCommandService orderCommandService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
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
    }
}
