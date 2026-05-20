package com.rocketcrew.pocat.domain.settlement.dto.response;

import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;

import java.time.LocalDateTime;

public record AdminSettlementResponse(
        String settlementUid,
        String orderUid,
        String sellerNickname,
        String bankName,
        String bankAccount,
        String cardName,
        String cardGrade,
        Long totalPrice,
        Long platformFee,
        Long sellerAmount,
        SettlementStatus status,
        LocalDateTime settledAt,
        LocalDateTime createdAt
) {
}
