package com.rocketcrew.pocat.domain.payment.client.out.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BillingPayRawResponse(
        String pgTxId,
        String paidAt
) {}