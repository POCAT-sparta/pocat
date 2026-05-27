package com.rocketcrew.pocat.domain.settlement.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventConsumer {

    private final SettlementRepository settlementRepository;
    private final NotificationCommandService notificationCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "settlement",
            groupId = "settlement-notification-group",
            containerFactory = "settlementKafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            SettlementEvent event = objectMapper.readValue(message, SettlementEvent.class);
            switch (event.getEventType()) {
                case "settlement.completed" -> handleSettlementCompleted(event);
                case "settlement.created"   -> log.debug("settlement.created 수신 — 현재 별도 처리 없음");
                default -> log.warn("알 수 없는 settlement 이벤트: {}", event.getEventType());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("정산 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    // 정산 완료 → 판매자 알림 (멱등성: DB에서 COMPLETED 상태 확인)
    private void handleSettlementCompleted(SettlementEvent event) {
        Optional<Settlement> settlementOpt = settlementRepository.findBySettlementUidAndSellerId(event.getSettlementUid(), event.getSellerId());
        if (settlementOpt.isEmpty()) {
            log.warn("settlement.completed 처리 - 정산 없음: settlementUid={}", event.getSettlementUid());
            return;
        }
        if (settlementOpt.get().getStatus() != SettlementStatus.COMPLETED) {
            log.warn("settlement.completed 처리 - COMPLETED 상태 아님: settlementUid={}, status={}",
                    event.getSettlementUid(), settlementOpt.get().getStatus());
            return;
        }

        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.SETTLEMENT_COMPLETED,
                    String.format("정산이 완료되었습니다. 정산 금액: %,d원", event.getSellerAmount()),
                    Map.of("settlementUid", event.getSettlementUid(), "sellerAmount", event.getSellerAmount())
            );
        } catch (Exception e) {
            log.error("정산 완료 알림 실패: settlementUid={}", event.getSettlementUid(), e);
        }
    }
}
