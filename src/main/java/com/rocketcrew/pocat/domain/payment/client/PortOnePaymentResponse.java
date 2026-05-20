package com.rocketcrew.pocat.domain.payment.client;

import java.time.LocalDateTime;

public record PortOnePaymentResponse(
        String status,
        Long amount,
        String paymentMethod,
        LocalDateTime paidAt
) {}
