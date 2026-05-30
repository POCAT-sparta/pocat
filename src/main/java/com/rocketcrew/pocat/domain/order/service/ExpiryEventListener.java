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
            log.warn("[PaymentExpiry] 파싱 불가 key={}", expiredKey);
            return;
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("[PaymentExpiry] 주문 없음 orderId={}", orderId);
            return;
        }

        try {
            orderCommandService.escalateToNextRank(order.getOrderUid());
        } catch (Exception e) {
            log.error("[PaymentExpiry] 다음 순위 주문 생성 실패 orderId={}", orderId, e);
        }
    }
}
