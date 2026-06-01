package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
    private final NotificationCommandService notificationCommandService;

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
            EscalationResult result = orderCommandService.escalateToNextRankWithDirectPayment(order.getOrderUid());

            switch (result.status()) {
                case ESCALATED -> {
                    try {
                        notificationCommandService.send(
                                result.nextBidderId(),
                                NotificationType.ESCALATED_PAYMENT_OPPORTUNITY,
                                "낙찰 기회가 생겼습니다. 1시간 내에 직접 결제를 진행해 주세요.",
                                Map.of("orderUid", result.nextOrderUid())
                        );
                    } catch (Exception e) {
                        log.error("[PaymentExpiry] 승격 결제 기회 알림 실패: nextBidderId={}", result.nextBidderId(), e);
                    }
                }
                case CANCELLED -> {
                    try {
                        notificationCommandService.send(
                                order.getSellerId(),
                                NotificationType.PAYMENT_FINAL_FAILED,
                                "구매자의 결제가 최종 실패하여 경매가 취소되었습니다.",
                                Map.of("orderUid", order.getOrderUid())
                        );
                    } catch (Exception e) {
                        log.error("[PaymentExpiry] 최종 결제 실패 판매자 알림 실패: orderId={}", orderId, e);
                    }
                }
                case SKIPPED -> log.info("[PaymentExpiry] 승격 처리 스킵 orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("[PaymentExpiry] 다음 순위 승격 실패 orderId={}", orderId, e);
        }
    }
}
