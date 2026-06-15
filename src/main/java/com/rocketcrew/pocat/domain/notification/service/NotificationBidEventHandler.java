package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.bid.event.BidOutbidEvent;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
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
public class NotificationBidEventHandler {

    private final NotificationCommandService notificationCommandService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(BidOutbidEvent event) {
        try {
            notificationCommandService.send(
                    event.getPreviousBidderId(),
                    NotificationType.BID_OUTBID,
                    "더 높은 입찰가가 등록되었습니다.",
                    Map.of("auctionId", event.getAuctionId(),
                           "currentHighestPrice", event.getCurrentHighestPrice())
            );
        } catch (Exception e) {
            log.error("Outbid 알림 실패: auctionId={}, previousBidderId={}",
                    event.getAuctionId(), event.getPreviousBidderId(), e);
        }
    }
}
