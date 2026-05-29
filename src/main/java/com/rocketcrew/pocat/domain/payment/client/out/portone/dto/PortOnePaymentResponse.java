package com.rocketcrew.pocat.domain.payment.client.out.portone.dto;

import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PortOnePaymentResponse(
        PortOneStatus status,
        Long amount,
        String paymentMethod,
        LocalDateTime paidAt,
        String portOneTxId,
        String failReason,
        String pgCode,
        String pgMessage
) {}
