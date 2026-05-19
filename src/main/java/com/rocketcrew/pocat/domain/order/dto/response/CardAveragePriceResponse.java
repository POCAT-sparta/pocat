package com.rocketcrew.pocat.domain.order.dto.response;

import java.time.LocalDateTime;

public record CardAveragePriceResponse(
        Long cardId,
        Long averagePrice,
        long transactionCount,
        LocalDateTime periodStart,
        LocalDateTime periodEnd
) {}
