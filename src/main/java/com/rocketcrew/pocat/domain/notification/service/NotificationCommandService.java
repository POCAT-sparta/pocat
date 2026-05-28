package com.rocketcrew.pocat.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.dto.event.NotificationEvent;
import com.rocketcrew.pocat.domain.notification.dto.response.NotificationResponse;
import com.rocketcrew.pocat.domain.notification.entity.Notification;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.repository.NotificationRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.NotificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC = "notification";

    // 서비스 레이어에서는 eventPublisher.publishEvent(NotificationSendEvent) 사용
    // Kafka 컨슈머처럼 트랜잭션 컨텍스트 밖에서 호출할 경우 sendFromConsumer() 사용
    void sendInternal(Long userId, NotificationType type, String message, Object relatedData) {
        String relatedDataJson = toJson(relatedData);
        Notification notification = Notification.create(userId, type, message, relatedDataJson);
        notificationRepository.save(notification);

        NotificationEvent event = NotificationEvent.builder()
                .notificationId(notification.getId())
                .userId(userId)
                .type(type.name())
                .message(message)
                .relatedData(relatedData)
                .createdAt(notification.getCreatedAt())
                .build();

        kafkaTemplate.send(TOPIC,
                String.valueOf(userId), // Key: userId 기준 파티션
                toJson(event))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 알림 전송 실패 - notificationId={}, userId={}", notification.getId(), userId, ex);
                    }
                });
    }

    // Kafka 컨슈머 전용 — 이미 커밋된 이벤트를 소비하여 알림 발송 시 사용
    public void send(Long userId, NotificationType type, String message, Object relatedData) {
        sendInternal(userId, type, message, relatedData);
    }

    // 개별 읽음 처리
    public NotificationResponse read(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getUserId().equals(userId)) {
            throw new NotificationException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
        notification.read();
        return NotificationResponse.from(notification);
    }

    // 전체 읽음 처리 (미읽음만)
    public void readAll(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    // 개별 삭제
    public void delete(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getUserId().equals(userId)) {
            throw new NotificationException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
        notificationRepository.delete(notification);
    }

    // 전체 삭제
    public void deleteAll(Long userId) {
        notificationRepository.softDeleteAllByUserId(userId);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new NotificationException(ErrorCode.NOTIFICATION_SERIALIZE_FAILED);
        }
    }
}
