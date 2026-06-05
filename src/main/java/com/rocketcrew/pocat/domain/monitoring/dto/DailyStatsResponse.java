package com.rocketcrew.pocat.domain.monitoring.dto;

import java.time.LocalDate;

public record DailyStatsResponse(
        LocalDate reportDate,
        long newAuctions,
        long completedOrders,
        long totalTradeVolume,
        long activeAuctions,
        long endedAuctions,
        long noBidderAuctions
) {}
