package com.rocketcrew.pocat.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.dto.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            NotificationEvent event = objectMapper.readValue(message.getBody(), NotificationEvent.class);
            // 연결된 유저에게 전송, 연결 안 된 유저라면 무시
            simpMessagingTemplate.convertAndSend("/sub/notifications/" + event.getUserId(), event);
        } catch (Exception e) {
            log.warn("알림 WebSocket 전송 실패", e);
        }
    }
}
