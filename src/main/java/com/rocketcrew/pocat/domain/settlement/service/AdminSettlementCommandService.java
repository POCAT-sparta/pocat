package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementCompleteResponse;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCompletedEvent;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
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

    public SettlementCompleteResponse completeSettlement(String settlementUid) {
        Settlement settlement = settlementRepository.findWithLockBySettlementUid(settlementUid)
                .orElseThrow(() -> new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));
        settlement.complete();

        // 정산 완료 이벤트 발행
        eventPublisher.publishEvent(new SettlementCompletedEvent(
                settlement.getSettlementUid(),
                settlement.getSellerId(),
                settlement.getSellerAmount()
        ));
        return SettlementCompleteResponse.from(settlement);
    }
}
