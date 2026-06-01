package com.rocketcrew.pocat.domain.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.dto.event.NotificationEvent;
import com.rocketcrew.pocat.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationRepository notificationRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "notification",
            groupId = "notification-ws-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        try {
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);

            if (!notificationRepository.existsById(event.getNotificationId())) {
                log.warn("알림 없음(삭제됨), WebSocket 전송 생략: notificationId={}", event.getNotificationId());
                return;
            }

            // Redis Pub/Sub 발행 → NotificationRedisSubscriber → WebSocket
            stringRedisTemplate.convertAndSend("notification:" + event.getUserId(), message);
            log.debug("Redis 알림 발행 완료: userId={}, notificationId={}", event.getUserId(), event.getNotificationId());

        } catch (Exception e) {
            log.error("알림 Kafka 소비 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}
