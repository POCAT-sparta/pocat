package com.rocketcrew.pocat.domain.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis keyspace expired 이벤트를 수신하여 TTL이 만료된 PENDING 결제를 FAILED 처리한다.
 * Redis에 "notify-keyspace-events Ex" 설정이 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryEventListener implements MessageListener {

    private final FailureService failureService;

    /**
     * ttl 만료시 이벤트를 받아서 완전 실패 처리
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!expiredKey.startsWith(FailureService.PAYMENT_EXPIRY_KEY_PREFIX)) return;

        String orderIdStr = expiredKey.substring(FailureService.PAYMENT_EXPIRY_KEY_PREFIX.length());
        try {
            Long orderId = Long.parseLong(orderIdStr);
            failureService.markFailed(orderId, "결제 시간 초과", "AUTO");
            failureService.cancelExpiry(orderId);
        } catch (NumberFormatException e) {
            log.warn("[PaymentExpiry] 파싱 불가 key={}", expiredKey);
        }
    }
}
