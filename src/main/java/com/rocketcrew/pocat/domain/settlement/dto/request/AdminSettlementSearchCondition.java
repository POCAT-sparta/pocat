package com.rocketcrew.pocat.domain.settlement.dto.request;

import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AdminSettlementSearchCondition(
        SettlementStatus status,
        String sellerNickname,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
) {
}
