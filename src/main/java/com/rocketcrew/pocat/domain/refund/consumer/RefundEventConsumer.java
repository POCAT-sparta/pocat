package com.rocketcrew.pocat.domain.refund.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundEventConsumer {

    private final NotificationCommandService notificationCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "refund",
            groupId = "refund-notification-group",
            containerFactory = "refundKafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            RefundEvent event = objectMapper.readValue(message, RefundEvent.class);
            switch (event.getEventType()) {
                case "refund.requested" -> handleRefundRequested(event);
                case "refund.approved"  -> handleRefundApproved(event);
                case "refund.rejected"  -> handleRefundRejected(event);
                default -> log.warn("알 수 없는 refund 이벤트: {}", event.getEventType());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("refund 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    // 환불 요청 → 구매자 알림 (관리자 검토 대기)
    private void handleRefundRequested(RefundEvent event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.REFUND_REQUESTED,
                    "환불 요청이 접수되었습니다. 검토 후 처리됩니다.",
                    Map.of("refundId", event.getRefundId(), "orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("환불 요청 알림 실패: refundId={}", event.getRefundId(), e);
        }
    }

    // 환불 승인 → 구매자 + 판매자 알림
    private void handleRefundApproved(RefundEvent event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.REFUND_APPROVED,
                    "환불이 승인되었습니다.",
                    Map.of("refundId", event.getRefundId(), "orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("구매자 환불 승인 알림 실패: refundId={}", event.getRefundId(), e);
        }
        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.REFUND_APPROVED,
                    "구매자의 환불 요청이 승인되었습니다.",
                    Map.of("refundId", event.getRefundId(), "orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("판매자 환불 승인 알림 실패: refundId={}", event.getRefundId(), e);
        }
    }

    // 환불 거절 → 구매자 알림
    private void handleRefundRejected(RefundEvent event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.REFUND_REJECTED,
                    "환불 요청이 거절되었습니다: " + event.getReason(),
                    Map.of("refundId", event.getRefundId(), "orderUid", event.getOrderUid(),
                           "reason", event.getReason() != null ? event.getReason() : "")
            );
        } catch (Exception e) {
            log.error("환불 거절 알림 실패: refundId={}", event.getRefundId(), e);
        }
    }
}
