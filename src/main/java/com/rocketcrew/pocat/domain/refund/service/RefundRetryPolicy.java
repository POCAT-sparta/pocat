package com.rocketcrew.pocat.domain.refund.service;

import java.time.LocalDateTime;

public final class RefundRetryPolicy {

    private static final int MAX_RETRY_COUNT = 5;

    private RefundRetryPolicy() {}

    public static LocalDateTime calculateNextRetryAt(int retryCount, LocalDateTime now) {
        int minutes = switch (retryCount) {
            case 0 -> 1;
            case 1 -> 5;
            case 2 -> 15;
            default -> 30;
        };
        return now.plusMinutes(minutes);
    }

    public static boolean isAutoRetryExhausted(int retryCount) {
        return retryCount >= MAX_RETRY_COUNT;
    }
}
