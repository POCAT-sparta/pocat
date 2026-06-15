package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCreatedEvent;
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
public class NotificationSettlementEventHandler {

    private final NotificationCommandService notificationCommandService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(SettlementCreatedEvent event) {
        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.SETTLEMENT_CREATED,
                    String.format("결제가 완료되어 정산이 시작되었습니다. 정산 예정 금액: %,d원", event.getSellerAmount()),
                    Map.of("settlementUid", event.getSettlementUid(), "sellerAmount", event.getSellerAmount())
            );
        } catch (Exception e) {
            log.error("정산 생성 알림 실패: settlementUid={}", event.getSettlementUid(), e);
        }
    }
}
