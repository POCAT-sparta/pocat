package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.order.event.OrderEscalatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOrderEscalationEventHandler {

    private final NotificationCommandService notificationCommandService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderEscalatedEvent event) {
        switch (event.getStatus()) {
            case ESCALATED -> {
                try {
                    notificationCommandService.send(
                            event.getNextBidderId(),
                            NotificationType.ESCALATED_PAYMENT_OPPORTUNITY,
                            "낙찰 기회가 생겼습니다. 1시간 내에 직접 결제를 진행해 주세요.",
                            Map.of("orderUid", event.getNextOrderUid())
                    );
                } catch (Exception e) {
                    log.error("[PAYMENT_ESCALATION] 승격 결제 기회 알림 실패 nextBidderId={}: {}", event.getNextBidderId(), e.getMessage(), e);
                }
            }
            case CANCELLED -> {
                try {
                    notificationCommandService.send(
                            event.getSellerId(),
                            NotificationType.PAYMENT_FINAL_FAILED,
                            "구매자의 결제가 최종 실패하여 경매가 취소되었습니다.",
                            Map.of("orderUid", event.getOrderUid())
                    );
                } catch (Exception e) {
                    log.error("[PAYMENT_ESCALATION] 최종 결제 실패 판매자 알림 실패 orderUid={}: {}", event.getOrderUid(), e.getMessage(), e);
                }
            }
            case SKIPPED -> { }
        }
    }
}
