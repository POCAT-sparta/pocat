package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementCompleteResponse;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCompletedEvent;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminSettlementCommandService {

    private final SettlementRepository settlementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;

    public SettlementCompleteResponse completeSettlement(String settlementUid) {
        Settlement settlement = settlementRepository.findWithLockBySettlementUid(settlementUid)
                .orElseThrow(() -> new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));
        settlement.complete();

        SettlementCompletedEvent event = new SettlementCompletedEvent(
                settlement.getSettlementUid(),
                settlement.getSellerId(),
                settlement.getSellerAmount()
        );
        outboxEventWriter.write("settlement", settlement.getSettlementUid(), event);
        eventPublisher.publishEvent(event);

        return SettlementCompleteResponse.from(settlement);
    }
}
