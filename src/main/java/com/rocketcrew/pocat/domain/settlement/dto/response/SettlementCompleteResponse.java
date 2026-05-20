package com.rocketcrew.pocat.domain.settlement.dto.response;

import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;

import java.time.LocalDateTime;

public record SettlementCompleteResponse(
        String settlementUid,
        SettlementStatus status,
        LocalDateTime settledAt
) {
    public static SettlementCompleteResponse from(Settlement settlement) {
        return new SettlementCompleteResponse(
                settlement.getSettlementUid(),
                settlement.getStatus(),
                settlement.getSettledAt()
        );
    }
}
