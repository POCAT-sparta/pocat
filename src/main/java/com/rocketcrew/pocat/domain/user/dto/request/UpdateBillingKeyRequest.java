package com.rocketcrew.pocat.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateBillingKeyRequest(@NotBlank String billingKey) {}
