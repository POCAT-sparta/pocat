package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.notification.dto.event.NotificationSendEvent;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementCompleteResponse;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminSettlementCommandService {

    private final SettlementRepository settlementRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SettlementCompleteResponse completeSettlement(String settlementUid) {
        Settlement settlement = settlementRepository.findWithLockBySettlementUid(settlementUid)
                .orElseThrow(() -> new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));
        settlement.complete();

        eventPublisher.publishEvent(new NotificationSendEvent(
                settlement.getSellerId(),
                NotificationType.SETTLEMENT_COMPLETED,
                String.format("정산이 완료되었습니다. 정산 금액: %,d원", settlement.getSellerAmount()),
                Map.of("settlementUid", settlement.getSettlementUid(), "sellerAmount", settlement.getSellerAmount())
        ));

        return SettlementCompleteResponse.from(settlement);
    }
}
