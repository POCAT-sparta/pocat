package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.notification.dto.response.NotificationListResponse;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketEventListener {

    private static final String NOTIFICATION_TOPIC_PREFIX = "/sub/notifications/";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationQueryService notificationQueryService;


    // WebSocket 연결
    // /ws 로 연결 요청이 들어오면 스프링이 자동으로 SessionConnectedEvent 발행
    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("WebSocket 연결: sessionId={}", accessor.getSessionId());
    }


    // 실시간 알림 수신 구독 — 구독 즉시 미읽음 알림 전송
    // /sub 으로 연결 요청이 들어오면 스프링이 자동으로 SessionSubscribeEvent 발행
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();

        if (destination == null || !destination.startsWith(NOTIFICATION_TOPIC_PREFIX)) {
            return;
        }

        try {
            Long pathUserId = Long.parseLong(destination.substring(NOTIFICATION_TOPIC_PREFIX.length()));

            Authentication auth = (Authentication) accessor.getUser();
            if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
                log.warn("알림 구독 인증 정보 없음: destination={}", destination);
                return;
            }
            if (!userDetails.getUserId().equals(pathUserId)) {
                log.warn("알림 구독 userId 불일치: authUserId={}, destination={}", userDetails.getUserId(), destination);
                return;
            }

            NotificationListResponse pending = notificationQueryService.getNotifications(pathUserId, null);

            // 구독한 세션에만 전송 — convertAndSend는 동일 destination의 모든 구독자에게 브로드캐스트되므로
            // 세션 ID를 헤더에 지정해 해당 세션에만 초기 목록을 전달
            SimpMessageHeaderAccessor replyHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            replyHeaders.setSessionId(accessor.getSessionId());
            replyHeaders.setLeaveMutable(true);
            messagingTemplate.convertAndSendToUser(
                    accessor.getSessionId(),
                    destination,
                    pending,
                    replyHeaders.getMessageHeaders()
            );
        } catch (NumberFormatException e) {
            log.warn("알림 구독 경로에서 userId 파싱 실패: destination={}", destination);
        }
    }

    // WebSocket 연결 해제
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        log.debug("WebSocket 연결 해제: sessionId={}", event.getSessionId());
    }
}
